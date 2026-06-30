package com.rorapps.eprid.service.report

import com.rorapps.eprid.entity.ForensicsStatus
import com.rorapps.eprid.entity.RiskRating
import com.rorapps.eprid.entity.SubCheckStatus
import com.rorapps.eprid.entity.VerificationCheck
import com.rorapps.eprid.repository.EvidenceRepository
import com.rorapps.eprid.repository.PlausibilityCheckRepository
import com.rorapps.eprid.repository.RegulatoryFindingRepository
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.springframework.stereotype.Service
import java.io.ByteArrayOutputStream
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Service
class ReportService(
    private val evidenceRepository: EvidenceRepository,
    private val plausibilityRepository: PlausibilityCheckRepository,
    private val regulatoryRepository: RegulatoryFindingRepository
) {
    private val DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm z").withZone(ZoneId.of("Asia/Kolkata"))
    private val BOLD = PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD)
    private val REGULAR = PDType1Font(Standard14Fonts.FontName.HELVETICA)
    private val MONO = PDType1Font(Standard14Fonts.FontName.COURIER)

    fun generateCheckReport(check: VerificationCheck): ByteArray {
        val evidence = evidenceRepository.findAllByCheckId(check.id!!)
        val plausibility = plausibilityRepository.findByCheckId(check.id)
        val findings = regulatoryRepository.findAllByCheckId(check.id)

        val document = PDDocument()
        val page = PDPage(PDRectangle.A4)
        document.addPage(page)

        PDPageContentStream(document, page).use { cs ->
            val width = page.mediaBox.width
            var y = page.mediaBox.height - 50f

            fun line(text: String, font: PDType1Font = REGULAR, size: Float = 10f, x: Float = 50f, gap: Float = 14f) {
                cs.beginText()
                cs.setFont(font, size)
                cs.newLineAtOffset(x, y)
                cs.showText(text.take(120))
                cs.endText()
                y -= gap
            }

            fun separator() { y -= 6f }

            // Header
            line("E-PRid Recycler Verification Report", BOLD, 16f, gap = 22f)
            line("Generated: ${DATE_FMT.format(java.time.Instant.now())}", size = 8f, gap = 18f)
            separator()

            // Check metadata
            line("CHECK DETAILS", BOLD, 11f)
            line("Check ID:        ${check.id}")
            line("Recycler:        ${check.recycler.name}")
            check.recycler.bwmrRegNumber?.let { line("BWMR Reg No:     $it") }
            line("Producer:        ${check.producer.name}")
            line("Batch weight:    ${check.batchWeightTonnes} tonnes")
            line("Claimed recovery:${check.claimedRecoveryPct}%")
            line("Processing date: ${check.processingDate}")
            separator()

            // Risk rating
            val ratingLabel = when (check.riskRating) {
                RiskRating.LOW -> "LOW RISK"
                RiskRating.MEDIUM -> "MEDIUM RISK"
                RiskRating.HIGH -> "HIGH RISK"
                null -> "PENDING"
            }
            line("OVERALL RISK RATING: $ratingLabel", BOLD, 13f, gap = 18f)
            check.riskSummary?.let { line(it, size = 9f) }
            separator()

            // Plausibility
            line("PLAUSIBILITY CHECK", BOLD, 11f)
            if (plausibility != null) {
                line("Recovery rate:   ${statusLabel(plausibility.recoveryStatus)} — ${plausibility.recoveryDetail}")
                line("Capacity ceiling:${statusLabel(plausibility.capacityStatus)} — ${plausibility.capacityDetail}")
                line("Batch size:      ${statusLabel(plausibility.batchSizeStatus)} — ${plausibility.batchSizeDetail}")
                line("Overall:         ${statusLabel(plausibility.overallStatus)}", BOLD)
            } else {
                line("Not yet run")
            }
            separator()

            // Document forensics
            line("DOCUMENT FORENSICS (${evidence.size} file(s))", BOLD, 11f)
            if (evidence.isEmpty()) {
                line("No evidence uploaded")
            } else {
                evidence.forEach { ev ->
                    line("• ${ev.fileName} [${ev.evidenceType}]  →  ${ev.forensicsStatus.name}", size = 9f)
                    ev.forensicsNotes?.lines()?.take(3)?.forEach { note ->
                        line("  $note", MONO, 8f, gap = 12f)
                    }
                }
            }
            separator()

            // Regulatory history
            line("REGULATORY HISTORY", BOLD, 11f)
            line("Status: ${check.regulatoryStatus.name}   Risk: ${check.regulatoryRisk ?: "—"}")
            check.regulatorySummary?.let { line(it, size = 9f) }
            if (findings.isNotEmpty()) {
                separator()
                line("Findings (${findings.size}):", size = 9f)
                findings.take(10).forEach { f ->
                    line("[${f.severity}] ${f.title}", size = 9f)
                    line("  Source: ${f.source}  Confidence: ${f.confidence}", MONO, 8f, gap = 12f)
                }
            }
            separator()

            // Disclaimer
            line("DISCLAIMER", BOLD, 9f)
            line("This report documents evidence reviewed as of the check date. It is not a legal opinion,", size = 8f, gap = 11f)
            line("audit certification, or guarantee of compliance. Rely on it as one input into due diligence,", size = 8f, gap = 11f)
            line("not as a substitute for regulatory filing or professional legal advice.", size = 8f, gap = 11f)
            line("E-PRid © ${java.time.Year.now().value}  |  eprid.rorapps.com", size = 7f)
        }

        val out = ByteArrayOutputStream()
        document.save(out)
        document.close()
        return out.toByteArray()
    }

    private fun statusLabel(status: SubCheckStatus) = when (status) {
        SubCheckStatus.PASS -> "PASS"
        SubCheckStatus.WARN -> "WARN"
        SubCheckStatus.FAIL -> "FAIL"
        SubCheckStatus.UNVERIFIABLE -> "UNVERIFIABLE"
    }
}
