package com.rorapps.eprid.service.einvoice

import com.rorapps.eprid.constants.EInvoiceIrp
import com.rorapps.eprid.entity.IrpPublicKey
import com.rorapps.eprid.entity.KeyFetchStatus
import com.rorapps.eprid.repository.IrpPublicKeyRepository
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.cert.X509Certificate
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.Date

@ExtendWith(MockitoExtension::class)
class IrpPublicKeyStoreTest {

    @Mock private lateinit var repository: IrpPublicKeyRepository
    @InjectMocks private lateinit var store: IrpPublicKeyStore

    private fun selfSignedCertPem(notBefore: Instant, notAfter: Instant): String {
        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val cert = buildSelfSignedX509(keyPair, notBefore, notAfter)
        val base64 = Base64.getMimeEncoder(64, byteArrayOf('\n'.code.toByte())).encodeToString(cert.encoded)
        return "-----BEGIN CERTIFICATE-----\n$base64\n-----END CERTIFICATE-----\n"
    }

    private fun buildSelfSignedX509(
        keyPair: java.security.KeyPair,
        notBefore: Instant,
        notAfter: Instant
    ): X509Certificate {
        val owner = X500Name("CN=Test")
        val builder = JcaX509v3CertificateBuilder(
            owner, BigInteger.valueOf(System.nanoTime()), Date.from(notBefore), Date.from(notAfter), owner, keyPair.public
        )
        val signer = JcaContentSignerBuilder("SHA256withRSA").build(keyPair.private)
        return JcaX509CertificateConverter().getCertificate(builder.build(signer))
    }

    private fun row(irp: EInvoiceIrp, keyId: String, notAfter: Instant, notBefore: Instant = Instant.now().minus(365, ChronoUnit.DAYS)) =
        IrpPublicKey(
            id = "row-$keyId", irp = irp, keyId = keyId,
            certPem = selfSignedCertPem(notBefore, notAfter),
            notBefore = notBefore, notAfter = notAfter,
            sourceUrl = "https://test", fetchStatus = KeyFetchStatus.MANUAL_SEED, active = true
        )

    @Test
    fun `active non-expired key is returned`() {
        val futureExpiry = Instant.now().plus(365, ChronoUnit.DAYS)
        val validRow = row(EInvoiceIrp.IRP4_CLEAR, "good-kid", futureExpiry)
        whenever(repository.findAllByIrpAndActiveTrue(EInvoiceIrp.IRP4_CLEAR)).thenReturn(listOf(validRow))

        val keys = store.activeTrustedKeys(EInvoiceIrp.IRP4_CLEAR)

        assertEquals(1, keys.size)
        assertEquals(EInvoiceIrp.IRP4_CLEAR, keys[0].irp)
    }

    @Test
    fun `expired local key cache is excluded and flagged as known-but-expired`() {
        val pastExpiry = Instant.now().minus(10, ChronoUnit.DAYS)
        val expiredRow = row(EInvoiceIrp.IRP5_EY, "expired-kid", pastExpiry)
        whenever(repository.findAllByIrpAndActiveTrue(EInvoiceIrp.IRP5_EY)).thenReturn(listOf(expiredRow))
        whenever(repository.findAllByKeyId("expired-kid")).thenReturn(listOf(expiredRow))

        val activeKeys = store.activeTrustedKeys(EInvoiceIrp.IRP5_EY)
        val knownExpired = store.isKnownButExpiredKeyId("expired-kid")

        assertTrue(activeKeys.isEmpty(), "expired key must never be returned as usable, even as a fallback")
        assertTrue(knownExpired)
    }

    @Test
    fun `untrusted IRP never returns keys even if rows exist`() {
        assertEquals(emptyList<TrustedKey>(), store.activeTrustedKeys(EInvoiceIrp.IRP3_CYGNET))
    }
}
