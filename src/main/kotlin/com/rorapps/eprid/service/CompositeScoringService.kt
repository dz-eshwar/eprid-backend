package com.rorapps.eprid.service

import com.rorapps.eprid.constants.CompositeScoreWeights
import com.rorapps.eprid.constants.CredentialCheckResult
import com.rorapps.eprid.constants.EvidenceType
import com.rorapps.eprid.constants.WasteStreamType
import com.rorapps.eprid.entity.ForensicsStatus
import com.rorapps.eprid.entity.SubCheckStatus
import com.rorapps.eprid.entity.VerificationCheck
import com.rorapps.eprid.repository.EvidenceRepository
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
 * Two of PRD §7.1a's hard-disqualification rules are wired up because their underlying signals
 * exist today (capacity ratio, active NGT/suspension finding). The rest — CPCB registration
 * expiry, composition-range chemistry violations, tyre geographic hotspots — aren't computed
 * because nothing in this codebase tracks registration expiry, per-metal composition, or the
 * hotspot table yet. Wiring those rules here would silently fabricate a check that isn't
 * actually being run.
 */
@Service
class CompositeScoringService(
    private val checkRepository: VerificationCheckRepository,
    private val plausibilityRepository: PlausibilityCheckRepository,
    private val evidenceRepository: EvidenceRepository,
    private val credentialCheckRepository: RecyclerCredentialCheckRepository,
    private val regulatoryFindingRepository: RegulatoryFindingRepository
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

    private fun capacitySubScore(check: VerificationCheck): Int {
        val plausibility = plausibilityRepository.findByCheckId(check.id!!)
            ?: return CompositeScoreWeights.NEUTRAL_SUB_SCORE
        return when (plausibility.overallStatus) {
            SubCheckStatus.PASS -> 0
            SubCheckStatus.WARN -> 40
            SubCheckStatus.FAIL -> 100
            SubCheckStatus.UNVERIFIABLE -> CompositeScoreWeights.NEUTRAL_SUB_SCORE
        }
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

    private fun checkHardDisqualification(check: VerificationCheck): String? {
        val plausibility = plausibilityRepository.findByCheckId(check.id!!)
        if (plausibility?.batchToCapacityRatio != null &&
            plausibility.batchToCapacityRatio > BigDecimal("3.0")
        ) {
            return "Certificate volume implies over 3x the recycler's registered capacity — " +
                "mathematically impossible given registered capacity."
        }

        val activeEnforcement = regulatoryFindingRepository.findAllByCheckId(check.id)
            .any { it.severity == "HIGH" && (it.findingType == "SUSPENSION" || it.findingType == "COURT_ORDER") }
        if (activeEnforcement) {
            return "Active NGT closure order or show-cause notice found against this recycler."
        }

        return null
    }
}
