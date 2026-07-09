package com.rorapps.eprid.service.einvoice

import com.rorapps.eprid.constants.EInvoiceIrp
import com.rorapps.eprid.entity.IrpPublicKey
import com.rorapps.eprid.entity.KeyFetchStatus
import com.rorapps.eprid.repository.IrpPublicKeyRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant

/**
 * Periodically re-fetches the 4 trusted IRPs' public keys (PRD §7.1, item 4). A key going
 * stale must be loud, not silent — every outcome is logged at WARN or above, and a failed
 * fetch never touches previously-stored active keys (they just age toward expiry naturally,
 * which [IrpPublicKeyStore] / [InvoiceQrVerifier] already fail closed on).
 */
@Service
class IrpKeyRefreshJob(
    private val fetcher: IrpKeyFetcher,
    private val repository: IrpPublicKeyRepository
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val expiringSoonWindow: Duration = Duration.ofDays(30)

    @Scheduled(cron = "0 0 3 * * *")
    fun refreshAll() {
        val outcomes = fetcher.fetchIrp1AndIrp2() + fetcher.fetchIrp4Clear() + fetcher.fetchIrp5Ey()
        outcomes.forEach { applyOutcome(it) }
    }

    fun applyOutcome(outcome: IrpFetchOutcome) {
        if (outcome.failureReason != null) {
            log.warn("IRP key fetch for ${outcome.irp} did not produce a usable key: ${outcome.failureReason}")
        }

        val existingKeyIds = repository.findAllByIrpAndActiveTrue(outcome.irp).mapNotNull { it.keyId }.toSet()

        outcome.keys.forEach { fetched ->
            if (fetched.keyId in existingKeyIds) return@forEach  // unchanged, nothing to do

            log.warn(
                "IRP key for ${outcome.irp} changed or is new (keyId=${fetched.keyId}, previously $existingKeyIds) " +
                    "— storing as active. Confirm this rotation was expected before relying on it."
            )
            repository.save(
                IrpPublicKey(
                    irp = fetched.irp,
                    keyId = fetched.keyId,
                    certPem = fetched.certPem,
                    notBefore = fetched.notBefore,
                    notAfter = fetched.notAfter,
                    sourceUrl = fetched.sourceUrl,
                    fetchStatus = KeyFetchStatus.FETCHED,
                    active = true
                )
            )
        }

        checkExpiry(outcome.irp)
    }

    private fun checkExpiry(irp: EInvoiceIrp) {
        val now = Instant.now()
        repository.findAllByIrpAndActiveTrue(irp).forEach { row ->
            val notAfter = row.notAfter ?: return@forEach
            when {
                notAfter.isBefore(now) -> log.warn(
                    "IRP key for $irp (keyId=${row.keyId}) has EXPIRED as of $notAfter — verification for " +
                        "this IRP will fail closed to COULD_NOT_VERIFY until a new key is fetched"
                )
                notAfter.isBefore(now.plus(expiringSoonWindow)) -> log.warn(
                    "IRP key for $irp (keyId=${row.keyId}) expires soon: $notAfter"
                )
            }
        }
    }
}
