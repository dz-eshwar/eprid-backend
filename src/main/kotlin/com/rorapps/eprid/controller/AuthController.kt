package com.rorapps.eprid.controller

import com.rorapps.eprid.dto.auth.AuthResponse
import com.rorapps.eprid.dto.auth.LoginRequest
import com.rorapps.eprid.dto.auth.RegisterRequest
import com.rorapps.eprid.dto.common.ApiResponse
import com.rorapps.eprid.service.auth.AuthService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Register and log in to E-PRid")
class AuthController(private val authService: AuthService) {

    @PostMapping("/register")
    @Operation(summary = "Register a new user")
    fun register(
        @Valid @RequestBody request: RegisterRequest
    ): ResponseEntity<ApiResponse<AuthResponse>> {
        val result = authService.register(request)
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(result, "Account created"))
    }

    @PostMapping("/login")
    @Operation(summary = "Log in and receive a JWT")
    fun login(
        @Valid @RequestBody request: LoginRequest
    ): ResponseEntity<ApiResponse<AuthResponse>> {
        val result = authService.login(request)
        return ResponseEntity.ok(ApiResponse.ok(result))
    }
}
