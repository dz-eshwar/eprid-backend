package com.rorapps.eprid.service.cpcbdirectory

import com.rorapps.eprid.entity.CpcbRecycler
import com.rorapps.eprid.entity.Recycler
import com.rorapps.eprid.repository.CpcbRecyclerRepository
import com.rorapps.eprid.repository.RecyclerRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Links an E-PRid [Recycler] to its row in the CPCB directory ([CpcbRecycler]) — the prerequisite
 * for hard-disqualification rules 1 and 2 (feature_spec_close_scoring_gaps.md §2). GST-number
 * format/normalization between the two tables isn't confirmed clean (open item, §6: CpcbRecycler's
 * `recyclerGstNo` has no validation or normalization applied anywhere in the ingest pipeline), so
 * this deliberately does NOT auto-apply a match — [suggestMatches] returns candidates for a human
 * to confirm via [link].
 */
@Service
class CpcbRecyclerLinkService(
    private val recyclerRepository: RecyclerRepository,
    private val cpcbRecyclerRepository: CpcbRecyclerRepository
) {

    private fun normalizeGst(gst: String) = gst.trim().uppercase().replace(" ", "")

    /** Candidate CPCB directory rows for [recyclerId], ranked GST-exact-match first, then
     *  name-contains fallback. Never auto-applied — caller (an admin flow) must confirm via [link]. */
    @Transactional(readOnly = true)
    fun suggestMatches(recyclerId: String): List<CpcbRecycler> {
        val recycler = recyclerRepository.findById(recyclerId).orElse(null) ?: return emptyList()

        val gstMatches = recycler.gstNumber?.let { gst ->
            cpcbRecyclerRepository.search(name = null, gst = normalizeGst(gst), stateId = null)
        }.orEmpty()
        if (gstMatches.isNotEmpty()) return gstMatches

        return cpcbRecyclerRepository.search(name = recycler.name, gst = null, stateId = null)
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
