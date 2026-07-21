package com.rorapps.eprid.service

import com.rorapps.eprid.constants.BatteryChemistry
import com.rorapps.eprid.constants.CompositeScoreWeights
import com.rorapps.eprid.constants.CompositionCheckResult
import com.rorapps.eprid.constants.CredentialCheckResult
import com.rorapps.eprid.constants.EvidenceType
import com.rorapps.eprid.constants.WasteStreamType
import com.rorapps.eprid.entity.CapacitySource
import com.rorapps.eprid.entity.ForensicsStatus
import com.rorapps.eprid.entity.SubCheckStatus
import com.rorapps.eprid.entity.VerificationCheck
import com.rorapps.eprid.repository.CpcbRecyclerAuthorizationRepository
import com.rorapps.eprid.repository.EvidenceRepository
import com.rorapps.eprid.repository.MetalCompositionCheckRepository
import com.rorapps.eprid.repository.PlausibilityCheckRepository
import com.rorapps.eprid.repository.RecyclerCredentialCheckRepository
import com.rorapps.eprid.repository.RegulatoryFindingRepository
import com.rorapps.eprid.repository.VerificationCheckRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

/**
 * Composite risk scoring and hard-disqualification (PRD §7.1a). Recomputed — not computed once —
 * because its five signals arrive at different times: capacity plausibility is available
 * immediately at check creation, document forensics only after evidence upload, regulatory
 * history only after that async research completes. Each recompute call re-derives every
 * sub-score from current data rather than patching one field, so an out-of-order or repeated
 * call is always safe.
 *
 * Only BATTERY and TYRE checks are scored — Module E (used-oil) has no Module A-style pipeline.
 *
 * Five of PRD §7.1a's six hard-disqualification rules are wired up (feature_spec_close_scoring_gaps.md
 * §2 closed rules 1-3): capacity ratio, active NGT/suspension finding, registration expired as of
 * the certificate date (rule 1 — only evaluates once [Recycler.cpcbRecyclerId] is linked), declared
 * chemistry vs CPCB-authorized category mismatch (rule 2 — same linking prerequisite), and a
 * composition-table 0%-cell violation (rule 3). Tyre geo-hotspot (rule 4) stays unwired — the
 * hotspot table itself is only partially corroborated (PRD §7.5), so building a hard-disqualification
 * rule on an unverified input table is backwards; see feature_spec_close_scoring_gaps.md §2.
 */
