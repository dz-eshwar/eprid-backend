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
                cs.showText(sanitizeForPdf(text).take(120))
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
                RiskRating.CRITICAL -> "CRITICAL RISK"
                null -> "PENDING"
            }
            line("OVERALL RISK RATING: $ratingLabel", BOLD, 13f, gap = 18f)
            line("This is a signal derived from available public/regulatory data, not a legal finding or a", size = 7f, gap = 10f)
            line("verdict on the recycler — see DISCLAIMER below.", size = 7f)
            check.compositeScore?.let { line("Composite score: $it / 100", size = 9f) }
            if (check.hardDisqualified) {
                line("HARD-DISQUALIFIED: ${check.hardDisqualificationReason}", BOLD, 9f)
            }
            check.riskSummary?.let { line(it, size = 9f) }
            separator()

            check.compositeScore?.let {
                line("COMPOSITE SCORE BREAKDOWN", BOLD, 11f)
                line("Registration/authorization: ${check.registrationSubScore ?: "—"} / 100")
                line("Capacity/plausibility:      ${check.capacitySubScore ?: "—"} / 100")
                line("Invoice/GST traceability:   ${check.invoiceSubScore ?: "—"} / 100")
                line("Document forensics:         ${check.forensicsSubScore ?: "—"} / 100")
                line("Regulatory history:         ${check.regulatorySubScore ?: "—"} / 100")
                separator()
            }

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
                    line("  Source: ${f.source}  Dated: ${f.findingDate ?: "not recorded"}  Confidence: ${f.confidence}", MONO, 8f, gap = 12f)
                }
            }
            separator()

            // Disclaimer
            line("DISCLAIMER", BOLD, 9f)
            line("This report documents evidence reviewed as of the check date. It is not a legal opinion,", size = 8f, gap = 11f)
            line("audit certification, or guarantee of compliance. Rely on it as one input into due diligence,", size = 8f, gap = 11f)
            line("not as a substitute for regulatory filing or professional legal advice.", size = 8f, gap = 11f)
            line("Risk weighting is a first-draft model, not yet calibrated against confirmed real-world cases.", size = 8f, gap = 11f)
            line("Findings are signals surfaced from public/government data sources as of the dates shown", size = 8f, gap = 11f)
            line("above, not independently re-verified with the recycler, and the recycler has not been given", size = 8f, gap = 11f)
            line("an opportunity to respond to any specific finding in this report. Source data may since have", size = 8f, gap = 11f)
            line("changed or been corrected. Treat every finding as worth checking, not as a proven fact.", size = 8f, gap = 11f)
            line("E-PRid © ${java.time.Year.now().value}  |  eprid.rorapps.com", size = 7f)
        }

        val out = ByteArrayOutputStream()
        document.save(out)
        document.close()
        return out.toByteArray()
    }

    /**
     * Standard14 PDF fonts (Helvetica/Courier) use WinAnsiEncoding — a ~220-char Windows-1252
     * repertoire. Free text rendered here can come from user input or from an LLM's prose
     * (regulatory summaries, findings), which routinely contains characters outside that
     * encoding (curly quotes, en/em dashes, ellipsis, rupee sign, non-Latin scripts). PDFBox
     * throws IllegalArgumentException on the first unencodable character, which the global
     * exception handler turns into an opaque 400 — so every string reaching showText() must be
     * sanitized first rather than trusting the source.
     */
    private fun sanitizeForPdf(text: String): String {
        val normalized = text
            .replace("…", "...")   // …
            .replace("‘", "'").replace("’", "'")   // ' '
            .replace("“", "\"").replace("”", "\"") // " "
            .replace("–", "-").replace("—", "-")   // – —
            .replace('\n', ' ').replace('\r', ' ')
        return buildString(normalized.length) {
            for (ch in normalized) {
                append(if (ch.code in 0x20..0x7E) ch else '?')
            }
        }
    }

    private fun statusLabel(status: SubCheckStatus) = when (status) {
        SubCheckStatus.PASS -> "PASS"
        SubCheckStatus.WARN -> "WARN"
        SubCheckStatus.FAIL -> "FAIL"
        SubCheckStatus.UNVERIFIABLE -> "UNVERIFIABLE"
    }
}
