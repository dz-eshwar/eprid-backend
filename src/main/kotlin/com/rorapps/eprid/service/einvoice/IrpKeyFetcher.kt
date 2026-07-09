package com.rorapps.eprid.service.einvoice

import com.rorapps.eprid.constants.EInvoiceIrp
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import java.io.ByteArrayInputStream
import java.net.URI
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.zip.ZipInputStream

data class FetchedIrpKey(
    val irp: EInvoiceIrp,
    val certPem: String,
    val keyId: String,
    val notBefore: Instant?,
    val notAfter: Instant?,
    val sourceUrl: String
)

data class IrpFetchOutcome(
    val irp: EInvoiceIrp,
    val keys: List<FetchedIrpKey>,
    val failureReason: String?
)

/**
 * Fetcher for the 4 trusted IRPs' public keys (PRD §7.1). Status per IRP as of 2026-07,
 * confirmed by direct page inspection + network trace:
 *  - IRP1 (NIC) / IRP2 (Cleartax): the key *table* on einvoice1.gst.gov.in is client-rendered,
 *    but the actual key files are static, stable URLs (zip archives) — fetched directly below,
 *    bypassing the table entirely. These filenames are dated (rotation-period-specific); a 404
 *    here means the IRP rotated and this URL needs updating, which [IrpKeyRefreshJob]'s failure
 *    logging will surface.
 *  - IRP4 (Clear): static HTML page with direct .pem links — scraped generically.
 *  - IRP5 (EY): confirmed to require a JS-driven client-side download with no static URL
 *    anywhere in the page or its script bundles — no auto-fetch path exists; relies on
 *    [KeyFetchStatus.MANUAL_SEED] until someone seeds a cert by hand.
 * In every case, the cert's own notAfter (not page/URL prose) is what determines validity.
 */
@Service
class IrpKeyFetcher {

    private val log = LoggerFactory.getLogger(javaClass)
    private val client = WebClient.builder()
        .defaultHeader("User-Agent", "E-PRid/1.0 (EPR e-invoice originality check; contact: admin@eprid.in)")
        .build()

    private val hrefPattern = Regex("""href\s*=\s*["']([^"']+\.(?:pem|cer))["']""", RegexOption.IGNORE_CASE)

    private val IRP1_KEY_ZIP_URL = "https://einvoice1.gst.gov.in/Documents/einv1publickeyApr2027.zip"
    private val IRP2_KEY_ZIP_URL = "https://einvoice1.gst.gov.in/Documents/einv2publickeyApr2027.zip"

    fun fetchIrp4Clear(): IrpFetchOutcome =
        fetchSingleIrp(EInvoiceIrp.IRP4_CLEAR, "https://einvoice4.gst.gov.in/certificates-and-security/")

    fun fetchIrp5Ey(): IrpFetchOutcome =
        fetchSingleIrp(EInvoiceIrp.IRP5_EY, "https://einvoice5.gst.gov.in/public-keys")

    fun fetchIrp1AndIrp2(): List<IrpFetchOutcome> = listOf(
        fetchZippedCert(EInvoiceIrp.IRP1_NIC, IRP1_KEY_ZIP_URL),
        fetchZippedCert(EInvoiceIrp.IRP2_CLEARTAX, IRP2_KEY_ZIP_URL)
    )