@Service
class CompositeScoringService(
    private val checkRepository: VerificationCheckRepository,
    private val plausibilityRepository: PlausibilityCheckRepository,
    private val evidenceRepository: EvidenceRepository,
    private val credentialCheckRepository: RecyclerCredentialCheckRepository,
    private val regulatoryFindingRepository: RegulatoryFindingRepository,
    private val compositionCheckRepository: MetalCompositionCheckRepository,
    private val cpcbRecyclerAuthorizationRepository: CpcbRecyclerAuthorizationRepository,
    private val registrationValidityAtDateService: RegistrationValidityAtDateService
) {

    @Transactional
    fun recomputeAndSave(check: VerificationCheck): VerificationCheck {
        if (check.wasteStream != WasteStreamType.BATTERY && check.wasteStream != WasteStreamType.TYRE) {
            return check
        }

        val registrationScore = registrationSubScore(check)
        val capacityScore     = capacitySubScore(check)
        val invoiceScore      = invoiceSubScore(check)
        val forensicsScore    = forensicsSubScore(check)
        val regulatoryScore   = regulatorySubScore(check)

        val hardDisqualification = checkHardDisqualification(check)

        val weights = if (check.wasteStream == WasteStreamType.TYRE)
            CompositeScoreWeights.TYRE else CompositeScoreWeights.BATTERY

        val composite = if (hardDisqualification != null) {
            100
        } else {
            val weightedSum =
                registrationScore * weights.registration +
                capacityScore     * weights.capacity +
                invoiceScore      * weights.invoice +
                forensicsScore    * weights.forensics +
                regulatoryScore   * weights.regulatory
            val totalWeight = weights.registration + weights.capacity + weights.invoice +
                weights.forensics + weights.regulatory
            weightedSum / totalWeight
        }

        val band = CompositeScoreWeights.bandFor(composite)
        val riskSummary = if (hardDisqualification != null)
            "${band.reportLanguage} Hard-disqualified: $hardDisqualification"
        else
            band.reportLanguage

        val updated = check.copy(
            compositeScore = composite,
            registrationSubScore = registrationScore,
            capacitySubScore = capacityScore,
            invoiceSubScore = invoiceScore,
            forensicsSubScore = forensicsScore,
            regulatorySubScore = regulatoryScore,
            hardDisqualified = hardDisqualification != null,
            hardDisqualificationReason = hardDisqualification,
            riskRating = band.rating,
            riskSummary = riskSummary
        )
        return checkRepository.save(updated)
    }

    // ─── Sub-scores (each 0-100 *risk*, not health — 0 = clean, 100 = red flag; ─────────────
    //     NEUTRAL_SUB_SCORE when the signal hasn't run yet)

    private fun registrationSubScore(check: VerificationCheck): Int {
        val checks = credentialCheckRepository.findAllByRecyclerIdOrderByCheckedAtDesc(check.recycler.id!!)
        if (checks.isEmpty()) return CompositeScoreWeights.NEUTRAL_SUB_SCORE
        return when {
            checks.any { it.result == CredentialCheckResult.FAIL } -> 100
            checks.all { it.result == CredentialCheckResult.PASS } -> 0
            else -> CompositeScoreWeights.NEUTRAL_SUB_SCORE
        }
    }

    /** Folds the composition-table check (§1) into capacitySubScore rather than adding a 6th signal
     *  (feature_spec_close_scoring_gaps.md §1: "extends the existing sub-score, not a new 6th
     *  signal"). Takes the worse of the two, since either one flagging a problem is a real signal. */
    private fun capacitySubScore(check: VerificationCheck): Int {
        val plausibility = plausibilityRepository.findByCheckId(check.id!!)
        val plausibilityScore = when (plausibility?.overallStatus) {
            SubCheckStatus.PASS -> 0
            SubCheckStatus.WARN -> 40
            SubCheckStatus.FAIL -> 100
            SubCheckStatus.UNVERIFIABLE, null -> CompositeScoreWeights.NEUTRAL_SUB_SCORE
        }

        val compositionResults = compositionCheckRepository.findAllByCheckId(check.id)
        if (compositionResults.isEmpty()) return plausibilityScore

        val compositionScore = when {
            compositionResults.any { it.result == CompositionCheckResult.ZERO_CELL_VIOLATION || it.result == CompositionCheckResult.FAIL } -> 100
            compositionResults.any { it.result == CompositionCheckResult.COULD_NOT_VERIFY } -> CompositeScoreWeights.NEUTRAL_SUB_SCORE
            else -> 0
        }

        return maxOf(plausibilityScore, compositionScore)
    }

    private fun invoiceSubScore(check: VerificationCheck): Int {
        val invoices = evidenceRepository.findAllByCheckId(check.id!!)
            .filter { it.evidenceType == EvidenceType.INVOICE }
        if (invoices.isEmpty()) return CompositeScoreWeights.NEUTRAL_SUB_SCORE
        return when {
            invoices.any { it.forensicsStatus == ForensicsStatus.FAIL } -> 100
            invoices.all { it.forensicsStatus == ForensicsStatus.PASS } -> 0
            else -> CompositeScoreWeights.NEUTRAL_SUB_SCORE
        }
    }

    private fun forensicsSubScore(check: VerificationCheck): Int {
        val evidence = evidenceRepository.findAllByCheckId(check.id!!)
        if (evidence.isEmpty()) return CompositeScoreWeights.NEUTRAL_SUB_SCORE
        return when {
            evidence.any { it.forensicsStatus == ForensicsStatus.FAIL } -> 100
            evidence.all { it.forensicsStatus == ForensicsStatus.PASS } -> 0
            else -> CompositeScoreWeights.NEUTRAL_SUB_SCORE
        }
    }

    private fun regulatorySubScore(check: VerificationCheck): Int = when (check.regulatoryRisk) {
        "LOW" -> 0
        "MEDIUM" -> 40
        "HIGH" -> 100
        else -> CompositeScoreWeights.NEUTRAL_SUB_SCORE // null | "UNKNOWN" | not yet run
    }

    // ─── Hard-disqualification (PRD §7.1a) — only the rules whose signals actually exist ─────

    /** Collects every rule that trips rather than stopping at the first — a check can fail rule 1
     *  and rule 3 simultaneously (feature_spec_close_scoring_gaps.md §6, schema-note open item).
     *  Rather than migrating [VerificationCheck.hardDisqualificationReason] off its single-TEXT-column
     *  shape, multiple reasons are joined into that one string — cheaper than a list table, and the
     *  column is already unbounded TEXT so nothing is lost. */
    private fun checkHardDisqualification(check: VerificationCheck): String? {
        val reasons = mutableListOf<String>()

        // Only evaluates once the capacity ceiling was benchmarked against the CPCB-registered
        // figure (not self-reported) — a self-reported number is gameable, so it must never alone
        // hard-disqualify. Mirrors rule 1/2's "only evaluate against the verified source if
        // linked, otherwise UNKNOWN" pattern (RegistrationValidityAtDateService).
        val plausibility = plausibilityRepository.findByCheckId(check.id!!)
        if (plausibility?.batchToCapacityRatio != null &&
            plausibility.capacitySource == CapacitySource.CPCB_VERIFIED &&
            plausibility.batchToCapacityRatio > BigDecimal("3.0")
        ) {
            reasons += "Certificate volume implies over 3x the recycler's CPCB-registered capacity — " +
                "mathematically impossible given registered capacity."
        }

        val activeEnforcement = regulatoryFindingRepository.findAllByCheckId(check.id)
            .any { it.severity == "HIGH" && (it.findingType == "SUSPENSION" || it.findingType == "COURT_ORDER") }
        if (activeEnforcement) {
            reasons += "Active NGT closure order or show-cause notice found against this recycler."
        }

        // Rule 3: composition-table 0%-cell violation (§1)
        val zeroCellViolations = compositionCheckRepository.findAllByCheckId(check.id)
            .filter { it.result == CompositionCheckResult.ZERO_CELL_VIOLATION }
        if (zeroCellViolations.isNotEmpty()) {
            reasons += "Chemistry-impossible metal claim: " +
                zeroCellViolations.joinToString("; ") { it.detail }
        }

        // Rule 1: registration expired as of the certificate date — only evaluable once the
        // recycler is linked to its CPCB directory row (CpcbRecyclerLinkService).
        val asOfDate = check.certificateDate ?: check.processingDate
        if (registrationValidityAtDateService.check(check.recycler.id!!, asOfDate) == RegistrationValidity.EXPIRED) {
            reasons += "Recycler's CPCB registration (consent/HWM/DIC authorization) had expired as of " +
                "$asOfDate, the certificate's effective date."
        }

        // Rule 2: declared chemistry doesn't match the recycler's CPCB-authorized category —
        // same linking prerequisite as rule 1.
        val cpcbRecyclerId = check.recycler.cpcbRecyclerId
        val declaredChemistry = check.declaredBatteryChemistry
        if (cpcbRecyclerId != null && declaredChemistry != null) {
            val authorizations = cpcbRecyclerAuthorizationRepository.findAllByRecyclerId(cpcbRecyclerId)
            if (authorizations.isNotEmpty() &&
                authorizations.none { it.categoryLabel.contains(CHEMISTRY_KEYWORDS.getValue(declaredChemistry), ignoreCase = true) }
            ) {
                reasons += "Declared chemistry (${declaredChemistry.label}) does not match this recycler's " +
                    "CPCB-authorized categories: " + authorizations.joinToString("; ") { it.categoryLabel }
            }
        }

        return reasons.takeIf { it.isNotEmpty() }?.joinToString(" | ")
    }

    companion object {
        /** Substring used to match a declared chemistry against CpcbRecyclerAuthorization's free-text
         *  categoryLabel (e.g. "R1: Lead Acid Battery Recycler") — the CPCB feed has no structured
         *  chemistry code, only this parsed label text. */
        private val CHEMISTRY_KEYWORDS = mapOf(
            BatteryChemistry.LEAD_ACID to "lead acid",
            BatteryChemistry.LITHIUM_ION to "lithium",
            BatteryChemistry.ZINC_BASED to "zinc",
            BatteryChemistry.NICKEL_CADMIUM to "nickel"
        )
    }
}
