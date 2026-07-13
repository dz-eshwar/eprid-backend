package com.rorapps.eprid.constants

import java.math.BigDecimal

/**
 * Schedule II COLLECTION targets from BWMR 2022 (S.O. 3984(E), 22-Aug-2022), as amended by
 * S.O. 4669(E) (25-Oct-2023, EV three-wheeler table + one four-wheeler typo fix) and
 * G.S.R. 190(E) (14-Mar-2024, carry-forward basis unified across categories).
 *
 * REAL FIX, 2026-07-10 — replaces the 2026-07-10 stage-1 patch, which inferred 3 flat-ish values per
 * category from a secondary tracker rather than reading Schedule II's actual ramp/plateau tables.
 * This file is sourced against the primary gazette PDFs directly (S.O. 3984(E) full text, S.O. 4669(E)
 * full text, G.S.R. 190(E) full text — all fetched and read line-by-line, not summarized secondhand).
 *
 * WHAT SCHEDULE II ACTUALLY IS (read this before touching the table below):
 *
 * 1. Schedule II sets a COLLECTION target — the % below — applied not to the current FY's quantity
 *    placed in market, but to a PRIOR reference year's quantity (a 3-8 year lag depending on category,
 *    see referenceFyOffset below). [BatteryCategory] splits what used to be one PORTABLE/one
 *    ELECTRIC_VEHICLE bucket into the 7 sub-categories Schedule II actually tracks, because their
 *    ramps, plateau years, cycle lengths, and start dates all genuinely differ — see that file's header.
 * 2. Schedule II ALSO sets a separate, always-100% refurbishment/recycling obligation on whatever was
 *    actually collected, due at the END of each multi-year compliance cycle (7/10/14 years depending
 *    on category — [complianceCycleYears] below) — not annually. This is a second obligation, not
 *    folded into the % below.
 * 3. Unmet collection-target quantity can carry forward into the next compliance cycle, capped at
 *    [carryForwardCapPercent]. Pre-G.S.R.190(E), this cap's *base* was "average quantity of Battery
 *    placed in the market per year during the cycle" and Automotive alone had a lower 20% cap (vs 60%
 *    everywhere else). G.S.R. 190(E) (14-Mar-2024) rewrote every category's carry-forward clause
 *    (Schedule II clauses vi-xii) to a single unified wording: up to 60% of the *remaining* quantity
 *    of battery placed in the market *during the applicable compliance cycle* — Automotive's old 20%
 *    discount is gone as of that amendment; every category is now 60%/remaining-quantity-basis. This
 *    file reflects the CURRENT (post-amendment) rule, not the original 2022 text.
 *
 * WHAT THIS FILE DOES NOT MODEL, DELIBERATELY (see ComplianceCalculatorService for how each gap is
 * surfaced rather than silently approximated):
 *
 * - The reference-year lag (point 1): [ComplianceEstimateRequest] only collects a single FY's
 *   "quantity placed in market" — there's no producer-history data store for prior-year placed
 *   quantities. The calculator applies the rate to whatever quantity the user enters for the FY they
 *   asked about, clearly labeled as a same-year proxy for the reference year's real figure, not a
 *   silent assumption that they're the same. Building real multi-year historical tracking is a
 *   distinct, larger product change (a producer profile with placed-quantity history), out of scope
 *   for this pass.
 * - The 100%-recycling obligation (point 2) and the carry-forward cap (point 3) are exposed as
 *   informational fields on the response ([complianceCycleYears], [carryForwardCapPercent] below,
 *   plus fixed descriptive text in the service) — not computed into a tonnage, since that needs
 *   cumulative multi-year collected/placed data the calculator doesn't track for the same reason.
 * - Rule 4(14) minimum recycled-material-content (a manufacturing-input obligation, not a collection
 *   target — different quantity base entirely, "dry weight of Battery *manufactured*" not "quantity
 *   collected") is modeled separately in [RecycledContentMinimums] and surfaced informationally, not
 *   folded into this collection-target shortfall/EC-exposure calculation. Explicit scoping choice,
 *   not an oversight — see that file's header for why.
 *
 * Every category/FY cell below is one of: a real rate (Schedule II applies for that FY), or absent
 * (the category's compliance cycle hasn't started yet as of that FY — [getRamp] returns null, and
 * callers must surface "not yet applicable", never a fabricated 0% or plateau value).
 */
object RecoveryTargets {

