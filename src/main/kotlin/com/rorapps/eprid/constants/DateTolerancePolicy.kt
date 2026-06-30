package com.rorapps.eprid.constants

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Returns a human-readable verdict and pass/fail for a document's date
 * relative to the batch's claimed processing date, using type-specific tolerances.
 *
 * Rules (days = difference between document date and processing date;
 *        negative = document is dated BEFORE processing date):
 *
 * SITE_PHOTO / WEIGHBRIDGE_SLIP  →  document must be within ±7 days
 * INVOICE                        →  within ±30 days
 * REGISTRATION_CERTIFICATE       →  must predate processing date (any age is fine)
 * AUDIT_REPORT                   →  must fall within the same Indian financial year (Apr–Mar)
 * OTHER                          →  within ±30 days (safe default)
 */
object DateTolerancePolicy {

    data class DateVerdict(val pass: Boolean, val detail: String)

    fun evaluate(
        documentDate: LocalDate,
        processingDate: LocalDate,
        evidenceType: EvidenceType
    ): DateVerdict {
        val daysDiff = ChronoUnit.DAYS.between(documentDate, processingDate)
        // daysDiff > 0 means document is dated BEFORE processing date
        // daysDiff < 0 means document is dated AFTER processing date

        return when (evidenceType) {
            EvidenceType.SITE_PHOTO, EvidenceType.WEIGHBRIDGE_SLIP -> {
                val within = daysDiff in -7..7
                DateVerdict(
                    pass = within,
                    detail = if (within)
                        "${evidenceType.label} date is within ±7 days of the claimed processing date — consistent"
                    else if (daysDiff < -7)
                        "${evidenceType.label} is dated ${-daysDiff} day(s) AFTER the claimed processing date — impossible for on-site evidence"
                    else
                        "${evidenceType.label} is dated ${daysDiff} day(s) before claimed processing date — more than 7 days, suspicious"
                )
            }

            EvidenceType.INVOICE -> {
                val within = daysDiff in -30..30
                DateVerdict(
                    pass = within,
                    detail = if (within)
                        "Invoice date is within ±30 days of the claimed processing date — acceptable"
                    else if (daysDiff < -30)
                        "Invoice is dated ${-daysDiff} day(s) after the claimed processing date — over 30 days, suspicious"
                    else
                        "Invoice is dated ${daysDiff} day(s) before claimed processing date — over 30 days, suspicious"
                )
            }

            EvidenceType.REGISTRATION_CERTIFICATE -> {
                // Must predate processing — a certificate issued after the batch date is impossible
                val valid = daysDiff >= 0
                DateVerdict(
                    pass = valid,
                    detail = if (valid)
                        "Registration certificate is dated before the processing date — valid (certificates are long-lived documents)"
                    else
                        "Registration certificate is dated ${-daysDiff} day(s) AFTER the claimed processing date — a certificate cannot be issued after the fact"
                )
            }

            EvidenceType.AUDIT_REPORT -> {
                val fy = financialYear(processingDate)
                val docFy = financialYear(documentDate)
                val sameFy = fy == docFy
                DateVerdict(
                    pass = sameFy,
                    detail = if (sameFy)
                        "Audit report is from FY $fy — same financial year as the claimed processing date — consistent"
                    else
                        "Audit report is from FY $docFy but the processing date falls in FY $fy — different financial years, suspicious"
                )
            }

            EvidenceType.OTHER -> {
                val within = daysDiff in -30..30
                DateVerdict(
                    pass = within,
                    detail = if (within)
                        "Document date is within ±30 days of the claimed processing date"
                    else if (daysDiff < -30)
                        "Document is dated ${-daysDiff} day(s) after the claimed processing date — over 30 days"
                    else
                        "Document is dated ${daysDiff} day(s) before claimed processing date — over 30 days"
                )
            }
        }
    }

    /** Returns the Indian financial year string (e.g. "2024-25") for a given date. */
    private fun financialYear(date: LocalDate): String {
        val year = if (date.monthValue >= 4) date.year else date.year - 1
        return "$year-${(year + 1).toString().takeLast(2)}"
    }
}
