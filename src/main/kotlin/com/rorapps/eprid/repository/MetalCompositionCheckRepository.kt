package com.rorapps.eprid.repository

import com.rorapps.eprid.entity.MetalCompositionCheck
import org.springframework.data.jpa.repository.JpaRepository

interface MetalCompositionCheckRepository : JpaRepository<MetalCompositionCheck, String> {
    fun findAllByCheckId(checkId: String): List<MetalCompositionCheck>
}
