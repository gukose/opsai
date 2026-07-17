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
import com.hotelopai.notification.application.NotificationRepository
import com.hotelopai.notification.domain.Notification
import com.hotelopai.notification.domain.NotificationRecipient
import com.hotelopai.notification.domain.NotificationStatus
import com.hotelopai.notification.domain.NotificationType
import com.hotelopai.shared.kernel.UuidV7Generator
import com.hotelopai.shared.security.PermissionCodes
import com.hotelopai.support.PostgresIntegrationTestSupport
import com.hotelopai.task.application.AssignmentCommand
import com.hotelopai.task.application.CreateTaskCommand
import com.hotelopai.task.application.TaskLifecycleService
import com.hotelopai.task.application.TaskRepository
import com.hotelopai.task.domain.Task
import com.hotelopai.task.domain.TaskAssigneeType
import com.hotelopai.task.domain.TaskIntentType
import com.hotelopai.task.domain.TaskPriority
import com.hotelopai.task.domain.TaskSource
import io.micrometer.core.instrument.MeterRegistry
import org.assertj.core.api.Assertions.assertThat
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
import java.util.UUID

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class DashboardControllerIntegrationTest : PostgresIntegrationTestSupport() {
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
    private lateinit var taskLifecycleService: TaskLifecycleService

    @Autowired
    private lateinit var taskRepository: TaskRepository

    @Autowired
    private lateinit var notificationRepository: NotificationRepository

    @Autowired
    private lateinit var meterRegistry: MeterRegistry

    private val httpClient = HttpClient.newHttpClient()

    @Test
    fun `dashboard summary requires authentication`() {
        val response = get("/api/v1/dashboard/summary")

        assertThat(response.statusCode()).isEqualTo(401)
    }

    @Test
    fun `dashboard summary rejects invalid range`() {
        val login = login()

        val response = get("/api/v1/dashboard/summary?range=month", login.accessToken)

        assertThat(response.statusCode()).isEqualTo(400)
        assertThat(json(response.body()).path("title").asText()).isEqualTo("Invalid dashboard range")
    }

    @Test
    fun `dashboard summary returns tenant scoped metrics and selected range`() {
        val login = login()
        val otherHotel = hotelRepository.save(Hotel(code = "dashboard-other-${UuidV7Generator.generate()}", name = "Other Hotel"))
        taskLifecycleService.createTask(
            command(
                hotelId = login.hotelId,
                title = "Dashboard high task",
                statusDeadline = Instant.now().plusSeconds(3600),
                priority = TaskPriority.HIGH
            )
        )
        val assignedUserTask = taskLifecycleService.createTask(
            command(
                hotelId = login.hotelId,
                title = "Assigned user task",
                statusDeadline = Instant.now().plusSeconds(7200),
                assignment = AssignmentCommand(
                    assigneeType = TaskAssigneeType.USER,
                    assigneeId = login.userId.toString(),
                    displayName = "Admin User"
                )
            )
        )
        taskLifecycleService.startTask(assignedUserTask.id.toString(), assignedUserTask.hotelId)
        val completed = taskLifecycleService.createTask(
            command(
                hotelId = login.hotelId,
                title = "Completed task",
                statusDeadline = Instant.now().plusSeconds(7200)
            )
        )
        taskLifecycleService.startTask(completed.id.toString(), completed.hotelId)
        taskLifecycleService.completeTask(completed.id.toString(), completed.hotelId)
        taskLifecycleService.createTask(
            command(
                hotelId = otherHotel.id,
                title = "Other hotel urgent task",
                statusDeadline = Instant.now().plusSeconds(3600),
                priority = TaskPriority.URGENT
            )
        )

        val response = get("/api/v1/dashboard/summary?range=today", login.accessToken)

        assertThat(response.statusCode()).isEqualTo(200)
        val body = json(response.body())
        assertThat(body.path("hotelId").asText()).isEqualTo(login.hotelId.toString())
        assertThat(body.path("range").asText()).isEqualTo("today")
        assertThat(body.path("generatedAt").asText()).isNotBlank()
        assertThat(body.path("tasks").path("createdInRange").path("total").asLong()).isEqualTo(3)
        assertThat(body.path("tasks").path("currentSnapshot").path("active").asLong()).isEqualTo(2)
        assertThat(body.path("tasks").path("currentSnapshot").path("urgent").asLong()).isEqualTo(1)
        assertThat(body.path("tasks").path("currentSnapshot").path("unassigned").asLong()).isEqualTo(1)
        assertThat(body.path("tasks").path("currentSnapshot").path("completionPercent").asInt()).isEqualTo(33)
        assertThat(body.path("workload").path("assignedToUser").asLong()).isEqualTo(1)
        assertThat(body.path("workload").path("unassigned").asLong()).isEqualTo(1)
        assertThat(meterRegistry.find("hotelopai.dashboard.summary.duration").timer()?.count() ?: 0)
            .isGreaterThanOrEqualTo(1)
    }

    @Test
    fun `dashboard summary preserves notification user and role visibility rules`() {
        val login = login()
        notificationRepository.save(notification(login.hotelId, NotificationRecipient.User(login.userId), "User visible"))
        notificationRepository.save(notification(login.hotelId, NotificationRecipient.Role("ADMIN"), "Role visible"))
        notificationRepository.save(notification(login.hotelId, NotificationRecipient.User(UuidV7Generator.generate()), "Other user"))
        notificationRepository.save(notification(login.hotelId, NotificationRecipient.Role("MANAGER"), "Other role"))
        val otherHotel = hotelRepository.save(Hotel(code = "dashboard-notification-other-${UuidV7Generator.generate()}", name = "Other Hotel"))
        notificationRepository.save(notification(otherHotel.id, NotificationRecipient.Role("ADMIN"), "Other hotel"))
        val read = notification(login.hotelId, NotificationRecipient.User(login.userId), "Read notification")
        notificationRepository.save(read.copy(status = NotificationStatus.READ, readAt = Instant.now()))

        val response = get("/api/v1/dashboard/summary?range=shift", login.accessToken)

        assertThat(response.statusCode()).isEqualTo(200)
        val notifications = json(response.body()).path("notifications")
        assertThat(notifications.path("unread").asLong()).isEqualTo(2)
        val titles = notifications.path("recent").map { it.path("title").asText() }.toSet()
        assertThat(titles).contains("User visible", "Role visible", "Read notification")
        assertThat(titles).doesNotContain("Other user", "Other role", "Other hotel")
    }

    @Test
    fun `dashboard summary calculates sla metrics`() {
        val login = login()
        taskLifecycleService.createTask(
            command(
                hotelId = login.hotelId,
                title = "Due soon",
                statusDeadline = Instant.now().plusSeconds(30 * 60)
            )
        )
        val now = Instant.now()
        taskRepository.save(
            Task.create(
                hotelId = login.hotelId,
                intentType = TaskIntentType.MAINTENANCE,
                source = TaskSource.MANUAL,
                title = "Overdue",
                description = "Overdue description",
                priority = TaskPriority.MEDIUM,
                createdAt = now.minusSeconds(60 * 60),
                slaDeadline = now.minusSeconds(30 * 60)
            )
        )
        val lateCompleted = taskLifecycleService.createTask(
            command(
                hotelId = login.hotelId,
                title = "Late completed",
                statusDeadline = Instant.now().plusSeconds(1)
            )
        )
        Thread.sleep(1200)
        taskLifecycleService.startTask(lateCompleted.id.toString(), lateCompleted.hotelId)
        taskLifecycleService.completeTask(lateCompleted.id.toString(), lateCompleted.hotelId)

        val response = get("/api/v1/dashboard/summary?range=7d", login.accessToken)

        assertThat(response.statusCode()).isEqualTo(200)
        val sla = json(response.body()).path("sla")
        assertThat(sla.path("dueSoon").asLong()).isEqualTo(1)
        assertThat(sla.path("overdue").asLong()).isGreaterThanOrEqualTo(1)
        assertThat(sla.path("breached").asLong()).isGreaterThanOrEqualTo(2)
    }

    private fun command(
        hotelId: UUID,
        title: String,
        statusDeadline: Instant,
        priority: TaskPriority = TaskPriority.MEDIUM,
        assignment: AssignmentCommand? = null
    ): CreateTaskCommand =
        CreateTaskCommand(
            hotelId = hotelId,
            intentType = TaskIntentType.MAINTENANCE,
            source = TaskSource.MANUAL,
            title = title,
            description = "$title description",
            priority = priority,
            slaDeadline = statusDeadline,
            assignment = assignment
        )

    private fun notification(
        hotelId: UUID,
        recipient: NotificationRecipient,
        title: String
    ): Notification {
        val now = Instant.now()
        return Notification(
            hotelId = hotelId,
            recipient = recipient,
            type = NotificationType.TASK_CREATED,
            title = title,
            body = "$title body",
            createdAt = now,
            updatedAt = now
        )
    }

    private fun login(): LoginSnapshot {
        val hotel = hotelRepository.save(
            Hotel(
                code = "dashboard-auth-${UuidV7Generator.generate()}",
                name = "Dashboard Auth Hotel"
            )
        )
        val role = roleRepository.save(
            Role(
                hotelId = hotel.id,
                code = "ADMIN",
                name = "Administrator",
                permissionIds = setOf(permissionId(PermissionCodes.DASHBOARD_READ))
            )
        )
        val email = "dashboard-${UuidV7Generator.generate()}@hotelopai.local"
        userRepository.save(
            User(
                hotelId = hotel.id,
                email = EmailAddress.of(email),
                displayName = "Dashboard User",
                passwordHash = passwordHasher.hash("dashboard123"),
                roleIds = setOf(role.id),
                status = UserStatus.ACTIVE
            )
        )
        val response = post(
            "/api/v1/auth/login",
            """{
              "hotelCode":"${hotel.code}",
              "email":"$email",
              "password":"dashboard123"
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
        val userId: UUID,
        val hotelId: UUID
    )
}
