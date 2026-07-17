package com.hotelopai.config

import com.hotelopai.shared.kernel.CorrelationIdContextHolder
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

@Component
class CorrelationIdFilter : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val correlationId = normalizeCorrelationId(request.getHeader("X-Correlation-Id"))

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

    private fun normalizeCorrelationId(value: String?): String {
        val trimmed = value?.trim().orEmpty()
        return if (
            trimmed.isNotBlank() &&
            trimmed.length <= MAX_CORRELATION_ID_LENGTH &&
            SAFE_CORRELATION_ID.matches(trimmed)
        ) {
            trimmed
        } else {
            UUID.randomUUID().toString()
        }
    }

    companion object {
        private const val MAX_CORRELATION_ID_LENGTH = 128
        private val SAFE_CORRELATION_ID = Regex("[A-Za-z0-9._:-]+")
    }
}
