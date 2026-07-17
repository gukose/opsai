package com.hotelopai.api.dashboard

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.hotelopai.auth.application.PasswordHasher
import com.hotelopai.auth.application.PermissionRepository
import com.hotelopai.auth.application.RoleRepository
import com.hotelopai.auth.application.UserRepository
import com.hotelopai.auth.domain.EmailAddress
import com.hotelopai.auth.domain.Role
import com.hotelopai.auth.domain.User
import com.hotelopai.auth.domain.UserStatus
import com.hotelopai.hotel.application.HotelRepository
import com.hotelopai.hotel.domain.Hotel
import com.hotelopai.shared.kernel.UuidV7Generator
import com.hotelopai.shared.security.PermissionCodes
import com.hotelopai.support.PostgresIntegrationTestSupport
import com.hotelopai.task.application.TaskRepository
import com.hotelopai.task.domain.Task
import com.hotelopai.task.domain.TaskIntentType
import com.hotelopai.task.domain.TaskPriority
import com.hotelopai.task.domain.TaskSource
import com.hotelopai.task.domain.TaskStatus
import io.micrometer.core.instrument.MeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.test.context.ActiveProfiles
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TaskReportingControllerIntegrationTest.FixedClockConfiguration::class)
class TaskReportingControllerIntegrationTest : PostgresIntegrationTestSupport() {
    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var hotelRepository: HotelRepository

    @Autowired
    private lateinit var permissionRepository: PermissionRepository

    @Autowired
    private lateinit var roleRepository: RoleRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var passwordHasher: PasswordHasher

    @Autowired
    private lateinit var taskRepository: TaskRepository

    @Autowired
    private lateinit var meterRegistry: MeterRegistry

    private val httpClient = HttpClient.newHttpClient()
    private val generatedAt = FIXED_NOW

    @Test
    fun `task reporting requires authentication and rejects invalid range`() {
        assertThat(get("/api/v1/dashboard/reports/tasks").statusCode()).isEqualTo(401)

        val login = login()
        val invalid = get("/api/v1/dashboard/reports/tasks?range=month", login.accessToken)

        assertThat(invalid.statusCode()).isEqualTo(400)
        assertThat(json(invalid.body()).path("title").asText()).isEqualTo("Invalid dashboard range")
    }

    @Test
    fun `task reporting defaults to today and returns required empty report sections`() {
        val login = login()

        val response = get("/api/v1/dashboard/reports/tasks", login.accessToken)

        assertThat(response.statusCode()).isEqualTo(200)
        val body = json(response.body())
        assertThat(body.path("hotelId").asText()).isEqualTo(login.hotelId.toString())
        assertThat(body.path("range").asText()).isEqualTo("today")
        assertThat(body.path("generatedAt").asText()).isEqualTo("2026-07-14T23:00:00Z")
        assertThat(body.path("window").path("startInclusive").asText()).isEqualTo("2026-07-14T00:00:00Z")
        assertThat(body.path("window").path("endExclusive").asText()).isEqualTo("2026-07-14T23:00:00Z")
        assertThat(body.path("window").path("timeBasis").asText()).isEqualTo("UTC")
        assertThat(body.path("createdInRange").path("total").asLong()).isEqualTo(0)
        assertThat(bucket(body.path("createdInRange").path("byStatus"), "CREATED")).isEqualTo(0)
        assertThat(bucket(body.path("createdInRange").path("byPriority"), "URGENT")).isEqualTo(0)
        assertThat(body.path("currentSnapshot").path("active").asLong()).isEqualTo(0)
        assertThat(meterRegistry.find("hotelopai.dashboard.reporting.duration").timer()?.count() ?: 0)
            .isGreaterThanOrEqualTo(1)
    }

