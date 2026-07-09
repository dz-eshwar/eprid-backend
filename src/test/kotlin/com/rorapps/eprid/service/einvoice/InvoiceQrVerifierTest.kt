package com.rorapps.eprid.service.einvoice

import com.rorapps.eprid.constants.EInvoiceIrp
import com.rorapps.eprid.constants.InvoiceOriginalityStatus
import com.rorapps.eprid.entity.IrpPublicKey
import com.rorapps.eprid.entity.KeyFetchStatus
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever
import java.security.KeyPairGenerator
import java.util.Base64

@ExtendWith(MockitoExtension::class)
class InvoiceQrVerifierTest {

    @Mock private lateinit var keyStore: IrpPublicKeyStore

    private val verifier by lazy { InvoiceQrVerifier(keyStore) }

    private val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    private val impostorKeyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()

    private fun buildJws(kid: String, signWith: java.security.PrivateKey = keyPair.private): String =
        Jwts.builder()
            .setHeaderParam("kid", kid)
            .claim("SellerGstin", "27ABCDE1234F1Z5")
            .claim("Irn", "abc123irn")
            .signWith(signWith, SignatureAlgorithm.RS256)
            .compact()

    private fun dummyRow(irp: EInvoiceIrp, keyId: String) = IrpPublicKey(
        id = "row-1", irp = irp, keyId = keyId, certPem = "unused-in-test",
        notBefore = null, notAfter = null, sourceUrl = "https://test",
        fetchStatus = KeyFetchStatus.MANUAL_SEED, active = true
    )

    @Test
    fun `valid signature against known kid returns VALID`() {
        val token = buildJws("test-kid")
        whenever(keyStore.activeTrustedKeyByKeyId("test-kid"))
            .thenReturn(TrustedKey(EInvoiceIrp.IRP1_NIC, keyPair.public, dummyRow(EInvoiceIrp.IRP1_NIC, "test-kid")))

        val result = verifier.verify(token)

        assertEquals(InvoiceOriginalityStatus.VALID, result.status)
        assertEquals(EInvoiceIrp.IRP1_NIC, result.irp)
        assertEquals("abc123irn", result.payload?.irn)
    }

    @Test
    fun `tampered payload fails signature and returns INVALID`() {
        val token = buildJws("test-kid")
        whenever(keyStore.activeTrustedKeyByKeyId("test-kid"))
            .thenReturn(TrustedKey(EInvoiceIrp.IRP1_NIC, keyPair.public, dummyRow(EInvoiceIrp.IRP1_NIC, "test-kid")))

        val parts = token.split(".")
        val decodedPayload = String(Base64.getUrlDecoder().decode(parts[1]))
        val tamperedPayload = decodedPayload.replace("abc123irn", "forged999irn")
        val tamperedSegment = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(tamperedPayload.toByteArray())
        val tamperedToken = "${parts[0]}.$tamperedSegment.${parts[2]}"

        val result = verifier.verify(tamperedToken)

        assertEquals(InvoiceOriginalityStatus.INVALID, result.status)
        assertEquals(EInvoiceIrp.IRP1_NIC, result.irp)
    }

    @Test
    fun `unknown kid not in trusted store returns COULD_NOT_VERIFY`() {
        val token = buildJws("cygnet-kid", signWith = impostorKeyPair.private)
        whenever(keyStore.activeTrustedKeyByKeyId("cygnet-kid")).thenReturn(null)
        whenever(keyStore.isKnownButExpiredKeyId("cygnet-kid")).thenReturn(false)

        val result = verifier.verify(token)

        assertEquals(InvoiceOriginalityStatus.COULD_NOT_VERIFY, result.status)
        assertNull(result.irp)
    }

    @Test
    fun `kid matches an expired local key returns COULD_NOT_VERIFY mentioning expiry`() {
        val token = buildJws("expired-kid")
        whenever(keyStore.activeTrustedKeyByKeyId("expired-kid")).thenReturn(null)
        whenever(keyStore.isKnownButExpiredKeyId("expired-kid")).thenReturn(true)

        val result = verifier.verify(token)

        assertEquals(InvoiceOriginalityStatus.COULD_NOT_VERIFY, result.status)
        assertTrue(result.reason.contains("expired"))
    }

    @Test
    fun `missing QR entirely returns NOT_APPLICABLE`() {
        val result = verifier.verify(null)

        assertEquals(InvoiceOriginalityStatus.NOT_APPLICABLE, result.status)
    }
}
