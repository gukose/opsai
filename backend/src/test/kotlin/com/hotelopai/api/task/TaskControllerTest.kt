package com.hotelopai.task.api

import com.hotelopai.hotel.application.HotelRepository
import com.hotelopai.hotel.domain.Hotel
import com.hotelopai.notification.application.NotificationRepository
import com.hotelopai.outbox.application.OperationalOutboxRepository
import com.hotelopai.outbox.domain.OperationalOutboxAggregateTypes
import com.hotelopai.outbox.domain.OperationalOutboxEventTypes
import com.hotelopai.outbox.domain.OperationalOutboxStatus
import com.hotelopai.support.PostgresIntegrationTestSupport
import com.hotelopai.task.application.TaskRepository
import com.hotelopai.task.domain.Task
import com.hotelopai.task.domain.TaskIntentType
import com.hotelopai.task.domain.TaskPriority
import com.hotelopai.task.domain.TaskSource
import com.hotelopai.task.domain.TaskStatus
import com.hotelopai.task.infrastructure.persistence.TaskLogJpaRepository
import com.hotelopai.task.infrastructure.persistence.TaskStateHistoryJpaRepository
import org.assertj.core.api.Assertions.assertThat
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
import java.util.UUID

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class TaskControllerTest : PostgresIntegrationTestSupport() {
    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var hotelRepository: HotelRepository

    @Autowired
    private lateinit var taskRepository: TaskRepository

    @Autowired
    private lateinit var taskStateHistoryJpaRepository: TaskStateHistoryJpaRepository

    @Autowired
    private lateinit var taskLogJpaRepository: TaskLogJpaRepository

    @Autowired
    private lateinit var notificationRepository: NotificationRepository

    @Autowired
    private lateinit var outboxRepository: OperationalOutboxRepository

    private val httpClient = HttpClient.newHttpClient()

    @Test
    fun `unauthenticated task create returns unauthorized`() {
        val response = post(
            "/api/v1/tasks",
            """{
              "hotelId":"${UuidV7Generator.generate()}",
              "intentType":"MAINTENANCE",
              "source":"MANUAL",
              "title":"Unauthorized create",
              "description":"Unauthorized create description",
              "priority":"MEDIUM",
              "slaDeadline":"${Instant.now().plusSeconds(3600)}"
            }"""
        )

        assertEquals(401, response.statusCode())
    }

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
    fun `task create derives hotel from authenticated user and rejects mismatched body hotel`() {
        val login = login()
        val otherHotel = hotelRepository.save(Hotel(code = "task-create-other-${UuidV7Generator.generate()}", name = "Other Hotel"))
        val deadline = Instant.now().plusSeconds(3600)

        val created = post(
            "/api/v1/tasks",
            """{
              "hotelId":"${login.hotelId}",
              "intentType":"MAINTENANCE",
              "source":"MANUAL",
              "title":"Scoped create",
              "description":"Scoped create description",
              "priority":"HIGH",
              "slaDeadline":"$deadline"
            }""",
            login.accessToken
        )

        assertEquals(200, created.statusCode())
        val taskId = UUID.fromString(extractId(created.body()))
        val task = taskRepository.findById(taskId) ?: error("created task not found")
        assertEquals(login.hotelId, task.hotelId)
        assertContains(created.body(), """"hotelId":"${login.hotelId}"""")
        assertThat(taskStateHistoryJpaRepository.findAllByTaskIdOrderByCreatedAtAsc(taskId).map { it.hotelId })
            .containsOnly(login.hotelId)
        assertThat(taskLogJpaRepository.findAllByTaskIdOrderByCreatedAtAsc(taskId).map { it.hotelId })
            .containsOnly(login.hotelId)
        assertEquals(0, notificationRepository.countBySourceTaskId(taskId))
        val outboxEvent = outboxRepository.findByEventAggregate(
            eventType = OperationalOutboxEventTypes.TASK_CREATED,
            aggregateType = OperationalOutboxAggregateTypes.TASK,
            aggregateId = taskId
        ) ?: error("created task did not enqueue outbox event")
        assertEquals(login.hotelId, outboxEvent.hotelId)
        assertEquals(OperationalOutboxStatus.PENDING, outboxEvent.status)

        val foreignTaskCountBefore = taskRepository.findAllByHotelId(otherHotel.id).size
        val mismatch = post(
            "/api/v1/tasks",
            """{
              "hotelId":"${otherHotel.id}",
              "intentType":"MAINTENANCE",
              "source":"MANUAL",
              "title":"Foreign scoped create",
              "description":"Foreign scoped create description",
              "priority":"HIGH",
              "slaDeadline":"$deadline"
            }""",
            login.accessToken
        )

        assertEquals(400, mismatch.statusCode())
        assertContains(mismatch.body(), """"detail":"hotelId must match the authenticated hotel"""")
        assertEquals(foreignTaskCountBefore, taskRepository.findAllByHotelId(otherHotel.id).size)
    }

    @Test
    fun `task lifecycle writes cannot mutate a foreign hotel task`() {
        val login = login()
        val otherHotel = hotelRepository.save(Hotel(code = "task-write-other-${UuidV7Generator.generate()}", name = "Other Hotel"))
        val foreignTask = taskRepository.save(
            Task.create(
                hotelId = otherHotel.id,
                intentType = TaskIntentType.MAINTENANCE,
                source = TaskSource.MANUAL,
                title = "Foreign task",
                description = "Foreign task description",
                priority = TaskPriority.HIGH,
                slaDeadline = Instant.now().plusSeconds(3600)
            )
        )
        val taskId = foreignTask.id
        val historyBefore = taskStateHistoryJpaRepository.countByTaskId(taskId)
        val logBefore = taskLogJpaRepository.countByTaskId(taskId)
        val notificationsBefore = notificationRepository.countBySourceTaskId(taskId)

        val writeRequests = listOf(
            WriteRequest(
                path = "/api/v1/tasks/$taskId/assign",
                body = """{"assigneeType":"TEAM","assigneeId":"MAINTENANCE","displayName":"Maintenance"}"""
            ),
            WriteRequest(path = "/api/v1/tasks/$taskId/start"),
            WriteRequest(path = "/api/v1/tasks/$taskId/pause"),
            WriteRequest(path = "/api/v1/tasks/$taskId/resume"),
            WriteRequest(path = "/api/v1/tasks/$taskId/complete"),
            WriteRequest(path = "/api/v1/tasks/$taskId/cancel"),
            WriteRequest(path = "/api/v1/tasks/$taskId/overdue")
        )

        writeRequests.forEach { request ->
            val response = post(request.path, request.body, login.accessToken)

            assertEquals(404, response.statusCode(), "Expected ${request.path} to reject foreign task")
            assertContains(response.body(), """"title":"Task not found"""")
        }
        val reloaded = taskRepository.findById(taskId) ?: error("foreign task missing")
        assertEquals(TaskStatus.CREATED, reloaded.status)
        assertEquals(historyBefore, taskStateHistoryJpaRepository.countByTaskId(taskId))
        assertEquals(logBefore, taskLogJpaRepository.countByTaskId(taskId))
        assertEquals(notificationsBefore, notificationRepository.countBySourceTaskId(taskId))
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
        return LoginSnapshot(accessToken = accessToken, hotelId = UUID.fromString(hotelId))
    }

    private fun createTask(accessToken: String, hotelId: UUID, title: String): String {
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
        val hotelId: UUID
    )

    private data class WriteRequest(
        val path: String,
        val body: String = ""
    )
}
