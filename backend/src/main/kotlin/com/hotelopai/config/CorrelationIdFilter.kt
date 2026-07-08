package com.hotelopai.config

import com.hotelopai.shared.kernel.CorrelationIdContextHolder
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class CorrelationIdFilter : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val correlationId = request.getHeader("X-Correlation-Id")
            ?.takeIf { it.isNotBlank() }
            ?: java.util.UUID.randomUUID().toString()

        CorrelationIdContextHolder.set(correlationId)
        MDC.put("correlationId", correlationId)
        response.setHeader("X-Correlation-Id", correlationId)

        try {
            filterChain.doFilter(request, response)
        } finally {
            MDC.remove("correlationId")
            CorrelationIdContextHolder.clear()
        }
    }
}
