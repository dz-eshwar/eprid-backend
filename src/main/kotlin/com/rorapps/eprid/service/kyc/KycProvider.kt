package com.rorapps.eprid.service.kyc

import com.rorapps.eprid.dto.kyc.CredentialCheckOutcome

/**
 * Recycler KYC/business-verification checks (Module A0, PRD §7.0 Phase 1).
 *
 * No vendor is chosen yet (Surepass is a candidate). [StubKycProvider] is the only
 * implementation today and always returns COULD_NOT_VERIFY (graceful degradation, same
 * convention as ClaudeApiClient's blank-API-key handling). A real provider can implement
 * this interface later with zero changes to any call site.
 *
 * [verifyGstOtp] is intentionally NOT called from AuthService.register() — GST OTP
 * verification is a two-step flow (submit GSTIN -> provider texts an OTP -> submit OTP)
 * that cannot complete inside a single synchronous registration request. The method exists
 * so the interface is complete for a future post-registration OTP endpoint.
 */
interface KycProvider {
    fun verifyGst(gstin: String, legalName: String): CredentialCheckOutcome
    fun verifyGstOtp(gstin: String, otp: String): CredentialCheckOutcome
    fun verifyUdyam(udyamNumber: String): CredentialCheckOutcome
    fun verifyMca(cinOrDin: String): CredentialCheckOutcome
    fun verifyPan(pan: String, legalName: String): CredentialCheckOutcome
}
