package com.rorapps.eprid.repository

import com.rorapps.eprid.entity.CpcbRefreshRun
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.domain.Pageable

interface CpcbRefreshRunRepository : JpaRepository<CpcbRefreshRun, String> {
    fun findAllByOrderByStartedAtDesc(pageable: Pageable): List<CpcbRefreshRun>
}
