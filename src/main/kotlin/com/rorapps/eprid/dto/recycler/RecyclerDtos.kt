package com.rorapps.eprid.dto.recycler

import com.rorapps.eprid.constants.CredentialCheckResult
import com.rorapps.eprid.constants.CredentialCheckType
import java.math.BigDecimal
import java.time.Instant

data class RecyclerProfileResponse(
    val id: String,
    val name: String,
    val bwmrRegNumber: String?,
    val selfReportedCapacityT: BigDecimal?,
    val state: String?
)

data class CredentialCheckOutcomeDto(
    val checkType: CredentialCheckType,
    val result: CredentialCheckResult,
    val provider: String,
    val reason: String?,
    val checkedAt: Instant
)
