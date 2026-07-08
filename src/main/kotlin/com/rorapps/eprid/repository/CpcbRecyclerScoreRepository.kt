package com.rorapps.eprid.repository

import com.rorapps.eprid.entity.CpcbRecyclerScore
import org.springframework.data.jpa.repository.JpaRepository

interface CpcbRecyclerScoreRepository : JpaRepository<CpcbRecyclerScore, String> {
    fun findFirstByRecyclerIdOrderByScoredAtDesc(recyclerId: String): CpcbRecyclerScore?
}
