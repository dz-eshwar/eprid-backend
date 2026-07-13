package com.rorapps.eprid.service

import com.rorapps.eprid.constants.BatteryCategory
import com.rorapps.eprid.constants.FinancialYear
import com.rorapps.eprid.constants.RecoveryTargets
import com.rorapps.eprid.dto.calculator.ComplianceEstimateRequest
import com.rorapps.eprid.entity.ComplianceEstimate
import com.rorapps.eprid.repository.ComplianceEstimateRepository
import com.rorapps.eprid.service.calculator.ComplianceCalculatorService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import java.math.BigDecimal

/**
 * Rewritten 2026-07-10 for the real Schedule II model (replaces the 2026-07-10 stage-1-patch test
 * version) — see RecoveryTargets.kt's header for the full sourced ramp per category and what's
 * deliberately still out of scope (reference-year quantity tracking, carry-forward tonnage,
 * recycled-content shortfall calculation).
 */
@ExtendWith(MockitoExtension::class)
class ComplianceCalculatorServiceTest {

    @Mock
    private lateinit var estimateRepository: ComplianceEstimateRepository

    @InjectMocks
    private lateinit var service: ComplianceCalculatorService

    private fun stubSave() {
        whenever(estimateRepository.save(any<ComplianceEstimate>())).thenAnswer { invocation ->
            (invocation.arguments[0] as ComplianceEstimate).copy(id = "test-uuid")
        }
    }

    // ── PORTABLE_RECHARGEABLE — plateaus 70% from FY2024-25 (clause vi), constant -5yr reference offset ──

    @Test
    fun `PORTABLE_RECHARGEABLE FY2024-25 - full shortfall when nothing fulfilled`() {
        stubSave()
        val request = ComplianceEstimateRequest(
            batteryCategory = BatteryCategory.PORTABLE_RECHARGEABLE,
            financialYear = "2024-25",
            quantityPlacedTonnes = BigDecimal("100"),
            quantityAlreadyFulfilledTonnes = null
        )

        val result = service.calculate(request)

        assertTrue(result.applicable)
        assertEquals(70, result.recoveryTargetPercent)
        assertEquals("2019-20", result.referenceFinancialYear)
        assertEquals(BigDecimal("70.000"), result.targetTonnes)
        assertEquals(BigDecimal("70.000"), result.shortfallTonnes)
        assertEquals(BigDecimal("70000.000"), result.shortfallKg)
        assertEquals(10, result.complianceCycleYears)
        assertEquals(60, result.carryForwardCapPercent)
        assertNotNull(result.estimateId)
    }

    @Test
    fun `PORTABLE_RECHARGEABLE FY2025-26 - partial fulfillment reduces shortfall`() {
        stubSave()
        val request = ComplianceEstimateRequest(
            batteryCategory = BatteryCategory.PORTABLE_RECHARGEABLE,
            financialYear = "2025-26",
            quantityPlacedTonnes = BigDecimal("100"),
            quantityAlreadyFulfilledTonnes = BigDecimal("30")
        )

        val result = service.calculate(request)

        assertEquals(70, result.recoveryTargetPercent)
        assertEquals(BigDecimal("70.000"), result.targetTonnes)
        assertEquals(BigDecimal("40.000"), result.shortfallTonnes)    // 70 - 30
        assertEquals(BigDecimal("40000.000"), result.shortfallKg)
    }

    // ── PORTABLE_NON_RECHARGEABLE — different plateau year (FY2027-28), cycle starts FY2025-26 ──

    @Test
    fun `PORTABLE_NON_RECHARGEABLE FY2024-25 is not yet applicable - cycle starts FY2025-26`() {
        val request = ComplianceEstimateRequest(
            batteryCategory = BatteryCategory.PORTABLE_NON_RECHARGEABLE,
            financialYear = "2024-25",
            quantityPlacedTonnes = BigDecimal("100")
        )

        val result = service.calculate(request)

        assertFalse(result.applicable)
        assertNull(result.estimateId)
        assertNull(result.recoveryTargetPercent)
        assertNull(result.shortfallTonnes)
        assertNull(result.ecExposure)
        assertTrue(result.notApplicableReason!!.contains("2025-26"))
    }

