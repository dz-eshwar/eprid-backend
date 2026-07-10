package com.rorapps.eprid.repository

import com.rorapps.eprid.entity.ClaimedMetalRecovery
import org.springframework.data.jpa.repository.JpaRepository

interface ClaimedMetalRecoveryRepository : JpaRepository<ClaimedMetalRecovery, String> {
    fun findAllByCheckId(checkId: String): List<ClaimedMetalRecovery>
}
