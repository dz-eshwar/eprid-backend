package com.rorapps.eprid.service.usedoil

import com.rorapps.eprid.dto.usedoil.UsedOilSummaryResponse
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.springframework.stereotype.Service
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Renders a [UsedOilSummaryResponse] as a downloadable PDF. Deliberately independent of
 * [com.rorapps.eprid.service.report.ReportService] — Module E has no entity/repository
 * coupling, and PDFBox is already a project dependency, so duplicating a small one-page
 * layout here is simpler than coupling Module E to Module A's report package.
 */
@Service
class UsedOilSummaryPdfService {
    private val dateFmt = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm z").withZone(ZoneId.of("Asia/Kolkata"))
    private val bold = PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD)
    private val regular = PDType1Font(Standard14Fonts.FontName.HELVETICA)

    fun generateSummaryPdf(summary: UsedOilSummaryResponse): ByteArray {
        val document = PDDocument()
        val page = PDPage(PDRectangle.A4)
        document.addPage(page)

        PDPageContentStream(document, page).use { cs ->
            var y = page.mediaBox.height - 50f

            fun line(text: String, font: PDType1Font = regular, size: Float = 10f, x: Float = 50f, gap: Float = 14f) {
                cs.beginText()
                cs.setFont(font, size)
                cs.newLineAtOffset(x, y)
                cs.showText(text.take(120))
                cs.endText()
                y -= gap
            }

            fun separator() { y -= 6f }
            fun wrapped(text: String, size: Float = 8f) {
                text.chunked(100).forEach { line(it, size = size, gap = 11f) }
            }

            line("E-PRid Used-Oil Registration Assistant — Summary", bold, 16f, gap = 22f)
            line("Generated: ${dateFmt.format(Instant.now())}", size = 8f, gap = 18f)
            separator()

            line("TIER: ${summary.tier}", bold, 13f, gap = 18f)
            separator()

            line("FEE ESTIMATE", bold, 11f)
            line("Registration fee:          Rs. ${summary.feeBreakdown.registrationFeeRs} (${summary.feeBreakdown.tierLabel})")
            line("Annual processing charge:  Rs. ${summary.feeBreakdown.annualProcessingChargeRs}")
            line("Total (first year):        Rs. ${summary.feeBreakdown.totalFirstYearRs}", bold)
            separator()

            line("PREREQUISITES MET", bold, 11f)
            if (summary.prerequisitesMet.isEmpty()) line("None yet")
            else summary.prerequisitesMet.forEach { line("- $it", size = 9f) }
            separator()

            line("PREREQUISITES OUTSTANDING", bold, 11f)
            if (summary.prerequisitesOutstanding.isEmpty()) line("None")
            else summary.prerequisitesOutstanding.forEach { line("- $it", size = 9f) }
            separator()

            line("NEXT STEP", bold, 11f)
            wrapped(summary.nextStep)
            separator()

            line("DISCLAIMER", bold, 9f)
            wrapped(summary.disclaimer, size = 8f)
            line("E-PRid (c) ${java.time.Year.now().value}  |  eprid.rorapps.com", size = 7f)
        }

        val out = ByteArrayOutputStream()
        document.save(out)
        document.close()
        return out.toByteArray()
    }
}
