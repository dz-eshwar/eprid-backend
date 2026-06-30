package com.rorapps.eprid.service.plausibility

import com.rorapps.eprid.constants.IndustryBenchmarks
import com.rorapps.eprid.dto.plausibility.PlausibilityCheckResponse
import com.rorapps.eprid.dto.plausibility.PlausibilitySubCheck
import com.rorapps.eprid.entity.PlausibilityCheck
import com.rorapps.eprid.entity.SubCheckStatus
import com.rorapps.eprid.entity.VerificationCheck
import com.rorapps.eprid.repository.PlausibilityCheckRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode

@Service
class PlausibilityCheckService(
    private val plausibilityRepository: PlausibilityCheckRepository
) {

    @Transactional
    fun runAndSave(check: VerificationCheck): PlausibilityCheckResponse {
        val recovery  = checkRecoveryRate(check.claimedRecoveryPct)
        val capacity  = checkCapacityCeiling(check.batchWeightTonnes, check.recycler.selfReportedCapacityT)
        val batchSize = checkAbsoluteBatchSize(check.batchWeightTonnes, check.recycler.selfReportedCapacityT)

        val overall = deriveOverall(listOf(recovery.status, capacity.status, batchSize.status))

        val entity = plausibilityRepository.save(
            PlausibilityCheck(
                check = check,
                claimedRecoveryPct = check.claimedRecoveryPct,
                recoveryStatus = recovery.status,
                recoveryDetail = recovery.detail,
                recyclerAnnualCapacityT = check.recycler.selfReportedCapacityT,
                batchToCapacityRatio = capacity.referenceValue?.let {
                    // ratio is embedded in capacity sub-check; retrieve from calculation
                    check.recycler.selfReportedCapacityT?.let { cap ->
                        check.batchWeightTonnes.divide(cap, 4, RoundingMode.HALF_UP)
                    }
                },
                capacityStatus = capacity.status,
                capacityDetail = capacity.detail,
                batchWeightT = check.batchWeightTonnes,
                batchSizeStatus = batchSize.status,
                batchSizeDetail = batchSize.detail,
                overallStatus = overall
            )
        )

        return PlausibilityCheckResponse(
            checkId = check.id!!,
            overallStatus = overall,
            subChecks = listOf(recovery, capacity, batchSize)
        )
    }

    @Transactional(readOnly = true)
    fun getForCheck(checkId: String): PlausibilityCheckResponse? {
        val entity = plausibilityRepository.findByCheckId(checkId) ?: return null
        return entity.toResponse()
    }

    // ─── Sub-check: recovery rate ─────────────────────────────────────────────

    private fun checkRecoveryRate(pct: BigDecimal): PlausibilitySubCheck {
        val b = IndustryBenchmarks
        return when {
            pct > b.RECOVERY_PCT_MAX -> PlausibilitySubCheck(
                name = "Recovery rate plausibility",
                status = SubCheckStatus.FAIL,
                detail = "Claimed recovery of ${pct}% exceeds ${b.RECOVERY_PCT_MAX}% — this is physically impossible. " +
                         "Battery recycling cannot recover more material than was input.",
                referenceValue = "Industry max: ${b.RECOVERY_PCT_MAX}%"
            )
            pct > b.RECOVERY_PCT_WARN -> PlausibilitySubCheck(
                name = "Recovery rate plausibility",
                status = SubCheckStatus.WARN,
                detail = "Claimed recovery of ${pct}% is unusually high. While technically possible for " +
                         "certain chemistries under optimal conditions, rates above ${b.RECOVERY_PCT_WARN}% " +
                         "are rare and warrant additional scrutiny.",
                referenceValue = "Industry typical: 55–${b.RECOVERY_PCT_WARN}%"
            )
            pct < b.RECOVERY_PCT_FLOOR -> PlausibilitySubCheck(
                name = "Recovery rate plausibility",
                status = SubCheckStatus.WARN,
                detail = "Claimed recovery of ${pct}% is below the ${b.RECOVERY_PCT_FLOOR}% floor typically seen " +
                         "in certified battery recycling operations. This may indicate an inefficient process " +
                         "or a batch composition that is difficult to process.",
                referenceValue = "Industry floor: ${b.RECOVERY_PCT_FLOOR}%"
            )
            else -> PlausibilitySubCheck(
                name = "Recovery rate plausibility",
                status = SubCheckStatus.PASS,
                detail = "Claimed recovery of ${pct}% is within the industry norm of " +
                         "${b.RECOVERY_PCT_FLOOR}%–${b.RECOVERY_PCT_WARN}%.",
                referenceValue = "Industry range: ${b.RECOVERY_PCT_FLOOR}–${b.RECOVERY_PCT_WARN}%"
            )
        }
    }

    // ─── Sub-check: capacity ceiling ──────────────────────────────────────────

    private fun checkCapacityCeiling(batchT: BigDecimal, annualCapacityT: BigDecimal?): PlausibilitySubCheck {
        if (annualCapacityT == null || annualCapacityT <= BigDecimal.ZERO) {
            return PlausibilitySubCheck(
                name = "Capacity ceiling check",
                status = SubCheckStatus.UNVERIFIABLE,
                detail = "This recycler has not disclosed their annual processing capacity. " +
                         "The batch weight cannot be benchmarked against their stated capacity.",
                referenceValue = null
            )
        }

        val ratio = batchT.divide(annualCapacityT, 4, RoundingMode.HALF_UP)
        val pct   = ratio.multiply(BigDecimal("100")).setScale(1, RoundingMode.HALF_UP)
        val b     = IndustryBenchmarks

        return when {
            ratio > b.BATCH_TO_CAPACITY_MAX_RATIO -> PlausibilitySubCheck(
                name = "Capacity ceiling check",
                status = SubCheckStatus.FAIL,
                detail = "This single batch (${batchT} T) exceeds the recycler's self-reported annual capacity " +
                         "(${annualCapacityT} T) — a single processing run cannot exceed what the facility " +
                         "can handle in a full year.",
                referenceValue = "Annual capacity: ${annualCapacityT} T (batch is ${pct}% of capacity)"
            )
            ratio > b.BATCH_TO_CAPACITY_HIGH_RATIO -> PlausibilitySubCheck(
                name = "Capacity ceiling check",
                status = SubCheckStatus.WARN,
                detail = "This batch (${batchT} T) represents ${pct}% of the recycler's stated annual capacity " +
                         "(${annualCapacityT} T). Processing more than ${(b.BATCH_TO_CAPACITY_HIGH_RATIO * BigDecimal("100")).toInt()}% " +
                         "of annual capacity in a single batch is unusual — verify with site inspection or weighbridge logs.",
                referenceValue = "Annual capacity: ${annualCapacityT} T (batch is ${pct}% of capacity)"
            )
            ratio > b.BATCH_TO_CAPACITY_WARN_RATIO -> PlausibilitySubCheck(
                name = "Capacity ceiling check",
                status = SubCheckStatus.WARN,
                detail = "This batch (${batchT} T) is ${pct}% of the recycler's annual capacity (${annualCapacityT} T). " +
                         "This is on the higher end but within the range seen at facilities running near full utilisation.",
                referenceValue = "Annual capacity: ${annualCapacityT} T (batch is ${pct}% of capacity)"
            )
            else -> PlausibilitySubCheck(
                name = "Capacity ceiling check",
                status = SubCheckStatus.PASS,
                detail = "Batch weight (${batchT} T) is ${pct}% of the recycler's stated annual capacity " +
                         "(${annualCapacityT} T) — within a plausible range for a single processing run.",
                referenceValue = "Annual capacity: ${annualCapacityT} T"
            )
        }
    }

    // ─── Sub-check: absolute batch size ──────────────────────────────────────

    private fun checkAbsoluteBatchSize(batchT: BigDecimal, annualCapacityT: BigDecimal?): PlausibilitySubCheck {
        // If capacity check already gave a definitive result, this is supplementary
        val b = IndustryBenchmarks
        val contextNote = if (annualCapacityT != null)
            " (used as a supplementary check — capacity check above is more precise)"
        else
            " (primary size check — no self-reported capacity on file)"

        return when {
            batchT > b.BATCH_SIZE_MAX_T -> PlausibilitySubCheck(
                name = "Absolute batch size check",
                status = SubCheckStatus.FAIL,
                detail = "A single processing batch of ${batchT} T exceeds ${b.BATCH_SIZE_MAX_T} T, " +
                         "which is implausible for any single recycling event registered under BWMR 2022. " +
                         "The largest certified battery recycling facilities in India process approximately " +
                         "this volume across an entire financial year$contextNote.",
                referenceValue = "Industry single-run max: ~${b.BATCH_SIZE_MAX_T} T"
            )
            batchT > b.BATCH_SIZE_HIGH_T -> PlausibilitySubCheck(
                name = "Absolute batch size check",
                status = SubCheckStatus.WARN,
                detail = "A single batch of ${batchT} T is very large. Batches of this size are only seen at " +
                         "large industrial-scale facilities with significant physical infrastructure. " +
                         "Request supporting documentation (facility photographs, weighbridge roll logs)$contextNote.",
                referenceValue = "Large facility threshold: ${b.BATCH_SIZE_HIGH_T} T"
            )
            batchT > b.BATCH_SIZE_WARN_T -> PlausibilitySubCheck(
                name = "Absolute batch size check",
                status = SubCheckStatus.WARN,
                detail = "Batch of ${batchT} T is on the larger side for a single processing run. " +
                         "Plausible for a mid-to-large facility but worth confirming with weighbridge evidence$contextNote.",
                referenceValue = "Mid-size threshold: ${b.BATCH_SIZE_WARN_T} T"
            )
            else -> PlausibilitySubCheck(
                name = "Absolute batch size check",
                status = SubCheckStatus.PASS,
                detail = "Batch of ${batchT} T is within the normal range for a single processing event at a " +
                         "registered battery recycling facility$contextNote.",
                referenceValue = "Normal range: up to ${b.BATCH_SIZE_WARN_T} T per batch"
            )
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun deriveOverall(statuses: List<SubCheckStatus>): SubCheckStatus = when {
        statuses.any { it == SubCheckStatus.FAIL } -> SubCheckStatus.FAIL
        statuses.any { it == SubCheckStatus.WARN } -> SubCheckStatus.WARN
        statuses.all { it == SubCheckStatus.PASS } -> SubCheckStatus.PASS
        else -> SubCheckStatus.UNVERIFIABLE
    }

    private fun PlausibilityCheck.toResponse() = PlausibilityCheckResponse(
        checkId = check.id!!,
        overallStatus = overallStatus,
        subChecks = listOf(
            PlausibilitySubCheck("Recovery rate plausibility", recoveryStatus, recoveryDetail),
            PlausibilitySubCheck(
                "Capacity ceiling check", capacityStatus, capacityDetail,
                recyclerAnnualCapacityT?.let { "Annual capacity: $it T" }
            ),
            PlausibilitySubCheck("Absolute batch size check", batchSizeStatus, batchSizeDetail)
        )
    )
}

private operator fun BigDecimal.compareTo(other: BigDecimal): Int = this.compareTo(other)
private operator fun BigDecimal.times(other: BigDecimal): BigDecimal = this.multiply(other)
