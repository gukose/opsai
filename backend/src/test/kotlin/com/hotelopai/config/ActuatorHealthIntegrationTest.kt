package com.hotelopai.config

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
class ActuatorHealthIntegrationTest : PostgresIntegrationTestSupport() {
    @LocalServerPort
    private var port: Int = 0

    private val httpClient = HttpClient.newHttpClient()

    @Test
    fun `health endpoint is exposed`() {
        val response = get("/actuator/health")

        assertThat(response.statusCode()).isEqualTo(200)
        assertThat(response.body()).contains("\"status\":\"UP\"")
    }

    @Test
    fun `liveness and readiness health groups are exposed`() {
        val liveness = get("/actuator/health/liveness")
        val readiness = get("/actuator/health/readiness")

        assertThat(liveness.statusCode()).isEqualTo(200)
        assertThat(liveness.body()).contains("\"status\":\"UP\"")
        assertThat(readiness.statusCode()).isEqualTo(200)
        assertThat(readiness.body()).contains("\"status\":\"UP\"")
    }

    @Test
    fun `metrics endpoint is not public`() {
        val response = get("/actuator/metrics")

        assertThat(response.statusCode()).isEqualTo(401)
    }

    private fun get(path: String): HttpResponse<String> {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$port$path"))
            .GET()
            .build()

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString())
    }
}
