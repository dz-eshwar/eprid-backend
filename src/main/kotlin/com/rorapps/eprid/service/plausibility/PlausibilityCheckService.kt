package com.rorapps.eprid.service.plausibility

import com.rorapps.eprid.constants.WasteStreamType
import com.rorapps.eprid.dto.plausibility.PlausibilityCheckResponse
import com.rorapps.eprid.dto.plausibility.PlausibilitySubCheck
import com.rorapps.eprid.entity.PlausibilityCheck
import com.rorapps.eprid.entity.VerificationCheck
import com.rorapps.eprid.repository.PlausibilityCheckRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.RoundingMode

/**
 * Thin router: picks the [PlausibilityStrategy] bean whose [PlausibilityStrategy.supports] matches
 * the check's waste stream, then persists the resulting 3 sub-checks into [PlausibilityCheck]'s
 * existing generic column-pairs (slot 1 = "recovery"-named columns, slot 2 = "capacity"-named
 * columns, slot 3 = "batchSize"-named columns — populated positionally regardless of waste stream,
 * per the PRD's explicit "no new Check/Evidence entity" instruction for Module D).
 */
@Service
class PlausibilityCheckService(
    private val plausibilityRepository: PlausibilityCheckRepository,
    private val strategies: List<PlausibilityStrategy>
) {

    @Transactional
    fun runAndSave(check: VerificationCheck): PlausibilityCheckResponse {
        val strategy = strategies.first { it.supports(check.wasteStream) }
        val subChecks = strategy.runChecks(check)
        require(subChecks.size == 3) { "PlausibilityStrategy must return exactly 3 sub-checks" }
        val (slot1, slot2, slot3) = Triple(subChecks[0], subChecks[1], subChecks[2])

        val overall = deriveOverall(listOf(slot1.status, slot2.status, slot3.status))

        plausibilityRepository.save(
            PlausibilityCheck(
                check = check,
                claimedRecoveryPct = check.claimedRecoveryPct,
                recoveryStatus = slot1.status,
                recoveryDetail = slot1.detail,
                recyclerAnnualCapacityT = check.recycler.selfReportedCapacityT,
                batchToCapacityRatio = check.recycler.selfReportedCapacityT?.let { cap ->
                    check.batchWeightTonnes.divide(cap, 4, RoundingMode.HALF_UP)
                },
                capacityStatus = slot2.status,
                capacityDetail = slot2.detail,
                batchWeightT = check.batchWeightTonnes,
                batchSizeStatus = slot3.status,
                batchSizeDetail = slot3.detail,
                overallStatus = overall
            )
        )

        return PlausibilityCheckResponse(
            checkId = check.id!!,
            overallStatus = overall,
            subChecks = listOf(slot1, slot2, slot3)
        )
    }

    @Transactional(readOnly = true)
    fun getForCheck(checkId: String): PlausibilityCheckResponse? {
        val entity = plausibilityRepository.findByCheckId(checkId) ?: return null
        return entity.toResponse()
    }

    private fun PlausibilityCheck.toResponse(): PlausibilityCheckResponse {
        // Slot 1's label depends on which strategy produced it — battery calls it a recovery-rate
        // check, tyre calls it a TPO-yield check. The column data is generic; only the label differs.
        val slot1Name = if (check.wasteStream == WasteStreamType.TYRE)
            "TPO yield plausibility"
        else
            "Recovery rate plausibility"

        return PlausibilityCheckResponse(
            checkId = check.id!!,
            overallStatus = overallStatus,
            subChecks = listOf(
                PlausibilitySubCheck(slot1Name, recoveryStatus, recoveryDetail),
                PlausibilitySubCheck(
                    "Capacity ceiling check", capacityStatus, capacityDetail,
                    recyclerAnnualCapacityT?.let { "Annual capacity: $it T" }
                ),
                PlausibilitySubCheck("Absolute batch size check", batchSizeStatus, batchSizeDetail)
            )
        )
    }
}
