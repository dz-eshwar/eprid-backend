package com.rorapps.eprid.repository

import com.rorapps.eprid.entity.Producer
import org.springframework.data.jpa.repository.JpaRepository

interface ProducerRepository : JpaRepository<Producer, String> {
    fun findAllByCreatedById(userId: String): List<Producer>
    fun existsByCpcbRegNumber(cpcbRegNumber: String): Boolean
}
