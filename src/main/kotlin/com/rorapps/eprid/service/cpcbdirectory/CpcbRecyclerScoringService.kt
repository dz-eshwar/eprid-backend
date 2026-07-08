package com.rorapps.eprid.service.cpcbdirectory

import com.fasterxml.jackson.databind.ObjectMapper
import com.rorapps.eprid.entity.CpcbRecycler
import com.rorapps.eprid.entity.CpcbRecyclerScore
import com.rorapps.eprid.repository.CpcbGeoRiskHotspotRepository
import com.rorapps.eprid.repository.CpcbRecyclerAuthorizationRepository
import com.rorapps.eprid.repository.CpcbRecyclerScoreRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** Orchestrates the pure [CpcbRecyclerScoring.score] function against DB state (authorizations,
 *  hotspot reference table) and persists the result. The scoring math itself lives in
 *  [CpcbRecyclerScoring] so it can be unit-tested without a database. */
@Service
class CpcbRecyclerScoringService(
    private val authorizationRepository: CpcbRecyclerAuthorizationRepository,
    private val hotspotRepository: CpcbGeoRiskHotspotRepository,
    private val scoreRepository: CpcbRecyclerScoreRepository,
    private val objectMapper: ObjectMapper
) {

    @Transactional
    fun scoreAndSave(recycler: CpcbRecycler): CpcbRecyclerScore {
        val authorizations = authorizationRepository.findAllByRecyclerId(recycler.id!!)
        val hotspots = hotspotRepository.findAll()
        val result = CpcbRecyclerScoring.score(recycler, authorizations, hotspots)

        return scoreRepository.save(
            CpcbRecyclerScore(
                recycler = recycler,
                compositeScore = result.compositeScore,
                riskBand = result.riskBand,
                flags = objectMapper.writeValueAsString(result.flags),
                unassessed = objectMapper.writeValueAsString(result.unassessed),
                layerBreakdown = objectMapper.writeValueAsString(result.layerBreakdown),
                scoreConfidence = result.scoreConfidence
            )
        )
    }

    @Transactional(readOnly = true)
    fun latestScore(recyclerId: String): CpcbRecyclerScore? =
        scoreRepository.findFirstByRecyclerIdOrderByScoredAtDesc(recyclerId)
}
