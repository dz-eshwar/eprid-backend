package com.rorapps.eprid.repository

import com.rorapps.eprid.entity.ComplianceEstimate
import org.springframework.data.jpa.repository.JpaRepository

interface ComplianceEstimateRepository : JpaRepository<ComplianceEstimate, String> {
    fun findAllByUserIdOrderByCreatedAtDesc(userId: String): List<ComplianceEstimate>
}
