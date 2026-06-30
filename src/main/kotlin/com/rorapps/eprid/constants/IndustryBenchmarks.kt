package com.rorapps.eprid.constants

import java.math.BigDecimal

/**
 * Industry benchmarks for battery waste recycling under BWMR 2022.
 *
 * Sources: CPCB annual reports, MoEF EPR guidelines, industry operator data.
 * These are indicative ranges — not hard regulatory limits. Any single check
 * result here contributes to the overall risk rating but does not alone determine it.
 *
 * Must be reviewed annually against updated CPCB data.
 */
object IndustryBenchmarks {

    // ── Recovery rate (% of input weight recovered as usable material) ────────
    /** Below this: unusually low — raises operational questions */
    val RECOVERY_PCT_FLOOR = BigDecimal("40.0")
    /** Above this: very high, unusual for most battery chemistries */
    val RECOVERY_PCT_WARN  = BigDecimal("95.0")
    /** Above this: physically implausible — mass cannot be created */
    val RECOVERY_PCT_MAX   = BigDecimal("99.0")

    // ── Single batch size relative to self-reported annual capacity ───────────
    /** Above this fraction of annual capacity in one batch: suspicious */
    val BATCH_TO_CAPACITY_WARN_RATIO  = BigDecimal("0.40")
    /** Above this fraction: very suspicious */
    val BATCH_TO_CAPACITY_HIGH_RATIO  = BigDecimal("0.70")
    /** Above 100%: impossible */
    val BATCH_TO_CAPACITY_MAX_RATIO   = BigDecimal("1.00")

    // ── Absolute batch size without known capacity ────────────────────────────
    /** Typical max single-batch run for a mid-sized recycler (tonnes) */
    val BATCH_SIZE_WARN_T = BigDecimal("500.0")
    /** Above this: only plausible for very large industrial facilities */
    val BATCH_SIZE_HIGH_T = BigDecimal("2000.0")
    /** Above this: implausible for a single processing event */
    val BATCH_SIZE_MAX_T  = BigDecimal("5000.0")
}
