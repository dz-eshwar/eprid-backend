package com.rorapps.eprid.controller

import com.rorapps.eprid.dto.common.ApiResponse
import com.rorapps.eprid.dto.cpcbdirectory.CpcbIngestionSummaryDto
import com.rorapps.eprid.dto.cpcbdirectory.CpcbPendingReviewItemDto
import com.rorapps.eprid.dto.cpcbdirectory.CpcbRecyclerSearchResult
import com.rorapps.eprid.dto.cpcbdirectory.CpcbRefreshRunSummaryDto
import com.rorapps.eprid.dto.cpcbdirectory.CpcbStateDto
import com.rorapps.eprid.service.cpcbdirectory.CpcbRecyclerIngestionService
import com.rorapps.eprid.service.cpcbdirectory.CpcbRecyclerRefreshService
import com.rorapps.eprid.service.cpcbdirectory.CpcbRecyclerSearchService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/api/v1/cpcb-recyclers")
@Tag(name = "CPCB Recycler Directory", description = "Public CPCB battery-recycler registry — Entity Risk Score, not the full Certificate Risk Score. Higher composite_score = riskier, 0 = cleanest.")
@SecurityRequirement(name = "Bearer Authentication")
class CpcbRecyclerController(
    private val ingestionService: CpcbRecyclerIngestionService,
    private val searchService: CpcbRecyclerSearchService,
    private val refreshService: CpcbRecyclerRefreshService
) {

    @PostMapping("/ingest", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
        summary = "Ingest a CPCB recycler-directory CSV",
        description = """
            Upserts by CPCB row id (rows missing a source id are inserted fresh and flagged
            data_quality_partial_capture). Re-scores every ingested row immediately. Expects the
            column shape of eprid_recyclers_seed_sample.csv — where the CSV was produced from
            (seed sample today, a full CPCB re-pull later) doesn't matter to this endpoint.
        """
    )
    fun ingest(@RequestParam("file") file: MultipartFile): ResponseEntity<ApiResponse<CpcbIngestionSummaryDto>> {
        val summary = file.inputStream.bufferedReader(Charsets.UTF_8).use { ingestionService.ingest(it) }
        return ResponseEntity.ok(ApiResponse.ok(summary))
    }

    @GetMapping("/search")
    @Operation(
        summary = "Search the CPCB recycler directory",
        description = "Returns Entity Risk Scores only — registration/authorization/geography. " +
            "Higher composite_score = riskier, 0 = cleanest. Does not include certificate-volume " +
            "or invoice-based risk (Certificate Risk Score, not built)."
    )
    fun search(
        @RequestParam(required = false) name: String?,
        @RequestParam(required = false) gst: String?,
        @RequestParam(required = false) state: String?
    ): ResponseEntity<ApiResponse<List<CpcbRecyclerSearchResult>>> =
        ResponseEntity.ok(ApiResponse.ok(searchService.search(name, gst, state)))

    @GetMapping("/states")
    @Operation(
        summary = "List known states for the directory search filter",
        description = "Resolved from cpcb_state_codes — CPCB's own state_id codes mapped to real " +
            "state names (inferred, not CPCB-published; see V17 migration). Only covers state_ids " +
            "actually observed in the loaded directory."
    )
    fun listStates(): ResponseEntity<ApiResponse<List<CpcbStateDto>>> =
        ResponseEntity.ok(ApiResponse.ok(searchService.listStates()))

    @PostMapping("/refresh")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
        summary = "Manually trigger a CPCB recycler directory refresh run",
        description = "Same diff-and-flag logic as the nightly scheduled job (CpcbRecyclerRefreshService) " +
            "— fetches the live CPCB dataset, diffs against stored state, flags risk-band changes for " +
            "review rather than silently applying them. Useful for an on-demand refresh outside the " +
            "scheduled early-morning slot."
    )
    fun triggerRefresh(): ResponseEntity<ApiResponse<CpcbRefreshRunSummaryDto>> =
        ResponseEntity.ok(ApiResponse.ok(refreshService.refresh()))

    @GetMapping("/refresh-runs")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Recent CPCB directory refresh run history")
    fun refreshRuns(): ResponseEntity<ApiResponse<List<CpcbRefreshRunSummaryDto>>> =
        ResponseEntity.ok(ApiResponse.ok(refreshService.recentRuns()))

    @GetMapping("/pending-review")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
        summary = "Recyclers whose risk band changed on the last refresh and haven't been reviewed",
        description = "feature_spec_cpcb_directory_refresh.md §4 review gate — a Low/Medium/High/Cautious " +
            "band flip stays flagged here (and does not silently show under the new band externally) " +
            "until cleared via confirm-review."
    )
    fun pendingReview(): ResponseEntity<ApiResponse<List<CpcbPendingReviewItemDto>>> =
        ResponseEntity.ok(ApiResponse.ok(refreshService.pendingReview()))

    @PostMapping("/{id}/confirm-review")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Clear the pending-review flag after a human has glanced at a band-changed recycler")
    fun confirmReview(@PathVariable id: String): ResponseEntity<ApiResponse<Map<String, Boolean>>> {
        refreshService.confirmReview(id)
        return ResponseEntity.ok(ApiResponse.ok(mapOf("confirmed" to true)))
    }
}
