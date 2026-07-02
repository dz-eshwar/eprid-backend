package com.rorapps.eprid.repository

import com.rorapps.eprid.entity.CheckStatus
import com.rorapps.eprid.entity.VerificationCheck
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface VerificationCheckRepository : JpaRepository<VerificationCheck, String> {
    fun findAllByRequestedByIdOrderByCreatedAtDesc(userId: String): List<VerificationCheck>
    fun findAllByRecyclerIdOrderByCreatedAtDesc(recyclerId: String): List<VerificationCheck>
    fun findAllByStatus(status: CheckStatus): List<VerificationCheck>

    // recycler/producer/requestedBy are LAZY — fetch join eagerly so reads outside the
    // create flow don't hit uninitialized associations (see VerificationCheckService.getCheck/listChecks)
    @Query(
        "SELECT c FROM VerificationCheck c " +
            "JOIN FETCH c.recycler JOIN FETCH c.producer JOIN FETCH c.requestedBy " +
            "WHERE c.id = :id"
    )
    fun findByIdFetched(id: String): VerificationCheck?

    @Query(
        "SELECT c FROM VerificationCheck c " +
            "JOIN FETCH c.recycler JOIN FETCH c.producer JOIN FETCH c.requestedBy " +
            "WHERE c.requestedBy.id = :userId ORDER BY c.createdAt DESC"
    )
    fun findAllByRequestedByIdFetched(userId: String): List<VerificationCheck>
}
