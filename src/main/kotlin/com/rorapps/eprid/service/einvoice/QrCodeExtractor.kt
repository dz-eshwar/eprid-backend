package com.rorapps.eprid.service.einvoice

import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.client.j2se.BufferedImageLuminanceSource
import com.google.zxing.common.HybridBinarizer
import org.apache.pdfbox.Loader
import org.apache.pdfbox.rendering.PDFRenderer
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Pulls the raw e-invoice QR payload (a compact JWS string) off an uploaded image or PDF.
 * Returns null on no-QR-found — that's a normal, expected outcome (most evidence isn't an
 * invoice, and not every invoice is e-invoiced), not an error.
 */
@Service
class QrCodeExtractor {

    private val log = LoggerFactory.getLogger(javaClass)
    private val hints = mapOf(DecodeHintType.TRY_HARDER to true)

    fun extractFromImage(file: File): String? = runCatching {
        val img = ImageIO.read(file) ?: return@runCatching null
        decode(img)
    }.onFailure { ex -> log.warn("QR extraction failed for ${file.name}: ${ex.message}") }.getOrNull()

    /** Renders up to [maxPages] pages (invoices are typically page 1) and tries each. */
    fun extractFromPdf(file: File, maxPages: Int = 3): String? = runCatching {
        Loader.loadPDF(file).use { doc ->
            val renderer = PDFRenderer(doc)
            val pageCount = minOf(doc.numberOfPages, maxPages)
            (0 until pageCount).firstNotNullOfOrNull { page ->
                decode(renderer.renderImageWithDPI(page, 200f))
            }
        }
    }.onFailure { ex -> log.warn("PDF QR extraction failed for ${file.name}: ${ex.message}") }.getOrNull()

    private fun decode(img: BufferedImage): String? {
        val bitmap = BinaryBitmap(HybridBinarizer(BufferedImageLuminanceSource(img)))
        return try {
            MultiFormatReader().apply { setHints(hints) }.decode(bitmap).text
        } catch (e: NotFoundException) {
            null
        }
    }
}
