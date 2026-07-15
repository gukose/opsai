package com.hotelopai.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.hotelopai.shared.error.ProblemDetailFactory
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.net.URI
import java.time.Clock
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
@EnableConfigurationProperties(RateLimitProperties::class)
class RateLimitFilter(
    private val properties: RateLimitProperties,
    private val objectMapper: ObjectMapper,
    private val clock: Clock
) : OncePerRequestFilter() {
    private val buckets = ConcurrentHashMap<String, RateLimitBucket>()

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        if (!properties.enabled || isExcluded(request) || !request.requestURI.startsWith("/api/v1/")) {
            filterChain.doFilter(request, response)
            return
        }

        val rule = ruleFor(request)
        val now = clock.millis()
        val windowMillis = properties.window.toMillis()
        val key = "${rule.name}:${principalKey(request)}"
        val bucket = buckets.compute(key) { _, current ->
            if (current == null || now >= current.windowEndsAtMillis) {
                RateLimitBucket(count = 1, windowEndsAtMillis = now + windowMillis)
            } else {
                current.copy(count = current.count + 1)
            }
        } ?: RateLimitBucket(count = 1, windowEndsAtMillis = now + windowMillis)

        if (bucket.count <= rule.limit) {
            filterChain.doFilter(request, response)
            return
        }

        val retryAfterSeconds = max(1L, (bucket.windowEndsAtMillis - now + 999L) / 1000L)
        val problemDetail = ProblemDetailFactory.create(
            status = HttpStatus.TOO_MANY_REQUESTS,
            title = "Too many requests",
            detail = "Rate limit exceeded. Please retry after $retryAfterSeconds seconds.",
            type = URI.create("https://hotelopai.com/problems/rate-limit-exceeded"),
            instance = URI.create(request.requestURI)
        )

        response.status = HttpStatus.TOO_MANY_REQUESTS.value()
        response.contentType = MediaType.APPLICATION_PROBLEM_JSON_VALUE
        response.setHeader(HttpHeaders.RETRY_AFTER, retryAfterSeconds.toString())
        objectMapper.writeValue(response.outputStream, problemDetail)
    }

    private fun isExcluded(request: HttpServletRequest): Boolean {
        val path = request.requestURI
        return request.method.equals("OPTIONS", ignoreCase = true) ||
            path == "/actuator/info" ||
            path == "/actuator/health" ||
            path.startsWith("/actuator/health/")
    }

    private fun ruleFor(request: HttpServletRequest): RateLimitRule {
        val path = request.requestURI
        return when {
            request.method == "POST" && (path == "/api/v1/auth/login" || path == "/api/v1/auth/refresh") ->
                RateLimitRule("auth", properties.authLimit)
            request.method == "POST" &&
                (path == "/api/v1/tasks" || path.startsWith("/api/v1/tasks/") || path.startsWith("/api/v1/assistant/conversations")) ->
                RateLimitRule("write", properties.writeLimit)
            request.method == "GET" ->
                RateLimitRule("read:${readBucketName(path)}", properties.defaultLimit)
            else ->
                RateLimitRule("default", properties.defaultLimit)
        }
    }

    private fun readBucketName(path: String): String =
        when {
            path == "/api/v1/dashboard/summary" -> "dashboard-summary"
            path == "/api/v1/dashboard/reports/tasks" -> "dashboard-reporting"
            path == "/api/v1/tasks" -> "tasks"
            path == "/api/v1/notifications" -> "notifications"
            path == "/api/v1/auth/me" -> "auth-me"
            else -> path.trim('/').replace('/', '-').ifBlank { "root" }
        }

    private fun principalKey(request: HttpServletRequest): String {
        val authorization = request.getHeader(HttpHeaders.AUTHORIZATION)
            ?.takeIf { it.isNotBlank() }
        if (authorization != null) {
            return "token:${authorization.hashCode()}"
        }

        return "ip:${request.remoteAddr ?: "unknown"}"
    }

    private data class RateLimitRule(
        val name: String,
        val limit: Int
    )

    private data class RateLimitBucket(
        val count: Int,
        val windowEndsAtMillis: Long
    )
}
