package com.rorapps.eprid.service.einvoice

import com.rorapps.eprid.constants.EInvoiceIrp
import com.rorapps.eprid.entity.IrpPublicKey
import com.rorapps.eprid.repository.IrpPublicKeyRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.ByteArrayInputStream
import java.security.PublicKey
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.time.Instant

data class TrustedKey(val irp: EInvoiceIrp, val publicKey: PublicKey, val row: IrpPublicKey)

/**
 * Reads persisted IRP certificates (populated by [IrpKeyFetcher] / [IrpKeyRefreshJob]) and
 * exposes only the currently-valid ones. An expired cert is excluded, never silently used —
 * callers that get an empty list for a trusted IRP must treat that as COULD_NOT_VERIFY,
 * not fall back to whatever was last cached.
 */
@Service
class IrpPublicKeyStore(private val repository: IrpPublicKeyRepository) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun activeTrustedKeys(irp: EInvoiceIrp): List<TrustedKey> {
        if (!irp.trusted) return emptyList()
        val now = Instant.now()
        return repository.findAllByIrpAndActiveTrue(irp)
            .filter { row -> row.notAfter == null || row.notAfter.isAfter(now) }
            .mapNotNull { row -> parseCert(row)?.let { TrustedKey(irp, it, row) } }
    }

    fun allTrustedActiveKeys(): List<TrustedKey> = EInvoiceIrp.TRUSTED.flatMap { activeTrustedKeys(it) }

    /** Active + currently-valid key matching this JWS "kid", if any trusted IRP has one on file. */
    fun activeTrustedKeyByKeyId(keyId: String): TrustedKey? =
        allTrustedActiveKeys().firstOrNull { it.row.keyId == keyId }

    /** True if a row for this kid exists on file but is excluded only because it's expired. */
    fun isKnownButExpiredKeyId(keyId: String): Boolean {
        val rows = repository.findAllByKeyId(keyId).filter { it.active }
        if (rows.isEmpty()) return false
        val now = Instant.now()
        return rows.all { it.notAfter != null && !it.notAfter.isAfter(now) }
    }

    /**
     * True if this IRP has at least one active row on file but every one of them has expired —
     * i.e. we HAD a key and it went stale with nothing to replace it, as opposed to never
     * having fetched one at all. Used to word the COULD_NOT_VERIFY reason precisely.
     */
    fun hasOnlyExpiredKeys(irp: EInvoiceIrp): Boolean {
        val rows = repository.findAllByIrpAndActiveTrue(irp)
        if (rows.isEmpty()) return false
        val now = Instant.now()
        return rows.all { it.notAfter != null && !it.notAfter.isAfter(now) }
    }

    private fun parseCert(row: IrpPublicKey): PublicKey? = runCatching {
        val cf = CertificateFactory.getInstance("X.509")
        (cf.generateCertificate(ByteArrayInputStream(row.certPem.toByteArray())) as X509Certificate).publicKey
    }.onFailure { ex ->
        log.warn("Could not parse stored cert for ${row.irp} (id=${row.id}): ${ex.message}")
    }.getOrNull()
}
