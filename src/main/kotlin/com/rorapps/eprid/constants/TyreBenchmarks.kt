package com.rorapps.eprid.constants

import java.math.BigDecimal

/**
 * Tyre/TPO (Tyre Pyrolysis Oil) plausibility benchmarks — Module D.
 *
 * TPO yield is physically bounded: roughly 400–450 litres of oil per tonne of waste tyre
 * processed. Source: PRD §7.5, grounded in CPCB's live tyre-EPR portal research (July 2026).
 */
object TyreBenchmarks {
    /** Below this litres/tonne: unusually low — may indicate under-reporting or a non-pyrolysis process */
    val TPO_YIELD_MIN_L_PER_T = BigDecimal("400.0")
    /** Above this litres/tonne: at the top of the plausible range */
    val TPO_YIELD_MAX_L_PER_T = BigDecimal("450.0")
    /** Multiplier applied to TPO_YIELD_MAX_L_PER_T: within this band above max is WARN, beyond is FAIL */
    val TPO_YIELD_WARN_MULTIPLIER = BigDecimal("1.10")
}
