package com.rorapps.eprid.controller

import com.rorapps.eprid.dto.common.ApiResponse
import com.rorapps.eprid.dto.cpcbdirectory.RecyclerLinkSuggestionDto
import com.rorapps.eprid.dto.cpcbdirectory.toDto
import com.rorapps.eprid.entity.LinkSuggestionStatus
import com.rorapps.eprid.repository.RecyclerLinkSuggestionRepository
import com.rorapps.eprid.service.cpcbdirectory.CpcbRecyclerLinkService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

/**
 * Admin review surface for pending Recycler <-> CPCB-directory link suggestions
 * (CpcbRecyclerLinkService — no tier auto-links today, see that service's doc for why). List-only
 * for now, no dedicated review UI — accept/reject via these two endpoints is enough until one exists.
 */
@RestController
@RequestMapping("/api/v1/admin/recycler-link-suggestions")
@Tag(name = "Recycler-CPCB Link Suggestions", description = "Pending suggested links between an E-PRid Recycler and its CPCB directory row — review-required, never auto-linked (feature_spec_close_scoring_gaps.md §6).")
@SecurityRequirement(name = "Bearer Authentication")
class RecyclerLinkSuggestionController(
    private val linkService: CpcbRecyclerLinkService,
    private val suggestionRepository: RecyclerLinkSuggestionRepository
) {

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List pending Recycler <-> CPCB-directory link suggestions awaiting review")
    fun listPending(): ResponseEntity<ApiResponse<List<RecyclerLinkSuggestionDto>>> =
        ResponseEntity.ok(
            ApiResponse.ok(
                suggestionRepository.findAllByStatus(LinkSuggestionStatus.PENDING).map { it.toDto() }
            )
        )

    @PostMapping("/{id}/accept")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Accept a suggestion — links the Recycler to this CPCB directory row")
    fun accept(@PathVariable id: String): ResponseEntity<ApiResponse<RecyclerLinkSuggestionDto>> =
        ResponseEntity.ok(ApiResponse.ok(linkService.resolveSuggestion(id, accept = true).toDto()))

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Reject a suggestion — does not link, just clears it from the pending list")
    fun reject(@PathVariable id: String): ResponseEntity<ApiResponse<RecyclerLinkSuggestionDto>> =
        ResponseEntity.ok(ApiResponse.ok(linkService.resolveSuggestion(id, accept = false).toDto()))

    @PostMapping("/backfill")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
        summary = "One-time batch pass: generate link suggestions for every existing Recycler not yet linked",
        description = "Run once after this endpoint ships so pre-existing Recycler rows aren't left " +
            "unmatched forever — going forward, VerificationCheckService.createCheck triggers this per-recycler automatically."
    )
    fun backfill(): ResponseEntity<ApiResponse<Map<String, Int>>> =
        ResponseEntity.ok(ApiResponse.ok(mapOf("suggestionsCreated" to linkService.backfillAllRecyclers())))
}
