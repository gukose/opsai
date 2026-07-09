package com.hotelopai.assistant.api

import com.hotelopai.support.PostgresIntegrationTestSupport
import com.hotelopai.hotel.application.HotelRepository
import com.hotelopai.hotel.domain.Hotel
import com.hotelopai.shared.kernel.UuidV7Generator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.ActiveProfiles
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AssistantConversationControllerTest : PostgresIntegrationTestSupport() {
    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var hotelRepository: HotelRepository

    private val httpClient = HttpClient.newHttpClient()

    @Test
    fun `assistant conversation endpoints support start message confirm and reset`() {
        val accessToken = loginAccessToken()
        val hotel = hotelRepository.save(
            Hotel(
                code = "assistant-hotel-${UuidV7Generator.generate()}",
                name = "Assistant Hotel"
            )
        )

        val startResponse = post(
            path = "/api/v1/assistant/conversations",
            body = """{"hotelId":"${hotel.id}","userId":"user-1"}""",
            bearerToken = accessToken
        )

        assertEquals(200, startResponse.statusCode())
        assertContains(startResponse.body(), """"state":"IDLE"""")
        val conversationId = extractConversationId(startResponse.body())

        val messageResponse = post(
            path = "/api/v1/assistant/conversations/$conversationId/messages",
            body = """{"text":"Room 101 AC not working","inputType":"TEXT"}""",
            bearerToken = accessToken
        )

        assertEquals(200, messageResponse.statusCode())
        assertContains(messageResponse.body(), """"state":"WAITING_FOR_CONFIRMATION"""")
        assertContains(messageResponse.body(), """"intent":"MAINTENANCE"""")
        assertContains(messageResponse.body(), """"type":"MAINTENANCE"""")
        assertContains(messageResponse.body(), """"roomNumber":"101"""")

        val confirmResponse = post(
            path = "/api/v1/assistant/conversations/$conversationId/confirm",
            body = """{"idempotencyKey":"confirm-101"}""",
            bearerToken = accessToken
        )

        assertEquals(200, confirmResponse.statusCode())
        assertContains(confirmResponse.body(), """"state":"TASK_CREATED"""")
        assertContains(confirmResponse.body(), """"idempotencyKey":"confirm-101"""")
        val createdTaskId = extractCreatedTaskId(confirmResponse.body())
        assertContains(confirmResponse.body(), """"createdTaskId":"$createdTaskId"""")
        assertContains(confirmResponse.body(), "Task ID: $createdTaskId")

        val duplicateConfirmResponse = post(
            path = "/api/v1/assistant/conversations/$conversationId/confirm",
            body = """{"idempotencyKey":"confirm-101"}""",
            bearerToken = accessToken
        )

        assertEquals(200, duplicateConfirmResponse.statusCode())
        assertContains(duplicateConfirmResponse.body(), """"createdTaskId":"$createdTaskId"""")

        val taskResponse = get("/api/v1/tasks/$createdTaskId", accessToken)
        assertEquals(200, taskResponse.statusCode())
        assertContains(taskResponse.body(), """"id":"$createdTaskId"""")
        assertContains(taskResponse.body(), """"intentType":"MAINTENANCE"""")

        val resetResponse = post(
            path = "/api/v1/assistant/conversations/$conversationId/reset",
            body = "",
            bearerToken = accessToken
        )

        assertEquals(200, resetResponse.statusCode())
        assertContains(resetResponse.body(), """"state":"IDLE"""")
        assertContains(resetResponse.body(), """"taskPreview":null""")
    }

    @Test
    fun `assistant conversation endpoints accept image attachments`() {
        val accessToken = loginAccessToken()
        val hotel = hotelRepository.save(
            Hotel(
                code = "assistant-hotel-${UuidV7Generator.generate()}",
                name = "Assistant Hotel"
            )
        )

        val conversationId = extractConversationId(
            post(
                path = "/api/v1/assistant/conversations",
                body = """{"hotelId":"${hotel.id}","userId":"user-1"}""",
                bearerToken = accessToken
            ).body()
        )

        val imageResponse = post(
            path = "/api/v1/assistant/conversations/$conversationId/messages",
            body = """
                {
                  "text":"",
                  "inputType":"IMAGE",
                  "attachments":[
                    {
                      "id":"att-1",
                      "originalFileName":"broken-door.jpg",
                      "mimeType":"image/jpeg",
                      "sizeBytes":123456,
                      "widthPx":1200,
                      "heightPx":900
                    }
                  ]
                }
            """.trimIndent(),
            bearerToken = accessToken
        )

        assertEquals(200, imageResponse.statusCode())
        assertContains(imageResponse.body(), """"inputType":"IMAGE"""")
        assertContains(imageResponse.body(), """"attachments":[{"id":"att-1"""")
        assertContains(imageResponse.body(), """"state":"WAITING_FOR_USER_ANSWER"""")
    }

    @Test
    fun `assistant asks follow-up when required fields are missing`() {
        val accessToken = loginAccessToken()
        val hotel = hotelRepository.save(
            Hotel(
                code = "assistant-hotel-${UuidV7Generator.generate()}",
                name = "Assistant Hotel"
            )
        )

        val conversationId = extractConversationId(
            post(
                path = "/api/v1/assistant/conversations",
                body = """{"hotelId":"${hotel.id}","userId":"user-1"}""",
                bearerToken = accessToken
            ).body()
        )

        val messageResponse = post(
            path = "/api/v1/assistant/conversations/$conversationId/messages",
            body = """{"text":"Guest needs towels","inputType":"TEXT"}""",
            bearerToken = accessToken
        )

        assertEquals(200, messageResponse.statusCode())
        assertContains(messageResponse.body(), """"state":"WAITING_FOR_USER_ANSWER"""")
        assertContains(messageResponse.body(), """"key":"roomNumber"""")
        assertContains(messageResponse.body(), """"prompt":"Which room is this request for?"""")
    }

    private fun post(
        path: String,
        body: String,
        bearerToken: String? = null
    ): HttpResponse<String> {
        val requestBuilder = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$port$path"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
        if (bearerToken != null) {
            requestBuilder.header("Authorization", "Bearer $bearerToken")
        }

        return httpClient.send(
            requestBuilder.build(),
            HttpResponse.BodyHandlers.ofString()
        )
    }

    private fun get(path: String, bearerToken: String? = null): HttpResponse<String> {
        val requestBuilder = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$port$path"))
            .GET()
        if (bearerToken != null) {
            requestBuilder.header("Authorization", "Bearer $bearerToken")
        }

        return httpClient.send(
            requestBuilder.build(),
            HttpResponse.BodyHandlers.ofString()
        )
    }

    private fun loginAccessToken(): String {
        val response = post(
            "/api/v1/auth/login",
            """{
              "hotelCode":"hotel-opai-demo",
              "email":"admin@hotelopai.local",
              "password":"admin123"
            }"""
        )
        assertEquals(200, response.statusCode())
        return Regex(""""accessToken":"([^"]+)"""")
            .find(response.body())
            ?.groupValues
            ?.get(1)
            ?: error("accessToken not found in response: ${response.body()}")
    }

    private fun extractConversationId(body: String): String =
        Regex(""""conversationId":"([^"]+)"""")
            .find(body)
            ?.groupValues
            ?.get(1)
            ?: error("conversationId not found in response: $body")

    private fun extractCreatedTaskId(body: String): String =
        Regex(""""createdTaskId":"([^"]+)"""")
            .find(body)
            ?.groupValues
            ?.get(1)
            ?: error("createdTaskId not found in response: $body")

    private fun assertContains(value: String, expected: String) {
        assertTrue(
            value.contains(expected),
            "Expected response to contain $expected but was $value"
        )
    }
}
