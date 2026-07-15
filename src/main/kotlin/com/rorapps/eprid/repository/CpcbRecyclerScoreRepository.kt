package com.rorapps.eprid.repository

import com.rorapps.eprid.entity.CpcbRecyclerScore
import org.springframework.data.jpa.repository.JpaRepository

interface CpcbRecyclerScoreRepository : JpaRepository<CpcbRecyclerScore, String> {
    fun findFirstByRecyclerIdOrderByScoredAtDesc(recyclerId: String): CpcbRecyclerScore?

    /** Two most recent scores, newest first. Used to hold back a just-flipped band from public
     *  view (feature_spec_cpcb_directory_refresh.md §4) — index 1 is "last score shown before the
     *  band changed," not the pending one at index 0. */
    fun findTop2ByRecyclerIdOrderByScoredAtDesc(recyclerId: String): List<CpcbRecyclerScore>
}
