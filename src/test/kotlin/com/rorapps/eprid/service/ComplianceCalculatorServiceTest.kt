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

    @Test
    fun `PORTABLE FY2024-25 - full shortfall when nothing fulfilled`() {
        stubSave()
        val request = ComplianceEstimateRequest(
            batteryCategory = BatteryCategory.PORTABLE,
            financialYear = "2024-25",
            quantityPlacedTonnes = BigDecimal("100"),
            quantityAlreadyFulfilledTonnes = null
        )

        val result = service.calculate(request)

        assertEquals(70, result.recoveryTargetPercent)
        assertEquals(BigDecimal("70.000"), result.targetTonnes)
        assertEquals(BigDecimal("70.000"), result.shortfallTonnes)
        assertEquals(BigDecimal("70000.000"), result.shortfallKg)
    }

    @Test
    fun `PORTABLE FY2025-26 - partial fulfillment reduces shortfall`() {
        stubSave()
        val request = ComplianceEstimateRequest(
            batteryCategory = BatteryCategory.PORTABLE,
            financialYear = "2025-26",
            quantityPlacedTonnes = BigDecimal("100"),
            quantityAlreadyFulfilledTonnes = BigDecimal("30")
        )

        val result = service.calculate(request)

        assertEquals(80, result.recoveryTargetPercent)
        assertEquals(BigDecimal("80.000"), result.targetTonnes)
        assertEquals(BigDecimal("50.000"), result.shortfallTonnes)    // 80 - 30
        assertEquals(BigDecimal("50000.000"), result.shortfallKg)
    }

    @Test
    fun `shortfall is zero when fulfilled exceeds target`() {
        stubSave()
        val request = ComplianceEstimateRequest(
            batteryCategory = BatteryCategory.AUTOMOTIVE,
            financialYear = "2025-26",
            quantityPlacedTonnes = BigDecimal("100"),
            quantityAlreadyFulfilledTonnes = BigDecimal("65")   // target is 60%
        )

        val result = service.calculate(request)

        assertEquals(60, result.recoveryTargetPercent)
        assertEquals(BigDecimal("0.000"), result.shortfallTonnes)
        assertEquals(BigDecimal("0.000"), result.shortfallKg)
    }

    @Test
    fun `ELECTRIC_VEHICLE FY2026-27 applies 90 percent target`() {
        stubSave()
        val request = ComplianceEstimateRequest(
            batteryCategory = BatteryCategory.ELECTRIC_VEHICLE,
            financialYear = "2026-27",
            quantityPlacedTonnes = BigDecimal("200"),
            quantityAlreadyFulfilledTonnes = null
        )

        val result = service.calculate(request)

        assertEquals(90, result.recoveryTargetPercent)
        assertEquals(BigDecimal("180.000"), result.targetTonnes)
        assertEquals(BigDecimal("180000.000"), result.shortfallKg)
    }

    @Test
    fun `INDUSTRIAL target matches AUTOMOTIVE for same FY`() {
        assertEquals(
            RecoveryTargets.getTargetPercent(BatteryCategory.INDUSTRIAL, FinancialYear.FY_2024_25),
            RecoveryTargets.getTargetPercent(BatteryCategory.AUTOMOTIVE, FinancialYear.FY_2024_25)
        )
    }

    @Test
    fun `unsupported financial year throws IllegalArgumentException`() {
        val request = ComplianceEstimateRequest(
            batteryCategory = BatteryCategory.PORTABLE,
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
            batteryCategory = BatteryCategory.PORTABLE,
            financialYear = "2024-25",
            quantityPlacedTonnes = BigDecimal("50")
        )

        val result = service.calculate(request)

        assertTrue(result.disclaimer.isNotBlank())
        assertTrue(result.callToAction.message.isNotBlank())
        assertEquals("VERIFY_RECYCLER", result.callToAction.action)
    }
}
