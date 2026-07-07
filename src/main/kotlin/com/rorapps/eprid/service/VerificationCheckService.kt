package com.rorapps.eprid.service

import com.rorapps.eprid.dto.check.CreateCheckRequest
import com.rorapps.eprid.dto.check.VerificationCheckResponse
import com.rorapps.eprid.dto.plausibility.PlausibilityCheckResponse
import com.rorapps.eprid.entity.Producer
import com.rorapps.eprid.entity.Recycler
import com.rorapps.eprid.entity.User
import com.rorapps.eprid.entity.VerificationCheck
import com.rorapps.eprid.repository.EvidenceRepository
import com.rorapps.eprid.repository.PlausibilityCheckRepository
import com.rorapps.eprid.repository.ProducerRepository
import com.rorapps.eprid.repository.RecyclerRepository
import com.rorapps.eprid.repository.VerificationCheckRepository
import com.rorapps.eprid.service.plausibility.PlausibilityCheckService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class VerificationCheckService(
    private val checkRepository: VerificationCheckRepository,
    private val producerRepository: ProducerRepository,
    private val recyclerRepository: RecyclerRepository,
    private val evidenceRepository: EvidenceRepository,
    private val plausibilityCheckService: PlausibilityCheckService,
    private val plausibilityCheckRepository: PlausibilityCheckRepository
) {

    @Transactional
    fun createCheck(request: CreateCheckRequest, requestedBy: User): VerificationCheckResponse {
        val recycler = upsertRecycler(request)
        val producer = upsertProducer(request, requestedBy)

        val check = checkRepository.save(
            VerificationCheck(
                producer = producer,
                recycler = recycler,
                requestedBy = requestedBy,
                complianceEstimateId = request.complianceEstimateId,
                wasteStream = request.wasteStream,
                batchWeightTonnes = request.batchWeightTonnes,
                claimedRecoveryPct = request.claimedRecoveryPct,
                claimedOutputQuantity = request.claimedOutputQuantity,
                tyreEndProduct = request.tyreEndProduct,
                tyreImported = request.tyreImported,
                claimedEprCreditKg = request.claimedEprCreditKg,
                processingDate = request.processingDate
            )
        )

        // Run plausibility checks synchronously — fast, no external calls
        val plausibility = plausibilityCheckService.runAndSave(check)

        return check.toResponse(evidenceCount = 0, plausibility = plausibility)
    }

    @Transactional(readOnly = true)
    fun getCheck(checkId: String, requestedBy: User): VerificationCheckResponse {
        val check = checkRepository.findByIdFetched(checkId)
            ?: throw NoSuchElementException("Check not found: $checkId")

        if (check.requestedBy.id != requestedBy.id) {
            throw SecurityException("Access denied to check: $checkId")
        }

        val evidenceCount = evidenceRepository.findAllByCheckId(checkId).size
        val plausibility  = plausibilityCheckRepository.findByCheckId(checkId)
            ?.let { plausibilityCheckService.getForCheck(checkId) }
        return check.toResponse(evidenceCount, plausibility)
    }

    @Transactional(readOnly = true)
    fun listChecks(requestedBy: User): List<VerificationCheckResponse> {
        return checkRepository.findAllByRequestedByIdFetched(requestedBy.id!!)
            .map { check ->
                val evidenceCount = evidenceRepository.findAllByCheckId(check.id!!).size
                check.toResponse(evidenceCount, plausibility = null)
            }
    }

    private fun upsertRecycler(request: CreateCheckRequest): Recycler {
        if (request.bwmrRegNumber != null) {
            val existing = recyclerRepository.findByBwmrRegNumber(request.bwmrRegNumber)
            if (existing != null) {
                // Update state/capacity if newly provided
                val needsUpdate = (request.recyclerState != null && existing.state == null) ||
                                  (request.recyclerSelfReportedCapacityT != null && existing.selfReportedCapacityT == null)
                if (needsUpdate) {
                    return recyclerRepository.save(
                        existing.copy(
                            state = request.recyclerState ?: existing.state,
                            selfReportedCapacityT = request.recyclerSelfReportedCapacityT ?: existing.selfReportedCapacityT
                        )
                    )
                }
                return existing
            }
        }
        return recyclerRepository.save(
            Recycler(
                name = request.recyclerName,
                bwmrRegNumber = request.bwmrRegNumber,
                state = request.recyclerState,
                selfReportedCapacityT = request.recyclerSelfReportedCapacityT,
                wasteStream = request.wasteStream
            )
        )
    }

    private fun upsertProducer(request: CreateCheckRequest, createdBy: User): Producer {
        if (request.cpcbRegNumber != null && producerRepository.existsByCpcbRegNumber(request.cpcbRegNumber)) {
            return producerRepository.findAll()
                .first { it.cpcbRegNumber == request.cpcbRegNumber }
        }
        return producerRepository.save(
            Producer(
                name = request.producerName,
                cpcbRegNumber = request.cpcbRegNumber,
                createdBy = createdBy,
                wasteStream = request.wasteStream
            )
        )
    }

    private fun VerificationCheck.toResponse(
        evidenceCount: Int,
        plausibility: PlausibilityCheckResponse?
    ) = VerificationCheckResponse(
        id = id!!,
        recyclerName = recycler.name,
        recyclerId = recycler.id!!,
        producerName = producer.name,
        producerId = producer.id!!,
        wasteStream = wasteStream,
        batchWeightTonnes = batchWeightTonnes,
        claimedRecoveryPct = claimedRecoveryPct,
        claimedOutputQuantity = claimedOutputQuantity,
        tyreEndProduct = tyreEndProduct,
        tyreImported = tyreImported,
        claimedEprCreditKg = claimedEprCreditKg,
        processingDate = processingDate,
        status = status,
        riskRating = riskRating,
        riskSummary = riskSummary,
        evidenceCount = evidenceCount,
        complianceEstimateId = complianceEstimateId,
        plausibility = plausibility,
        regulatoryStatus = regulatoryStatus,
        regulatoryRisk = regulatoryRisk,
        regulatorySummary = regulatorySummary
    )
}