    @Test
    fun `PORTABLE_NON_RECHARGEABLE FY2025-26 is mid-ramp at 50 percent, not plateaued`() {
        stubSave()
        val request = ComplianceEstimateRequest(
            batteryCategory = BatteryCategory.PORTABLE_NON_RECHARGEABLE,
            financialYear = "2025-26",
            quantityPlacedTonnes = BigDecimal("100")
        )

        val result = service.calculate(request)

        assertTrue(result.applicable)
        assertEquals(50, result.recoveryTargetPercent)
        assertEquals("2022-23", result.referenceFinancialYear)
    }

    // ── AUTOMOTIVE / INDUSTRIAL diverge by FY2025-26 (Automotive plateaus 90%, Industrial 70%) ──

    @Test
    fun `shortfall is zero when fulfilled exceeds target`() {
        stubSave()
        // Automotive FY2025-26 target is 90% (ramp plateau) — fulfilled must exceed 90 tonnes of 100
        // placed to actually demonstrate the zero-shortfall floor.
        val request = ComplianceEstimateRequest(
            batteryCategory = BatteryCategory.AUTOMOTIVE,
            financialYear = "2025-26",
            quantityPlacedTonnes = BigDecimal("100"),
            quantityAlreadyFulfilledTonnes = BigDecimal("95")   // target is 90%
        )

        val result = service.calculate(request)

        assertEquals(90, result.recoveryTargetPercent)
        assertEquals(BigDecimal("0.000"), result.shortfallTonnes)
        assertEquals(BigDecimal("0.000"), result.shortfallKg)
        assertNull(result.ecExposure) // no shortfall -> no EC exposure
    }

    @Test
    fun `AUTOMOTIVE and INDUSTRIAL targets diverge by FY2025-26 despite similar ramps`() {
        assertEquals(90, RecoveryTargets.getRamp(BatteryCategory.AUTOMOTIVE, FinancialYear.FY_2025_26)!!.ratePercent.multiply(BigDecimal(100)).toInt())
        assertEquals(70, RecoveryTargets.getRamp(BatteryCategory.INDUSTRIAL, FinancialYear.FY_2025_26)!!.ratePercent.multiply(BigDecimal(100)).toInt())
    }

    // ── EV sub-categories — three-wheeler/two-wheeler don't start until FY2026-27; four-wheeler ────
    // ── doesn't apply to any FY this app currently supports at all ──────────────────────────────

    @Test
    fun `EV_THREE_WHEELER FY2026-27 applies 70 percent flat target`() {
        stubSave()
        val request = ComplianceEstimateRequest(
            batteryCategory = BatteryCategory.EV_THREE_WHEELER,
            financialYear = "2026-27",
            quantityPlacedTonnes = BigDecimal("200"),
            quantityAlreadyFulfilledTonnes = null
        )

        val result = service.calculate(request)

        assertTrue(result.applicable)
        assertEquals(70, result.recoveryTargetPercent)
        assertEquals(BigDecimal("140.000"), result.targetTonnes)
        assertEquals(BigDecimal("140000.000"), result.shortfallKg)
    }

    @Test
    fun `EV_THREE_WHEELER FY2024-25 is not yet applicable - amended cycle starts FY2026-27`() {
        val request = ComplianceEstimateRequest(
            batteryCategory = BatteryCategory.EV_THREE_WHEELER,
            financialYear = "2024-25",
            quantityPlacedTonnes = BigDecimal("200")
        )

        val result = service.calculate(request)

        assertFalse(result.applicable)
    }

    @Test
    fun `EV_TWO_WHEELER FY2025-26 is not yet applicable`() {
        val request = ComplianceEstimateRequest(
            batteryCategory = BatteryCategory.EV_TWO_WHEELER,
            financialYear = "2025-26",
            quantityPlacedTonnes = BigDecimal("200")
        )

        val result = service.calculate(request)

        assertFalse(result.applicable)
    }

    @Test
    fun `EV_FOUR_WHEELER is not yet applicable for any currently supported FY`() {
        for (fy in listOf("2024-25", "2025-26", "2026-27")) {
            val request = ComplianceEstimateRequest(
                batteryCategory = BatteryCategory.EV_FOUR_WHEELER,
                financialYear = fy,
                quantityPlacedTonnes = BigDecimal("200")
            )
            val result = service.calculate(request)
            assertFalse(result.applicable, "EV_FOUR_WHEELER should not be applicable in $fy")
            assertTrue(result.notApplicableReason!!.contains("2029-30"))
        }
    }

