package com.hotelopai.shared.api

import com.hotelopai.support.PostgresIntegrationTestSupport
import org.assertj.core.api.Assertions.assertThat
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
class ApiVersionHeaderFilterIntegrationTest : PostgresIntegrationTestSupport() {
    @LocalServerPort
    private var port: Int = 0

    private val httpClient = HttpClient.newHttpClient()

    @Test
    fun `v1 API responses include stable version header`() {
        val response = get("/api/v1/tasks")

        assertThat(response.statusCode()).isEqualTo(401)
        assertThat(response.headers().firstValue(ApiVersions.VERSION_HEADER).orElse(null))
            .isEqualTo(ApiVersions.V1)
    }

    @Test
    fun `non API responses do not include API version header`() {
        val response = get("/actuator/health")

        assertThat(response.statusCode()).isEqualTo(200)
        assertThat(response.headers().firstValue(ApiVersions.VERSION_HEADER)).isEmpty()
    }

    private fun get(path: String): HttpResponse<String> {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$port$path"))
            .GET()
            .build()

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString())
    }
}
