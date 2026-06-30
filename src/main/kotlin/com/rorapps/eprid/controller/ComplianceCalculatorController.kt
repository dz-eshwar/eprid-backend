package com.rorapps.eprid.controller

import com.rorapps.eprid.dto.calculator.ComplianceEstimateRequest
import com.rorapps.eprid.dto.calculator.ComplianceEstimateResponse
import com.rorapps.eprid.dto.common.ApiResponse
import com.rorapps.eprid.service.calculator.ComplianceCalculatorService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/calculator")
@Tag(name = "EPR Compliance Calculator", description = "Estimate battery EPR obligations under BWMR 2022")
class ComplianceCalculatorController(
    private val calculatorService: ComplianceCalculatorService
) {

    @PostMapping("/estimate")
    @Operation(
        summary = "Calculate EPR obligation",
        description = "Returns target tonnes, shortfall, and certificate volume needed. No login required."
    )
    fun estimate(
        @Valid @RequestBody request: ComplianceEstimateRequest
    ): ResponseEntity<ApiResponse<ComplianceEstimateResponse>> {
        val result = calculatorService.calculate(request)
        return ResponseEntity.ok(ApiResponse.ok(result))
    }
}
