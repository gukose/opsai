package com.hotelopai.api.task

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.hotelopai.hotel.application.HotelRepository
import com.hotelopai.hotel.domain.Hotel
import com.hotelopai.shared.kernel.UuidV7Generator
import com.hotelopai.support.PostgresIntegrationTestSupport
import com.hotelopai.task.application.TaskRepository
import com.hotelopai.task.domain.Task
import com.hotelopai.task.domain.TaskAssignment
import com.hotelopai.task.domain.TaskAssigneeType
import com.hotelopai.task.domain.TaskIntentType
import com.hotelopai.task.domain.TaskPriority
import com.hotelopai.task.domain.TaskSource
import com.hotelopai.task.domain.TaskStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.ActiveProfiles
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.UUID

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class TaskSearchControllerIntegrationTest : PostgresIntegrationTestSupport() {
    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var hotelRepository: HotelRepository

    @Autowired
    private lateinit var taskRepository: TaskRepository

    private val httpClient = HttpClient.newHttpClient()

    @Test
    fun `unauthenticated task search returns unauthorized`() {
        val response = get("/api/v1/tasks?q=ac")

        assertThat(response.statusCode()).isEqualTo(401)
    }

    @Test
    fun `list and search are scoped to authenticated hotel`() {
        val login = login()
        val otherHotel = hotelRepository.save(Hotel(code = "task-search-other-${UuidV7Generator.generate()}", name = "Other Hotel"))
        seed(login.hotelId, title = "current scoped fixture")
        val otherTask = seed(otherHotel.id, title = "other scoped fixture")

        val list = get("/api/v1/tasks?page=0&size=50", login.accessToken)
        val search = get("/api/v1/tasks?q=${encode("other scoped fixture")}", login.accessToken)

        assertThat(list.statusCode()).isEqualTo(200)
        assertThat(search.statusCode()).isEqualTo(200)
        assertThat(list.body()).doesNotContain(otherTask.id.toString())
        assertThat(search.body()).doesNotContain(otherTask.id.toString())
        assertThat(json(search.body()).path("items")).isEmpty()
    }

    @Test
    fun `task by id is scoped to authenticated hotel`() {
        val login = login()
        val otherHotel = hotelRepository.save(Hotel(code = "task-by-id-other-${UuidV7Generator.generate()}", name = "Other Hotel"))
        val currentTask = seed(login.hotelId, title = "current readable task")
        val otherTask = seed(otherHotel.id, title = "other unreadable task")

        assertThat(get("/api/v1/tasks/${currentTask.id}", login.accessToken).statusCode()).isEqualTo(200)
        assertThat(get("/api/v1/tasks/${otherTask.id}", login.accessToken).statusCode()).isEqualTo(404)
    }

    @Test
    fun `free text search matches intended task fields and blank q is absent`() {
        val login = login()
        seed(login.hotelId, title = "free text title needle")
        seed(login.hotelId, title = "description search task", description = "description needle lives here")
        seed(login.hotelId, roomNumber = "901A")
        seed(login.hotelId, title = "Assigned task", assignment = userAssignment(login.userId, "Needle Assignee"))

        assertSingleSearchMatch(login, "title needle", "free text title needle")
        assertSingleSearchMatch(login, "description needle", "description search task")
        assertSingleSearchMatch(login, "901a", "Room task")
        assertSingleSearchMatch(login, "needle assignee", "Assigned task")

        val blank = get("/api/v1/tasks?q=%20%20&page=0&size=5", login.accessToken)
        assertThat(blank.statusCode()).isEqualTo(200)
        assertThat(json(blank.body()).path("items").size()).isGreaterThan(0)
    }

    @Test
    fun `invalid search text length returns bad request`() {
        val login = login()
        val tooLong = "x".repeat(101)

        val response = get("/api/v1/tasks?q=$tooLong", login.accessToken)

        assertThat(response.statusCode()).isEqualTo(400)
        assertThat(json(response.body()).path("title").asText()).isEqualTo("Invalid task request")
    }

    @Test
    fun `status filters support single and comma separated values`() {
        val login = login()
        val prefix = "status-${UuidV7Generator.generate()}"
        TaskStatus.entries.forEach { status ->
            seed(login.hotelId, title = "$prefix ${status.name}", status = status)

            val response = get("/api/v1/tasks?q=$prefix&status=${status.name}", login.accessToken)

            assertThat(response.statusCode()).isEqualTo(200)
            assertThat(itemTitles(response)).containsExactly("$prefix ${status.name}")
        }

        val multi = get("/api/v1/tasks?q=$prefix&status=CREATED,COMPLETED", login.accessToken)

        assertThat(multi.statusCode()).isEqualTo(200)
        assertThat(itemTitles(multi)).containsExactly("$prefix COMPLETED", "$prefix CREATED")
    }

    @Test
    fun `invalid status returns bad request`() {
        val login = login()

        val response = get("/api/v1/tasks?status=NOPE", login.accessToken)

        assertThat(response.statusCode()).isEqualTo(400)
    }

    @Test
    fun `priority filters support single and comma separated values`() {
        val login = login()
        val prefix = "priority-${UuidV7Generator.generate()}"
        TaskPriority.entries.forEach { priority ->
            seed(login.hotelId, title = "$prefix ${priority.name}", priority = priority)
        }

        val high = get("/api/v1/tasks?q=$prefix&priority=HIGH", login.accessToken)
        val multi = get("/api/v1/tasks?q=$prefix&priority=HIGH,URGENT", login.accessToken)

        assertThat(high.statusCode()).isEqualTo(200)
        assertThat(itemTitles(high)).containsExactly("$prefix HIGH")
        assertThat(multi.statusCode()).isEqualTo(200)
        assertThat(itemTitles(multi)).containsExactly("$prefix URGENT", "$prefix HIGH")
    }

    @Test
    fun `invalid priority returns bad request`() {
        val login = login()

        val response = get("/api/v1/tasks?priority=MAX", login.accessToken)

        assertThat(response.statusCode()).isEqualTo(400)
    }

    @Test
    fun `assignment filters use existing assignment model and authenticated context`() {
        val login = login()
        val prefix = "assignment-${UuidV7Generator.generate()}"
        seed(login.hotelId, title = "$prefix unassigned")
        seed(login.hotelId, title = "$prefix mine", assignment = userAssignment(login.userId, "Current User"))
        seed(login.hotelId, title = "$prefix other user", assignment = userAssignment(UuidV7Generator.generate(), "Other User"))
        seed(login.hotelId, title = "$prefix admin role", assignment = teamAssignment("ADMIN", "Admin Team"))
        seed(login.hotelId, title = "$prefix other team", assignment = teamAssignment("HOUSEKEEPING", "Housekeeping"))

        assertThat(itemTitles(get("/api/v1/tasks?q=$prefix&assignment=assigned", login.accessToken)))
            .containsExactly("$prefix other team", "$prefix admin role", "$prefix other user", "$prefix mine")
        assertThat(itemTitles(get("/api/v1/tasks?q=$prefix&assignment=unassigned", login.accessToken)))
            .containsExactly("$prefix unassigned")
        assertThat(itemTitles(get("/api/v1/tasks?q=$prefix&assignment=mine", login.accessToken)))
            .containsExactly("$prefix mine")
        assertThat(itemTitles(get("/api/v1/tasks?q=$prefix&assignment=role", login.accessToken)))
            .containsExactly("$prefix admin role")
        assertThat(itemTitles(get("/api/v1/tasks?q=$prefix&assignment=user", login.accessToken)))
            .containsExactly("$prefix other user", "$prefix mine")
        assertThat(itemTitles(get("/api/v1/tasks?q=$prefix&assignment=team", login.accessToken)))
            .containsExactly("$prefix other team", "$prefix admin role")
    }

    @Test
    fun `invalid assignment returns bad request`() {
        val login = login()

        val response = get("/api/v1/tasks?assignment=everyone", login.accessToken)

        assertThat(response.statusCode()).isEqualTo(400)
    }

    @Test
    fun `created date filters use inclusive from and exclusive to boundaries`() {
        val login = login()
        val prefix = "created-${UuidV7Generator.generate()}"
        val start = Instant.parse("2026-07-10T10:00:00Z")
        val end = Instant.parse("2026-07-11T10:00:00Z")
        seed(login.hotelId, title = "$prefix before", createdAt = start.minusSeconds(1))
        seed(login.hotelId, title = "$prefix at start", createdAt = start)
        seed(login.hotelId, title = "$prefix middle", createdAt = start.plusSeconds(3600))
        seed(login.hotelId, title = "$prefix at end", createdAt = end)

        val fromOnly = get("/api/v1/tasks?q=$prefix&createdFrom=$start", login.accessToken)
        val toOnly = get("/api/v1/tasks?q=$prefix&createdTo=$end", login.accessToken)
        val combined = get("/api/v1/tasks?q=$prefix&createdFrom=$start&createdTo=$end", login.accessToken)

        assertThat(itemTitles(fromOnly)).contains("$prefix at start", "$prefix middle", "$prefix at end")
        assertThat(itemTitles(fromOnly)).doesNotContain("$prefix before")
        assertThat(itemTitles(toOnly)).contains("$prefix before", "$prefix at start", "$prefix middle")
        assertThat(itemTitles(toOnly)).doesNotContain("$prefix at end")
        assertThat(itemTitles(combined)).containsExactly("$prefix middle", "$prefix at start")
    }

    @Test
    fun `invalid created date filters return bad request`() {
        val login = login()

        assertThat(get("/api/v1/tasks?createdFrom=not-a-date", login.accessToken).statusCode()).isEqualTo(400)
        assertThat(
            get(
                "/api/v1/tasks?createdFrom=2026-07-10T10:00:00Z&createdTo=2026-07-10T10:00:00Z",
                login.accessToken
            ).statusCode()
        ).isEqualTo(400)
    }

    @Test
    fun `combined filters and empty results return paged envelope`() {
        val login = login()
        val prefix = "combined-${UuidV7Generator.generate()}"
        val createdAt = Instant.parse("2026-07-12T09:00:00Z")
        seed(
            login.hotelId,
            title = "$prefix target",
            description = "combined filter target",
            status = TaskStatus.STARTED,
            priority = TaskPriority.URGENT,
            assignment = userAssignment(login.userId, "Current User"),
            createdAt = createdAt
        )
        seed(
            login.hotelId,
            title = "$prefix miss",
            status = TaskStatus.CREATED,
            priority = TaskPriority.LOW,
            createdAt = createdAt
        )

        val response = get(
            "/api/v1/tasks?q=$prefix&status=STARTED&priority=URGENT&assignment=mine" +
                "&createdFrom=2026-07-12T00:00:00Z&createdTo=2026-07-13T00:00:00Z",
            login.accessToken
        )
        val empty = get("/api/v1/tasks?q=${prefix}-missing", login.accessToken)

        assertThat(itemTitles(response)).containsExactly("$prefix target")
        val emptyBody = json(empty.body())
        assertThat(empty.statusCode()).isEqualTo(200)
        assertThat(emptyBody.path("items")).isEmpty()
        assertThat(emptyBody.path("totalItems").asLong()).isEqualTo(0)
        assertThat(emptyBody.path("totalPages").asInt()).isEqualTo(0)
    }

    @Test
    fun `pagination metadata and ordering are correct under filters`() {
        val login = login()
        val prefix = "page-${UuidV7Generator.generate()}"
        seed(login.hotelId, title = "$prefix first", updatedAt = Instant.parse("2026-07-12T09:00:00Z"))
        seed(login.hotelId, title = "$prefix second", updatedAt = Instant.parse("2026-07-12T10:00:00Z"))
        seed(login.hotelId, title = "$prefix third", updatedAt = Instant.parse("2026-07-12T11:00:00Z"))

        val firstPage = get("/api/v1/tasks?q=$prefix&page=0&size=2", login.accessToken)
        val secondPage = get("/api/v1/tasks?q=$prefix&page=1&size=2", login.accessToken)

        assertThat(itemTitles(firstPage)).containsExactly("$prefix third", "$prefix second")
        assertThat(json(firstPage.body()).path("totalItems").asLong()).isEqualTo(3)
        assertThat(json(firstPage.body()).path("totalPages").asInt()).isEqualTo(2)
        assertThat(json(firstPage.body()).path("hasNext").asBoolean()).isTrue()
        assertThat(json(firstPage.body()).path("hasPrevious").asBoolean()).isFalse()
        assertThat(itemTitles(secondPage)).containsExactly("$prefix first")
        assertThat(json(secondPage.body()).path("hasNext").asBoolean()).isFalse()
        assertThat(json(secondPage.body()).path("hasPrevious").asBoolean()).isTrue()
    }

    private fun assertSingleSearchMatch(login: LoginSnapshot, query: String, expectedTitle: String) {
        val response = get("/api/v1/tasks?q=${encode(query)}", login.accessToken)

        assertThat(response.statusCode()).isEqualTo(200)
        assertThat(itemTitles(response)).contains(expectedTitle)
    }

    private fun seed(
        hotelId: UUID,
        title: String = "Room task",
        description: String = "$title description",
        roomNumber: String? = null,
        status: TaskStatus = TaskStatus.CREATED,
        priority: TaskPriority = TaskPriority.MEDIUM,
        assignment: TaskAssignment? = null,
        createdAt: Instant = Instant.now(),
        updatedAt: Instant = createdAt
    ): Task {
        val task = Task.create(
            hotelId = hotelId,
            intentType = TaskIntentType.MAINTENANCE,
            source = TaskSource.MANUAL,
            title = title,
            description = description,
            roomNumber = roomNumber,
            priority = priority,
            slaDeadline = createdAt.plusSeconds(3600),
            createdAt = createdAt
        ).copy(
            status = status,
            assignment = assignment,
            updatedAt = updatedAt
        )
        return taskRepository.save(task)
    }

    private fun userAssignment(userId: UUID, displayName: String): TaskAssignment =
        TaskAssignment(
            assigneeType = TaskAssigneeType.USER,
            assigneeId = userId.toString(),
            displayName = displayName,
            assignedAt = Instant.now()
        )

    private fun teamAssignment(roleCode: String, displayName: String): TaskAssignment =
        TaskAssignment(
            assigneeType = TaskAssigneeType.TEAM,
            assigneeId = roleCode,
            displayName = displayName,
            assignedAt = Instant.now()
        )

    private fun itemTitles(response: HttpResponse<String>): List<String> {
        assertThat(response.statusCode()).isEqualTo(200)
        return json(response.body()).path("items").map { it.path("title").asText() }
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
        assertThat(response.statusCode()).isEqualTo(200)
        val body = json(response.body())
        return LoginSnapshot(
            accessToken = body.path("accessToken").asText(),
            userId = UUID.fromString(body.path("user").path("userId").asText()),
            hotelId = UUID.fromString(body.path("user").path("hotelId").asText())
        )
    }

    private fun post(path: String, body: String): HttpResponse<String> =
        httpClient.send(
            HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:$port$path"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build(),
            HttpResponse.BodyHandlers.ofString()
        )

    private fun get(path: String, bearerToken: String? = null): HttpResponse<String> {
        val builder = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$port$path"))
            .GET()
        if (bearerToken != null) {
            builder.header("Authorization", "Bearer $bearerToken")
        }
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString())
    }

    private fun json(body: String): JsonNode =
        objectMapper.readTree(body)

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8)

    private data class LoginSnapshot(
        val accessToken: String,
        val userId: UUID,
        val hotelId: UUID
    )
}
