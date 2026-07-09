package com.rorapps.eprid.service.einvoice

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.rorapps.eprid.constants.EInvoiceIrp
import com.rorapps.eprid.constants.InvoiceOriginalityStatus
import com.rorapps.eprid.dto.einvoice.EInvoiceQrPayload
import com.rorapps.eprid.dto.einvoice.InvoiceOriginalityResult
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.SignatureException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.Base64

/**
 * Verifies a signed e-invoice QR (RS256 JWS) against the correct IRP's public key (PRD §7.1).
 *
 * Issuer identification: real e-invoice JWS headers/payloads were not confirmed against a live
 * sample at implementation time (task called this out explicitly — "confirm the actual field
 * during implementation"). Rather than guess a claim name, this reads the standard JWS "kid"
 * header when present and looks it up against stored keys; only when no kid is present does it
 * fall back to trying every trusted IRP's key. Flag to revisit once a real invoice sample lets
 * us confirm what field (if any) the IRPs actually populate.
 */
@Service
class InvoiceQrVerifier(private val keyStore: IrpPublicKeyStore) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val objectMapper = ObjectMapper().registerKotlinModule()
    private val urlDecoder = Base64.getUrlDecoder()

    fun verify(rawQr: String?): InvoiceOriginalityResult {
        if (rawQr.isNullOrBlank()) {
            return InvoiceOriginalityResult(
                status = InvoiceOriginalityStatus.NOT_APPLICABLE,
                irp = null,
                reason = "No e-invoice QR code found on this document — expected for recyclers " +
                    "below the mandatory e-invoicing turnover threshold, not itself a red flag"
            )
        }

        val parts = rawQr.trim().split(".")
        if (parts.size != 3) {
            return InvoiceOriginalityResult(
                status = InvoiceOriginalityStatus.COULD_NOT_VERIFY,
                irp = null,
                reason = "QR content is not a recognizable signed JWT (expected 3 dot-separated segments, found ${parts.size})"
            )
        }

        val kid = readKid(parts[0])

        if (kid != null) {
            val matched = keyStore.activeTrustedKeyByKeyId(kid)
            if (matched == null) {
                val expiredReason = if (keyStore.isKnownButExpiredKeyId(kid))
                    "the key on file for this kid has expired and no replacement has been fetched yet"
                else
                    "kid '$kid' does not match any currently trusted, active IRP key on file " +
                        "(could be an untrusted/unwired IRP such as Cygnet or IRIS, or a key fetch failure)"
                return InvoiceOriginalityResult(
                    status = InvoiceOriginalityStatus.COULD_NOT_VERIFY,
                    irp = null,
                    reason = "Could not verify — $expiredReason"
                )
            }
            return validateAgainst(rawQr, matched, ambiguousOnFailure = false)
        }

        // No kid header — try every trusted key. On full failure we cannot tell "tampered"
        // apart from "issued by an untrusted IRP", so fail closed rather than guess.
        val trustedKeys = keyStore.allTrustedActiveKeys()
        if (trustedKeys.isEmpty()) {
            return InvoiceOriginalityResult(
                status = InvoiceOriginalityStatus.COULD_NOT_VERIFY,
                irp = null,
                reason = "No current trusted IRP public keys are available — key fetch may have " +
                    "failed or all cached keys have expired"
            )
        }
        for (key in trustedKeys) {
            val result = validateAgainst(rawQr, key, ambiguousOnFailure = true)
            if (result.status == InvoiceOriginalityStatus.VALID) return result
        }
        return InvoiceOriginalityResult(
            status = InvoiceOriginalityStatus.COULD_NOT_VERIFY,
            irp = null,
            reason = "No kid header present and signature did not validate against any of the " +
                "${trustedKeys.size} trusted IRP keys on file — could be an untrusted/unwired IRP " +
                "(Cygnet/IRIS) or the QR content may be tampered; cannot distinguish the two without an issuer field"
        )
    }

    /**
     * @param ambiguousOnFailure true when this key was tried speculatively (no kid to pin the
     *   issuer down) — a failure here doesn't by itself mean INVALID, the caller decides after
     *   trying all candidates. false when [key] was positively identified via kid — a failure
     *   here IS a definitive INVALID (we expected exactly this key to work).
     */
    private fun validateAgainst(rawQr: String, key: TrustedKey, ambiguousOnFailure: Boolean): InvoiceOriginalityResult {
        return runCatching {
            Jwts.parserBuilder().setSigningKey(key.publicKey).build().parseClaimsJws(rawQr)
        }.fold(
            onSuccess = { jws ->
                val payload = objectMapper.convertValue(jws.body, EInvoiceQrPayload::class.java)
                InvoiceOriginalityResult(
                    status = InvoiceOriginalityStatus.VALID,
                    irp = key.irp,
                    reason = "Signature verified against ${key.irp.label}'s current public key",
                    payload = payload
                )
            },
            onFailure = { ex ->
                when {
                    ambiguousOnFailure -> InvoiceOriginalityResult(
                        status = InvoiceOriginalityStatus.COULD_NOT_VERIFY,
                        irp = null,
                        reason = "Did not validate against ${key.irp.label}'s key"
                    )
                    ex is SignatureException -> InvoiceOriginalityResult(
                        status = InvoiceOriginalityStatus.INVALID,
                        irp = key.irp,
                        reason = "Signature verification failed against ${key.irp.label}'s current key " +
                            "(kid matched, signature did not) — tampered payload or forged QR"
                    )
                    else -> InvoiceOriginalityResult(
                        status = InvoiceOriginalityStatus.COULD_NOT_VERIFY,
                        irp = null,
                        reason = "Matched kid for ${key.irp.label} but could not parse JWT claims: ${ex.message}"
                    )
                }
            }
        )
    }

    private fun readKid(headerSegment: String): String? = runCatching {
        val json = String(urlDecoder.decode(headerSegment))
        @Suppress("UNCHECKED_CAST")
        (objectMapper.readValue(json, Map::class.java) as Map<String, Any?>)["kid"] as? String
    }.onFailure { ex -> log.debug("Could not read JWS header: ${ex.message}") }.getOrNull()
}
