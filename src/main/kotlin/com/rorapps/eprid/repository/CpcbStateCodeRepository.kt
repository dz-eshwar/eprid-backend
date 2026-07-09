package com.rorapps.eprid.repository

import com.rorapps.eprid.entity.CpcbStateCode
import org.springframework.data.jpa.repository.JpaRepository

interface CpcbStateCodeRepository : JpaRepository<CpcbStateCode, String> {
    fun findByStateNameIgnoreCase(stateName: String): CpcbStateCode?
    fun findAllByOrderByStateNameAsc(): List<CpcbStateCode>
}
