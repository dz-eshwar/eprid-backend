package com.rorapps.eprid.repository

import com.rorapps.eprid.entity.Recycler
import org.springframework.data.jpa.repository.JpaRepository

interface RecyclerRepository : JpaRepository<Recycler, String> {
    fun findByBwmrRegNumber(bwmrRegNumber: String): Recycler?
    fun findByNameContainingIgnoreCase(name: String): List<Recycler>
    fun findByUserId(userId: String): Recycler?
}
