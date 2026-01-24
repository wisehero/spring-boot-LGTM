package com.example.observability.filter

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Request/Response logging filter.
 *
 * IMPORTANT: DO NOT manually propagate traceId/spanId to MDC.
 * Micrometer Tracing with micrometer-tracing-bridge-otel automatically
 * populates MDC with traceId and spanId.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
class RequestLoggingFilter : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger(RequestLoggingFilter::class.java)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        // Skip actuator endpoints for cleaner logs
        if (request.requestURI.startsWith("/actuator")) {
            filterChain.doFilter(request, response)
            return
        }

        val startTime = System.currentTimeMillis()

        log.info(">>> {} {} (traceId in MDC)", request.method, request.requestURI)

        try {
            filterChain.doFilter(request, response)
        } finally {
            val duration = System.currentTimeMillis() - startTime
            log.info("<<< {} {} - status={} duration={}ms",
                request.method, request.requestURI, response.status, duration)
        }
    }
}