    // ── compliance cycle / carry-forward / recycled-content informational fields ─────────────────

    @Test
    fun `compliance cycle lengths differ by category - EV_FOUR_WHEELER is the only 14-year one`() {
        assertEquals("2029-30", RecoveryTargets.scheduleStartFy(BatteryCategory.EV_FOUR_WHEELER))
        assertEquals(10, RecoveryTargets.getRamp(BatteryCategory.PORTABLE_RECHARGEABLE, FinancialYear.FY_2024_25)!!.complianceCycleYears)
        assertEquals(7, RecoveryTargets.getRamp(BatteryCategory.AUTOMOTIVE, FinancialYear.FY_2024_25)!!.complianceCycleYears)
        assertEquals(7, RecoveryTargets.getRamp(BatteryCategory.EV_THREE_WHEELER, FinancialYear.FY_2026_27)!!.complianceCycleYears)
    }

    @Test
    fun `carry-forward cap is a uniform 60 percent post G_S_R_190E, including Automotive`() {
        // Pre-amendment Automotive was capped at 20% — G.S.R. 190(E) (14-Mar-2024) unified every
        // category to 60% of the remaining quantity placed during the cycle. Automotive must reflect
        // the current rule, not the historical 20% figure.
        stubSave()
        val request = ComplianceEstimateRequest(
            batteryCategory = BatteryCategory.AUTOMOTIVE,
            financialYear = "2024-25",
            quantityPlacedTonnes = BigDecimal("100")
        )
        val result = service.calculate(request)
        assertEquals(60, result.carryForwardCapPercent)
        assertTrue(result.carryForwardBasisNote!!.contains("remaining quantity"))
    }

    @Test
    fun `response always includes the recycling-refurbishment obligation when applicable`() {
        stubSave()
        val request = ComplianceEstimateRequest(
            batteryCategory = BatteryCategory.INDUSTRIAL,
            financialYear = "2024-25",
            quantityPlacedTonnes = BigDecimal("100")
        )
        val result = service.calculate(request)
        assertNotNull(result.recyclingRefurbishmentObligation)
        assertEquals(100, result.recyclingRefurbishmentObligation!!.percent)
        assertEquals(7, result.recyclingRefurbishmentObligation!!.cycleYears)
    }

    @Test
    fun `recycled-content obligation is informational and not yet applicable for any supported FY`() {
        stubSave()
        val request = ComplianceEstimateRequest(
            batteryCategory = BatteryCategory.PORTABLE_RECHARGEABLE,
            financialYear = "2026-27",
            quantityPlacedTonnes = BigDecimal("100")
        )
        val result = service.calculate(request)
        assertNotNull(result.recycledContentObligation)
        assertFalse(result.recycledContentObligation!!.applicableNow)
        assertEquals("2027-28", result.recycledContentObligation!!.startsFinancialYear)
        assertEquals(4, result.recycledContentObligation!!.ramp.size)
        // Informational only - the collection-target shortfall above is computed independently:
        assertNotNull(result.shortfallTonnes)
    }

    @Test
    fun `unsupported financial year throws IllegalArgumentException`() {
        val request = ComplianceEstimateRequest(
            batteryCategory = BatteryCategory.PORTABLE_RECHARGEABLE,
            financialYear = "2099-00",
            quantityPlacedTonnes = BigDecimal("100")
        )
        assertThrows(IllegalArgumentException::class.java) {
            service.calculate(request)
        }
    }

    @Test
    fun `response includes non-empty disclaimer and CTA`() {
        stubSave()
        val request = ComplianceEstimateRequest(
            batteryCategory = BatteryCategory.PORTABLE_RECHARGEABLE,
            financialYear = "2024-25",
            quantityPlacedTonnes = BigDecimal("50")
        )

        val result = service.calculate(request)

        assertTrue(result.disclaimer.isNotBlank())
        assertTrue(result.callToAction.message.isNotBlank())
        assertEquals("VERIFY_RECYCLER", result.callToAction.action)
    }
}
