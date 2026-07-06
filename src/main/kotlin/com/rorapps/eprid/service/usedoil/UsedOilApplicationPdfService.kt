package com.rorapps.eprid.service.usedoil

import com.rorapps.eprid.constants.UsedOilTier
import com.rorapps.eprid.dto.usedoil.Ca1ApplicationDetails
import com.rorapps.eprid.dto.usedoil.Ca2ApplicationDetails
import com.rorapps.eprid.dto.usedoil.UsedOilSummaryRequest
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
 * Renders a prefilled CA-1/CA-2 application worksheet from whatever fields the user supplied —
 * a printable/uploadable companion to the CPCB portal form, not a substitute for it. Fields the
 * user left blank are rendered as "— fill in on CPCB portal —" so nothing silently disappears.
 * Independent of [UsedOilSummaryPdfService]/[com.rorapps.eprid.service.report.ReportService] —
 * Module E stays zero-coupled.
 */
@Service
class UsedOilApplicationPdfService {
    private val dateFmt = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm z").withZone(ZoneId.of("Asia/Kolkata"))
    private val bold = PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD)
    private val regular = PDType1Font(Standard14Fonts.FontName.HELVETICA)
    private val blank = "— fill in on CPCB portal —"

    fun generateApplicationPdf(request: UsedOilSummaryRequest): ByteArray {
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
            fun field(label: String, value: String?) = line("$label: ${value?.takeIf { it.isNotBlank() } ?: blank}", size = 9f, gap = 13f)
            fun section(title: String) { line(title, bold, 11f) }

            val tierText = if (request.tier == UsedOilTier.CA_1) "CA-1" else "CA-2"
            line("E-PRid Used-Oil $tierText Application Worksheet", bold, 16f, gap = 22f)
            line("Generated: ${dateFmt.format(Instant.now())} — prefilled from your inputs, for reference only", size = 8f, gap = 18f)
            separator()

            section("AUTHORIZED PERSON DETAILS")
            val person = request.ca1ApplicationDetails?.let {
                listOf(it.authorizedPersonName, it.authorizedPersonDesignation, it.authorizedPersonMobile, it.authorizedPersonEmail)
            } ?: request.ca2ApplicationDetails?.let {
                listOf(it.authorizedPersonName, it.authorizedPersonDesignation, it.authorizedPersonMobile, it.authorizedPersonEmail)
            } ?: listOf(null, null, null, null)
            field("Name", person[0])
            field("Designation", person[1])
            field("Mobile", person[2])
            field("Email", person[3])
            separator()

            if (request.tier == UsedOilTier.CA_1) {
                val d = request.ca1ApplicationDetails
                section("VEHICLE DETAILS")
                field("Registration number", d?.vehicleRegistrationNumber)
                field("Vehicle type", d?.vehicleType)
                field("Capacity (KL)", d?.vehicleCapacityKl?.toString())
                separator()

                section("OIL COLLECTION DETAILS")
                field("Collection areas / routes", d?.collectionAreas)
                field("Estimated monthly collection (KL)", d?.estimatedMonthlyCollectionKl?.toString())
                separator()
            } else {
                val d = request.ca2ApplicationDetails
                section("STORAGE FACILITY DETAILS")
                field("Facility address", d?.storageFacilityAddress)
                field("Storage capacity (KL)", d?.storageCapacityKl?.toString())
                separator()

                section("GENERAL & LAB DETAILS")
                field("GST number", d?.gstNumber)
                field("Lab facility", d?.labFacilityDetails)
                field("Attached CA-1s / recyclers", d?.attachedCa1sOrRecyclers)
                separator()
            }

            line("This worksheet is informational only — it is not submitted anywhere on your behalf.", size = 8f, gap = 12f)
            line("Copy these values into the official CPCB portal form yourself; verify each before submitting.", size = 8f, gap = 16f)
            line("E-PRid (c) ${java.time.Year.now().value}  |  eprid.rorapps.com", size = 7f)
        }

        val out = ByteArrayOutputStream()
        document.save(out)
        document.close()
        return out.toByteArray()
    }
}
