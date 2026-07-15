package com.hotelopai.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.hotelopai.support.PostgresIntegrationTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
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
class WebCorsConfigurationTest : PostgresIntegrationTestSupport() {
    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var corsProperties: WebCorsProperties

    private val httpClient = HttpClient.newHttpClient()

    @Test
    fun `preflight request from expo web origin is allowed`() {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$port/api/v1/assistant/conversations"))
            .header("Origin", "http://localhost:8081")
            .header("Access-Control-Request-Method", "POST")
            .header("Access-Control-Request-Headers", "Content-Type,Authorization,X-Correlation-Id")
            .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

        assertEquals(200, response.statusCode())
        assertEquals("http://localhost:8081", response.headers().firstValue("access-control-allow-origin").orElse(null))
        assertTrue(
            response.headers().firstValue("access-control-allow-methods")
                .orElse("")
                .contains("POST")
        )
        assertTrue(
            response.headers().firstValue("access-control-allow-headers")
                .orElse("")
                .contains("Authorization")
        )
        assertTrue(
            response.headers().firstValue("access-control-allow-headers")
                .orElse("")
                .contains("X-Correlation-Id")
        )
    }

    @Test
    fun `sprint six authenticated read endpoints include expo web cors origin`() {
        val token = login()

        listOf(
            "/api/v1/dashboard/summary?range=today",
            "/api/v1/dashboard/reports/tasks?range=today",
            "/api/v1/tasks",
            "/api/v1/tasks?q=%C5%9Farap",
            "/api/v1/notifications"
        ).forEach { path ->
            val response = get(path, origin = "http://localhost:8081", bearerToken = token)

            assertThat(response.statusCode())
                .withFailMessage("Expected $path to be reachable, body=${response.body()}")
                .isEqualTo(200)
            assertThat(response.headers().firstValue("access-control-allow-origin").orElse(null))
                .withFailMessage("Missing CORS origin for $path")
                .isEqualTo("http://localhost:8081")
            assertThat(response.headers().firstValue("access-control-allow-credentials").orElse(null))
                .isEqualTo("true")
        }
    }

    @Test
    fun `protected unauthorized response still includes expo web cors origin`() {
        val response = get("/api/v1/tasks", origin = "http://localhost:8081")

        assertThat(response.statusCode()).isEqualTo(401)
        assertThat(response.headers().firstValue("access-control-allow-origin").orElse(null))
            .isEqualTo("http://localhost:8081")
    }

    @Test
    fun `unsupported origin is not granted cors access`() {
        val token = login()

        val response = get("/api/v1/tasks", origin = "https://evil.example", bearerToken = token)

        assertThat(response.statusCode()).isEqualTo(403)
        assertThat(response.headers().firstValue("access-control-allow-origin")).isEmpty
    }

    @Test
    fun `cors defaults stay explicit and do not use wildcard origins`() {
        assertTrue(corsProperties.allowedOrigins.contains("http://localhost:8081"))
        assertFalse(corsProperties.allowedOrigins.contains("*"))
    }

    private fun get(path: String, origin: String, bearerToken: String? = null): HttpResponse<String> {
        val builder = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$port$path"))
            .header("Origin", origin)
            .GET()
        if (bearerToken != null) {
            builder.header("Authorization", "Bearer $bearerToken")
        }
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString())
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
