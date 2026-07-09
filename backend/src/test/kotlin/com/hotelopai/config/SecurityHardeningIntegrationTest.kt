package com.hotelopai.config

import com.fasterxml.jackson.databind.JsonNode
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class SecurityHardeningIntegrationTest : PostgresIntegrationTestSupport() {
    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    private val httpClient = HttpClient.newHttpClient()

    @Test
    fun `public endpoints remain accessible without bearer token`() {
        assertThat(get("/actuator/health").statusCode()).isEqualTo(200)
        assertThat(get("/actuator/health/liveness").statusCode()).isEqualTo(200)
        assertThat(get("/actuator/health/readiness").statusCode()).isEqualTo(200)

        val login = post(
            "/api/v1/auth/login",
            """{
              "hotelCode":"hotel-opai-demo",
              "email":"admin@hotelopai.local",
              "password":"admin123"
            }"""
        )

        assertThat(login.statusCode()).isEqualTo(200)
        assertThat(json(login.body()).path("accessToken").asText()).isNotBlank()
    }

    @Test
    fun `cors preflight remains public`() {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$port/api/v1/assistant/conversations"))
            .header("Origin", "http://localhost:8081")
            .header("Access-Control-Request-Method", "POST")
            .header("Access-Control-Request-Headers", "Content-Type,Authorization,X-Correlation-Id")
            .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

        assertThat(response.statusCode()).isEqualTo(200)
        assertThat(response.headers().firstValue("access-control-allow-origin").orElse(null))
            .isEqualTo("http://localhost:8081")
    }

    @Test
    fun `protected endpoints reject missing bearer token`() {
        assertThat(get("/api/v1/tasks").statusCode()).isEqualTo(401)
        assertThat(
            post(
                "/api/v1/assistant/conversations",
                """{"hotelId":"00000000-0000-0000-0000-000000000001","userId":"user-1"}"""
            ).statusCode()
        ).isEqualTo(401)
        assertThat(get("/api/v1/dev/pms/rooms").statusCode()).isEqualTo(401)
    }

    @Test
    fun `security headers are applied`() {
        val response = get("/actuator/health")

        assertThat(response.statusCode()).isEqualTo(200)
        assertThat(response.headers().firstValue("X-Content-Type-Options").orElse(null))
            .isEqualTo("nosniff")
        assertThat(response.headers().firstValue("X-Frame-Options").orElse(null))
            .isEqualTo("DENY")
        assertThat(response.headers().firstValue("Referrer-Policy").orElse(null))
            .isEqualTo("no-referrer")
        assertThat(response.headers().firstValue("Content-Security-Policy").orElse(null))
            .isEqualTo("default-src 'self'; base-uri 'self'; object-src 'none'; frame-ancestors 'none'")
        assertThat(response.headers().firstValue("Permissions-Policy").orElse(null))
            .isEqualTo("camera=(), microphone=(), geolocation=()")
    }

    private fun get(path: String): HttpResponse<String> {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$port$path"))
            .GET()
            .build()

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString())
    }

    private fun post(path: String, body: String): HttpResponse<String> {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$port$path"))
            .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString())
    }

    private fun json(value: String): JsonNode =
        objectMapper.readTree(value)
}
