package com.rorapps.eprid.dto.plausibility

import com.rorapps.eprid.entity.SubCheckStatus
import java.math.BigDecimal

data class PlausibilitySubCheck(
    val name: String,
    val status: SubCheckStatus,
    val detail: String,
    val referenceValue: String? = null   // e.g. "Annual capacity: 1,200 T"
)

data class PlausibilityCheckResponse(
    val checkId: String,
    val overallStatus: SubCheckStatus,
    val subChecks: List<PlausibilitySubCheck>,
    val caveat: String =
        "Benchmarks are based on industry norms and self-reported recycler data. " +
        "Live CPCB capacity registry data is not yet integrated — results are indicative, not conclusive."
)
