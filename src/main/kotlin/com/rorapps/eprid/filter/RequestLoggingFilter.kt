package com.rorapps.eprid.filter

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

/**
 * Assigns a correlation id to every request (reusing an inbound X-Request-Id if present),
 * puts it + method/path in MDC so every log line for the request can be tied together,
 * and logs one summary line per request on completion.
 */
@Component
class RequestLoggingFilter : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger(RequestLoggingFilter::class.java)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain
    ) {
        val requestId = request.getHeader(REQUEST_ID_HEADER)?.takeIf { it.isNotBlank() }
            ?: UUID.randomUUID().toString()
        response.setHeader(REQUEST_ID_HEADER, requestId)

        MDC.put("requestId", requestId)
        MDC.put("method", request.method)
        MDC.put("path", request.requestURI)

        val start = System.currentTimeMillis()
        try {
            chain.doFilter(request, response)
        } finally {
            val durationMs = System.currentTimeMillis() - start
            log.info("{} {} -> {} ({}ms)", request.method, request.requestURI, response.status, durationMs)
            MDC.clear()
        }
    }

    companion object {
        const val REQUEST_ID_HEADER = "X-Request-Id"
    }
}
