package com.rorapps.eprid.repository

import com.rorapps.eprid.entity.CpcbRecyclerSnapshotDiff
import org.springframework.data.jpa.repository.JpaRepository

interface CpcbRecyclerSnapshotDiffRepository : JpaRepository<CpcbRecyclerSnapshotDiff, String> {
    fun findAllByRecyclerIdOrderByDetectedAtDesc(recyclerId: String): List<CpcbRecyclerSnapshotDiff>
}
