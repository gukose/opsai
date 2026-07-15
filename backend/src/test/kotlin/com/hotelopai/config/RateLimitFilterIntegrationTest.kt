package com.hotelopai.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.hotelopai.support.PostgresIntegrationTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "ops.ai.rate-limit.default-limit=2",
        "ops.ai.rate-limit.auth-limit=2",
        "ops.ai.rate-limit.write-limit=2",
        "ops.ai.rate-limit.window=PT60S"
    ]
)
@ActiveProfiles("test")
class RateLimitFilterIntegrationTest : PostgresIntegrationTestSupport() {
    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    private val httpClient = HttpClient.newHttpClient()

    @Test
    fun `rate limited API requests return 429 and retry after`() {
        assertThat(get("/api/v1/tasks").statusCode()).isEqualTo(401)
        assertThat(get("/api/v1/tasks").statusCode()).isEqualTo(401)

        val limited = get("/api/v1/tasks", origin = "http://localhost:8081")

        assertThat(limited.statusCode()).isEqualTo(429)
        assertThat(limited.headers().firstValue("Retry-After")).isPresent
        assertThat(limited.headers().firstValue("access-control-allow-origin").orElse(null))
            .isEqualTo("http://localhost:8081")
        val body = objectMapper.readTree(limited.body())
        assertThat(body.path("title").asText()).isEqualTo("Too many requests")
    }

    @Test
    fun `auth endpoints use auth rate limit bucket`() {
        assertThat(invalidLogin().statusCode()).isEqualTo(401)
        assertThat(invalidLogin().statusCode()).isEqualTo(401)

        val limited = invalidLogin()

        assertThat(limited.statusCode()).isEqualTo(429)
        assertThat(limited.headers().firstValue("Retry-After")).isPresent
    }

    @Test
    fun `health and options requests are excluded from rate limiting`() {
        repeat(5) {
            assertThat(get("/actuator/health").statusCode()).isEqualTo(200)
            assertThat(options("/api/v1/tasks").statusCode()).isEqualTo(200)
        }
    }

    private fun get(path: String, origin: String? = null): HttpResponse<String> {
        val builder = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$port$path"))
            .GET()
        if (origin != null) {
            builder.header("Origin", origin)
        }

        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString())
    }

    private fun options(path: String): HttpResponse<String> {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$port$path"))
            .header("Origin", "http://localhost:8081")
            .header("Access-Control-Request-Method", "GET")
            .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
            .build()

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString())
    }

    private fun invalidLogin(): HttpResponse<String> {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$port/api/v1/auth/login"))
            .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .POST(
                HttpRequest.BodyPublishers.ofString(
                    """{
                      "hotelCode":"hotel-opai-demo",
                      "email":"admin@hotelopai.local",
                      "password":"wrong-password"
                    }"""
                )
            )
            .build()

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString())
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
class RateLimitReadBucketIntegrationTest : PostgresIntegrationTestSupport() {
    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    private val httpClient = HttpClient.newHttpClient()

    @Test
    fun `normal dashboard home read sequence does not share one tiny bucket`() {
        val token = login()

        repeat(2) {
            assertThat(authenticatedGet("/api/v1/dashboard/summary?range=today", token).statusCode()).isEqualTo(200)
            assertThat(authenticatedGet("/api/v1/dashboard/reports/tasks?range=today", token).statusCode()).isEqualTo(200)
            assertThat(authenticatedGet("/api/v1/tasks", token).statusCode()).isEqualTo(200)
            assertThat(authenticatedGet("/api/v1/notifications", token).statusCode()).isEqualTo(200)
        }
    }

    @Test
    fun `dashboard summary remains independently rate limited with retry after and cors`() {
        val token = login()

        assertThat(authenticatedGet("/api/v1/dashboard/summary?range=today", token).statusCode()).isEqualTo(200)
        assertThat(authenticatedGet("/api/v1/dashboard/summary?range=today", token).statusCode()).isEqualTo(200)

        val limited = authenticatedGet("/api/v1/dashboard/summary?range=today", token)

        assertThat(limited.statusCode()).isEqualTo(429)
        assertThat(limited.headers().firstValue("Retry-After")).isPresent
        assertThat(limited.headers().firstValue("access-control-allow-origin").orElse(null))
            .isEqualTo("http://localhost:8081")
    }

    @Test
    fun `filtered task get uses read task bucket instead of write bucket`() {
        val token = login()

        assertThat(authenticatedGet("/api/v1/tasks?q=%C5%9Farap", token).statusCode()).isEqualTo(200)
        assertThat(authenticatedGet("/api/v1/tasks?q=wine", token).statusCode()).isEqualTo(200)

        val limited = authenticatedGet("/api/v1/tasks?q=maintenance", token)

        assertThat(limited.statusCode()).isEqualTo(429)
        assertThat(limited.headers().firstValue("Retry-After")).isPresent
    }

    private fun authenticatedGet(path: String, token: String): HttpResponse<String> {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$port$path"))
            .header("Origin", "http://localhost:8081")
            .header("Authorization", "Bearer $token")
            .GET()
            .build()

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString())
    }

    private fun login(): String {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$port/api/v1/auth/login"))
            .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .POST(
                HttpRequest.BodyPublishers.ofString(
                    """{
                      "hotelCode":"hotel-opai-demo",
                      "email":"admin@hotelopai.local",
                      "password":"admin123"
                    }"""
                )
            )
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        assertThat(response.statusCode()).isEqualTo(200)
        return objectMapper.readTree(response.body()).path("accessToken").asText()
    }
}

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["ops.ai.rate-limit.enabled=false"]
)
@ActiveProfiles("test")
class RateLimitDisabledIntegrationTest : PostgresIntegrationTestSupport() {
    @LocalServerPort
    private var port: Int = 0

    private val httpClient = HttpClient.newHttpClient()

    @Test
    fun `disabled rate limiter does not return 429`() {
        repeat(5) {
            assertThat(get("/api/v1/tasks").statusCode()).isEqualTo(401)
        }
    }

    private fun get(path: String): HttpResponse<String> {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$port$path"))
            .GET()
            .build()

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString())
    }
}
