package com.rorapps.eprid.service.calculator

import com.rorapps.eprid.constants.EcRates
import com.rorapps.eprid.constants.FinancialYear
import com.rorapps.eprid.constants.RecoveryTargets
import com.rorapps.eprid.dto.calculator.CallToAction
import com.rorapps.eprid.dto.calculator.ComplianceEstimateRequest
import com.rorapps.eprid.dto.calculator.ComplianceEstimateResponse
import com.rorapps.eprid.dto.calculator.EcExposureBreakdown
import com.rorapps.eprid.entity.ComplianceEstimate
import com.rorapps.eprid.repository.ComplianceEstimateRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode

@Service
class ComplianceCalculatorService(
    private val estimateRepository: ComplianceEstimateRepository
) {

    companion object {
        private val KG_PER_TONNE = BigDecimal("1000")
        private const val DISCLAIMER =
            "This is an estimate for planning purposes only. It is not a substitute for your official " +
            "CPCB portal filing, a formal compliance audit, or professional legal/financial advice. " +
            "Target percentages are sourced from BWMR 2022 Schedule II and may be amended; " +
            "verify against the latest gazette notification before acting on this estimate."
        private const val CTA_MESSAGE =
            "You are about to buy certificates. Verify the recycler before committing to this volume."
        private const val CTA_ACTION = "VERIFY_RECYCLER"
    }

    @Transactional
    fun calculate(request: ComplianceEstimateRequest, userId: String? = null): ComplianceEstimateResponse {
        val fy = FinancialYear.fromLabel(request.financialYear)
        val targetRate = RecoveryTargets.getTarget(request.batteryCategory, fy)
        val targetPercent = RecoveryTargets.getTargetPercent(request.batteryCategory, fy)

        val fulfilled = request.quantityAlreadyFulfilledTonnes ?: BigDecimal.ZERO
        val targetTonnes = request.quantityPlacedTonnes.multiply(targetRate).setScale(3, RoundingMode.HALF_UP)
        val shortfallTonnes = (targetTonnes - fulfilled).coerceAtLeast(BigDecimal.ZERO).setScale(3, RoundingMode.HALF_UP)
        val shortfallKg = shortfallTonnes.multiply(KG_PER_TONNE).setScale(3, RoundingMode.HALF_UP)

        val saved = estimateRepository.save(
            ComplianceEstimate(
                batteryCategory = request.batteryCategory,
                financialYear = request.financialYear,
                quantityPlacedTonnes = request.quantityPlacedTonnes,
                quantityAlreadyFulfilledTonnes = fulfilled,
                recoveryTargetPercent = targetPercent,
                targetTonnes = targetTonnes,
                shortfallTonnes = shortfallTonnes,
                shortfallKg = shortfallKg,
                userId = userId
            )
        )

        val ecExposure = computeEcExposure(request.batteryCategory, shortfallTonnes)

        return ComplianceEstimateResponse(
            estimateId = saved.id!!,
            batteryCategory = request.batteryCategory,
            financialYear = request.financialYear,
            quantityPlacedTonnes = request.quantityPlacedTonnes,
            quantityAlreadyFulfilledTonnes = fulfilled,
            recoveryTargetPercent = targetPercent,
            targetTonnes = targetTonnes,
            shortfallTonnes = shortfallTonnes,
            shortfallKg = shortfallKg,
            ecExposure = ecExposure,
            disclaimer = DISCLAIMER,
            callToAction = CallToAction(message = CTA_MESSAGE, action = CTA_ACTION)
        )
    }

    private fun computeEcExposure(category: com.rorapps.eprid.constants.BatteryCategory, shortfallTonnes: BigDecimal): EcExposureBreakdown? {
        if (shortfallTonnes <= BigDecimal.ZERO) return null
        val rate = EcRates.EC_RATE_PER_TONNE[category] ?: return null
        val deposited = shortfallTonnes.multiply(rate).setScale(0, RoundingMode.HALF_UP)
        fun netCost(yearsElapsed: Int): BigDecimal {
            val refund = deposited.multiply(EcRates.refundPercent(yearsElapsed)).setScale(0, RoundingMode.HALF_UP)
            return deposited.subtract(refund)
        }
        return EcExposureBreakdown(
            ecDepositedRs = deposited,
            netIfResolvedYear1Rs = netCost(1),
            netIfResolvedYear2Rs = netCost(2),
            netIfResolvedYear3Rs = netCost(3),
            netIfForfeitedRs = deposited,
            ecRatePerTonneRs = rate,
            caveat = "EC exposure is time-varying. Resolving the shortfall earlier reduces net cost " +
                     "via the carry-forward refund (75% / 60% / 40% in years 1–3; full forfeiture " +
                     "after year 3). Late payment attracts 12% p.a. (≤1 month) or 24% p.a. (1–3 months). " +
                     "Source: CPCB EC Guidelines Aug 2024."
        )
    }
}

private fun BigDecimal.coerceAtLeast(min: BigDecimal): BigDecimal = if (this < min) min else this
