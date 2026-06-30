package com.rorapps.eprid.constants

import java.math.BigDecimal

/**
 * Schedule II recovery targets from BWMR 2022, Rule 10(4).
 * Source: gazette notification. Must be re-verified against latest CPCB amendment before each FY rollover.
 * Last verified: June 2026 against PRD appendix (original gazette + 2025 amendment scope not fully line-verified).
 */
object RecoveryTargets {

    private val targets: Map<Pair<BatteryCategory, FinancialYear>, BigDecimal> = mapOf(
        // Portable
        (BatteryCategory.PORTABLE to FinancialYear.FY_2024_25) to BigDecimal("0.70"),
        (BatteryCategory.PORTABLE to FinancialYear.FY_2025_26) to BigDecimal("0.80"),
        (BatteryCategory.PORTABLE to FinancialYear.FY_2026_27) to BigDecimal("0.90"),

        // Automotive
        (BatteryCategory.AUTOMOTIVE to FinancialYear.FY_2024_25) to BigDecimal("0.55"),
        (BatteryCategory.AUTOMOTIVE to FinancialYear.FY_2025_26) to BigDecimal("0.60"),
        (BatteryCategory.AUTOMOTIVE to FinancialYear.FY_2026_27) to BigDecimal("0.60"),

        // Industrial
        (BatteryCategory.INDUSTRIAL to FinancialYear.FY_2024_25) to BigDecimal("0.55"),
        (BatteryCategory.INDUSTRIAL to FinancialYear.FY_2025_26) to BigDecimal("0.60"),
        (BatteryCategory.INDUSTRIAL to FinancialYear.FY_2026_27) to BigDecimal("0.60"),

        // Electric Vehicle
        (BatteryCategory.ELECTRIC_VEHICLE to FinancialYear.FY_2024_25) to BigDecimal("0.70"),
        (BatteryCategory.ELECTRIC_VEHICLE to FinancialYear.FY_2025_26) to BigDecimal("0.80"),
        (BatteryCategory.ELECTRIC_VEHICLE to FinancialYear.FY_2026_27) to BigDecimal("0.90"),
    )

    fun getTarget(category: BatteryCategory, fy: FinancialYear): BigDecimal =
        targets[category to fy]
            ?: throw IllegalArgumentException("No recovery target defined for $category in $fy")

    fun getTargetPercent(category: BatteryCategory, fy: FinancialYear): Int =
        (getTarget(category, fy) * BigDecimal("100")).toInt()
}
