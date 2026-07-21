package com.rorapps.eprid.dto.plausibility

import com.rorapps.eprid.entity.CapacitySource
import com.rorapps.eprid.entity.SubCheckStatus
import java.math.BigDecimal

data class PlausibilitySubCheck(
    val name: String,
    val status: SubCheckStatus,
    val detail: String,
    val referenceValue: String? = null,   // e.g. "Annual capacity (CPCB-verified): 1,200 T"
    /** Set only on the capacity-ceiling sub-check (slot 2) — null on the other two slots. */
    val capacitySource: CapacitySource? = null,
    /** The raw capacity figure the capacity-ceiling sub-check actually benchmarked against — set
     *  only on slot 2. [PlausibilityCheckService] persists this (not a value it recomputes itself)
     *  so the persisted `batchToCapacityRatio` always matches what the sub-check's own text says,
     *  regardless of whether that came from CPCB or self-reported data. */
    val effectiveCapacityT: BigDecimal? = null
)

data class PlausibilityCheckResponse(
    val checkId: String,
    val overallStatus: SubCheckStatus,
    val subChecks: List<PlausibilitySubCheck>,
    val caveat: String =
        "Benchmarks are based on industry norms and self-reported recycler data. " +
        "Live CPCB capacity registry data is not yet integrated — results are indicative, not conclusive."
)
