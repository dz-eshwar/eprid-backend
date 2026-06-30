package com.rorapps.eprid.service.forensics

import com.rorapps.eprid.constants.DateTolerancePolicy
import com.rorapps.eprid.constants.EvidenceType
import com.rorapps.eprid.dto.forensics.ForensicsCheckResult
import com.rorapps.eprid.dto.forensics.ForensicsCheckStatus
import org.apache.pdfbox.Loader
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Calendar

data class PdfMetadataResult(
    val author: String?,
    val creator: String?,
    val createdAt: Instant?,
    val modifiedAt: Instant?,
    val checks: List<ForensicsCheckResult>
)

@Service
class PdfForensicsService {

    private val log = LoggerFactory.getLogger(javaClass)

    fun analyze(file: File, claimedProcessingDate: LocalDate, evidenceType: EvidenceType = EvidenceType.OTHER): PdfMetadataResult {
        val checks = mutableListOf<ForensicsCheckResult>()
        var author: String? = null
        var creator: String? = null
        var createdAt: Instant? = null
        var modifiedAt: Instant? = null

        runCatching {
            Loader.loadPDF(file).use { doc ->
                val info = doc.documentInformation

                author = info.author?.takeIf { it.isNotBlank() }
                creator = info.creator?.takeIf { it.isNotBlank() }

                // ── Creation date vs claimed processing date (Fix 1: type-aware) ──
                val created = info.creationDate
                if (created != null) {
                    createdAt = created.toInstant()
                    val docDate = created.toInstant().atZone(ZoneOffset.UTC).toLocalDate()
                    val verdict = DateTolerancePolicy.evaluate(docDate, claimedProcessingDate, evidenceType)

                    checks += ForensicsCheckResult(
                        checkName = "PDF creation date check (${evidenceType.label})",
                        status = if (verdict.pass) ForensicsCheckStatus.PASS else ForensicsCheckStatus.FAIL,
                        detail = verdict.detail
                    )
                } else {
                    checks += unverifiable(
                        "PDF creation date check (${evidenceType.label})",
                        "No creation date in PDF metadata"
                    )
                }

                // ── Modification date consistency ─────────────────────────────
                val modified = info.modificationDate
                if (modified != null) {
                    modifiedAt = modified.toInstant()
                    val modDate = modified.toInstant().atZone(ZoneOffset.UTC).toLocalDate()
                    if (created != null) {
                        val modAfterCreate = !modified.before(created)
                        checks += ForensicsCheckResult(
                            checkName = "PDF modification date consistency",
                            status = if (modAfterCreate) ForensicsCheckStatus.PASS else ForensicsCheckStatus.FAIL,
                            detail = if (modAfterCreate)
                                "Last modified on $modDate — after creation date, consistent"
                            else
                                "Modification date ($modDate) is BEFORE creation date — indicates metadata tampering"
                        )
                    }
                } else {
                    checks += unverifiable("PDF modification date consistency", "No modification date in PDF metadata")
                }

                // ── Author / producer metadata ────────────────────────────────
                checks += ForensicsCheckResult(
                    checkName = "PDF authorship metadata",
                    status = if (author != null || creator != null) ForensicsCheckStatus.PASS
                             else ForensicsCheckStatus.UNVERIFIABLE,
                    detail = buildString {
                        if (author != null) append("Author: $author. ")
                        if (creator != null) append("Created with: $creator. ")
                        if (author == null && creator == null) append("No author or creator metadata present — may have been stripped")
                    }
                )
            }
        }.onFailure { ex ->
            log.warn("PDF metadata extraction failed for ${file.name}: ${ex.message}")
            checks += unverifiable("PDF metadata extraction", "Could not read PDF metadata: ${ex.message}")
        }

        return PdfMetadataResult(author, creator, createdAt, modifiedAt, checks)
    }

    private fun unverifiable(name: String, detail: String) =
        ForensicsCheckResult(name, ForensicsCheckStatus.UNVERIFIABLE, detail)
}

private fun Calendar.toInstant(): Instant = this.time.toInstant()
private fun Calendar.before(other: Calendar): Boolean = this.time.before(other.time)
