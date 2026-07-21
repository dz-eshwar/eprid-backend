package com.rorapps.eprid.service.plausibility

import com.rorapps.eprid.constants.IndustryBenchmarks
import com.rorapps.eprid.dto.plausibility.PlausibilitySubCheck
import com.rorapps.eprid.entity.CapacitySource
import com.rorapps.eprid.entity.Recycler
import com.rorapps.eprid.entity.SubCheckStatus
import com.rorapps.eprid.repository.CpcbRecyclerRepository
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Sanity checks that are generic across waste streams (annual capacity vs. batch weight; absolute
 * batch size). Shared by [BatteryPlausibilityStrategy] and [TyrePlausibilityStrategy] — not
 * battery-specific despite reusing [IndustryBenchmarks]' capacity/batch-size thresholds as an accepted
 * cross-stream approximation (PRD only calls out a tyre-specific *yield* rule, not a tyre-specific
 * capacity/batch-size rule).
 */

/**
 * Resolves which capacity figure a check should benchmark against: the linked CPCB directory
 * row's own `recycling_capacity` when [Recycler.cpcbRecyclerId] is set AND that row actually has a
 * capacity on file, otherwise the recycler's self-reported number. Never silently treats
 * self-reported data as CPCB-verified — a link with no capacity on the CPCB side still falls back
 * to self-reported, labeled as such (mirrors [com.rorapps.eprid.service.RegistrationValidityAtDateService]'s
 * "only evaluate against the verified source if linked, otherwise UNKNOWN" pattern).
 */
internal fun resolveEffectiveCapacity(
    recycler: Recycler,
    cpcbRecyclerRepository: CpcbRecyclerRepository
): Pair<BigDecimal?, CapacitySource> {
    val cpcbRecyclerId = recycler.cpcbRecyclerId
    if (cpcbRecyclerId != null) {
        val cpcbCapacity = cpcbRecyclerRepository.findById(cpcbRecyclerId).orElse(null)?.recyclingCapacity
        if (cpcbCapacity != null && cpcbCapacity > BigDecimal.ZERO) {
            return cpcbCapacity to CapacitySource.CPCB_VERIFIED
        }
    }
    return recycler.selfReportedCapacityT to CapacitySource.SELF_REPORTED
}

internal fun checkCapacityCeiling(
    batchT: BigDecimal,
    annualCapacityT: BigDecimal?,
    capacitySource: CapacitySource
): PlausibilitySubCheck {
    val sourceLabel = if (capacitySource == CapacitySource.CPCB_VERIFIED) "CPCB-verified" else "self-reported, unverified"

    if (annualCapacityT == null || annualCapacityT <= BigDecimal.ZERO) {
        return PlausibilitySubCheck(
            name = "Capacity ceiling check",
            status = SubCheckStatus.UNVERIFIABLE,
            detail = "This recycler has not disclosed their annual processing capacity. " +
                     "The batch weight cannot be benchmarked against their stated capacity.",
            referenceValue = null,
            capacitySource = capacitySource,
            effectiveCapacityT = null
        )
    }

    val ratio = batchT.divide(annualCapacityT, 4, RoundingMode.HALF_UP)
    val pct   = ratio.multiply(BigDecimal("100")).setScale(1, RoundingMode.HALF_UP)
    val b     = IndustryBenchmarks

    return when {
        ratio > b.BATCH_TO_CAPACITY_MAX_RATIO -> PlausibilitySubCheck(
            name = "Capacity ceiling check",
            status = SubCheckStatus.FAIL,
            detail = "This single batch (${batchT} T) exceeds the recycler's $sourceLabel annual capacity " +
                     "(${annualCapacityT} T) — a single processing run cannot exceed what the facility " +
                     "can handle in a full year.",
            referenceValue = "Annual capacity ($sourceLabel): ${annualCapacityT} T (batch is ${pct}% of capacity)",
            capacitySource = capacitySource,
            effectiveCapacityT = annualCapacityT
        )
        ratio > b.BATCH_TO_CAPACITY_HIGH_RATIO -> PlausibilitySubCheck(
            name = "Capacity ceiling check",
            status = SubCheckStatus.WARN,
            detail = "This batch (${batchT} T) represents ${pct}% of the recycler's $sourceLabel annual capacity " +
                     "(${annualCapacityT} T). Processing more than ${(b.BATCH_TO_CAPACITY_HIGH_RATIO * BigDecimal("100")).toInt()}% " +
                     "of annual capacity in a single batch is unusual — verify with site inspection or weighbridge logs.",
            referenceValue = "Annual capacity ($sourceLabel): ${annualCapacityT} T (batch is ${pct}% of capacity)",
            capacitySource = capacitySource,
            effectiveCapacityT = annualCapacityT
        )
        ratio > b.BATCH_TO_CAPACITY_WARN_RATIO -> PlausibilitySubCheck(
            name = "Capacity ceiling check",
            status = SubCheckStatus.WARN,
            detail = "This batch (${batchT} T) is ${pct}% of the recycler's $sourceLabel annual capacity (${annualCapacityT} T). " +
                     "This is on the higher end but within the range seen at facilities running near full utilisation.",
            referenceValue = "Annual capacity ($sourceLabel): ${annualCapacityT} T (batch is ${pct}% of capacity)",
            capacitySource = capacitySource,
            effectiveCapacityT = annualCapacityT
        )
        else -> PlausibilitySubCheck(
            name = "Capacity ceiling check",
            status = SubCheckStatus.PASS,
            detail = "Batch weight (${batchT} T) is ${pct}% of the recycler's $sourceLabel annual capacity " +
                     "(${annualCapacityT} T) — within a plausible range for a single processing run.",
            referenceValue = "Annual capacity ($sourceLabel): ${annualCapacityT} T",
            capacitySource = capacitySource,
            effectiveCapacityT = annualCapacityT
        )
    }
}

internal fun checkAbsoluteBatchSize(batchT: BigDecimal, annualCapacityT: BigDecimal?): PlausibilitySubCheck {
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
                     "which is implausible for any single recycling event registered under India's EPR rules. " +
                     "The largest certified recycling facilities in India process approximately " +
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
                     "registered recycling facility$contextNote.",
            referenceValue = "Normal range: up to ${b.BATCH_SIZE_WARN_T} T per batch"
        )
    }
}

internal fun deriveOverall(statuses: List<SubCheckStatus>): SubCheckStatus = when {
    statuses.any { it == SubCheckStatus.FAIL } -> SubCheckStatus.FAIL
    statuses.any { it == SubCheckStatus.WARN } -> SubCheckStatus.WARN
    statuses.all { it == SubCheckStatus.PASS } -> SubCheckStatus.PASS
    else -> SubCheckStatus.UNVERIFIABLE
}

internal operator fun BigDecimal.compareTo(other: BigDecimal): Int = this.compareTo(other)
internal operator fun BigDecimal.times(other: BigDecimal): BigDecimal = this.multiply(other)
