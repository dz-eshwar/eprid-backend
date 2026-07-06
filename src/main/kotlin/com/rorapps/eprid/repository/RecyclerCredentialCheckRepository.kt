package com.rorapps.eprid.repository

import com.rorapps.eprid.entity.RecyclerCredentialCheck
import org.springframework.data.jpa.repository.JpaRepository

interface RecyclerCredentialCheckRepository : JpaRepository<RecyclerCredentialCheck, String> {
    fun findAllByRecyclerIdOrderByCheckedAtDesc(recyclerId: String): List<RecyclerCredentialCheck>
}
