package com.rorapps.eprid.service.plausibility

import com.rorapps.eprid.entity.*
import com.rorapps.eprid.repository.PlausibilityCheckRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.math.BigDecimal
import java.time.LocalDate

class PlausibilityCheckServiceTest {

    private val repo    = mock<PlausibilityCheckRepository>()
    private val service = PlausibilityCheckService(repo, listOf(BatteryPlausibilityStrategy(), TyrePlausibilityStrategy()))

    @BeforeEach
    fun setup() {
        whenever(repo.save(any<PlausibilityCheck>())).thenAnswer { it.arguments[0] }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun makeCheck(
        batchWeightT: String,
        recoveryPct: String,
        annualCapacityT: String? = null
    ): VerificationCheck {
        val user = User(id = "u1", email = "x@x.com", fullName = "X", passwordHash = "", role = UserRole.CONSULTANT)
        val recycler = Recycler(
            id = "r1",
            name = "Test Recycler",
            selfReportedCapacityT = annualCapacityT?.let { BigDecimal(it) }
        )
        val producer = Producer(
            id = "p1",
            name = "Test Producer",
            createdBy = user
        )
        return VerificationCheck(
            id = "c1",
            producer = producer,
            recycler = recycler,
            requestedBy = user,
            batchWeightTonnes = BigDecimal(batchWeightT),
            claimedRecoveryPct = BigDecimal(recoveryPct),
            processingDate = LocalDate.now()
        )
    }

    // ── recovery rate ─────────────────────────────────────────────────────────

    @Test
    fun `recovery rate within norm passes`() {
        val result = service.runAndSave(makeCheck("100", "75"))
        val sub = result.subChecks.first { it.name == "Recovery rate plausibility" }
        assertEquals(SubCheckStatus.PASS, sub.status)
    }

    @Test
    fun `recovery rate above warn threshold is WARN`() {
        val result = service.runAndSave(makeCheck("100", "96"))
        val sub = result.subChecks.first { it.name == "Recovery rate plausibility" }
        assertEquals(SubCheckStatus.WARN, sub.status)
    }

    @Test
    fun `recovery rate above 99 percent fails`() {
        val result = service.runAndSave(makeCheck("100", "99.5"))
        val sub = result.subChecks.first { it.name == "Recovery rate plausibility" }
        assertEquals(SubCheckStatus.FAIL, sub.status)
        assertTrue(sub.detail.contains("physically impossible"))
    }

    @Test
    fun `recovery rate below 40 percent is WARN`() {
        val result = service.runAndSave(makeCheck("100", "35"))
        val sub = result.subChecks.first { it.name == "Recovery rate plausibility" }
        assertEquals(SubCheckStatus.WARN, sub.status)
    }

    // ── capacity ceiling ──────────────────────────────────────────────────────

    @Test
    fun `no self reported capacity gives UNVERIFIABLE on capacity check`() {
        val result = service.runAndSave(makeCheck("200", "75", annualCapacityT = null))
        val sub = result.subChecks.first { it.name == "Capacity ceiling check" }
        assertEquals(SubCheckStatus.UNVERIFIABLE, sub.status)
    }

    @Test
    fun `batch within 40 percent of capacity passes`() {
        val result = service.runAndSave(makeCheck("300", "75", annualCapacityT = "1000"))
        val sub = result.subChecks.first { it.name == "Capacity ceiling check" }
        assertEquals(SubCheckStatus.PASS, sub.status)
    }

    @Test
    fun `batch over annual capacity fails`() {
        val result = service.runAndSave(makeCheck("1200", "75", annualCapacityT = "1000"))
        val sub = result.subChecks.first { it.name == "Capacity ceiling check" }
        assertEquals(SubCheckStatus.FAIL, sub.status)
        assertTrue(sub.detail.contains("exceeds"))
    }

    @Test
    fun `batch at 50 percent of capacity is WARN`() {
        val result = service.runAndSave(makeCheck("500", "75", annualCapacityT = "1000"))
        val sub = result.subChecks.first { it.name == "Capacity ceiling check" }
        assertEquals(SubCheckStatus.WARN, sub.status)
    }

    // ── absolute batch size ───────────────────────────────────────────────────

    @Test
    fun `small batch passes absolute check`() {
        val result = service.runAndSave(makeCheck("100", "75"))
        val sub = result.subChecks.first { it.name == "Absolute batch size check" }
        assertEquals(SubCheckStatus.PASS, sub.status)
    }

    @Test
    fun `batch over 5000 T fails absolute check`() {
        val result = service.runAndSave(makeCheck("6000", "75"))
        val sub = result.subChecks.first { it.name == "Absolute batch size check" }
        assertEquals(SubCheckStatus.FAIL, sub.status)
    }

    // ── overall rollup ────────────────────────────────────────────────────────

    @Test
    fun `any FAIL makes overall FAIL`() {
        val result = service.runAndSave(makeCheck("100", "99.9"))
        assertEquals(SubCheckStatus.FAIL, result.overallStatus)
    }

    @Test
    fun `pass checks with no capacity gives UNVERIFIABLE overall`() {
        // Recovery and batch size both PASS, but capacity is UNVERIFIABLE (no self-reported capacity)
        // deriveOverall has no FAIL/WARN but not all PASS → UNVERIFIABLE
        val result = service.runAndSave(makeCheck("100", "75"))
        assertEquals(SubCheckStatus.UNVERIFIABLE, result.overallStatus)
    }

    @Test
    fun `all checks pass when capacity provided and batch is small`() {
        val result = service.runAndSave(makeCheck("100", "75", annualCapacityT = "1000"))
        assertEquals(SubCheckStatus.PASS, result.overallStatus)
    }
}
