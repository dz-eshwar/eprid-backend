package com.rorapps.eprid.service.plausibility

import com.rorapps.eprid.constants.IndustryBenchmarks
import com.rorapps.eprid.constants.WasteStreamType
import com.rorapps.eprid.dto.plausibility.PlausibilitySubCheck
import com.rorapps.eprid.entity.SubCheckStatus
import com.rorapps.eprid.entity.VerificationCheck
import org.springframework.stereotype.Component
import java.math.BigDecimal

/** Battery plausibility logic — extracted verbatim from the original PlausibilityCheckService. */
@Component
class BatteryPlausibilityStrategy : PlausibilityStrategy {

    override fun supports(wasteStream: WasteStreamType) = wasteStream == WasteStreamType.BATTERY

    override fun runChecks(check: VerificationCheck): List<PlausibilitySubCheck> {
        val recovery  = checkRecoveryRate(check.claimedRecoveryPct)
        val capacity  = checkCapacityCeiling(check.batchWeightTonnes, check.recycler.selfReportedCapacityT)
        val batchSize = checkAbsoluteBatchSize(check.batchWeightTonnes, check.recycler.selfReportedCapacityT)
        return listOf(recovery, capacity, batchSize)
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
}
