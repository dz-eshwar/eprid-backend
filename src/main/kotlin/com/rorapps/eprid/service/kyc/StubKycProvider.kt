package com.rorapps.eprid.service.kyc

import com.rorapps.eprid.constants.CredentialCheckResult
import com.rorapps.eprid.constants.CredentialCheckType
import com.rorapps.eprid.dto.kyc.CredentialCheckOutcome
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.time.Instant

/**
 * Default KYC provider — always returns COULD_NOT_VERIFY (no vendor selected yet).
 * Active when `app.kyc.provider` is unset or "stub". A real provider (e.g. Surepass) can be
 * added later as a separate @Component with `@ConditionalOnProperty(..., havingValue = "surepass")`
 * and selected via config, with zero changes here or at any call site.
 */
@Component
@ConditionalOnProperty(name = ["app.kyc.provider"], havingValue = "stub", matchIfMissing = true)
class StubKycProvider(
    @Value("\${app.kyc.api-key:}") private val apiKey: String
) : KycProvider {

    private fun notConfigured(type: CredentialCheckType) = CredentialCheckOutcome(
        checkType = type,
        result = CredentialCheckResult.COULD_NOT_VERIFY,
        provider = "STUB",
        reason = "KYC provider not configured",
        checkedAt = Instant.now()
    )

    override fun verifyGst(gstin: String, legalName: String) = notConfigured(CredentialCheckType.GST_VERIFICATION)
    override fun verifyGstOtp(gstin: String, otp: String) = notConfigured(CredentialCheckType.GST_OTP_VERIFICATION)
    override fun verifyUdyam(udyamNumber: String) = notConfigured(CredentialCheckType.UDYAM_VERIFICATION)
    override fun verifyMca(cinOrDin: String) = notConfigured(CredentialCheckType.MCA_VERIFICATION)
}
