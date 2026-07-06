package com.rorapps.eprid.service.auth

import com.rorapps.eprid.dto.auth.AuthResponse
import com.rorapps.eprid.dto.auth.LoginRequest
import com.rorapps.eprid.dto.auth.RegisterRequest
import com.rorapps.eprid.entity.Recycler
import com.rorapps.eprid.entity.RecyclerCredentialCheck
import com.rorapps.eprid.entity.User
import com.rorapps.eprid.entity.UserRole
import com.rorapps.eprid.repository.RecyclerCredentialCheckRepository
import com.rorapps.eprid.repository.RecyclerRepository
import com.rorapps.eprid.repository.UserRepository
import com.rorapps.eprid.service.kyc.KycProvider
import org.slf4j.LoggerFactory
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val recyclerRepository: RecyclerRepository,
    private val recyclerCredentialCheckRepository: RecyclerCredentialCheckRepository,
    private val kycProvider: KycProvider,
    private val passwordEncoder: PasswordEncoder,
    private val jwtService: JwtService,
    private val authenticationManager: AuthenticationManager
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun register(request: RegisterRequest): AuthResponse {
        if (userRepository.existsByEmail(request.email)) {
            throw IllegalArgumentException("An account with this email already exists")
        }

        val user = userRepository.save(
            User(
                email = request.email,
                passwordHash = passwordEncoder.encode(request.password),
                fullName = request.fullName,
                role = request.role
            )
        )

        if (user.role == UserRole.RECYCLER) {
            val recycler = recyclerRepository.save(Recycler(name = user.fullName, userId = user.id))
            runCredentialChecks(recycler, request)
        }

        return buildAuthResponse(user)
    }

    /**
     * Module A0 — Phase 1 KYC checks. Wrapped so a KYC provider failure can never block
     * account creation; results are "step one of an ongoing verification relationship,"
     * not a hard registration gate (PRD §7.0).
     */
    private fun runCredentialChecks(recycler: Recycler, request: RegisterRequest) {
        try {
            val outcomes = buildList {
                if (request.gstin != null && request.legalName != null)
                    add(kycProvider.verifyGst(request.gstin, request.legalName))
                if (request.udyamNumber != null)
                    add(kycProvider.verifyUdyam(request.udyamNumber))
                if (request.cinOrDin != null)
                    add(kycProvider.verifyMca(request.cinOrDin))
            }
            outcomes.forEach { outcome ->
                recyclerCredentialCheckRepository.save(
                    RecyclerCredentialCheck(
                        recycler = recycler,
                        checkType = outcome.checkType,
                        result = outcome.result,
                        provider = outcome.provider,
                        reason = outcome.reason,
                        checkedAt = outcome.checkedAt
                    )
                )
            }
        } catch (ex: Exception) {
            log.error("Credential checks failed for recycler ${recycler.id}: ${ex.message}")
            // Swallow — registration must succeed regardless of KYC provider availability.
        }
    }

    fun login(request: LoginRequest): AuthResponse {
        authenticationManager.authenticate(
            UsernamePasswordAuthenticationToken(request.email, request.password)
        )
        val user = userRepository.findByEmail(request.email)
            .orElseThrow { IllegalStateException("User not found after successful authentication") }

        return buildAuthResponse(user)
    }

    private fun buildAuthResponse(user: User) = AuthResponse(
        token = jwtService.generateToken(user),
        userId = user.id!!,
        email = user.email,
        fullName = user.fullName,
        role = user.role.name,
        recyclerId = if (user.role == UserRole.RECYCLER)
            recyclerRepository.findByUserId(user.id!!)?.id
        else null
    )
}
