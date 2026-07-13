package com.rorapps.eprid.constants

import java.math.BigDecimal

/**
 * Environmental Compensation rates sourced from CPCB "Notice_EC Guidelines" PDF (Aug 2024),
 * eprbattery.cpcb.gov.in "Important Communication" section.
 * Full tables logged in EPRid_validation_tracker.xlsx, Regulatory Findings, row 22.
 *
 * Two regimes:
 *  1. Metal-wise EPR-target non-fulfilment: per-metal rate × shortfall weight
 *  2. General non-compliance (no category registration, no return, etc.): flat penalty
 *
 * Carry-forward / refund mechanic (Rule 13):
 *  - Year 1 resolution: 75% refund of EC deposited
 *  - Year 2 resolution: 60% refund
 *  - Year 3 resolution: 40% refund
 *  - Beyond year 3: full forfeiture, no refund
 *
 * Late-payment interest on EC amount:
 *  - ≤1 month late: 12% p.a.
 *  - 1–3 months late: 24% p.a.
 *  - >3 months: unit closure + EPA Section 15(1) action
 */
object EcRates {

    /** EC rate per tonne of shortfall for EPR-target non-fulfilment, by battery category.
     *  Source: CPCB EC Guidelines Aug 2024. Using the general battery EPR non-fulfilment tier — this
     *  flat per-category rate is itself a known approximation (PRODUCT_DOCUMENT.md §6): the real EC
     *  Guidelines rate is per-METAL (lead-acid ₹18/kg, lithium ₹2,400/kg, etc.), not per battery
     *  category, and this calculator has no per-metal composition input to apply that properly.
     *  Remapped 2026-07-10 onto the 7-way [BatteryCategory] split (was 4-way) — same rate carried
     *  over to both Portable sub-categories and all three EV sub-categories; the EC-rate-per-category
     *  approximation itself is unchanged, out of scope for this pass. */
    val EC_RATE_PER_TONNE: Map<BatteryCategory, BigDecimal> = mapOf(
        BatteryCategory.PORTABLE_RECHARGEABLE     to BigDecimal("50000"),  // ₹50,000 per tonne
        BatteryCategory.PORTABLE_NON_RECHARGEABLE to BigDecimal("50000"),  // ₹50,000 per tonne
        BatteryCategory.AUTOMOTIVE                to BigDecimal("40000"),  // ₹40,000 per tonne
        BatteryCategory.INDUSTRIAL                to BigDecimal("40000"),  // ₹40,000 per tonne
        BatteryCategory.EV_THREE_WHEELER          to BigDecimal("60000"), // ₹60,000 per tonne
        BatteryCategory.EV_TWO_WHEELER            to BigDecimal("60000"), // ₹60,000 per tonne
        BatteryCategory.EV_FOUR_WHEELER           to BigDecimal("60000"), // ₹60,000 per tonne
    )

    /** Refund percentage remaining after resolution in year N (1-indexed). */
    fun refundPercent(yearsElapsed: Int): BigDecimal = when {
        yearsElapsed <= 1 -> BigDecimal("0.75")
        yearsElapsed == 2 -> BigDecimal("0.60")
        yearsElapsed == 3 -> BigDecimal("0.40")
        else -> BigDecimal.ZERO
    }

    /** Annual interest rate on unpaid EC, by months overdue. */
    fun interestRate(monthsOverdue: Int): BigDecimal = when {
        monthsOverdue <= 1 -> BigDecimal("0.12")
        monthsOverdue <= 3 -> BigDecimal("0.24")
        else -> BigDecimal("0.24")  // beyond 3 months: unit closure action; 24% is the last numeric rate
    }
}
