package com.rorapps.eprid.dto.cpcbdirectory

import com.rorapps.eprid.entity.LinkMatchTier
import com.rorapps.eprid.entity.LinkSuggestionStatus
import com.rorapps.eprid.entity.RecyclerLinkSuggestion
import java.math.BigDecimal
import java.time.Instant

data class RecyclerLinkSuggestionDto(
    val id: String,
    val recyclerId: String,
    val recyclerName: String,
    val cpcbRecyclerId: String,
    val cpcbRecyclerName: String,
    val cpcbRecyclingCapacity: BigDecimal?,
    val matchTier: LinkMatchTier,
    val status: LinkSuggestionStatus,
    val createdAt: Instant
)

fun RecyclerLinkSuggestion.toDto() = RecyclerLinkSuggestionDto(
    id = id!!,
    recyclerId = recycler.id!!,
    recyclerName = recycler.name,
    cpcbRecyclerId = cpcbRecycler.id!!,
    cpcbRecyclerName = cpcbRecycler.recyclerName,
    cpcbRecyclingCapacity = cpcbRecycler.recyclingCapacity,
    matchTier = matchTier,
    status = status,
    createdAt = createdAt
)
