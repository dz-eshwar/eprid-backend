package com.rorapps.eprid.dto.calculator

import com.rorapps.eprid.constants.BatteryCategory
import java.math.BigDecimal

data class ComplianceEstimateResponse(
    val estimateId: String,
    val batteryCategory: BatteryCategory,
    val financialYear: String,
    val quantityPlacedTonnes: BigDecimal,
    val quantityAlreadyFulfilledTonnes: BigDecimal,
    val recoveryTargetPercent: Int,
    val targetTonnes: BigDecimal,
    val shortfallTonnes: BigDecimal,
    val shortfallKg: BigDecimal,
    val ecExposure: EcExposureBreakdown?,
    val disclaimer: String,
    val callToAction: CallToAction
)

/** EC exposure estimate — time-varying; shows how liability changes if unresolved across years.
 *  Source: CPCB EC Guidelines Aug 2024. Full tables in EPRid_validation_tracker.xlsx row 22. */
data class EcExposureBreakdown(
    /** EC deposited at time of shortfall (before any refund) */
    val ecDepositedRs: BigDecimal,
    /** If resolved in year 1: net cost after 75% refund */
    val netIfResolvedYear1Rs: BigDecimal,
    /** If resolved in year 2: net cost after 60% refund */
    val netIfResolvedYear2Rs: BigDecimal,
    /** If resolved in year 3: net cost after 40% refund */
    val netIfResolvedYear3Rs: BigDecimal,
    /** If unresolved beyond year 3: full forfeiture, no refund */
    val netIfForfeitedRs: BigDecimal,
    /** EC rate per tonne used (₹) */
    val ecRatePerTonneRs: BigDecimal,
    val caveat: String
)

data class CallToAction(
    val message: String,
    val action: String
)
