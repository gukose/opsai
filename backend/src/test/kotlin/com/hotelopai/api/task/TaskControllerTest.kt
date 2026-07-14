package com.hotelopai.task.api

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
import java.time.Instant
import com.hotelopai.shared.kernel.UuidV7Generator

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class TaskControllerTest : PostgresIntegrationTestSupport() {
    @LocalServerPort
    private var port: Int = 0

    private val httpClient = HttpClient.newHttpClient()

    @Test
    fun `task endpoints support create get list start pause resume complete and reject invalid transition`() {
        val login = login()
        val firstDeadline = Instant.now().plusSeconds(3600)
        val createResponse = post(
            "/api/v1/tasks",
            """{
              "hotelId":"${login.hotelId}",
              "intentType":"MAINTENANCE",
              "source":"ASSISTANT",
              "title":"AC not working",
              "description":"Room 101 AC not working",
              "priority":"HIGH",
              "slaDeadline":"$firstDeadline"
            }""",
            login.accessToken
        )

        assertEquals(200, createResponse.statusCode())
        assertContains(createResponse.body(), """"status":"CREATED"""")
        val taskId = extractId(createResponse.body())

        val getResponse = get("/api/v1/tasks/$taskId", login.accessToken)
        assertEquals(200, getResponse.statusCode())
        assertContains(getResponse.body(), """"id":"$taskId"""")
        assertContains(getResponse.body(), """"title":"AC not working"""")

        val listResponse = get("/api/v1/tasks", login.accessToken)
        assertEquals(200, listResponse.statusCode())
        assertTrue(listResponse.body().contains(taskId))

        assertEquals(200, post("/api/v1/tasks/$taskId/start", "", login.accessToken).statusCode())
        assertEquals(200, post("/api/v1/tasks/$taskId/pause", "", login.accessToken).statusCode())
        assertEquals(200, post("/api/v1/tasks/$taskId/resume", "", login.accessToken).statusCode())
        assertEquals(200, post("/api/v1/tasks/$taskId/complete", "", login.accessToken).statusCode())

        val invalid = post("/api/v1/tasks/$taskId/start", "", login.accessToken)
        assertEquals(400, invalid.statusCode())
        assertContains(invalid.body(), """"title":"Invalid task request"""")
        assertContains(invalid.body(), """"detail":"Invalid workflow transition from COMPLETED to STARTED"""")
    }

    @Test
    fun `task can be cancelled from created state`() {
        val login = login()
        val deadline = Instant.now().plusSeconds(5400)
        val createResponse = post(
            "/api/v1/tasks",
            """{
              "hotelId":"${login.hotelId}",
              "intentType":"GUEST_REQUEST",
              "source":"MANUAL",
              "title":"Extra towels",
              "description":"Guest needs extra towels",
              "priority":"MEDIUM",
              "slaDeadline":"$deadline"
            }""",
            login.accessToken
        )

        val taskId = extractId(createResponse.body())
        val cancelResponse = post("/api/v1/tasks/$taskId/cancel", "", login.accessToken)

        assertEquals(200, cancelResponse.statusCode())
        assertContains(cancelResponse.body(), """"status":"CANCELLED"""")
    }

    @Test
    fun `task list remains an array when pagination params are omitted`() {
        val login = login()

        val response = get("/api/v1/tasks", login.accessToken)

        assertEquals(200, response.statusCode())
        assertTrue(response.body().trim().startsWith("["))
    }

    @Test
    fun `task list returns paged envelope when pagination params are supplied`() {
        val login = login()
        createTask(login.accessToken, login.hotelId, "Paged task one")
        createTask(login.accessToken, login.hotelId, "Paged task two")

        val response = get("/api/v1/tasks?page=0&size=1", login.accessToken)

        assertEquals(200, response.statusCode())
        assertContains(response.body(), """"items":[{""")
        assertContains(response.body(), """"page":0""")
        assertContains(response.body(), """"size":1""")
        assertContains(response.body(), """"totalItems":""")
        assertContains(response.body(), """"totalPages":""")
        assertContains(response.body(), """"hasNext":true""")
        assertContains(response.body(), """"hasPrevious":false""")
    }

    @Test
    fun `task list rejects invalid pagination params`() {
        val login = login()

        assertEquals(400, get("/api/v1/tasks?page=-1&size=20", login.accessToken).statusCode())
        assertEquals(400, get("/api/v1/tasks?page=0&size=0", login.accessToken).statusCode())
        assertEquals(400, get("/api/v1/tasks?page=0&size=101", login.accessToken).statusCode())
    }

    private fun post(path: String, body: String, bearerToken: String? = null): HttpResponse<String> {
        val builder = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$port$path"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
        if (bearerToken != null) {
            builder.header("Authorization", "Bearer $bearerToken")
        }
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString())
    }

    private fun get(path: String, bearerToken: String? = null): HttpResponse<String> {
        val builder = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$port$path"))
            .GET()
        if (bearerToken != null) {
            builder.header("Authorization", "Bearer $bearerToken")
        }
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString())
    }

    private fun login(): LoginSnapshot {
        val response = post(
            "/api/v1/auth/login",
            """{
              "hotelCode":"hotel-opai-demo",
              "email":"admin@hotelopai.local",
              "password":"admin123"
            }"""
        )
        assertEquals(200, response.statusCode())
        val accessToken = Regex(""""accessToken":"([^"]+)"""")
            .find(response.body())
            ?.groupValues
            ?.get(1)
            ?: error("accessToken not found in response: ${response.body()}")
        val hotelId = Regex(""""hotelId":"([^"]+)"""")
            .find(response.body())
            ?.groupValues
            ?.get(1)
            ?: error("hotelId not found in response: ${response.body()}")
        return LoginSnapshot(accessToken = accessToken, hotelId = hotelId)
    }

    private fun createTask(accessToken: String, hotelId: String, title: String): String {
        val response = post(
            "/api/v1/tasks",
            """{
              "hotelId":"$hotelId",
              "intentType":"MAINTENANCE",
              "source":"MANUAL",
              "title":"$title",
              "description":"$title description",
              "priority":"MEDIUM",
              "slaDeadline":"${Instant.now().plusSeconds(3600)}"
            }""",
            accessToken
        )
        assertEquals(200, response.statusCode())
        return extractId(response.body())
    }

    private fun extractId(body: String): String =
        Regex(""""id":"([^"]+)"""")
            .find(body)
            ?.groupValues
            ?.get(1)
            ?: error("task id not found in response: $body")

    private fun assertContains(value: String, expected: String) {
        assertTrue(
            value.contains(expected),
            "Expected response to contain $expected but was $value"
        )
    }

    private data class LoginSnapshot(
        val accessToken: String,
        val hotelId: String
    )
}
