package com.rorapps.eprid.service

import com.rorapps.eprid.constants.DateTolerancePolicy
import com.rorapps.eprid.constants.EvidenceType
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.LocalDate

class DateTolerancePolicyTest {

    private val processingDate = LocalDate.of(2025, 3, 15)

    // ── SITE_PHOTO ────────────────────────────────────────────────────────────

    @Test
    fun `SITE_PHOTO taken on same day passes`() {
        val result = DateTolerancePolicy.evaluate(processingDate, processingDate, EvidenceType.SITE_PHOTO)
        assertTrue(result.pass)
    }

    @Test
    fun `SITE_PHOTO taken 6 days before passes`() {
        val result = DateTolerancePolicy.evaluate(processingDate.minusDays(6), processingDate, EvidenceType.SITE_PHOTO)
        assertTrue(result.pass)
    }

    @Test
    fun `SITE_PHOTO taken 8 days before fails`() {
        val result = DateTolerancePolicy.evaluate(processingDate.minusDays(8), processingDate, EvidenceType.SITE_PHOTO)
        assertFalse(result.pass)
    }

    @Test
    fun `SITE_PHOTO taken 1 day AFTER processing date passes within window`() {
        // ±7 day window is symmetric — a photo taken the next day is still plausible
        val result = DateTolerancePolicy.evaluate(processingDate.plusDays(1), processingDate, EvidenceType.SITE_PHOTO)
        assertTrue(result.pass)
    }

    @Test
    fun `SITE_PHOTO taken 8 days AFTER processing date fails`() {
        val result = DateTolerancePolicy.evaluate(processingDate.plusDays(8), processingDate, EvidenceType.SITE_PHOTO)
        assertFalse(result.pass)
        assertTrue(result.detail.contains("AFTER", ignoreCase = true))
    }

    // ── INVOICE ───────────────────────────────────────────────────────────────

    @Test
    fun `INVOICE dated 25 days before processing passes`() {
        val result = DateTolerancePolicy.evaluate(processingDate.minusDays(25), processingDate, EvidenceType.INVOICE)
        assertTrue(result.pass)
    }

    @Test
    fun `INVOICE dated 35 days before processing fails`() {
        val result = DateTolerancePolicy.evaluate(processingDate.minusDays(35), processingDate, EvidenceType.INVOICE)
        assertFalse(result.pass)
    }

    // ── REGISTRATION_CERTIFICATE ──────────────────────────────────────────────

    @Test
    fun `REGISTRATION_CERTIFICATE from 3 years ago passes`() {
        val result = DateTolerancePolicy.evaluate(processingDate.minusYears(3), processingDate, EvidenceType.REGISTRATION_CERTIFICATE)
        assertTrue(result.pass)
        assertTrue(result.detail.contains("long-lived", ignoreCase = true))
    }

    @Test
    fun `REGISTRATION_CERTIFICATE dated after processing date fails`() {
        val result = DateTolerancePolicy.evaluate(processingDate.plusDays(10), processingDate, EvidenceType.REGISTRATION_CERTIFICATE)
        assertFalse(result.pass)
        assertTrue(result.detail.contains("after", ignoreCase = true))
    }

    // ── AUDIT_REPORT ─────────────────────────────────────────────────────────

    @Test
    fun `AUDIT_REPORT from same FY passes`() {
        // Processing date is March 2025 (FY 2024-25); report from November 2024 (same FY)
        val result = DateTolerancePolicy.evaluate(
            LocalDate.of(2024, 11, 1), processingDate, EvidenceType.AUDIT_REPORT
        )
        assertTrue(result.pass)
    }

    @Test
    fun `AUDIT_REPORT from previous FY fails`() {
        // Processing date is March 2025 (FY 2024-25); report from FY 2023-24
        val result = DateTolerancePolicy.evaluate(
            LocalDate.of(2023, 12, 1), processingDate, EvidenceType.AUDIT_REPORT
        )
        assertFalse(result.pass)
        assertTrue(result.detail.contains("different financial years", ignoreCase = true))
    }
}
