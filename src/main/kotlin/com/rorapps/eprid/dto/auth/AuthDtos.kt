package com.rorapps.eprid.dto.auth

import com.rorapps.eprid.entity.UserRole
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class RegisterRequest(
    @field:NotBlank @field:Email
    val email: String,

    @field:NotBlank @field:Size(min = 8, message = "Password must be at least 8 characters")
    val password: String,

    @field:NotBlank
    val fullName: String,

    val role: UserRole = UserRole.CONSULTANT,

    // Module A0 — optional recycler KYC fields. Registration must succeed regardless of
    // whether these are provided or whether the underlying checks pass.
    val gstin: String? = null,
    val legalName: String? = null,
    val udyamNumber: String? = null,
    val cinOrDin: String? = null
)

data class LoginRequest(
    @field:NotBlank @field:Email
    val email: String,

    @field:NotBlank
    val password: String
)

data class AuthResponse(
    val token: String,
    val userId: String,
    val email: String,
    val fullName: String,
    val role: String,
    val recyclerId: String? = null
)
