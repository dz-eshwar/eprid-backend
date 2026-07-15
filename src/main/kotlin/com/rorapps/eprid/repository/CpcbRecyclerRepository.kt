package com.rorapps.eprid.repository

import com.rorapps.eprid.entity.CpcbRecycler
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface CpcbRecyclerRepository : JpaRepository<CpcbRecycler, String> {
    fun findByCpcbId(cpcbId: String): CpcbRecycler?

    fun findAllByPendingReviewTrue(): List<CpcbRecycler>

    fun findAllByCpcbIdIsNotNull(): List<CpcbRecycler>

    // Explicit CAST on each nullable param — without it, Hibernate/pgjdbc can infer a bare `:name`
    // bound to null as bytea (not text), and Postgres then rejects LOWER(bytea) even though the
    // `IS NULL` branch would short-circuit logically. Reproduced with all three params blank
    // (a legitimate "browse all" search) — CAST forces the parameter type unambiguously.
    @Query(
        "SELECT r FROM CpcbRecycler r WHERE " +
            "(CAST(:name AS string) IS NULL OR LOWER(r.recyclerName) LIKE LOWER(CONCAT('%', CAST(:name AS string), '%'))) AND " +
            "(CAST(:gst AS string) IS NULL OR r.recyclerGstNo = CAST(:gst AS string)) AND " +
            "(CAST(:stateId AS string) IS NULL OR r.stateId = CAST(:stateId AS string))"
    )
    fun search(
        @Param("name") name: String?,
        @Param("gst") gst: String?,
        @Param("stateId") stateId: String?
    ): List<CpcbRecycler>
}
