package com.rorapps.eprid.constants

/**
 * BWMR 2022 Schedule II tracks 7 distinct sub-categories, not 4 (S.O. 3984(E), 22-Aug-2022,
 * Schedule II clauses (vi)-(xii); (x) later substituted by S.O. 4669(E), 25-Oct-2023, clause 11(b)).
 * Portable splits into rechargeable/non-rechargeable (different plateau years); Electric Vehicle
 * splits into three distinct vehicle classes (different start years AND cycle lengths — four-wheeler
 * runs a 14-year cycle, the rest run 7). Collapsing these into one PORTABLE / one ELECTRIC_VEHICLE
 * bucket (the pre-2026-07-10 shape of this enum) silently averaged away real regulatory differences —
 * see feature request "compliance calculator real Schedule II fix" and RecoveryTargets.kt for the
 * full sourced ramp per category.
 */
enum class BatteryCategory {
    /** Schedule II clause (vi): portable Battery used in consumer electronics which are rechargeable. */
    PORTABLE_RECHARGEABLE,

    /** Schedule II clause (vii): portable Battery except those used in consumer electronics which are
     *  rechargeable — a genuinely separate ramp/plateau, not a variant of the above. */
    PORTABLE_NON_RECHARGEABLE,

    /** Schedule II clause (viii). */
    AUTOMOTIVE,

    /** Schedule II clause (ix). */
    INDUSTRIAL,

    /** Schedule II clause (x), as substituted by S.O. 4669(E) 25-Oct-2023 clause 11(b): EV Battery of
     *  three-wheelers (E-rickshaw, categories L5/L5-M/L5-N, E-cart per Central Motor Vehicle Rules 1989). */
    EV_THREE_WHEELER,

    /** Schedule II clause (xi): EV Battery of two-wheelers. */
    EV_TWO_WHEELER,

    /** Schedule II clause (xii): EV Battery of four-wheelers — the only category on a 14-year (not
     *  7-year) compliance cycle, and the last to start (FY2029-30). */
    EV_FOUR_WHEELER
}
