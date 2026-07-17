package com.hotelopai.observability

import com.hotelopai.auth.application.AccessTokenContext
import com.hotelopai.auth.application.AccessTokenService
import com.hotelopai.config.CorrelationIdFilter
import com.hotelopai.shared.kernel.CorrelationIdContextHolder
import com.hotelopai.support.PostgresIntegrationTestSupport
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import jakarta.servlet.FilterChain
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.test.context.ActiveProfiles
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Clock
import java.util.UUID

class OperationalObservabilityTest {
    private val registry = SimpleMeterRegistry()
    private val observability = OperationalObservability(registry)

    @Test
    fun `confidence bucket boundaries are finite and stable`() {
        assertThat(observability.confidenceBucket(null)).isEqualTo("none")
        assertThat(observability.confidenceBucket("0.49".toBigDecimal())).isEqualTo("low")
        assertThat(observability.confidenceBucket("0.50".toBigDecimal())).isEqualTo("medium")
        assertThat(observability.confidenceBucket("0.79".toBigDecimal())).isEqualTo("medium")
        assertThat(observability.confidenceBucket("0.80".toBigDecimal())).isEqualTo("high")
    }

    @Test
    fun `endpoint grouping avoids resource id paths`() {
        assertThat(observability.endpointGroup("/api/v1/tasks/00000000-0000-7000-8000-000000000001"))
            .isEqualTo("tasks")
        assertThat(observability.endpointGroup("/api/v1/assistant/conversations/conversation-1/messages"))
            .isEqualTo("assistant")
        assertThat(observability.endpointGroup("/api/v1/dashboard/reports/tasks")).isEqualTo("reporting")
        assertThat(observability.endpointGroup("/unmatched/path")).isEqualTo("other")
    }

    @Test
    fun `metric helper ignores disallowed high cardinality tag keys`() {
        observability.incrementCounter(
            "hotelopai.test.counter",
            "operation" to "sample",
            "hotelId" to UUID.randomUUID().toString(),
            "conversationId" to "conversation-123"
        )

        val meter = registry.find("hotelopai.test.counter").counter()
        assertThat(meter).isNotNull
        assertThat(meter!!.id.tags.map { it.key }).containsExactly("operation")
    }

    @Test
    fun `timer helper records observations with safe tags`() {
        val sample = observability.startTimer()

        observability.stopTimer(
            sample,
            "hotelopai.test.duration",
            "operation" to "sample",
            "taskId" to UUID.randomUUID().toString()
        )

        val timer = registry.find("hotelopai.test.duration").timer()
        assertThat(timer).isNotNull
        assertThat(timer!!.count()).isEqualTo(1)
        assertThat(timer.id.tags.map { it.key }).containsExactly("operation")
    }
}

class CorrelationIdFilterTest {
    private val filter = CorrelationIdFilter()

    @Test
    fun `missing header generates safe correlation id and clears context`() {
        val response = execute()

        val correlationId = response.getHeader("X-Correlation-Id")
        assertThat(correlationId).matches(SAFE_CORRELATION_ID)
        assertThat(CorrelationIdContextHolder.current()).isNull()
        assertThat(MDC.get("correlationId")).isNull()
    }

    @Test
    fun `safe header is trimmed preserved and available during request`() {
        var duringRequestContext: String? = null
        var duringRequestMdc: String? = null

        val response = execute("  trace-123._:abc  ") {
            duringRequestContext = CorrelationIdContextHolder.current()
            duringRequestMdc = MDC.get("correlationId")
        }

        assertThat(response.getHeader("X-Correlation-Id")).isEqualTo("trace-123._:abc")
        assertThat(duringRequestContext).isEqualTo("trace-123._:abc")
        assertThat(duringRequestMdc).isEqualTo("trace-123._:abc")
        assertThat(CorrelationIdContextHolder.current()).isNull()
        assertThat(MDC.get("correlationId")).isNull()
    }

    @Test
    fun `malformed and oversized headers are replaced without echoing unsafe input`() {
        val malformed = execute("trace with spaces!")
        val oversized = execute("a".repeat(129))

        assertThat(malformed.getHeader("X-Correlation-Id")).matches(SAFE_CORRELATION_ID)
        assertThat(malformed.getHeader("X-Correlation-Id")).isNotEqualTo("trace with spaces!")
        assertThat(oversized.getHeader("X-Correlation-Id")).matches(SAFE_CORRELATION_ID)
        assertThat(oversized.getHeader("X-Correlation-Id")).isNotEqualTo("a".repeat(129))
    }

    private fun execute(
        correlationId: String? = null,
        duringRequest: () -> Unit = {}
    ): MockHttpServletResponse {
        val request = MockHttpServletRequest("GET", "/actuator/health")
        correlationId?.let { request.addHeader("X-Correlation-Id", it) }
        val response = MockHttpServletResponse()
        val chain = FilterChain { _, _ -> duringRequest() }

        filter.doFilter(request, response, chain)
        return response
    }

