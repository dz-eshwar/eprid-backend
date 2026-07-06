package com.rorapps.eprid.service.plausibility

import com.rorapps.eprid.constants.TyreBenchmarks
import com.rorapps.eprid.constants.WasteStreamType
import com.rorapps.eprid.dto.plausibility.PlausibilitySubCheck
import com.rorapps.eprid.entity.SubCheckStatus
import com.rorapps.eprid.entity.VerificationCheck
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Tyre/TPO plausibility logic (Module D). Slot 2 (capacity ceiling) and slot 3 (absolute batch
 * size) reuse the generic checks unchanged — only the yield check (slot 1) is tyre-specific,
 * per the PRD's "what's new" scope for this module.
 */
@Component
class TyrePlausibilityStrategy : PlausibilityStrategy {

    override fun supports(wasteStream: WasteStreamType) = wasteStream == WasteStreamType.TYRE

    override fun runChecks(check: VerificationCheck): List<PlausibilitySubCheck> {
        val yieldCheck = checkTpoYield(check.claimedOutputQuantity, check.batchWeightTonnes)
        val capacity   = checkCapacityCeiling(check.batchWeightTonnes, check.recycler.selfReportedCapacityT)
        val batchSize  = checkAbsoluteBatchSize(check.batchWeightTonnes, check.recycler.selfReportedCapacityT)
        return listOf(yieldCheck, capacity, batchSize)
    }

    // ─── Sub-check: TPO yield plausibility ────────────────────────────────────

    private fun checkTpoYield(outputLitres: BigDecimal?, batchWeightT: BigDecimal): PlausibilitySubCheck {
        val b = TyreBenchmarks

        if (outputLitres == null) {
            return PlausibilitySubCheck(
                name = "TPO yield plausibility",
                status = SubCheckStatus.UNVERIFIABLE,
                detail = "No claimed TPO (Tyre Pyrolysis Oil) output was provided — cannot assess yield plausibility.",
                referenceValue = "Expected range: ${b.TPO_YIELD_MIN_L_PER_T}–${b.TPO_YIELD_MAX_L_PER_T} L/tonne"
            )
        }

        val yieldPerTonne = outputLitres.divide(batchWeightT, 2, RoundingMode.HALF_UP)
        val warnCeiling = b.TPO_YIELD_MAX_L_PER_T.multiply(b.TPO_YIELD_WARN_MULTIPLIER)

        return when {
            yieldPerTonne > warnCeiling -> PlausibilitySubCheck(
                name = "TPO yield plausibility",
                status = SubCheckStatus.FAIL,
                detail = "Claimed yield of ${yieldPerTonne} L/tonne far exceeds the physically plausible ceiling " +
                         "of ${b.TPO_YIELD_MAX_L_PER_T} L/tonne for tyre pyrolysis oil.",
                referenceValue = "Physical ceiling: ~${b.TPO_YIELD_MAX_L_PER_T} L/tonne"
            )
            yieldPerTonne > b.TPO_YIELD_MAX_L_PER_T -> PlausibilitySubCheck(
                name = "TPO yield plausibility",
                status = SubCheckStatus.WARN,
                detail = "Claimed yield of ${yieldPerTonne} L/tonne is above the typical ${b.TPO_YIELD_MIN_L_PER_T}–" +
                         "${b.TPO_YIELD_MAX_L_PER_T} L/tonne range — high but not yet implausible; verify process details.",
                referenceValue = "Typical range: ${b.TPO_YIELD_MIN_L_PER_T}–${b.TPO_YIELD_MAX_L_PER_T} L/tonne"
            )
            yieldPerTonne < b.TPO_YIELD_MIN_L_PER_T -> PlausibilitySubCheck(
                name = "TPO yield plausibility",
                status = SubCheckStatus.WARN,
                detail = "Claimed yield of ${yieldPerTonne} L/tonne is below the typical ${b.TPO_YIELD_MIN_L_PER_T} L/tonne " +
                         "floor — not physically impossible, but may indicate under-reporting or a non-pyrolysis process.",
                referenceValue = "Typical floor: ${b.TPO_YIELD_MIN_L_PER_T} L/tonne"
            )
            else -> PlausibilitySubCheck(
                name = "TPO yield plausibility",
                status = SubCheckStatus.PASS,
                detail = "Claimed yield of ${yieldPerTonne} L/tonne is within the typical " +
                         "${b.TPO_YIELD_MIN_L_PER_T}–${b.TPO_YIELD_MAX_L_PER_T} L/tonne range for tyre pyrolysis oil.",
                referenceValue = "Typical range: ${b.TPO_YIELD_MIN_L_PER_T}–${b.TPO_YIELD_MAX_L_PER_T} L/tonne"
            )
        }
    }
}
