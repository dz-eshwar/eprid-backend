package com.rorapps.eprid.repository

import com.rorapps.eprid.entity.CheckStatus
import com.rorapps.eprid.entity.VerificationCheck
import org.springframework.data.jpa.repository.JpaRepository

interface VerificationCheckRepository : JpaRepository<VerificationCheck, String> {
    fun findAllByRequestedByIdOrderByCreatedAtDesc(userId: String): List<VerificationCheck>
    fun findAllByRecyclerIdOrderByCreatedAtDesc(recyclerId: String): List<VerificationCheck>
    fun findAllByStatus(status: CheckStatus): List<VerificationCheck>
}
