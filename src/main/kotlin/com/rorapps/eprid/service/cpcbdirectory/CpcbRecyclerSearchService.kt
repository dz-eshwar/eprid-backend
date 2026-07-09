package com.rorapps.eprid.service.cpcbdirectory

import com.fasterxml.jackson.databind.ObjectMapper
import com.rorapps.eprid.dto.cpcbdirectory.CpcbAuthorizationDto
import com.rorapps.eprid.dto.cpcbdirectory.CpcbRecyclerScoreDto
import com.rorapps.eprid.dto.cpcbdirectory.CpcbRecyclerSearchResult
import com.rorapps.eprid.dto.cpcbdirectory.CpcbStateDto
import com.rorapps.eprid.entity.CpcbRecycler
import com.rorapps.eprid.repository.CpcbRecyclerAuthorizationRepository
import com.rorapps.eprid.repository.CpcbRecyclerRepository
import com.rorapps.eprid.repository.CpcbRecyclerScoreRepository
import com.rorapps.eprid.repository.CpcbStateCodeRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Customer-facing directory search. Deliberately never returns authorizedName/Email/Mobile —
 * that's PII of an individual contact, not the company, and stays internal/ops-only (see
 * product_document_built_state.md's PII note). Sorted highest-risk-first by default so a
 * consultant scanning a state sees the recyclers most worth a closer look up top.
 */
@Service
class CpcbRecyclerSearchService(
    private val recyclerRepository: CpcbRecyclerRepository,
    private val authorizationRepository: CpcbRecyclerAuthorizationRepository,
    private val scoreRepository: CpcbRecyclerScoreRepository,
    private val stateCodeRepository: CpcbStateCodeRepository,
    private val objectMapper: ObjectMapper
) {

    /** Sorted by name — small, static reference list (see V17), fine to return in full for a
     *  frontend dropdown rather than a search-as-you-type endpoint. */
    @Transactional(readOnly = true)
    fun listStates(): List<CpcbStateDto> =
        stateCodeRepository.findAllByOrderByStateNameAsc().map { CpcbStateDto(it.stateId, it.stateName) }

    @Transactional(readOnly = true)
    fun search(name: String?, gst: String?, state: String?): List<CpcbRecyclerSearchResult> {
        val trimmedState = state?.trim()?.ifBlank { null }
        // A state name with no match in cpcb_state_codes can't be resolved to any state_id —
        // returning zero results (not falling through to an unfiltered search) matches what the
        // caller asked for: "recyclers in state X", not "recyclers, state filter ignored".
        val resolvedStateId = trimmedState?.let {
            stateCodeRepository.findByStateNameIgnoreCase(it)?.stateId ?: return emptyList()
        }
        val stateNamesById = stateCodeRepository.findAll().associate { it.stateId to it.stateName }

        val results = recyclerRepository.search(
            name?.trim()?.ifBlank { null },
            gst?.trim()?.ifBlank { null },
            resolvedStateId
        ).map { toSearchResult(it, stateNamesById) }

        return results.sortedWith(
            compareByDescending<CpcbRecyclerSearchResult> { it.latestScore?.compositeScore ?: -1 }
                .thenBy { it.recyclerName }
        )
    }

    private fun toSearchResult(recycler: CpcbRecycler, stateNamesById: Map<String, String>): CpcbRecyclerSearchResult {
        val authorizations = authorizationRepository.findAllByRecyclerId(recycler.id!!)
            .map { CpcbAuthorizationDto(it.categoryCode, it.categoryLabel) }
        val latest = scoreRepository.findFirstByRecyclerIdOrderByScoredAtDesc(recycler.id)?.let { score ->
            CpcbRecyclerScoreDto(
                compositeScore = score.compositeScore,
                riskBand = score.riskBand,
                flags = objectMapper.readValue(score.flags, List::class.java).map { it.toString() },
                unassessed = objectMapper.readValue(score.unassessed, List::class.java).map { it.toString() },
                layerBreakdown = objectMapper.readValue(score.layerBreakdown, Map::class.java)
                    .entries.associate { it.key.toString() to it.value },
                scoreConfidence = score.scoreConfidence,
                scoredAt = score.scoredAt.toString()
            )
        }

        return CpcbRecyclerSearchResult(
            id = recycler.id,
            cpcbId = recycler.cpcbId,
            recyclerName = recycler.recyclerName,
            recyclerAddress = recycler.recyclerAddress,
            stateId = recycler.stateId,
            stateName = recycler.stateId?.let { stateNamesById[it] },
            recyclerGstNo = recycler.recyclerGstNo,
            consentAirExpiry = recycler.consentAirExpiry,
            consentWaterExpiry = recycler.consentWaterExpiry,
            hwmdValidExpiry = recycler.hwmdValidExpiry,
            dicValidExpiry = recycler.dicValidExpiry,
            recyclingCapacity = recycler.recyclingCapacity,
            latitude = recycler.latitude,
            longitude = recycler.longitude,
            authorizations = authorizations,
            dataQualityPartialCapture = recycler.dataQualityPartialCapture,
            dataQualityNotes = recycler.dataQualityNotes,
            latestScore = latest
        )
    }
}
