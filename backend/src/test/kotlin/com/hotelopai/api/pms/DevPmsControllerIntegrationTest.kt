package com.hotelopai.integration.unimock.api

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.hotelopai.support.MockHttpResponse
import com.hotelopai.support.MockHttpServer
import com.hotelopai.support.PostgresIntegrationTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class DevPmsControllerIntegrationTest : PostgresIntegrationTestSupport() {
    companion object {
        @JvmStatic
        val mockUniMockServer = MockHttpServer().apply { start() }

        @JvmStatic
        @DynamicPropertySource
        fun registerProperties(registry: DynamicPropertyRegistry) {
            registry.add("ops.ai.unimock.base-url") { mockUniMockServer.baseUrl }
            registry.add("ops.ai.unimock.request-timeout") { "100ms" }
            registry.add("ops.ai.unimock.connect-timeout") { "100ms" }
            registry.add("ops.ai.unimock.retries.max-attempts") { 1 }
        }

        @JvmStatic
        @AfterAll
        fun tearDownServer() {
            mockUniMockServer.stop()
        }
    }

    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    private val httpClient = HttpClient.newHttpClient()
    private var accessToken: String? = null

    @BeforeEach
    fun resetServer() {
        mockUniMockServer.reset()
        accessToken = null
    }

    @Test
    fun `successful room fetch comes from UniMock`() {
        mockUniMockServer.stub(
            method = "GET",
            path = "/api/pms/rooms/101",
            response = MockHttpResponse(
                status = 200,
                body = """
                    {
                      "roomId":"room-101",
                      "roomNumber":"101",
                      "roomTypeId":"type-deluxe",
                      "roomTypeName":"Deluxe",
                      "floor":"1",
                      "occupied":false,
                      "status":"VACANT"
                    }
                """.trimIndent()
            )
        )

        val response = get("/api/v1/dev/pms/rooms/101")

        assertThat(response.statusCode()).isEqualTo(200)
        val body = json(response.body())
        assertThat(body.path("roomNumber").asText()).isEqualTo("101")
        val upstream = mockUniMockServer.lastRequest("GET", "/api/pms/rooms/101")
        assertThat(upstream).isNotNull
        val responseCorrelationId = response.headers().firstValue("X-Correlation-Id").orElse(null)
        assertThat(responseCorrelationId).isNotBlank()
        assertThat(upstream!!.headers["x-correlation-id"]?.first()).isEqualTo(responseCorrelationId)
    }

    @Test
    fun `successful room status fetch comes from UniMock`() {
        mockUniMockServer.stub(
            method = "GET",
            path = "/api/pms/rooms/101",
            response = MockHttpResponse(
                status = 200,
                body = """
                    {
                      "roomId":"room-101",
                      "roomNumber":"101",
                      "roomTypeId":"type-deluxe",
                      "roomTypeName":"Deluxe",
                      "floor":"1",
                      "occupied":false,
                      "status":"VACANT"
                    }
                """.trimIndent()
            )
        )
        mockUniMockServer.stub(
            method = "GET",
            path = "/api/pms/rooms/101/status",
            response = MockHttpResponse(
                status = 200,
                body = """
                    {
                      "roomNumber":"101",
                      "status":"VACANT",
                      "updatedAt":"2026-07-08T10:00:00Z"
                    }
                """.trimIndent()
            )
        )

        val response = get("/api/v1/dev/pms/rooms/101/status")

        assertThat(response.statusCode()).isEqualTo(200)
        val body = json(response.body())
        assertThat(body.path("roomNumber").asText()).isEqualTo("101")
        assertThat(body.path("status").asText()).isEqualTo("VACANT")
        assertThat(mockUniMockServer.requests("GET", "/api/pms/rooms/101/status")).hasSize(1)
    }

    @Test
    fun `404 mapping returns problem details`() {
        mockUniMockServer.stub(
            method = "GET",
            path = "/api/pms/rooms/404/status",
            response = MockHttpResponse(
                status = 404,
                body = """{"message":"Room not found"}"""
            )
        )
        mockUniMockServer.stub(
            method = "GET",
            path = "/api/pms/rooms/404",
            response = MockHttpResponse(
                status = 404,
                body = """{"message":"Room not found"}"""
            )
        )

        val response = get("/api/v1/dev/pms/rooms/404/status")

        assertThat(response.statusCode()).isEqualTo(404)
        val body = json(response.body())
        assertThat(body.path("type").asText()).contains("dev-pms-resource-not-found")
    }

    @Test
    fun `503 mapping returns problem details`() {
        mockUniMockServer.stub(
            method = "GET",
            path = "/api/pms/rooms",
            response = MockHttpResponse(
                status = 503,
                body = """{"message":"UniMock is unavailable"}"""
            )
        )

        val response = get("/api/v1/dev/pms/rooms")

        assertThat(response.statusCode()).isEqualTo(503)
        val body = json(response.body())
        assertThat(body.path("type").asText()).contains("dev-unimock-unavailable")
    }

    @Test
    fun `timeout mapping returns problem details`() {
        mockUniMockServer.stub(
            method = "GET",
            path = "/api/pms/rooms",
            response = MockHttpResponse(
                status = 200,
                body = "[]",
                delayMs = 350
            )
        )

        val response = get("/api/v1/dev/pms/rooms")

        assertThat(response.statusCode()).isEqualTo(503)
        val body = json(response.body())
        assertThat(body.path("type").asText()).contains("dev-unimock-unavailable")
    }

    @Test
    fun `backend smoke test proxies room status update to UniMock`() {
        mockUniMockServer.stub(
            method = "GET",
            path = "/api/pms/rooms/101",
            response = MockHttpResponse(
                status = 200,
                body = """
                    {
                      "roomId":"room-101",
                      "roomNumber":"101",
                      "roomTypeId":"type-deluxe",
                      "roomTypeName":"Deluxe",
                      "floor":"1",
                      "occupied":false,
                      "status":"VACANT"
                    }
                """.trimIndent()
            )
        )
        mockUniMockServer.stub(
            method = "POST",
            path = "/api/pms/rooms/101/status",
            response = MockHttpResponse(
                status = 200,
                body = """
                    {
                      "verificationLogId":"11111111-1111-1111-1111-111111111111",
                      "entityId":"101",
                      "operation":"ROOM_STATUS_UPDATE",
                      "status":"OUT_OF_ORDER"
                    }
                """.trimIndent()
            )
        )

        val response = post(
            "/api/v1/dev/pms/rooms/101/status",
            """{"status":"OUT_OF_ORDER"}"""
        )

        assertThat(response.statusCode()).isEqualTo(200)
        val body = json(response.body())
        assertThat(body.path("verificationLogId").asText()).isEqualTo("11111111-1111-1111-1111-111111111111")
        val upstream = mockUniMockServer.lastRequest("POST", "/api/pms/rooms/101/status")
        assertThat(upstream).isNotNull
        assertThat(upstream!!.body)
            .contains("OUT_OF_ORDER")
        assertThat(upstream.headers["x-correlation-id"]?.first()).isEqualTo(
            response.headers().firstValue("X-Correlation-Id").orElse(null)
        )
    }

    @Test
    fun `incoming correlation id is forwarded to UniMock`() {
        mockUniMockServer.stub(
            method = "GET",
            path = "/api/pms/rooms/101",
            response = MockHttpResponse(
                status = 200,
                body = """
                    {
                      "roomId":"room-101",
                      "roomNumber":"101",
                      "roomTypeId":"type-deluxe",
                      "roomTypeName":"Deluxe",
                      "floor":"1",
                      "occupied":false,
                      "status":"VACANT"
                    }
                """.trimIndent()
            )
        )

        val response = get(
            path = "/api/v1/dev/pms/rooms/101",
            correlationId = "corr-12345"
        )

        assertThat(response.statusCode()).isEqualTo(200)
        val upstream = mockUniMockServer.lastRequest("GET", "/api/pms/rooms/101")
        assertThat(upstream?.headers?.get("x-correlation-id")?.first()).isEqualTo("corr-12345")
    }

    private fun get(path: String, correlationId: String? = null): HttpResponse<String> {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$port$path"))
            .header("Authorization", "Bearer ${loginAccessToken()}")
        if (correlationId != null) {
            request.header("X-Correlation-Id", correlationId)
        }
        request.GET()
        val httpRequest = request.build()
        return httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString())
    }

    private fun post(
        path: String,
        body: String,
        correlationId: String? = null
    ): HttpResponse<String> {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$port$path"))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer ${loginAccessToken()}")
        if (correlationId != null) {
            request.header("X-Correlation-Id", correlationId)
        }
        request.POST(HttpRequest.BodyPublishers.ofString(body))
        return httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString())
    }

    private fun loginAccessToken(): String {
        accessToken?.let { return it }
        val request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$port/api/v1/auth/login"))
            .header("Content-Type", "application/json")
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
        val token = json(response.body()).path("accessToken").asText()
        assertThat(token).isNotBlank()
        accessToken = token
        return token
    }

    private fun json(value: String): JsonNode = objectMapper.readTree(value)
}
