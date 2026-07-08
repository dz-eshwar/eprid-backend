package com.rorapps.eprid.repository

import com.rorapps.eprid.entity.CpcbRecyclerAuthorization
import org.springframework.data.jpa.repository.JpaRepository

interface CpcbRecyclerAuthorizationRepository : JpaRepository<CpcbRecyclerAuthorization, String> {
    fun findAllByRecyclerId(recyclerId: String): List<CpcbRecyclerAuthorization>
    fun deleteAllByRecyclerId(recyclerId: String)
}
