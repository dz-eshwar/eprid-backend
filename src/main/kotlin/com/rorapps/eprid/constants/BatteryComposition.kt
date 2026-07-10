package com.rorapps.eprid.constants

import java.math.BigDecimal

/** Chemistry declared by the recycler for a battery check (PRD §7.1). */
enum class BatteryChemistry(val label: String) {
    LEAD_ACID("Lead Acid"),
    LITHIUM_ION("Lithium Ion"),
    ZINC_BASED("Zinc-based"),
    NICKEL_CADMIUM("Nickel-Cadmium")
}

enum class BatteryMetal { PB, LI, MN, ZN, NI, CO, CD, AL, FE, CU }

/** Result of comparing one claimed metal's recovered % against [BatteryCompositionRanges]. */
enum class CompositionCheckResult {
    PASS,
    FAIL,
    /** Claimed weight > 0 for a metal whose max range is 0% for this chemistry — chemistry-impossible. */
    ZERO_CELL_VIOLATION,
    /** No claimed weight submitted for a metal the chemistry table expects — explicit non-silent-pass (PRD §7.1, line 123). */
    COULD_NOT_VERIFY
}

data class MetalRange(val min: BigDecimal, val max: BigDecimal)

/**
 * Per-metal composition ranges by battery chemistry, sourced directly from CPCB's "Mechanism for
 * Generation of EPR Certificates" doc (PRD §7.1). Coarse-grained only — Li-ion sub-chemistry
 * breakdown (NCA/LMO/NMC/LCO/LFP) exists in CPCB's source but is deliberately out of this pass
 * (feature_spec_close_scoring_gaps.md §1).
 */
object BatteryCompositionRanges {
    private fun r(min: String, max: String) = MetalRange(BigDecimal(min), BigDecimal(max))
    private fun zero() = r("0", "0")

    val TABLE: Map<BatteryChemistry, Map<BatteryMetal, MetalRange>> = mapOf(
        BatteryChemistry.LEAD_ACID to mapOf(
            BatteryMetal.PB to r("60", "80"),
            BatteryMetal.LI to zero(),
            BatteryMetal.MN to zero(),
            BatteryMetal.ZN to zero(),
            BatteryMetal.NI to zero(),
            BatteryMetal.CO to zero(),
            BatteryMetal.CD to zero(),
            BatteryMetal.AL to r("0", "3"),
            BatteryMetal.FE to r("0", "3"),
            BatteryMetal.CU to r("0", "3")
        ),
        BatteryChemistry.LITHIUM_ION to mapOf(
            BatteryMetal.PB to zero(),
            BatteryMetal.LI to r("1", "5"),
            BatteryMetal.MN to r("0", "15"),
            BatteryMetal.ZN to r("0", "1"),
            BatteryMetal.NI to r("0", "15"),
            BatteryMetal.CO to r("0", "20"),
            BatteryMetal.CD to zero(),
            BatteryMetal.AL to r("5", "25"),
            BatteryMetal.FE to r("1", "46"),
            BatteryMetal.CU to r("2", "18")
        ),
        BatteryChemistry.ZINC_BASED to mapOf(
            BatteryMetal.PB to zero(),
            BatteryMetal.LI to zero(),
            BatteryMetal.MN to r("15", "30"),
            BatteryMetal.ZN to r("12", "40"),
            BatteryMetal.NI to zero(),
            BatteryMetal.CO to zero(),
            BatteryMetal.CD to zero(),
            BatteryMetal.AL to r("0", "20"),
            BatteryMetal.FE to r("15", "40"),
            BatteryMetal.CU to r("0", "2")
        ),
        BatteryChemistry.NICKEL_CADMIUM to mapOf(
            BatteryMetal.PB to zero(),
            BatteryMetal.LI to zero(),
            BatteryMetal.MN to zero(),
            BatteryMetal.ZN to zero(),
            BatteryMetal.NI to r("18", "30"),
            BatteryMetal.CO to zero(),
            BatteryMetal.CD to r("10", "20"),
            BatteryMetal.AL to r("0", "2"),
            BatteryMetal.FE to r("25", "35"),
            BatteryMetal.CU to r("0", "2")
        )
    )

    /** Every metal the table tracks for this chemistry — used to detect metals with no claimed weight at all. */
    fun trackedMetals(chemistry: BatteryChemistry): Set<BatteryMetal> = TABLE.getValue(chemistry).keys
}
