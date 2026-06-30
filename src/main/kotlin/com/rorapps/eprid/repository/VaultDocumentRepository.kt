package com.rorapps.eprid.repository

import com.rorapps.eprid.entity.VaultDocument
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface VaultDocumentRepository : JpaRepository<VaultDocument, String> {

    @Query("SELECT v FROM VaultDocument v WHERE v.recycler.id = :recyclerId AND v.deletedAt IS NULL ORDER BY v.uploadedAt DESC")
    fun findActiveByRecyclerId(recyclerId: String): List<VaultDocument>

    @Query("SELECT v FROM VaultDocument v WHERE v.uploadedBy.id = :userId AND v.deletedAt IS NULL ORDER BY v.uploadedAt DESC")
    fun findActiveByUserId(userId: String): List<VaultDocument>
}
