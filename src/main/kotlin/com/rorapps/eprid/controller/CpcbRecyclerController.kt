package com.rorapps.eprid.controller

import com.rorapps.eprid.dto.common.ApiResponse
import com.rorapps.eprid.dto.cpcbdirectory.CpcbIngestionSummaryDto
import com.rorapps.eprid.dto.cpcbdirectory.CpcbRecyclerSearchResult
import com.rorapps.eprid.service.cpcbdirectory.CpcbRecyclerIngestionService
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
@Tag(name = "CPCB Recycler Directory", description = "Public CPCB battery-recycler registry — Entity Health Score, not the full Certificate Risk Score")
@SecurityRequirement(name = "Bearer Authentication")
class CpcbRecyclerController(
    private val ingestionService: CpcbRecyclerIngestionService,
    private val searchService: CpcbRecyclerSearchService
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
        description = "Returns Entity Health Scores only — registration/authorization/geography. " +
            "Does not include certificate-volume or invoice-based risk (Certificate Risk Score, not built)."
    )
    fun search(
        @RequestParam(required = false) name: String?,
        @RequestParam(required = false) gst: String?,
        @RequestParam(required = false) stateId: String?
    ): ResponseEntity<ApiResponse<List<CpcbRecyclerSearchResult>>> =
        ResponseEntity.ok(ApiResponse.ok(searchService.search(name, gst, stateId)))
}
