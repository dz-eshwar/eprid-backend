package com.rorapps.eprid.filter

import com.rorapps.eprid.service.auth.JwtService
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class JwtAuthFilter(
    private val jwtService: JwtService,
    private val userDetailsService: UserDetailsService
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain
    ) {
        val authHeader = request.getHeader("Authorization")

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            chain.doFilter(request, response)
            return
        }

        val token = authHeader.substring(7)
        val email = runCatching { jwtService.extractUsername(token) }.getOrNull()
            ?: run { chain.doFilter(request, response); return }

        if (SecurityContextHolder.getContext().authentication == null) {
            val userDetails = runCatching { userDetailsService.loadUserByUsername(email) }.getOrNull()
                ?: run { chain.doFilter(request, response); return }

            if (jwtService.isTokenValid(token, userDetails)) {
                val authToken = UsernamePasswordAuthenticationToken(
                    userDetails, null, userDetails.authorities
                ).apply { details = WebAuthenticationDetailsSource().buildDetails(request) }

                SecurityContextHolder.getContext().authentication = authToken
                MDC.put("userId", email)
            }
        }

        chain.doFilter(request, response)
    }
}
