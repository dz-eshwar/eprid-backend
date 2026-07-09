package com.rorapps.eprid.entity

import com.rorapps.eprid.constants.EInvoiceIrp
import jakarta.persistence.*
import java.time.Instant

/** How this key row came to exist — drives whether a fetch failure should flag it. */
enum class KeyFetchStatus { FETCHED, MANUAL_SEED, FETCH_FAILED }

/**
 * One IRP signing certificate, keyed by IRP + its own validity window (read from the
 * X.509 certificate itself, never from webpage prose — see IrpKeyFetcher).
 * Multiple rows can exist per IRP across key-rotation periods; [active] marks the one
 * currently used for verification.
 */
@Entity
@Table(name = "irp_public_keys", schema = "eprid")
data class IrpPublicKey(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val irp: EInvoiceIrp,

    /**
     * JWS "kid" header value this cert corresponds to, when known. Real e-invoice JWS
     * headers were not confirmed against a live sample at implementation time (no test
     * invoice was available) — if a QR's kid doesn't match any row here, or carries no
     * kid at all, [InvoiceQrVerifier] falls back to trying every trusted key. Defaults
     * to the certificate's own serial number (hex) when fetched, since that's always
     * derivable without guessing a JWT claim name.
     */
    val keyId: String?,

    @Column(nullable = false, columnDefinition = "TEXT")
    val certPem: String,

    val notBefore: Instant?,
    val notAfter: Instant?,

    @Column(nullable = false)
    val sourceUrl: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val fetchStatus: KeyFetchStatus,

    @Column(nullable = false)
    val active: Boolean = true,

    @Column(nullable = false)
    val fetchedAt: Instant = Instant.now()
)
