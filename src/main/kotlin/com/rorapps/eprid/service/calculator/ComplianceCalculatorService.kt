package com.rorapps.eprid.service.calculator

import com.rorapps.eprid.constants.BatteryCategory
import com.rorapps.eprid.constants.EcRates
import com.rorapps.eprid.constants.FinancialYear
import com.rorapps.eprid.constants.RecoveryTargets
import com.rorapps.eprid.constants.RecycledContentMinimums
import com.rorapps.eprid.dto.calculator.CallToAction
import com.rorapps.eprid.dto.calculator.ComplianceEstimateRequest
import com.rorapps.eprid.dto.calculator.ComplianceEstimateResponse
import com.rorapps.eprid.dto.calculator.EcExposureBreakdown
import com.rorapps.eprid.dto.calculator.RecycledContentObligation
import com.rorapps.eprid.dto.calculator.RecycledContentRampEntry
import com.rorapps.eprid.dto.calculator.RecyclingObligation
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
        private const val REFERENCE_YEAR_CAVEAT =
            "Schedule II applies this percentage to the quantity placed in market in referenceFinancialYear, " +
            "not the FY you entered — this calculator doesn't yet track a producer's prior-year placed " +
            "quantities, so the amount you entered is used as a same-year proxy."
        private const val CTA_MESSAGE =
            "You are about to buy certificates. Verify the recycler before committing to this volume."
        private const val CTA_ACTION = "VERIFY_RECYCLER"
    }

    @Transactional
    fun calculate(request: ComplianceEstimateRequest, userId: String? = null): ComplianceEstimateResponse {
        val fy = FinancialYear.fromLabel(request.financialYear)
        val fulfilled = request.quantityAlreadyFulfilledTonnes ?: BigDecimal.ZERO
        val recycledContentObligation = recycledContentObligation(request.batteryCategory, fy)

        val ramp = RecoveryTargets.getRamp(request.batteryCategory, fy)
            ?: return notApplicableResponse(request, fulfilled, recycledContentObligation)

        val targetPercent = (ramp.ratePercent * BigDecimal("100")).toInt()
        val targetTonnes = request.quantityPlacedTonnes.multiply(ramp.ratePercent).setScale(3, RoundingMode.HALF_UP)
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
            applicable = true,
            recoveryTargetPercent = targetPercent,
            referenceFinancialYear = ramp.referenceFinancialYear,
            targetTonnes = targetTonnes,
            shortfallTonnes = shortfallTonnes,
            shortfallKg = shortfallKg,
            complianceCycleYears = ramp.complianceCycleYears,
            recyclingRefurbishmentObligation = RecyclingObligation(
                cycleYears = ramp.complianceCycleYears,
                note = "100% of whatever is actually collected must be refurbished or recycled by the end of " +
                    "this ${ramp.complianceCycleYears}-year compliance cycle — a separate obligation from the " +
                    "collection target above, not an annual requirement."
            ),
            carryForwardCapPercent = (ramp.carryForwardCapPercent * BigDecimal("100")).toInt(),
            carryForwardBasisNote = "Up to ${(ramp.carryForwardCapPercent * BigDecimal("100")).toInt()}% of the " +
                "remaining quantity of battery placed in the market during this compliance cycle may be carried " +
                "forward to the next one (G.S.R. 190(E), 14-Mar-2024 — unified wording/basis across all categories).",
            recycledContentObligation = recycledContentObligation,
            ecExposure = ecExposure,
            disclaimer = "$DISCLAIMER $REFERENCE_YEAR_CAVEAT",
            callToAction = CallToAction(message = CTA_MESSAGE, action = CTA_ACTION)
        )
    }

    /** Nothing is persisted here — there's no target to compute, and an estimate row with a fabricated
     *  0%/shortfall would misrepresent a category whose Schedule II cycle hasn't started yet. */
    private fun notApplicableResponse(
        request: ComplianceEstimateRequest,
        fulfilled: BigDecimal,
        recycledContentObligation: RecycledContentObligation
    ): ComplianceEstimateResponse {
        val startsFy = RecoveryTargets.scheduleStartFy(request.batteryCategory)
        return ComplianceEstimateResponse(
            estimateId = null,
            batteryCategory = request.batteryCategory,
            financialYear = request.financialYear,
            quantityPlacedTonnes = request.quantityPlacedTonnes,
            quantityAlreadyFulfilledTonnes = fulfilled,
            applicable = false,
            notApplicableReason = "Schedule II's collection-target cycle for ${request.batteryCategory} does " +
                "not start until FY $startsFy — there is no mandatory collection target for FY ${request.financialYear}.",
            recycledContentObligation = recycledContentObligation,
            ecExposure = null,
            disclaimer = DISCLAIMER,
            callToAction = CallToAction(message = CTA_MESSAGE, action = CTA_ACTION)
        )
    }

    private fun recycledContentObligation(category: BatteryCategory, fy: FinancialYear): RecycledContentObligation {
        val applicableNow = fy.label >= RecycledContentMinimums.STARTS_FY
        return RecycledContentObligation(
            startsFinancialYear = RecycledContentMinimums.STARTS_FY,
            applicableNow = applicableNow,
            ramp = RecycledContentMinimums.rampFor(category).map {
                RecycledContentRampEntry(it.financialYear, (it.ratePercent * BigDecimal("100")).toInt())
            },
            note = "Rule 4(14) (S.O. 2374(E), 20-Jun-2024) — a minimum-recycled-material-content requirement on " +
                "NEW batteries manufactured, separate from the collection target above (different quantity base: " +
                "dry weight of battery manufactured, not collected). Informational only — not computed into the " +
                "shortfall or EC exposure above; doesn't apply to any FY this calculator currently supports " +
                "(starts FY ${RecycledContentMinimums.STARTS_FY})."
        )
    }

    private fun computeEcExposure(category: BatteryCategory, shortfallTonnes: BigDecimal): EcExposureBreakdown? {
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
