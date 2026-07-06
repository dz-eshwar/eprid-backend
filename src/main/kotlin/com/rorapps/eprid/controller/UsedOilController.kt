package com.rorapps.eprid.controller

import com.rorapps.eprid.dto.common.ApiResponse
import com.rorapps.eprid.dto.usedoil.*
import com.rorapps.eprid.constants.UsedOilTier
import com.rorapps.eprid.service.usedoil.UsedOilApplicationPdfService
import com.rorapps.eprid.service.usedoil.UsedOilAssistantService
import com.rorapps.eprid.service.usedoil.UsedOilSummaryPdfService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/used-oil")
@Tag(name = "Used-Oil Registration Assistant", description = "Module E — CA-1/CA-2 registration guidance. No login required.")
class UsedOilController(
    private val assistantService: UsedOilAssistantService,
    private val pdfService: UsedOilSummaryPdfService,
    private val applicationPdfService: UsedOilApplicationPdfService
) {

    @PostMapping("/tier-determination")
    @Operation(summary = "Determine CA-1 vs CA-2 tier fit")
    fun determineTier(@RequestBody request: TierDeterminationRequest): ResponseEntity<ApiResponse<TierDeterminationResponse>> =
        ResponseEntity.ok(ApiResponse.ok(assistantService.determineTier(request)))

    @PostMapping("/ca1-prerequisite-check")
    @Operation(summary = "Check the CA-1 signed-agreement prerequisite gate")
    fun checkCa1Prerequisite(@RequestBody request: Ca1PrerequisiteCheckRequest): ResponseEntity<ApiResponse<Ca1PrerequisiteCheckResponse>> =
        ResponseEntity.ok(ApiResponse.ok(assistantService.checkCa1Prerequisite(request)))

    @GetMapping("/ca2-readiness-checklist")
    @Operation(summary = "Get the CA-2 physical-readiness checklist")
    fun ca2ReadinessChecklist(): ResponseEntity<ApiResponse<Ca2ReadinessChecklistResponse>> =
        ResponseEntity.ok(ApiResponse.ok(assistantService.getCa2ReadinessChecklist()))

    @PostMapping("/fee-calculation")
    @Operation(summary = "Calculate CA-1/CA-2 registration fee + annual processing charge")
    fun calculateFee(@Valid @RequestBody request: FeeCalculationRequest): ResponseEntity<ApiResponse<FeeCalculationResponse>> =
        ResponseEntity.ok(ApiResponse.ok(assistantService.calculateFee(request)))

    @GetMapping("/ca1-form-checklist")
    @Operation(summary = "Get the CA-1 form/content walkthrough checklist")
    fun ca1FormChecklist(): ResponseEntity<ApiResponse<Ca1FormChecklistResponse>> =
        ResponseEntity.ok(ApiResponse.ok(assistantService.getCa1FormChecklist()))

    @PostMapping("/summary")
    @Operation(summary = "Build the downloadable registration summary (JSON)")
    fun summary(@Valid @RequestBody request: UsedOilSummaryRequest): ResponseEntity<ApiResponse<UsedOilSummaryResponse>> =
        ResponseEntity.ok(ApiResponse.ok(assistantService.buildSummary(request)))

    @PostMapping("/summary/pdf")
    @Operation(summary = "Download the registration summary as PDF")
    fun summaryPdf(@Valid @RequestBody request: UsedOilSummaryRequest): ResponseEntity<ByteArray> {
        val summary = assistantService.buildSummary(request)
        val pdf = pdfService.generateSummaryPdf(summary)
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"eprid-used-oil-summary.pdf\"")
            .contentType(MediaType.APPLICATION_PDF)
            .body(pdf)
    }

    @PostMapping("/application/pdf")
    @Operation(summary = "Download a prefilled CA-1/CA-2 application worksheet built from the fields you entered")
    fun applicationPdf(@Valid @RequestBody request: UsedOilSummaryRequest): ResponseEntity<ByteArray> {
        val pdf = applicationPdfService.generateApplicationPdf(request)
        val tier = if (request.tier == UsedOilTier.CA_1) "ca1" else "ca2"
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"eprid-used-oil-$tier-application.pdf\"")
            .contentType(MediaType.APPLICATION_PDF)
            .body(pdf)
    }
}
