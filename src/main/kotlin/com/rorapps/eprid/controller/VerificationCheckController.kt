package com.rorapps.eprid.controller

import com.rorapps.eprid.dto.check.CreateCheckRequest
import com.rorapps.eprid.dto.check.VerificationCheckResponse
import com.rorapps.eprid.dto.common.ApiResponse
import com.rorapps.eprid.entity.User
import com.rorapps.eprid.repository.VerificationCheckRepository
import com.rorapps.eprid.service.VerificationCheckService
import com.rorapps.eprid.service.report.ReportService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/checks")
@Tag(name = "Verification Checks", description = "Create and manage recycler verification checks")
@SecurityRequirement(name = "Bearer Authentication")
class VerificationCheckController(
    private val checkService: VerificationCheckService,
    private val checkRepository: VerificationCheckRepository,
    private val reportService: ReportService
) {

    @PostMapping
    @Operation(summary = "Create a new verification check")
    fun createCheck(
        @Valid @RequestBody request: CreateCheckRequest,
        @AuthenticationPrincipal currentUser: User
    ): ResponseEntity<ApiResponse<VerificationCheckResponse>> {
        val result = checkService.createCheck(request, currentUser)
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(result))
    }

    @GetMapping("/{checkId}")
    @Operation(summary = "Get a check by ID")
    fun getCheck(
        @PathVariable checkId: String,
        @AuthenticationPrincipal currentUser: User
    ): ResponseEntity<ApiResponse<VerificationCheckResponse>> {
        val result = checkService.getCheck(checkId, currentUser)
        return ResponseEntity.ok(ApiResponse.ok(result))
    }

    @GetMapping
    @Operation(summary = "List all checks for the current user")
    fun listChecks(
        @AuthenticationPrincipal currentUser: User
    ): ResponseEntity<ApiResponse<List<VerificationCheckResponse>>> {
        val result = checkService.listChecks(currentUser)
        return ResponseEntity.ok(ApiResponse.ok(result))
    }

    @GetMapping("/{checkId}/report")
    @Operation(summary = "Download the verification report as PDF")
    fun downloadReport(
        @PathVariable checkId: String,
        @AuthenticationPrincipal currentUser: User
    ): ResponseEntity<ByteArray> {
        val check = checkRepository.findByIdFetched(checkId)
            ?: throw NoSuchElementException("Check not found: $checkId")
        if (check.requestedBy.id != currentUser.id)
            return ResponseEntity.status(403).build()
        val pdf = reportService.generateCheckReport(check)
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"eprid-report-$checkId.pdf\"")
            .contentType(MediaType.APPLICATION_PDF)
            .body(pdf)
    }
}
