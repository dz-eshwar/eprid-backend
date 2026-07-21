package com.rorapps.eprid.service.cpcbdirectory

import com.rorapps.eprid.entity.CpcbRecycler
import com.rorapps.eprid.entity.LinkMatchTier
import com.rorapps.eprid.entity.LinkSuggestionStatus
import com.rorapps.eprid.entity.Recycler
import com.rorapps.eprid.entity.RecyclerLinkSuggestion
import com.rorapps.eprid.repository.CpcbRecyclerRepository
import com.rorapps.eprid.repository.RecyclerLinkSuggestionRepository
import com.rorapps.eprid.repository.RecyclerRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/** A candidate CPCB directory row for a [Recycler], tagged with how it was found. */
data class LinkCandidate(val cpcbRecycler: CpcbRecycler, val tier: LinkMatchTier)

/**
 * Links an E-PRid [Recycler] to its row in the CPCB directory ([CpcbRecycler]) — the prerequisite
 * for hard-disqualification rules 1 and 2, and for the capacity-ceiling check preferring
 * CPCB-verified capacity (feature_spec_close_scoring_gaps.md §2). GST-number format/normalization
 * between the two tables isn't confirmed clean (open item, §6: `CpcbRecycler.recyclerGstNo` has no
 * validation or normalization applied anywhere in the ingest pipeline, and at the time this was
 * built `recyclers` had 0 rows — nothing to test the join against). So this deliberately does NOT
 * auto-apply a match on ANY tier, including exact-GST — [suggestMatches] / [generateSuggestions]
 * return candidates for a human to confirm via [link] (or [resolveSuggestion]).
 */
@Service
class CpcbRecyclerLinkService(
    private val recyclerRepository: RecyclerRepository,
    private val cpcbRecyclerRepository: CpcbRecyclerRepository,
    private val suggestionRepository: RecyclerLinkSuggestionRepository
) {

    private fun normalizeGst(gst: String) = gst.trim().uppercase().replace(" ", "")

    /** Candidate CPCB directory rows for [recyclerId], ranked GST-exact-match first, then
     *  name-contains fallback. Never auto-applied — caller must confirm via [link]. */
    @Transactional(readOnly = true)
    fun suggestMatches(recyclerId: String): List<LinkCandidate> {
        val recycler = recyclerRepository.findById(recyclerId).orElse(null) ?: return emptyList()

        val gstMatches = recycler.gstNumber?.let { gst ->
            cpcbRecyclerRepository.search(name = null, gst = normalizeGst(gst), stateId = null)
        }.orEmpty()
        if (gstMatches.isNotEmpty()) return gstMatches.map { LinkCandidate(it, LinkMatchTier.GST_EXACT) }

        return cpcbRecyclerRepository.search(name = recycler.name, gst = null, stateId = null)
            .map { LinkCandidate(it, LinkMatchTier.NAME_FALLBACK) }
    }

    /**
     * Persists every [suggestMatches] candidate as a PENDING [RecyclerLinkSuggestion] for admin
     * review — called at check-creation time (right after `upsertRecycler`) and by [backfillAllRecyclers].
     * No-op if [recyclerId] is already linked (nothing to suggest) or has no candidates. Skips
     * candidates that already have a PENDING suggestion on file rather than duplicating them.
     */
    @Transactional
    fun generateSuggestions(recyclerId: String): List<RecyclerLinkSuggestion> {
        val recycler = recyclerRepository.findById(recyclerId).orElse(null) ?: return emptyList()
        if (recycler.cpcbRecyclerId != null) return emptyList()

        return suggestMatches(recyclerId).mapNotNull { candidate ->
            val cpcbId = candidate.cpcbRecycler.id!!
            val existing = suggestionRepository.findByRecycler_IdAndCpcbRecycler_IdAndStatus(
                recyclerId, cpcbId, LinkSuggestionStatus.PENDING
            )
            if (existing != null) return@mapNotNull null
            suggestionRepository.save(
                RecyclerLinkSuggestion(
                    recycler = recycler,
                    cpcbRecycler = candidate.cpcbRecycler,
                    matchTier = candidate.tier
                )
            )
        }
    }

    /** Step 3.2 one-time batch pass — runs [generateSuggestions] against every existing,
     *  not-yet-linked [Recycler] row. Returns the total number of new suggestions created. */
    @Transactional
    fun backfillAllRecyclers(): Int =
        recyclerRepository.findAll()
            .filter { it.cpcbRecyclerId == null }
            .sumOf { generateSuggestions(it.id!!).size }

    /** Admin accepts or rejects a pending suggestion. Accepting calls [link]; rejecting just marks
     *  the suggestion resolved so it stops surfacing in the pending list. */
    @Transactional
    fun resolveSuggestion(suggestionId: String, accept: Boolean): RecyclerLinkSuggestion {
        val suggestion = suggestionRepository.findById(suggestionId).orElseThrow {
            NoSuchElementException("Suggestion not found: $suggestionId")
        }
        if (accept) {
            link(suggestion.recycler.id!!, suggestion.cpcbRecycler.id!!)
        }
        return suggestionRepository.save(
            suggestion.copy(
                status = if (accept) LinkSuggestionStatus.ACCEPTED else LinkSuggestionStatus.REJECTED,
                resolvedAt = Instant.now()
            )
        )
    }

    @Transactional
    fun link(recyclerId: String, cpcbRecyclerId: String): Recycler {
        val recycler = recyclerRepository.findById(recyclerId).orElseThrow {
            NoSuchElementException("Recycler not found: $recyclerId")
        }
        require(cpcbRecyclerRepository.existsById(cpcbRecyclerId)) {
            "CPCB directory recycler not found: $cpcbRecyclerId"
        }
        return recyclerRepository.save(recycler.copy(cpcbRecyclerId = cpcbRecyclerId))
    }
}
