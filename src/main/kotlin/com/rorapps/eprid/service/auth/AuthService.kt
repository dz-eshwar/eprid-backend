package com.rorapps.eprid.service.auth

import com.rorapps.eprid.dto.auth.AuthResponse
import com.rorapps.eprid.dto.auth.LoginRequest
import com.rorapps.eprid.dto.auth.RegisterRequest
import com.rorapps.eprid.entity.Recycler
import com.rorapps.eprid.entity.User
import com.rorapps.eprid.entity.UserRole
import com.rorapps.eprid.repository.RecyclerRepository
import com.rorapps.eprid.repository.UserRepository
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val recyclerRepository: RecyclerRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtService: JwtService,
    private val authenticationManager: AuthenticationManager
) {

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
            recyclerRepository.save(Recycler(name = user.fullName, userId = user.id))
        }

        return buildAuthResponse(user)
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
