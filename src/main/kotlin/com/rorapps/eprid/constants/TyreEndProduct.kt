package com.rorapps.eprid.constants

import java.math.BigDecimal

/**
 * Tyre EPR certificate-generation formula (Module D) — QEPR = QP × CF × WP, read directly from
 * CPCB's "Guidance Document for Generation and Transfer of EPR Certificate/Credit for Waste Tyre
 * Management" (PRD §7.5). Supersedes the earlier 400-450 L/tonne TPO-yield-ratio benchmark, which
 * was an unverified secondary-source estimate.
 *
 * QP = quantity of end-product sold (recycler-declared unit for that product).
 * CF = conversion factor, WP = weightage — both fixed per end-product type below.
 * WP = 1.0 for all categories where the underlying tyre was imported, regardless of end-product.
 */
enum class TyreEndProduct(val weightage: BigDecimal, val conversionFactor: BigDecimal) {
    CRUMB_RUBBER(BigDecimal("1.0"), BigDecimal("1.333")),
    RECLAIMED_RUBBER(BigDecimal("1.3"), BigDecimal("1.298")),
    CRMB(BigDecimal("1.1"), BigDecimal("0.2")),
    RECOVERED_CARBON_BLACK(BigDecimal("1.25"), BigDecimal("3.676")),
    PYROLYSIS_OIL_CONTINUOUS(BigDecimal("0.8"), BigDecimal("1.49")),
    CHAR_BATCH(BigDecimal("0.5"), BigDecimal("1.49"))
}

object TyreEprReconciliation {
    /** WP is fixed at 1.0 for any end-product when the underlying tyre was imported. */
    val IMPORTED_TYRE_WEIGHTAGE: BigDecimal = BigDecimal("1.0")

    /** Deviation tolerance between claimed and CF×WP-computed EPR credit. Not CPCB-specified —
     *  a provisional default until real pilot cases exist to calibrate against. */
    val PASS_TOLERANCE_PCT = BigDecimal("5")
    val WARN_TOLERANCE_PCT = BigDecimal("20")
}
