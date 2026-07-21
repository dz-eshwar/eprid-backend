package com.rorapps.eprid.entity

import jakarta.persistence.*
import java.time.Instant

/** How [CpcbRecyclerLinkService.suggestMatches] found this candidate. Both tiers land as PENDING
 *  suggestions today, not auto-linked — see [RecyclerLinkSuggestion]'s own doc for why. */
enum class LinkMatchTier { GST_EXACT, NAME_FALLBACK }

enum class LinkSuggestionStatus { PENDING, ACCEPTED, REJECTED }

/**
 * A candidate link between an E-PRid [Recycler] and its row in the CPCB directory
 * ([CpcbRecycler]), awaiting human review — never auto-applied. GST-format normalization between
 * [Recycler.gstNumber] and [CpcbRecycler.recyclerGstNo] has never been confirmed against real
 * self-reported data (the `recyclers` table had 0 rows at the time this was built), so even a
 * GST_EXACT match stays a suggestion, not an automatic [CpcbRecyclerLinkService.link] call. See
 * feature_spec_close_scoring_gaps.md §6.
 */
@Entity
@Table(name = "recycler_link_suggestions", schema = "eprid")
data class RecyclerLinkSuggestion(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: String? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recycler_id", nullable = false)
    val recycler: Recycler,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cpcb_recycler_id", nullable = false)
    val cpcbRecycler: CpcbRecycler,

    @Enumerated(EnumType.STRING)
    @Column(name = "match_tier", nullable = false)
    val matchTier: LinkMatchTier,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val status: LinkSuggestionStatus = LinkSuggestionStatus.PENDING,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "resolved_at", nullable = true)
    val resolvedAt: Instant? = null
)
