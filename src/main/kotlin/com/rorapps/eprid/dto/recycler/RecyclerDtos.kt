package com.rorapps.eprid.dto.recycler

import java.math.BigDecimal

data class RecyclerProfileResponse(
    val id: String,
    val name: String,
    val bwmrRegNumber: String?,
    val selfReportedCapacityT: BigDecimal?,
    val state: String?
)
