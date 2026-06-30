package com.rorapps.eprid.controller

import com.rorapps.eprid.dto.plausibility.PlausibilityCheckResponse
import com.rorapps.eprid.entity.User
import com.rorapps.eprid.repository.VerificationCheckRepository
import com.rorapps.eprid.service.plausibility.PlausibilityCheckService
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/checks/{checkId}/plausibility")
@SecurityRequirement(name = "Bearer Authentication")
class PlausibilityCheckController(
    private val plausibilityService: PlausibilityCheckService,
    private val checkRepository: VerificationCheckRepository
) {

    @GetMapping
    fun get(
        @PathVariable checkId: String,
        @AuthenticationPrincipal user: User
    ): ResponseEntity<PlausibilityCheckResponse> {
        val check = checkRepository.findById(checkId)
            .orElseThrow { NoSuchElementException("Check not found: $checkId") }

        if (check.requestedBy.id != user.id) {
            throw SecurityException("Access denied to check: $checkId")
        }

        val result = plausibilityService.getForCheck(checkId)
            ?: return ResponseEntity.notFound().build()

        return ResponseEntity.ok(result)
    }
}
