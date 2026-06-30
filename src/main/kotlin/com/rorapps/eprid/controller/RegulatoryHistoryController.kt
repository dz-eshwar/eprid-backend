package com.rorapps.eprid.controller

import com.rorapps.eprid.dto.regulatory.RegulatoryHistoryResponse
import com.rorapps.eprid.entity.User
import com.rorapps.eprid.repository.VerificationCheckRepository
import com.rorapps.eprid.service.regulatory.RegulatoryHistoryService
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/checks/{checkId}/regulatory-history")
@SecurityRequirement(name = "Bearer Authentication")
class RegulatoryHistoryController(
    private val regulatoryService: RegulatoryHistoryService,
    private val checkRepository: VerificationCheckRepository
) {

    /**
     * Triggers async regulatory research for a check.
     * Safe to call multiple times — if research is already running or complete it is a no-op.
     * Returns 202 Accepted immediately; poll GET to see when it completes.
     */
    @PostMapping
    fun trigger(
        @PathVariable checkId: String,
        @AuthenticationPrincipal user: User
    ): ResponseEntity<Map<String, String>> {
        val check = checkRepository.findById(checkId)
            .orElseThrow { NoSuchElementException("Check not found: $checkId") }

        if (check.requestedBy.id != user.id) {
            throw SecurityException("Access denied to check: $checkId")
        }

        regulatoryService.triggerAsync(check)

        return ResponseEntity.accepted().body(
            mapOf(
                "checkId" to checkId,
                "message" to "Regulatory research started. Poll GET /api/v1/checks/$checkId/regulatory-history for results."
            )
        )
    }

    /**
     * Returns the current regulatory research status and any findings.
     */
    @GetMapping
    fun get(
        @PathVariable checkId: String,
        @AuthenticationPrincipal user: User
    ): ResponseEntity<RegulatoryHistoryResponse> {
        val response = regulatoryService.getForCheck(checkId, user.id!!)
        return ResponseEntity.ok(response)
    }
}
