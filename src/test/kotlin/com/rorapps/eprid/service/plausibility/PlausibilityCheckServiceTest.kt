package com.rorapps.eprid.service.plausibility

import com.rorapps.eprid.constants.TyreEndProduct
import com.rorapps.eprid.constants.WasteStreamType
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

    private fun makeTyreCheck(
        qp: String?,
        endProduct: TyreEndProduct?,
        claimedCreditKg: String?,
        imported: Boolean = false
    ): VerificationCheck {
        val user = User(id = "u1", email = "x@x.com", fullName = "X", passwordHash = "", role = UserRole.CONSULTANT)
        val recycler = Recycler(id = "r1", name = "Test Tyre Recycler", wasteStream = WasteStreamType.TYRE)
        val producer = Producer(id = "p1", name = "Test Producer", createdBy = user, wasteStream = WasteStreamType.TYRE)
        return VerificationCheck(
            id = "c1",
            producer = producer,
            recycler = recycler,
            requestedBy = user,
            wasteStream = WasteStreamType.TYRE,
            batchWeightTonnes = BigDecimal("100"),
            claimedRecoveryPct = BigDecimal("75"),
            processingDate = LocalDate.now(),
            claimedOutputQuantity = qp?.let { BigDecimal(it) },
            tyreEndProduct = endProduct,
            tyreImported = imported,
            claimedEprCreditKg = claimedCreditKg?.let { BigDecimal(it) }
        )
    }

    // ── tyre EPR credit reconciliation (QEPR = QP × CF × WP) ───────────────────

    @Test
    fun `tyre reconciliation passes when claimed credit matches formula`() {
        // QP=1000, CRUMB_RUBBER: CF=1.333, WP=1.0 -> QEPR = 1333.000
        val result = service.runAndSave(makeTyreCheck("1000", TyreEndProduct.CRUMB_RUBBER, "1333"))
        val sub = result.subChecks.first { it.name == "EPR credit reconciliation (QEPR = QP × CF × WP)" }
        assertEquals(SubCheckStatus.PASS, sub.status)
    }

    @Test
    fun `tyre reconciliation warns on moderate deviation`() {
        // Computed QEPR = 1333; claim it at 1466 (~10% over) -> WARN
        val result = service.runAndSave(makeTyreCheck("1000", TyreEndProduct.CRUMB_RUBBER, "1466"))
        val sub = result.subChecks.first { it.name == "EPR credit reconciliation (QEPR = QP × CF × WP)" }
        assertEquals(SubCheckStatus.WARN, sub.status)
    }

    @Test
    fun `tyre reconciliation fails on large deviation`() {
        val result = service.runAndSave(makeTyreCheck("1000", TyreEndProduct.CRUMB_RUBBER, "3000"))
        val sub = result.subChecks.first { it.name == "EPR credit reconciliation (QEPR = QP × CF × WP)" }
        assertEquals(SubCheckStatus.FAIL, sub.status)
    }

    @Test
    fun `tyre reconciliation is unverifiable when end product missing`() {
        val result = service.runAndSave(makeTyreCheck("1000", null, "1333"))
        val sub = result.subChecks.first { it.name == "EPR credit reconciliation (QEPR = QP × CF × WP)" }
        assertEquals(SubCheckStatus.UNVERIFIABLE, sub.status)
    }

    @Test
    fun `imported tyre forces weightage to 1 regardless of end product`() {
        // RECLAIMED_RUBBER has WP=1.3 normally; imported forces WP=1.0 -> QEPR = 1000*1.298*1.0 = 1298
        val result = service.runAndSave(makeTyreCheck("1000", TyreEndProduct.RECLAIMED_RUBBER, "1298", imported = true))
        val sub = result.subChecks.first { it.name == "EPR credit reconciliation (QEPR = QP × CF × WP)" }
        assertEquals(SubCheckStatus.PASS, sub.status)
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
