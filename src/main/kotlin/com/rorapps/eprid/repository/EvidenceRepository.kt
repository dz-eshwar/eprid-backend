package com.rorapps.eprid.repository

import com.rorapps.eprid.entity.Evidence
import org.springframework.data.jpa.repository.JpaRepository

interface EvidenceRepository : JpaRepository<Evidence, String> {
    fun findAllByCheckId(checkId: String): List<Evidence>
    fun findAllByImagePhashNotNull(): List<Evidence>
}
