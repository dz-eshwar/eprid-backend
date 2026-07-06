package com.rorapps.eprid.controller

import com.rorapps.eprid.dto.common.ApiResponse
import com.rorapps.eprid.dto.recycler.CredentialCheckOutcomeDto
import com.rorapps.eprid.dto.recycler.RecyclerProfileResponse
import com.rorapps.eprid.entity.User
import com.rorapps.eprid.entity.UserRole
import com.rorapps.eprid.repository.RecyclerCredentialCheckRepository
import com.rorapps.eprid.repository.RecyclerRepository
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/api/v1/recyclers")
@Tag(name = "Recycler Profile", description = "Profile for authenticated RECYCLER users")
@SecurityRequirement(name = "Bearer Authentication")
class RecyclerController(
    private val recyclerRepository: RecyclerRepository,
    private val recyclerCredentialCheckRepository: RecyclerCredentialCheckRepository
) {

    @GetMapping("/me")
    @Operation(
        summary = "Get recycler profile for the current user",
        description = "RECYCLER role only. Returns the recycler entity linked to this account. 403 for other roles, 404 if no recycler entity exists."
    )
    fun myProfile(
        @AuthenticationPrincipal currentUser: User
    ): ResponseEntity<ApiResponse<RecyclerProfileResponse>> {
        if (currentUser.role != UserRole.RECYCLER) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Only RECYCLER users can access this endpoint")
        }
        val recycler = recyclerRepository.findByUserId(currentUser.id!!)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "No recycler profile linked to your account")
        return ResponseEntity.ok(ApiResponse.ok(
            RecyclerProfileResponse(
                id = recycler.id!!,
                name = recycler.name,
                bwmrRegNumber = recycler.bwmrRegNumber,
                selfReportedCapacityT = recycler.selfReportedCapacityT,
                state = recycler.state
            )
        ))
    }

    @GetMapping("/me/credential-checks")
    @Operation(
        summary = "Get credential/KYC check history for the current user's recycler account",
        description = "RECYCLER role only. Module A0 — shows GST/Udyam/MCA check results, most recent first."
    )
    fun myCredentialChecks(
        @AuthenticationPrincipal currentUser: User
    ): ResponseEntity<ApiResponse<List<CredentialCheckOutcomeDto>>> {
        if (currentUser.role != UserRole.RECYCLER) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Only RECYCLER users can access this endpoint")
        }
        val recycler = recyclerRepository.findByUserId(currentUser.id!!)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "No recycler profile linked to your account")
        val checks = recyclerCredentialCheckRepository.findAllByRecyclerIdOrderByCheckedAtDesc(recycler.id!!)
            .map {
                CredentialCheckOutcomeDto(
                    checkType = it.checkType,
                    result = it.result,
                    provider = it.provider,
                    reason = it.reason,
                    checkedAt = it.checkedAt
                )
            }
        return ResponseEntity.ok(ApiResponse.ok(checks))
    }
}
