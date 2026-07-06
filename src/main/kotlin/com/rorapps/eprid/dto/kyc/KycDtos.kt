package com.rorapps.eprid.dto.kyc

import com.rorapps.eprid.constants.CredentialCheckResult
import com.rorapps.eprid.constants.CredentialCheckType
import java.time.Instant

data class CredentialCheckOutcome(
    val checkType: CredentialCheckType,
    val result: CredentialCheckResult,
    val provider: String,
    val reason: String?,
    val checkedAt: Instant = Instant.now()
)
