package com.rorapps.eprid.repository

import com.rorapps.eprid.entity.RegulatoryFinding
import org.springframework.data.jpa.repository.JpaRepository

interface RegulatoryFindingRepository : JpaRepository<RegulatoryFinding, String> {
    fun findAllByCheckId(checkId: String): List<RegulatoryFinding>
    fun findAllByRecyclerId(recyclerId: String): List<RegulatoryFinding>
}
