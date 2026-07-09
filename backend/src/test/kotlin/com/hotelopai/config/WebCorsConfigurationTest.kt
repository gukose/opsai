package com.hotelopai.config

import com.hotelopai.support.PostgresIntegrationTestSupport
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
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
}