    /** Downloads a zip archive and parses the first .pem/.cer entry inside it as an X.509 cert. */
    private fun fetchZippedCert(irp: EInvoiceIrp, zipUrl: String): IrpFetchOutcome {
        val bytes = runCatching {
            client.get().uri(zipUrl).retrieve().bodyToMono(ByteArray::class.java).block(Duration.ofSeconds(15))
        }.getOrElse { ex ->
            log.warn("Failed to fetch $zipUrl for $irp: ${ex.message}")
            return IrpFetchOutcome(irp, emptyList(), "Fetch failed: ${ex.message}")
        }
        if (bytes == null) {
            return IrpFetchOutcome(irp, emptyList(), "Empty response from $zipUrl")
        }

        val entryBytes = runCatching { extractCertEntry(bytes) }.getOrNull()
        if (entryBytes == null) {
            return IrpFetchOutcome(irp, emptyList(), "No .pem/.cer entry found inside zip at $zipUrl")
        }

        val cert = runCatching {
            CertificateFactory.getInstance("X.509").generateCertificate(ByteArrayInputStream(entryBytes)) as X509Certificate
        }.getOrElse { ex ->
            log.warn("Zip entry from $zipUrl for $irp did not parse as X.509: ${ex.message}")
            return IrpFetchOutcome(irp, emptyList(), "Zip entry did not parse as a valid X.509 certificate")
        }

        val key = FetchedIrpKey(
            irp = irp,
            certPem = certToPem(cert),
            keyId = cert.serialNumber.toString(16),
            notBefore = cert.notBefore?.toInstant(),
            notAfter = cert.notAfter?.toInstant(),
            sourceUrl = zipUrl
        )
        return IrpFetchOutcome(irp, listOf(key), null)
    }

    private fun extractCertEntry(zipBytes: ByteArray): ByteArray? {
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val name = entry.name.lowercase()
                if (!entry.isDirectory && (name.endsWith(".pem") || name.endsWith(".cer"))) {
                    return zis.readBytes()
                }
                entry = zis.nextEntry
            }
        }
        return null
    }

    private fun fetchSingleIrp(irp: EInvoiceIrp, url: String): IrpFetchOutcome {
        val links = runCatching { fetchCertLinks(url) }.getOrElse { ex ->
            log.warn("Failed to fetch $url: ${ex.message}")
            return IrpFetchOutcome(irp, emptyList(), "Fetch failed: ${ex.message}")
        }
        if (links.isEmpty()) {
            return IrpFetchOutcome(irp, emptyList(), "No .pem/.cer links found on $url — page may be client-rendered")
        }
        val keys = links.mapNotNull { downloadAndParseCert(it, irp) }
        return IrpFetchOutcome(
            irp, keys,
            if (keys.isEmpty()) "Found ${links.size} link(s) but none parsed as a valid X.509 certificate" else null
        )
    }

    private fun fetchCertLinks(pageUrl: String): List<String> {
        val html = client.get().uri(pageUrl).retrieve().bodyToMono(String::class.java).block(Duration.ofSeconds(10))
            ?: return emptyList()
        val base = URI(pageUrl)
        return hrefPattern.findAll(html)
            .map { it.groupValues[1] }
            .map { href -> runCatching { base.resolve(href).toString() }.getOrDefault(href) }
            .distinct()
            .toList()
    }

    private fun downloadAndParseCert(certUrl: String, irp: EInvoiceIrp): FetchedIrpKey? = runCatching {
        val bytes = client.get().uri(certUrl).retrieve().bodyToMono(ByteArray::class.java).block(Duration.ofSeconds(10))
            ?: return null
        val cert = CertificateFactory.getInstance("X.509")
            .generateCertificate(ByteArrayInputStream(bytes)) as X509Certificate
        FetchedIrpKey(
            irp = irp,
            certPem = certToPem(cert),
            keyId = cert.serialNumber.toString(16),
            notBefore = cert.notBefore?.toInstant(),
            notAfter = cert.notAfter?.toInstant(),
            sourceUrl = certUrl
        )
    }.onFailure { ex ->
        log.warn("Could not download/parse cert at $certUrl for $irp: ${ex.message}")
    }.getOrNull()

    /** Re-encodes to canonical PEM regardless of whether the source was PEM text or DER (.cer). */
    private fun certToPem(cert: X509Certificate): String {
        val base64 = Base64.getMimeEncoder(64, byteArrayOf('\n'.code.toByte())).encodeToString(cert.encoded)
        return "-----BEGIN CERTIFICATE-----\n$base64\n-----END CERTIFICATE-----\n"
    }
}
