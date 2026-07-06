package com.rorapps.eprid.constants

import java.math.BigDecimal

enum class UsedOilTier { CA_1, CA_2 }

/**
 * Used-oil CA-1/CA-2 registration fee schedule — Module E.
 *
 * Source: CPCB's official "Guidance Document for Registration of Collection Agent-1/2",
 * read directly from eprusedoil.cpcb.gov.in (July 2026). Fee tiers set by average quantity
 * collected across FY22-23 and FY23-24. Same schedule applies to both CA-1 and CA-2.
 * Re-verify against any future CPCB amendment before relying on this in production (PRD §7.6 open item).
 */
object UsedOilFeeTiers {
    fun registrationFee(avgAnnualQuantityMt: BigDecimal): BigDecimal = when {
        avgAnnualQuantityMt > BigDecimal("10000") -> BigDecimal("10000")
        avgAnnualQuantityMt > BigDecimal("5000")  -> BigDecimal("5000")
        avgAnnualQuantityMt > BigDecimal("2000")  -> BigDecimal("2000")
        avgAnnualQuantityMt > BigDecimal("500")   -> BigDecimal("1000")
        else -> BigDecimal("500")
    }

    /** Annual processing charge is 25% of the registration fee, on top of it. */
    val ANNUAL_PROCESSING_CHARGE_PCT: BigDecimal = BigDecimal("0.25")

    fun tierLabel(fee: BigDecimal): String = when (fee) {
        BigDecimal("10000") -> "> 10,000 MT/year"
        BigDecimal("5000")  -> "5,000–10,000 MT/year"
        BigDecimal("2000")  -> "2,000–5,000 MT/year"
        BigDecimal("1000")  -> "500–2,000 MT/year"
        else -> "< 500 MT/year"
    }
}
