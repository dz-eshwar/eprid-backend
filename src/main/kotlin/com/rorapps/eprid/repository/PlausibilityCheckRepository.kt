package com.rorapps.eprid.repository

import com.rorapps.eprid.entity.PlausibilityCheck
import org.springframework.data.jpa.repository.JpaRepository

interface PlausibilityCheckRepository : JpaRepository<PlausibilityCheck, String> {
    fun findByCheckId(checkId: String): PlausibilityCheck?
}
