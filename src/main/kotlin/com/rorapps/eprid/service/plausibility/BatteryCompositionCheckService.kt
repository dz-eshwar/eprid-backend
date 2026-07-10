package com.rorapps.eprid.service.plausibility

import com.rorapps.eprid.constants.BatteryCompositionRanges
import com.rorapps.eprid.constants.BatteryMetal
import com.rorapps.eprid.constants.CompositionCheckResult
import com.rorapps.eprid.entity.ClaimedMetalRecovery
import com.rorapps.eprid.entity.MetalCompositionCheck
import com.rorapps.eprid.entity.VerificationCheck
import com.rorapps.eprid.repository.MetalCompositionCheckRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Battery composition-table check (feature_spec_close_scoring_gaps.md §1) — compares each claimed
 * metal recovery weight against CPCB's published per-chemistry composition range. Called from
 * [VerificationCheckService] right after [ClaimedMetalRecovery] rows are persisted for a battery
 * check, alongside [BatteryPlausibilityStrategy]'s capacity/batch-size checks.
 *
 * A [CompositionCheckResult.ZERO_CELL_VIOLATION] (claimed weight > 0 for a metal whose max is 0%
 * for the declared chemistry) is chemistry-impossible, not just out-of-range — [CompositeScoringService]
 * treats it as hard-disqualification (rule 3). A metal the table tracks but with no claimed weight
 * submitted is [CompositionCheckResult.COULD_NOT_VERIFY], never a silent PASS (PRD §7.1, line 123).
 */
@Service
class BatteryCompositionCheckService(
    private val repository: MetalCompositionCheckRepository
) {

    @Transactional
    fun runAndSave(check: VerificationCheck, claimedRecoveries: List<ClaimedMetalRecovery>): List<MetalCompositionCheck> {
        val chemistry = check.declaredBatteryChemistry
            ?: return emptyList()

        val ranges = BatteryCompositionRanges.TABLE[chemistry] ?: return emptyList()
        val batchWeightKg = check.batchWeightTonnes.multiply(BigDecimal(1000))
        val claimedByMetal: Map<BatteryMetal, BigDecimal> =
            claimedRecoveries.associate { it.metal to it.claimedWeightKg }

        val results = ranges.keys.map { metal ->
            val range = ranges.getValue(metal)
            val claimedKg = claimedByMetal[metal]

            val (pct, result, detail) = when {
                claimedKg == null -> Triple(
                    null,
                    CompositionCheckResult.COULD_NOT_VERIFY,
                    "No claimed weight submitted for ${metal.name} — could not verify against the " +
                        "${chemistry.label} composition range (${range.min}-${range.max}%)."
                )
                else -> {
                    val pct = if (batchWeightKg > BigDecimal.ZERO)
                        claimedKg.divide(batchWeightKg, 4, RoundingMode.HALF_UP).multiply(BigDecimal(100))
                    else BigDecimal.ZERO

                    when {
                        range.max.compareTo(BigDecimal.ZERO) == 0 && pct > BigDecimal.ZERO -> Triple(
                            pct,
                            CompositionCheckResult.ZERO_CELL_VIOLATION,
                            "${metal.name} claimed at ${pct}% but ${chemistry.label} batteries should contain " +
                                "0% ${metal.name} — chemistry-impossible, not just out of range."
                        )
                        pct < range.min || pct > range.max -> Triple(
                            pct,
                            CompositionCheckResult.FAIL,
                            "${metal.name} claimed at ${pct}%, outside the ${chemistry.label} expected range " +
                                "of ${range.min}-${range.max}%."
                        )
                        else -> Triple(
                            pct,
                            CompositionCheckResult.PASS,
                            "${metal.name} claimed at ${pct}%, within the ${chemistry.label} expected range " +
                                "of ${range.min}-${range.max}%."
                        )
                    }
                }
            }

            MetalCompositionCheck(
                check = check,
                metal = metal,
                claimedPct = pct,
                expectedMin = range.min,
                expectedMax = range.max,
                result = result,
                detail = detail
            )
        }

        return repository.saveAll(results)
    }
}
