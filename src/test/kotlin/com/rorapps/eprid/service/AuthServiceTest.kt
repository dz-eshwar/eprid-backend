package com.rorapps.eprid.service

import com.rorapps.eprid.dto.auth.LoginRequest
import com.rorapps.eprid.dto.auth.RegisterRequest
import com.rorapps.eprid.entity.User
import com.rorapps.eprid.entity.UserRole
import com.rorapps.eprid.repository.RecyclerCredentialCheckRepository
import com.rorapps.eprid.repository.RecyclerRepository
import com.rorapps.eprid.repository.UserRepository
import com.rorapps.eprid.service.auth.AuthService
import com.rorapps.eprid.service.auth.JwtService
import com.rorapps.eprid.service.kyc.KycProvider
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.crypto.password.PasswordEncoder
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class AuthServiceTest {

    @Mock private lateinit var userRepository: UserRepository
    @Mock private lateinit var recyclerRepository: RecyclerRepository
    @Mock private lateinit var recyclerCredentialCheckRepository: RecyclerCredentialCheckRepository
    @Mock private lateinit var kycProvider: KycProvider
    @Mock private lateinit var passwordEncoder: PasswordEncoder
    @Mock private lateinit var jwtService: JwtService
    @Mock private lateinit var authenticationManager: AuthenticationManager

    @InjectMocks private lateinit var authService: AuthService

    private val savedUser = User(
        id = "user-123",
        email = "test@example.com",
        passwordHash = "hashed",
        fullName = "Test User",
        role = UserRole.CONSULTANT
    )

    @Test
    fun `register creates user and returns JWT`() {
        whenever(userRepository.existsByEmail("test@example.com")).thenReturn(false)
        whenever(passwordEncoder.encode("password123")).thenReturn("hashed")
        whenever(userRepository.save(any())).thenReturn(savedUser)
        whenever(jwtService.generateToken(any())).thenReturn("jwt-token")

        val result = authService.register(
            RegisterRequest(email = "test@example.com", password = "password123", fullName = "Test User")
        )

        assertEquals("jwt-token", result.token)
        assertEquals("user-123", result.userId)
        assertEquals("test@example.com", result.email)
        verify(userRepository).save(any())
    }

    @Test
    fun `register throws when email already exists`() {
        whenever(userRepository.existsByEmail("test@example.com")).thenReturn(true)

        assertThrows<IllegalArgumentException> {
            authService.register(
                RegisterRequest(email = "test@example.com", password = "password123", fullName = "Test User")
            )
        }
        verify(userRepository, never()).save(any())
    }

    @Test
    fun `register rejects self-registration as ADMIN`() {
        whenever(userRepository.existsByEmail("admin@example.com")).thenReturn(false)

        assertThrows<IllegalArgumentException> {
            authService.register(
                RegisterRequest(
                    email = "admin@example.com",
                    password = "password123",
                    fullName = "Would-be Admin",
                    role = UserRole.ADMIN
                )
            )
        }
        verify(userRepository, never()).save(any())
    }

    @Test
    fun `login authenticates and returns JWT`() {
        whenever(authenticationManager.authenticate(any())).thenReturn(
            UsernamePasswordAuthenticationToken("test@example.com", "password123")
        )
        whenever(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(savedUser))
        whenever(jwtService.generateToken(any())).thenReturn("jwt-token")

        val result = authService.login(LoginRequest(email = "test@example.com", password = "password123"))

        assertEquals("jwt-token", result.token)
        assertEquals("CONSULTANT", result.role)
    }
}
