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
 * Request/Response 로깅 필터
 *
 * 중요: traceId/spanId를 MDC에 수동으로 전파하지 마세요.
 * OpenTelemetry Java Agent가 trace_id와 span_id(snake_case)를 MDC에 자동으로 채웁니다.
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
        // 깔끔한 로그를 위해 actuator 엔드포인트 건너뛰기
        if (request.requestURI.startsWith("/actuator")) {
            filterChain.doFilter(request, response)
            return
        }

        val startTime = System.currentTimeMillis()

        log.info(">>> 요청 수신: {} {} (traceId는 MDC에 자동 포함)", request.method, request.requestURI)

        try {
            filterChain.doFilter(request, response)
        } finally {
            val duration = System.currentTimeMillis() - startTime
            log.info("<<< 응답 완료: {} {} - status={} duration={}ms",
                request.method, request.requestURI, response.status, duration)
        }
    }
}
