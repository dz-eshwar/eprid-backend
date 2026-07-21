package com.rorapps.eprid.repository

import com.rorapps.eprid.entity.LinkSuggestionStatus
import com.rorapps.eprid.entity.RecyclerLinkSuggestion
import org.springframework.data.jpa.repository.JpaRepository

interface RecyclerLinkSuggestionRepository : JpaRepository<RecyclerLinkSuggestion, String> {
    fun findByRecycler_IdAndCpcbRecycler_IdAndStatus(
        recyclerId: String,
        cpcbRecyclerId: String,
        status: LinkSuggestionStatus
    ): RecyclerLinkSuggestion?

    fun findAllByStatus(status: LinkSuggestionStatus): List<RecyclerLinkSuggestion>
}