    /**
     * One Schedule II collection-target cell.
     * @param ratePercent fraction of the reference year's quantity placed in market (e.g. 0.70 = 70%)
     * @param referenceFinancialYear the year whose "quantity placed in market" this % actually applies
     *   to (Schedule II's own wording, e.g. "minimum 70% of the quantity of Battery placed in the
     *   market in 2019-20") — NOT the FY being asked about.
     * @param complianceCycleYears length of this category's compliance cycle (7, 10, or 14) — the
     *   100%-recycling obligation and the carry-forward cap are both measured against this cycle.
     * @param carryForwardCapPercent current (post G.S.R. 190(E)) carry-forward cap, uniform 0.60
     *   across every category — see file header point 3.
     */
    data class CollectionTargetRamp(
        val ratePercent: BigDecimal,
        val referenceFinancialYear: String,
        val complianceCycleYears: Int,
        val carryForwardCapPercent: BigDecimal = BigDecimal("0.60")
    )

    private fun ramp(rate: String, refFy: String, cycleYears: Int) =
        CollectionTargetRamp(BigDecimal(rate), refFy, cycleYears)

    // ── PORTABLE_RECHARGEABLE — Schedule II clause (vi), 10-year cycle, plateaus 70% from FY2024-25.
    //    Reference-year offset is a constant -5 years across the entire table (not just at plateau).
    // ── PORTABLE_NON_RECHARGEABLE — clause (vii), 10-year cycle, plateaus 70% from FY2027-28 — a
    //    year later than rechargeable. Cycle doesn't start until FY2025-26 (offset -3 years).
    // ── AUTOMOTIVE — clause (viii), 7-year cycle, ramps 30%->50%->70%->90%, plateaus 90% from
    //    FY2025-26. Reference-year offset constant -3 years.
    // ── INDUSTRIAL — clause (ix), 7-year cycle, ramps 40%->50%->60%->70%, plateaus 70% from
    //    FY2025-26. Reference-year offset constant -3 years.
    // ── EV_THREE_WHEELER — clause (x) AS SUBSTITUTED by S.O. 4669(E) 25-Oct-2023 clause 11(b): the
    //    original 2022 text started this category's cycle at FY2024-25; the amendment moved the start
    //    to FY2026-27 (a genuine substantive change, not just a vehicle-category clarification, despite
    //    the amendment's own framing as adding "categories L5/L5-M/L5-N, E-cart" definitional detail).
    //    Flat 70%, 7-year cycle, offset -5 years.
    // ── EV_TWO_WHEELER — clause (xi), unmodified by S.O. 4669(E). Flat 70%, 7-year cycle, starts
    //    FY2026-27, offset -4 years.
    // ── EV_FOUR_WHEELER — clause (xii), 14-year cycle (the only category not on 7 or 10 years). Flat
    //    70% (one far-future cell, FY2039-40, read "80%" in the original 2022 text — S.O. 4669(E)
    //    clause 11(c) corrected Sl.No.(xi) col.(4) to 70%, confirmed a typo fix, not a real rate change).
    //    Starts FY2029-30, offset -8 years — doesn't apply to any FY this app currently supports.
    private val targets: Map<Pair<BatteryCategory, FinancialYear>, CollectionTargetRamp> = mapOf(
        (BatteryCategory.PORTABLE_RECHARGEABLE to FinancialYear.FY_2024_25) to ramp("0.70", "2019-20", 10),
        (BatteryCategory.PORTABLE_RECHARGEABLE to FinancialYear.FY_2025_26) to ramp("0.70", "2020-21", 10),
        (BatteryCategory.PORTABLE_RECHARGEABLE to FinancialYear.FY_2026_27) to ramp("0.70", "2021-22", 10),

        // PORTABLE_NON_RECHARGEABLE: cycle starts FY2025-26 — no cell for FY2024-25 (not yet applicable).
        (BatteryCategory.PORTABLE_NON_RECHARGEABLE to FinancialYear.FY_2025_26) to ramp("0.50", "2022-23", 10),
        (BatteryCategory.PORTABLE_NON_RECHARGEABLE to FinancialYear.FY_2026_27) to ramp("0.60", "2023-24", 10),

        (BatteryCategory.AUTOMOTIVE to FinancialYear.FY_2024_25) to ramp("0.70", "2021-22", 7),
        (BatteryCategory.AUTOMOTIVE to FinancialYear.FY_2025_26) to ramp("0.90", "2022-23", 7),
        (BatteryCategory.AUTOMOTIVE to FinancialYear.FY_2026_27) to ramp("0.90", "2023-24", 7),

        (BatteryCategory.INDUSTRIAL to FinancialYear.FY_2024_25) to ramp("0.60", "2021-22", 7),
        (BatteryCategory.INDUSTRIAL to FinancialYear.FY_2025_26) to ramp("0.70", "2022-23", 7),
        (BatteryCategory.INDUSTRIAL to FinancialYear.FY_2026_27) to ramp("0.70", "2023-24", 7),

        // EV_THREE_WHEELER: amended cycle starts FY2026-27 — no cells for FY2024-25/FY2025-26.
        (BatteryCategory.EV_THREE_WHEELER to FinancialYear.FY_2026_27) to ramp("0.70", "2021-22", 7),

        // EV_TWO_WHEELER: cycle starts FY2026-27 — no cells for FY2024-25/FY2025-26.
        (BatteryCategory.EV_TWO_WHEELER to FinancialYear.FY_2026_27) to ramp("0.70", "2022-23", 7)

        // EV_FOUR_WHEELER: cycle doesn't start until FY2029-30 — no cells at all for currently
        // supported FYs. Deliberately absent, not a gap: getRamp() returning null here is correct.
    )