    companion object {
        private const val SAFE_CORRELATION_ID = "[A-Za-z0-9._:-]+"
    }
}

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "ops.ai.rate-limit.default-limit=2",
        "ops.ai.rate-limit.auth-limit=20",
        "ops.ai.rate-limit.write-limit=2",
        "ops.ai.rate-limit.window=PT60S"
    ]
)
@ActiveProfiles("test")
class OperationalObservabilityIntegrationTest : PostgresIntegrationTestSupport() {
    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var meterRegistry: MeterRegistry

    @Autowired
    private lateinit var accessTokenService: AccessTokenService

    @Autowired
    private lateinit var clock: Clock

    private val httpClient = HttpClient.newHttpClient()

    @Test
    fun `actuator metrics remain protected while health info remain public`() {
        assertThat(get("/actuator/health").statusCode()).isEqualTo(200)
        assertThat(get("/actuator/info").statusCode()).isEqualTo(200)
        assertThat(get("/actuator/metrics").statusCode()).isEqualTo(401)
    }

    @Test
    fun `security denial metrics count unauthorized and forbidden requests`() {
        val unauthorizedBefore = counter(
            "hotelopai.security.denial.total",
            "status" to "401",
            "endpoint_group" to "notifications"
        )
        val forbiddenBefore = counter(
            "hotelopai.security.denial.total",
            "status" to "403",
            "endpoint_group" to "tasks"
        )

        assertThat(get("/api/v1/notifications").statusCode()).isEqualTo(401)
        assertThat(get("/api/v1/tasks", tokenWithoutPermissions()).statusCode()).isEqualTo(403)

        assertThat(
            counter(
                "hotelopai.security.denial.total",
                "status" to "401",
                "endpoint_group" to "notifications"
            )
        ).isEqualTo(unauthorizedBefore + 1.0)
        assertThat(
            counter(
                "hotelopai.security.denial.total",
                "status" to "403",
                "endpoint_group" to "tasks"
            )
        ).isEqualTo(forbiddenBefore + 1.0)
    }

    @Test
    fun `rate-limit rejection metric increments without changing response behavior`() {
        val token = tokenWithoutPermissions()
        val before = counter(
            "hotelopai.rate_limit.rejection.total",
            "endpoint_group" to "tasks",
            "reason_code" to "limit_exceeded"
        )

        assertThat(get("/api/v1/tasks", token).statusCode()).isEqualTo(403)
        assertThat(get("/api/v1/tasks", token).statusCode()).isEqualTo(403)
        val limited = get("/api/v1/tasks", token)

        assertThat(limited.statusCode()).isEqualTo(429)
        assertThat(limited.headers().firstValue("Retry-After")).isPresent
        assertThat(
            counter(
                "hotelopai.rate_limit.rejection.total",
                "endpoint_group" to "tasks",
                "reason_code" to "limit_exceeded"
            )
        ).isEqualTo(before + 1.0)
    }

    @Test
    fun `security and rate limit metric tags stay low cardinality`() {
        val token = tokenWithoutPermissions()
        get("/api/v1/notifications")
        get("/api/v1/tasks", token)
        get("/api/v1/tasks", token)
        get("/api/v1/tasks", token)

        val forbiddenTagKeys = setOf(
            "hotelId",
            "userId",
            "conversationId",
            "taskId",
            "attachmentId",
            "analysisId",
            "notificationId",
            "email",
            "filename",
            "correlationId"
        )
        val observedKeys = meterRegistry.meters
            .filter {
                it.id.name == "hotelopai.security.denial.total" ||
                    it.id.name == "hotelopai.rate_limit.rejection.total"
            }
            .flatMap { meter -> meter.id.tags.map { it.key } }
            .toSet()

        assertThat(observedKeys).doesNotContainAnyElementsOf(forbiddenTagKeys)
    }

    private fun get(path: String, bearerToken: String? = null): HttpResponse<String> {
        val builder = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$port$path"))
            .GET()
        bearerToken?.let { builder.header("Authorization", "Bearer $it") }
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString())
    }

    private fun tokenWithoutPermissions(): String =
        accessTokenService.issueToken(
            AccessTokenContext(
                userId = UUID.randomUUID(),
                hotelId = UUID.randomUUID(),
                employeeId = null,
                email = "observability-test@hotelopai.local",
                displayName = "Observability Test",
                hotelName = "Observability Hotel",
                sessionId = UUID.randomUUID(),
                roleIds = emptySet(),
                roleCodes = emptySet(),
                permissionIds = emptySet(),
                permissionCodes = emptySet()
            ),
            clock.instant()
        ).token

    private fun counter(name: String, vararg tags: Pair<String, String>): Double =
        meterRegistry.find(name)
            .tags(*tags.flatMap { listOf(it.first, it.second) }.toTypedArray())
            .counter()
            ?.count()
            ?: 0.0
}
