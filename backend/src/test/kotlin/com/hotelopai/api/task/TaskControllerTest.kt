package com.hotelopai.task.api

import com.hotelopai.support.PostgresIntegrationTestSupport
import com.hotelopai.hotel.application.HotelRepository
import com.hotelopai.hotel.domain.Hotel
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
import java.time.Instant
import com.hotelopai.shared.kernel.UuidV7Generator

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class TaskControllerTest : PostgresIntegrationTestSupport() {
    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var hotelRepository: HotelRepository

    private val httpClient = HttpClient.newHttpClient()

    @Test
    fun `task endpoints support create get list start pause resume complete and reject invalid transition`() {
        val hotel = hotelRepository.save(Hotel(code = "task-controller-${UuidV7Generator.generate()}", name = "Task Controller Hotel"))
        val firstDeadline = Instant.now().plusSeconds(3600)
        val createResponse = post(
            "/api/v1/tasks",
            """{
              "hotelId":"${hotel.id}",
              "intentType":"MAINTENANCE",
              "source":"ASSISTANT",
              "title":"AC not working",
              "description":"Room 101 AC not working",
              "priority":"HIGH",
              "slaDeadline":"$firstDeadline"
            }"""
        )

        assertEquals(200, createResponse.statusCode())
        assertContains(createResponse.body(), """"status":"CREATED"""")
        val taskId = extractId(createResponse.body())

        val getResponse = get("/api/v1/tasks/$taskId")
        assertEquals(200, getResponse.statusCode())
        assertContains(getResponse.body(), """"id":"$taskId"""")
        assertContains(getResponse.body(), """"title":"AC not working"""")

        val listResponse = get("/api/v1/tasks")
        assertEquals(200, listResponse.statusCode())
        assertTrue(listResponse.body().contains(taskId))

        assertEquals(200, post("/api/v1/tasks/$taskId/start", "").statusCode())
        assertEquals(200, post("/api/v1/tasks/$taskId/pause", "").statusCode())
        assertEquals(200, post("/api/v1/tasks/$taskId/resume", "").statusCode())
        assertEquals(200, post("/api/v1/tasks/$taskId/complete", "").statusCode())

        val invalid = post("/api/v1/tasks/$taskId/start", "")
        assertEquals(400, invalid.statusCode())
        assertContains(invalid.body(), """"title":"Invalid task request"""")
        assertContains(invalid.body(), """"detail":"Invalid workflow transition from COMPLETED to STARTED"""")
    }

    @Test
    fun `task can be cancelled from created state`() {
        val hotel = hotelRepository.save(Hotel(code = "task-controller-${UuidV7Generator.generate()}", name = "Task Controller Hotel"))
        val deadline = Instant.now().plusSeconds(5400)
        val createResponse = post(
            "/api/v1/tasks",
            """{
              "hotelId":"${hotel.id}",
              "intentType":"GUEST_REQUEST",
              "source":"MANUAL",
              "title":"Extra towels",
              "description":"Guest needs extra towels",
              "priority":"MEDIUM",
              "slaDeadline":"$deadline"
            }"""
        )

        val taskId = extractId(createResponse.body())
        val cancelResponse = post("/api/v1/tasks/$taskId/cancel", "")

        assertEquals(200, cancelResponse.statusCode())
        assertContains(cancelResponse.body(), """"status":"CANCELLED"""")
    }

    private fun post(path: String, body: String): HttpResponse<String> {
        val builder = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$port$path"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString())
    }

    private fun get(path: String): HttpResponse<String> {
        val builder = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$port$path"))
            .GET()
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString())
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
}