    /** The FY this category's Schedule II compliance cycle first applies from — used to word the
     *  "not yet applicable" explanation when [getRamp] returns null for a supported FY. */
    fun scheduleStartFy(category: BatteryCategory): String = when (category) {
        BatteryCategory.PORTABLE_RECHARGEABLE -> "2022-23"
        BatteryCategory.PORTABLE_NON_RECHARGEABLE -> "2025-26"
        BatteryCategory.AUTOMOTIVE -> "2022-23"
        BatteryCategory.INDUSTRIAL -> "2022-23"
        BatteryCategory.EV_THREE_WHEELER -> "2026-27"
        BatteryCategory.EV_TWO_WHEELER -> "2026-27"
        BatteryCategory.EV_FOUR_WHEELER -> "2029-30"
    }

    /** Null means this category's Schedule II collection-target cycle has not started as of [fy] —
     *  callers MUST treat that as "not yet applicable" (see [scheduleStartFy]), never substitute a
     *  fabricated 0% or the eventual plateau value. */
    fun getRamp(category: BatteryCategory, fy: FinancialYear): CollectionTargetRamp? = targets[category to fy]
}

/**
 * Rule 4(14) minimum recycled-material-content — a manufacturing-input obligation on NEW batteries,
 * substituted by S.O. 2374(E) (20-Jun-2024). Confirmed against the primary gazette text directly
 * (the Table under rule 4, sub-rule (14) as substituted): all 4 categories share the same 4 financial
 * years (2027-28 / 2028-29 / 2029-30 / 2030-31-and-onwards), only the percentages differ by category.
 *
 * Deliberately kept separate from [RecoveryTargets] / [ComplianceCalculatorService]'s shortfall and
 * EC-exposure calculation — scoping decision, not an oversight:
 * 1. Different quantity base entirely: "total dry weight of a Battery" being *manufactured*, not
 *    "quantity placed in market" or "quantity collected". Folding it into the same shortfall/EC
 *    formula would silently conflate two unrelated obligations with different units.
 * 2. None of it applies before FY2027-28 — every FY this app currently supports (FY2024-25 through
 *    FY2026-27) predates the obligation, so for the calculator's current window the correct answer is
 *    always "not yet applicable" regardless of which category. Surfaced informationally in the
 *    response so a producer isn't left unaware it's coming, not computed into a number.
 * 3. Computing a real recycled-content shortfall would need battery material-composition data
 *    (dry-weight breakdown of what's being manufactured) that [ComplianceEstimateRequest] doesn't
 *    collect and that isn't this calculator's job to collect — that's Module A's composition-check
 *    territory (`BatteryCompositionCheckService`), a different obligation on a different entity
 *    (recyclers verifying claimed composition) than this one (producers meeting a manufacturing floor).
 */
object RecycledContentMinimums {
    data class Ramp(val ratePercent: BigDecimal, val financialYear: String)

    private val PORTABLE_AND_EV = listOf(
        Ramp(BigDecimal("0.05"), "2027-28"),
        Ramp(BigDecimal("0.10"), "2028-29"),
        Ramp(BigDecimal("0.15"), "2029-30"),
        Ramp(BigDecimal("0.20"), "2030-31 and onwards")
    )

    private val AUTOMOTIVE_AND_INDUSTRIAL = listOf(
        Ramp(BigDecimal("0.35"), "2027-28"),
        Ramp(BigDecimal("0.35"), "2028-29"),
        Ramp(BigDecimal("0.40"), "2029-30"),
        Ramp(BigDecimal("0.40"), "2030-31 and onwards")
    )

    /** Full ramp for this category — starts FY2027-28 regardless of category. */
    fun rampFor(category: BatteryCategory): List<Ramp> = when (category) {
        BatteryCategory.PORTABLE_RECHARGEABLE, BatteryCategory.PORTABLE_NON_RECHARGEABLE,
        BatteryCategory.EV_THREE_WHEELER, BatteryCategory.EV_TWO_WHEELER, BatteryCategory.EV_FOUR_WHEELER
            -> PORTABLE_AND_EV
        BatteryCategory.AUTOMOTIVE, BatteryCategory.INDUSTRIAL -> AUTOMOTIVE_AND_INDUSTRIAL
    }

    const val STARTS_FY = "2027-28"
}