    @Test
    fun `task reporting aggregates dimensions sla buckets range boundaries and tenant isolation`() {
        val login = login()
        val otherHotel = hotelRepository.save(Hotel(code = "report-other-${UuidV7Generator.generate()}", name = "Other Hotel"))
        saveTask(login.hotelId, "at start", Instant.parse("2026-07-14T00:00:00Z"), TaskStatus.CREATED, TaskIntentType.MAINTENANCE, TaskPriority.HIGH, generatedAt.plusSeconds(3600))
        saveTask(login.hotelId, "completed on time", generatedAt.minusSeconds(3 * 60 * 60), TaskStatus.COMPLETED, TaskIntentType.HOUSEKEEPING, TaskPriority.LOW, generatedAt.minusSeconds(2 * 60 * 60), generatedAt.minusSeconds(2 * 60 * 60))
        saveTask(login.hotelId, "completed late", generatedAt.minusSeconds(2 * 60 * 60), TaskStatus.COMPLETED, TaskIntentType.HOUSEKEEPING, TaskPriority.URGENT, generatedAt.minusSeconds(90 * 60), generatedAt.minusSeconds(60 * 60))
        saveTask(login.hotelId, "open within sla", generatedAt.minusSeconds(60 * 60), TaskStatus.IN_PROGRESS, TaskIntentType.MAINTENANCE, TaskPriority.MEDIUM, generatedAt.plusSeconds(90 * 60))
        saveTask(login.hotelId, "open overdue", generatedAt.minusSeconds(30 * 60), TaskStatus.OVERDUE, TaskIntentType.GUEST_REQUEST, TaskPriority.URGENT, generatedAt.minusSeconds(60))
        saveTask(login.hotelId, "cancelled", generatedAt.minusSeconds(10 * 60), TaskStatus.CANCELLED, TaskIntentType.GUEST_REQUEST, TaskPriority.LOW, generatedAt.plusSeconds(3600))
        saveTask(login.hotelId, "at end excluded", generatedAt, TaskStatus.CREATED, TaskIntentType.MAINTENANCE, TaskPriority.HIGH, generatedAt.plusSeconds(3600))
        saveTask(login.hotelId, "before range current active", generatedAt.minusSeconds(25 * 60 * 60), TaskStatus.WAITING, TaskIntentType.LAUNDRY, TaskPriority.HIGH, generatedAt.plusSeconds(3 * 60 * 60))
        saveTask(otherHotel.id, "other hotel", generatedAt.minusSeconds(60), TaskStatus.CREATED, TaskIntentType.MAINTENANCE, TaskPriority.URGENT, generatedAt.plusSeconds(3600))

        val response = get("/api/v1/dashboard/reports/tasks?range=today&hotelId=${otherHotel.id}", login.accessToken)

        assertThat(response.statusCode()).isEqualTo(200)
        val body = json(response.body())
        assertThat(body.path("hotelId").asText()).isEqualTo(login.hotelId.toString())
        assertThat(body.path("createdInRange").path("total").asLong()).isEqualTo(6)
        assertThat(bucket(body.path("createdInRange").path("byType"), "MAINTENANCE")).isEqualTo(2)
        assertThat(bucket(body.path("createdInRange").path("byType"), "HOUSEKEEPING")).isEqualTo(2)
        assertThat(bucket(body.path("createdInRange").path("byStatus"), "CREATED")).isEqualTo(1)
        assertThat(bucket(body.path("createdInRange").path("byStatus"), "COMPLETED")).isEqualTo(2)
        assertThat(bucket(body.path("createdInRange").path("byPriority"), "URGENT")).isEqualTo(2)
        val createdSla = body.path("createdInRange").path("sla")
        assertThat(createdSla.path("completedOnTime").asLong()).isEqualTo(1)
        assertThat(createdSla.path("completedLate").asLong()).isEqualTo(1)
        assertThat(createdSla.path("openWithinSla").asLong()).isEqualTo(2)
        assertThat(createdSla.path("openOverdue").asLong()).isEqualTo(1)
        assertThat(createdSla.path("cancelled").asLong()).isEqualTo(1)
        assertThat(createdSla.path("breached").asLong()).isEqualTo(2)

        val current = body.path("currentSnapshot")
        assertThat(current.path("active").asLong()).isEqualTo(5)
        assertThat(bucket(current.path("byStatus"), "WAITING")).isEqualTo(1)
        assertThat(bucket(current.path("byPriority"), "HIGH")).isEqualTo(3)
        assertThat(current.path("sla").path("dueSoon").asLong()).isEqualTo(3)
        assertThat(current.path("sla").path("overdue").asLong()).isEqualTo(1)
    }

