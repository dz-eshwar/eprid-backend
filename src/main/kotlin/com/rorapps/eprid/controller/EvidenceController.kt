package com.rorapps.eprid.controller

import com.rorapps.eprid.constants.EvidenceType
import com.rorapps.eprid.dto.common.ApiResponse
import com.rorapps.eprid.dto.forensics.EvidenceSummaryDto
import com.rorapps.eprid.dto.forensics.EvidenceUploadResponse
import com.rorapps.eprid.entity.User
import com.rorapps.eprid.repository.EvidenceRepository
import com.rorapps.eprid.repository.VerificationCheckRepository
import com.rorapps.eprid.service.forensics.DocumentForensicsOrchestrator
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/api/v1/checks/{checkId}/evidence")
@Tag(name = "Evidence & Forensics", description = "Upload evidence files and run document forensics")
@SecurityRequirement(name = "Bearer Authentication")
class EvidenceController(
    private val checkRepository: VerificationCheckRepository,
    private val evidenceRepository: EvidenceRepository,
    private val forensicsOrchestrator: DocumentForensicsOrchestrator
) {

    @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @Operation(
        summary = "Upload evidence files",
        description = """
            Accepts JPEG, PNG, WebP, TIFF, or PDF.
            Provide one `types` value per file (aligned by index) from:
            SITE_PHOTO, WEIGHBRIDGE_SLIP, INVOICE, REGISTRATION_CERTIFICATE, AUDIT_REPORT, OTHER.
            Each document type uses its own date-tolerance window against the batch's processing date.
            GPS photos are verified against the recycler's registered state (Fix 2).
            Any check that cannot run reports UNVERIFIABLE rather than silently passing.
        """
    )
    fun uploadEvidence(
        @PathVariable checkId: String,
        @RequestParam("files") files: List<MultipartFile>,
        @Parameter(description = "Evidence type per file, aligned by index. Defaults to OTHER.")
        @RequestParam("types", required = false) rawTypes: List<String>?,
        @AuthenticationPrincipal currentUser: User
    ): ResponseEntity<ApiResponse<EvidenceUploadResponse>> {
        val check = checkRepository.findByIdFetched(checkId)
            ?: throw NoSuchElementException("Check not found: $checkId")

        if (check.requestedBy.id != currentUser.id) {
            return ResponseEntity.status(403)
                .body(ApiResponse.error("You do not have access to this check"))
        }

        val evidenceTypes = rawTypes
            ?.map { raw -> runCatching { EvidenceType.valueOf(raw.uppercase()) }.getOrDefault(EvidenceType.OTHER) }
            ?: emptyList()

        val result = forensicsOrchestrator.processUploads(check, files, evidenceTypes)
        return ResponseEntity.ok(ApiResponse.ok(result))
    }

    @GetMapping
    @Operation(
        summary = "List evidence already uploaded for a check",
        description = "Read-later summary (no per-sub-check breakdown, only the joined forensics notes) — " +
            "used when reopening a completed check's results outside the upload flow."
    )
    fun listEvidence(
        @PathVariable checkId: String,
        @AuthenticationPrincipal currentUser: User
    ): ResponseEntity<ApiResponse<List<EvidenceSummaryDto>>> {
        val check = checkRepository.findByIdFetched(checkId)
            ?: throw NoSuchElementException("Check not found: $checkId")

        if (check.requestedBy.id != currentUser.id) {
            return ResponseEntity.status(403)
                .body(ApiResponse.error("You do not have access to this check"))
        }

        val summaries = evidenceRepository.findAllByCheckId(checkId).map {
            EvidenceSummaryDto(
                evidenceId = it.id!!,
                fileName = it.fileName,
                evidenceType = it.evidenceType.name,
                overallStatus = it.forensicsStatus,
                notes = it.forensicsNotes,
                uploadedAt = it.uploadedAt
            )
        }
        return ResponseEntity.ok(ApiResponse.ok(summaries))
    }
}
