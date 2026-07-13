package com.rorapps.eprid.dto.calculator

import com.rorapps.eprid.constants.BatteryCategory
import java.math.BigDecimal

data class ComplianceEstimateResponse(
    /** Null when [applicable] is false — nothing is persisted for a not-yet-applicable category/FY. */
    val estimateId: String?,
    val batteryCategory: BatteryCategory,
    val financialYear: String,
    val quantityPlacedTonnes: BigDecimal,
    val quantityAlreadyFulfilledTonnes: BigDecimal,

    /** False when this category's Schedule II collection-target cycle hasn't started as of
     *  [financialYear] yet (see RecoveryTargets.scheduleStartFy) — e.g. EV four-wheeler doesn't apply
     *  to any FY this app currently supports. When false, every field below except [notApplicableReason]
     *  is null: there is no target to compute, and returning a fabricated 0%/shortfall would be worse
     *  than saying so plainly. */
    val applicable: Boolean,
    val notApplicableReason: String? = null,

    val recoveryTargetPercent: Int? = null,
    /** The FY whose "quantity placed in market" Schedule II's % nominally applies to — see
     *  [com.rorapps.eprid.dto.calculator.ComplianceEstimateRequest]'s quantityPlacedTonnes doc. */
    val referenceFinancialYear: String? = null,
    val targetTonnes: BigDecimal? = null,
    val shortfallTonnes: BigDecimal? = null,
    val shortfallKg: BigDecimal? = null,

    /** Length of this category's compliance cycle (7/10/14 years) — both the 100%-recycling
     *  obligation and the carry-forward cap are measured against this window, not annually. */
    val complianceCycleYears: Int? = null,
    val recyclingRefurbishmentObligation: RecyclingObligation? = null,
    val carryForwardCapPercent: Int? = null,
    val carryForwardBasisNote: String? = null,

    /** Rule 4(14) minimum recycled-material-content — informational only, not folded into
     *  [shortfallTonnes]/[EcExposureBreakdown]. See RecycledContentMinimums.kt for why. */
    val recycledContentObligation: RecycledContentObligation? = null,

    val ecExposure: EcExposureBreakdown?,
    val disclaimer: String,
    val callToAction: CallToAction
)

/** Schedule II's second obligation: 100% of whatever was collected must be refurbished or recycled by
 *  the end of each multi-year compliance cycle — not computed as a tonnage here (needs cumulative
 *  multi-year collected/placed data this calculator doesn't track), surfaced as fixed guidance instead. */
data class RecyclingObligation(
    val percent: Int = 100,
    val cycleYears: Int,
    val note: String
)

/** Rule 4(14) — manufacturing-input minimum, different obligation entirely from the collection target
 *  above (different quantity base: dry weight of battery *manufactured*, not *collected*). */
data class RecycledContentObligation(
    val startsFinancialYear: String,
    val applicableNow: Boolean,
    val ramp: List<RecycledContentRampEntry>,
    val note: String
)

data class RecycledContentRampEntry(
    val financialYear: String,
    val minimumPercent: Int
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