    @Test
    fun `task reporting supports shift and seven day ranges`() {
        val login = login()

        val shift = get("/api/v1/dashboard/reports/tasks?range=shift", login.accessToken)
        val sevenDays = get("/api/v1/dashboard/reports/tasks?range=7d", login.accessToken)

        assertThat(shift.statusCode()).isEqualTo(200)
        assertThat(json(shift.body()).path("window").path("startInclusive").asText()).isEqualTo("2026-07-14T15:00:00Z")
        assertThat(sevenDays.statusCode()).isEqualTo(200)
        assertThat(json(sevenDays.body()).path("window").path("startInclusive").asText()).isEqualTo("2026-07-07T23:00:00Z")
    }

    private fun saveTask(
        hotelId: UUID,
        title: String,
        createdAt: Instant,
        status: TaskStatus,
        intentType: TaskIntentType,
        priority: TaskPriority,
        slaDeadline: Instant,
        completedAt: Instant? = null
    ) {
        taskRepository.save(
            Task.create(
                hotelId = hotelId,
                intentType = intentType,
                source = TaskSource.MANUAL,
                title = title,
                description = "$title description",
                priority = priority,
                slaDeadline = slaDeadline,
                createdAt = createdAt
            ).copy(
                status = status,
                completedAt = completedAt,
                cancelledAt = if (status == TaskStatus.CANCELLED) createdAt.plusSeconds(60) else null,
                overdueAt = if (status == TaskStatus.OVERDUE) slaDeadline else null,
                updatedAt = completedAt ?: createdAt
            )
        )
    }

    private fun login(): LoginSnapshot {
        val hotel = hotelRepository.save(
            Hotel(
                code = "report-auth-${UuidV7Generator.generate()}",
                name = "Report Auth Hotel"
            )
        )
        val role = roleRepository.save(
            Role(
                hotelId = hotel.id,
                code = "ADMIN",
                name = "Administrator",
                permissionIds = setOf(permissionId(PermissionCodes.REPORT_READ))
            )
        )
        val email = "report-${UuidV7Generator.generate()}@hotelopai.local"
        userRepository.save(
            User(
                hotelId = hotel.id,
                email = EmailAddress.of(email),
                displayName = "Report User",
                passwordHash = passwordHasher.hash("report123"),
                roleIds = setOf(role.id),
                status = UserStatus.ACTIVE
            )
        )
        val response = post(
            "/api/v1/auth/login",
            """{
              "hotelCode":"${hotel.code}",
              "email":"$email",
              "password":"report123"
            }"""
        )
        assertThat(response.statusCode()).isEqualTo(200)
        val body = json(response.body())
        return LoginSnapshot(
            accessToken = body.path("accessToken").asText(),
            hotelId = UUID.fromString(body.path("user").path("hotelId").asText())
        )
    }

    private fun bucket(buckets: JsonNode, key: String): Long =
        buckets.firstOrNull { it.path("key").asText() == key }?.path("count")?.asLong()
            ?: error("Bucket $key not found in $buckets")

    private fun get(path: String, bearerToken: String? = null): HttpResponse<String> {
        val builder = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$port$path"))
            .GET()
        if (bearerToken != null) {
            builder.header("Authorization", "Bearer $bearerToken")
        }
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString())
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

    private fun permissionId(code: String): UUID =
        requireNotNull(permissionRepository.findByCode(code)) { "Missing permission: $code" }.id

    private fun json(value: String): JsonNode = objectMapper.readTree(value)

    private data class LoginSnapshot(
        val accessToken: String,
        val hotelId: UUID
    )

    @TestConfiguration
    class FixedClockConfiguration {
        @Bean
        @Primary
        fun fixedClock(): Clock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC)
    }

    companion object {
        private val FIXED_NOW: Instant = Instant.parse("2026-07-14T23:00:00Z")
    }
}
