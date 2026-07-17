package com.hotelopai.api.security

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
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
import com.hotelopai.shared.kernel.UuidV7Generator
import com.hotelopai.shared.security.PermissionCodes
import com.hotelopai.support.PostgresIntegrationTestSupport
import com.hotelopai.task.application.TaskRepository
import com.hotelopai.task.domain.Task
import com.hotelopai.task.domain.TaskIntentType
import com.hotelopai.task.domain.TaskPriority
import com.hotelopai.task.domain.TaskSource
import com.hotelopai.task.infrastructure.persistence.TaskLogJpaRepository
import com.hotelopai.task.infrastructure.persistence.TaskStateHistoryJpaRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.test.context.ActiveProfiles
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Instant
import java.util.UUID

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PermissionMatrixIntegrationTest : PostgresIntegrationTestSupport() {
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
    private lateinit var passwordEncoder: BCryptPasswordEncoder

    @Autowired
    private lateinit var taskRepository: TaskRepository

    @Autowired
    private lateinit var taskStateHistoryJpaRepository: TaskStateHistoryJpaRepository

    @Autowired
    private lateinit var taskLogJpaRepository: TaskLogJpaRepository

    @Autowired
    private lateinit var notificationRepository: NotificationRepository

    private val httpClient = HttpClient.newHttpClient()

    @Test
    fun `login token contains operational permissions and refresh reflects updated mappings`() {
        val user = createUser(
            roleCode = "TOKEN_TEST",
            permissions = setOf(PermissionCodes.AUTH_VIEW, PermissionCodes.TASK_READ)
        )
        val login = login(user)

        assertThat(login.permissions).contains(PermissionCodes.AUTH_VIEW, PermissionCodes.TASK_READ)
        assertThat(get("/api/v1/auth/me", login.accessToken).statusCode()).isEqualTo(200)

        assertThat(get("/api/v1/auth/me", login.accessToken).statusCode()).isEqualTo(200)
        val changed = createUser(roleCode = "TOKEN_REFRESH_CHANGED", permissions = setOf(PermissionCodes.TASK_READ))
        val changedLogin = login(changed)
        val refreshed = refresh(changedLogin.refreshToken)

        assertThat(refreshed.statusCode()).isEqualTo(200)
        val refreshedBody = json(refreshed.body())
        assertThat(refreshedBody.path("user").path("permissions").map { it.path("code").asText() })
            .doesNotContain(PermissionCodes.AUTH_VIEW)
        assertThat(get("/api/v1/auth/me", refreshedBody.path("accessToken").asText()).statusCode()).isEqualTo(403)
    }

    @Test
    fun `auth me requires AUTH_VIEW but logout remains authenticated-only`() {
        val user = createUser(roleCode = "NO_AUTH_VIEW", permissions = emptySet())
        val login = login(user)

        assertThat(get("/api/v1/auth/me", login.accessToken).statusCode()).isEqualTo(403)
        assertThat(post("/api/v1/auth/logout", "", login.accessToken).statusCode()).isEqualTo(200)
        assertThat(post("/api/v1/auth/logout", "").statusCode()).isEqualTo(401)
    }

    @Test
    fun `manager permissions can use operational APIs except Dev PMS`() {
        val manager = login(createUser(roleCode = "MANAGER_TEST", permissions = MANAGER_PERMISSIONS))

        assertThat(get("/api/v1/dashboard/summary?range=today", manager.accessToken).statusCode()).isEqualTo(200)
        assertThat(get("/api/v1/dashboard/reports/tasks?range=today", manager.accessToken).statusCode()).isEqualTo(200)
        assertThat(get("/api/v1/notifications", manager.accessToken).statusCode()).isEqualTo(200)
        assertThat(post("/api/v1/assistant/conversations", """{"hotelId":"ignored","userId":"ignored"}""", manager.accessToken).statusCode())
            .isEqualTo(200)
        assertThat(get("/api/v1/dev/pms/rooms", manager.accessToken).statusCode()).isEqualTo(403)
    }

    @Test
    fun `front desk can create and assign but cannot perform lifecycle or reporting operations`() {
        val frontDesk = login(createUser(roleCode = "FRONT_DESK_TEST", permissions = FRONT_DESK_PERMISSIONS))
        val taskId = createTask(frontDesk)

        assertThat(post("/api/v1/tasks/$taskId/assign", ASSIGN_BODY, frontDesk.accessToken).statusCode()).isEqualTo(200)
        assertThat(post("/api/v1/tasks/$taskId/start", "", frontDesk.accessToken).statusCode()).isEqualTo(403)
        assertThat(post("/api/v1/tasks/$taskId/complete", "", frontDesk.accessToken).statusCode()).isEqualTo(403)
        assertThat(post("/api/v1/tasks/$taskId/cancel", "", frontDesk.accessToken).statusCode()).isEqualTo(403)
        assertThat(post("/api/v1/tasks/$taskId/overdue", "", frontDesk.accessToken).statusCode()).isEqualTo(403)
        assertThat(get("/api/v1/dashboard/reports/tasks?range=today", frontDesk.accessToken).statusCode()).isEqualTo(403)
    }

    @Test
    fun `maintenance and housekeeping have the same approved capability baseline`() {
        val maintenance = login(createUser(roleCode = "MAINTENANCE_TEST", permissions = MAINTENANCE_PERMISSIONS))
        val housekeeping = login(createUser(roleCode = "HOUSEKEEPING_TEST", permissions = HOUSEKEEPING_PERMISSIONS))

        listOf(maintenance, housekeeping).forEach { login ->
            val taskId = createTask(login)

            assertThat(post("/api/v1/tasks/$taskId/start", "", login.accessToken).statusCode()).isEqualTo(200)
            assertThat(post("/api/v1/tasks/$taskId/pause", "", login.accessToken).statusCode()).isEqualTo(200)
            assertThat(post("/api/v1/tasks/$taskId/resume", "", login.accessToken).statusCode()).isEqualTo(200)
            assertThat(post("/api/v1/tasks/$taskId/complete", "", login.accessToken).statusCode()).isEqualTo(200)
            assertThat(post("/api/v1/tasks/$taskId/assign", ASSIGN_BODY, login.accessToken).statusCode()).isEqualTo(403)
            assertThat(post("/api/v1/tasks/$taskId/cancel", "", login.accessToken).statusCode()).isEqualTo(403)
            assertThat(get("/api/v1/dashboard/summary?range=today", login.accessToken).statusCode()).isEqualTo(403)
        }
    }

    @Test
    fun `staff can act on tasks but cannot create confirm import dashboard or report`() {
        val staff = login(createUser(roleCode = "STAFF_TEST", permissions = STAFF_PERMISSIONS))
        val taskId = seedTask(staff.hotelId)

        assertThat(get("/api/v1/tasks", staff.accessToken).statusCode()).isEqualTo(200)
        assertThat(post("/api/v1/tasks/$taskId/start", "", staff.accessToken).statusCode()).isEqualTo(200)
        assertThat(post("/api/v1/tasks/$taskId/pause", "", staff.accessToken).statusCode()).isEqualTo(200)
        assertThat(post("/api/v1/tasks/$taskId/resume", "", staff.accessToken).statusCode()).isEqualTo(200)
        assertThat(post("/api/v1/tasks/$taskId/complete", "", staff.accessToken).statusCode()).isEqualTo(200)
        assertThat(post("/api/v1/tasks", createTaskBody(staff.hotelId), staff.accessToken).statusCode()).isEqualTo(403)
        assertThat(post("/api/v1/assistant/conversations/conversation-1/confirm", """{"idempotencyKey":"key"}""", staff.accessToken).statusCode())
            .isEqualTo(403)
        assertThat(
            post(
                "/api/v1/assistant/conversations/conversation-1/vision-analyses/${UuidV7Generator.generate()}/import",
                "{}",
                staff.accessToken
            ).statusCode()
        ).isEqualTo(403)
        assertThat(get("/api/v1/dashboard/summary?range=today", staff.accessToken).statusCode()).isEqualTo(403)
        assertThat(get("/api/v1/dashboard/reports/tasks?range=today", staff.accessToken).statusCode()).isEqualTo(403)
    }

    @Test
    fun `every operational endpoint requires its mapped permission`() {
        val noPermissions = login(createUser(roleCode = "NO_OPERATIONAL_PERMISSIONS", permissions = emptySet()))
        val taskId = UuidV7Generator.generate()
        val analysisId = UuidV7Generator.generate()
        val notificationId = UuidV7Generator.generate()

        val checks = listOf(
            RequestCheck("GET", "/api/v1/tasks"),
            RequestCheck("GET", "/api/v1/tasks?q=ac"),
            RequestCheck("GET", "/api/v1/tasks/$taskId"),
            RequestCheck("POST", "/api/v1/tasks", createTaskBody(noPermissions.hotelId)),
            RequestCheck("GET", "/api/v1/tasks/$taskId/attachments"),
            RequestCheck("POST", "/api/v1/tasks/$taskId/assign", ASSIGN_BODY),
            RequestCheck("POST", "/api/v1/tasks/$taskId/start"),
            RequestCheck("POST", "/api/v1/tasks/$taskId/pause"),
            RequestCheck("POST", "/api/v1/tasks/$taskId/resume"),
            RequestCheck("POST", "/api/v1/tasks/$taskId/complete"),
            RequestCheck("POST", "/api/v1/tasks/$taskId/cancel"),
            RequestCheck("POST", "/api/v1/tasks/$taskId/overdue"),
            RequestCheck("POST", "/api/v1/assistant/conversations", """{"hotelId":"ignored","userId":"ignored"}"""),
            RequestCheck("POST", "/api/v1/assistant/conversations/conversation-1/messages", """{"text":"hello","inputType":"TEXT"}"""),
            RequestCheck("POST", "/api/v1/assistant/conversations/conversation-1/reset"),
            RequestCheck("POST", "/api/v1/assistant/conversations/conversation-1/confirm", """{"idempotencyKey":"key"}"""),
            RequestCheck("POST", "/api/v1/assistant/conversations/conversation-1/attachments", ATTACHMENT_BODY),
            RequestCheck("POST", "/api/v1/assistant/conversations/conversation-1/vision-analyses/$analysisId/import", "{}"),
            RequestCheck("GET", "/api/v1/notifications"),
            RequestCheck("POST", "/api/v1/notifications/$notificationId/read"),
            RequestCheck("GET", "/api/v1/dashboard/summary?range=today"),
            RequestCheck("GET", "/api/v1/dashboard/reports/tasks?range=today"),
            RequestCheck("GET", "/api/v1/dev/pms/rooms"),
            RequestCheck("POST", "/api/v1/dev/pms/events", DEV_PMS_EVENT_BODY)
        )

        checks.forEach { check ->
            val response = request(check.method, check.path, check.body, noPermissions.accessToken)
            assertThat(response.statusCode()).describedAs("${check.method} ${check.path}").isEqualTo(403)
        }
    }

    @Test
    fun `permission denial causes no task side effects`() {
        val reader = login(createUser(roleCode = "TASK_READER_ONLY", permissions = setOf(PermissionCodes.TASK_READ)))
        val beforeTasks = taskRepository.findAllByHotelId(reader.hotelId).size
        val createDenied = post("/api/v1/tasks", createTaskBody(reader.hotelId), reader.accessToken)

        assertThat(createDenied.statusCode()).isEqualTo(403)
        assertThat(taskRepository.findAllByHotelId(reader.hotelId)).hasSize(beforeTasks)

        val taskId = seedTask(reader.hotelId)
        val historyBefore = taskStateHistoryJpaRepository.countByTaskId(taskId)
        val logsBefore = taskLogJpaRepository.countByTaskId(taskId)
        val notificationsBefore = notificationRepository.countBySourceTaskId(taskId)
        val mutationDenied = post("/api/v1/tasks/$taskId/start", "", reader.accessToken)

        assertThat(mutationDenied.statusCode()).isEqualTo(403)
        assertThat(taskRepository.findById(taskId)?.status?.name).isEqualTo("CREATED")
        assertThat(taskStateHistoryJpaRepository.countByTaskId(taskId)).isEqualTo(historyBefore)
        assertThat(taskLogJpaRepository.countByTaskId(taskId)).isEqualTo(logsBefore)
        assertThat(notificationRepository.countBySourceTaskId(taskId)).isEqualTo(notificationsBefore)
    }

    @Test
    fun `permission granted foreign task remains safe not found`() {
        val current = login(createUser(roleCode = "TASK_WRITER_CURRENT", permissions = MANAGER_PERMISSIONS))
        val other = createUser(roleCode = "TASK_WRITER_OTHER", permissions = MANAGER_PERMISSIONS)
        val foreignTaskId = seedTask(other.hotel.id)

        val response = post("/api/v1/tasks/$foreignTaskId/start", "", current.accessToken)

        assertThat(response.statusCode()).isEqualTo(404)
    }

    private fun createUser(roleCode: String, permissions: Set<String>): TestUser {
        val suffix = UuidV7Generator.generate().toString().takeLast(12)
        val hotel = hotelRepository.save(Hotel(code = "perm-$suffix", name = "Permission Hotel $suffix"))
        val role = roleRepository.save(
            Role(
                hotelId = hotel.id,
                code = roleCode,
                name = "$roleCode Role",
                permissionIds = idsFor(*permissions.toTypedArray())
            )
        )
        val email = "perm-$suffix@hotelopai.local"
        userRepository.save(
            User(
                hotelId = hotel.id,
                email = EmailAddress.of(email),
                displayName = "Permission User $suffix",
                passwordHash = requireNotNull(passwordEncoder.encode(PASSWORD)),
                roleIds = setOf(role.id),
                status = UserStatus.ACTIVE
            )
        )
        return TestUser(hotel = hotel, role = role, email = email)
    }

    private fun idsFor(vararg codes: String): Set<UUID> =
        codes.map { code ->
            requireNotNull(permissionRepository.findByCode(code)) { "Missing permission: $code" }.id
        }.toSet()

    private fun login(user: TestUser): LoginSnapshot {
        val response = post(
            "/api/v1/auth/login",
            """{"hotelCode":"${user.hotel.code}","email":"${user.email}","password":"$PASSWORD"}"""
        )
        assertThat(response.statusCode()).isEqualTo(200)
        val body = json(response.body())
        return LoginSnapshot(
            accessToken = body.path("accessToken").asText(),
            refreshToken = body.path("refreshToken").asText(),
            hotelId = UUID.fromString(body.path("user").path("hotelId").asText()),
            permissions = body.path("user").path("permissions").map { it.path("code").asText() }.toSet()
        )
    }

    private fun refresh(refreshToken: String): HttpResponse<String> =
        post("/api/v1/auth/refresh", """{"refreshToken":"$refreshToken"}""")

    private fun createTask(login: LoginSnapshot): UUID {
        val response = post("/api/v1/tasks", createTaskBody(login.hotelId), login.accessToken)
        assertThat(response.statusCode()).isEqualTo(200)
        return UUID.fromString(json(response.body()).path("id").asText())
    }

    private fun seedTask(hotelId: UUID): UUID =
        taskRepository.save(
            Task.create(
                hotelId = hotelId,
                intentType = TaskIntentType.MAINTENANCE,
                source = TaskSource.MANUAL,
                title = "Permission seeded task",
                description = "Task seeded for permission tests",
                priority = TaskPriority.HIGH,
                slaDeadline = Instant.now().plusSeconds(3600)
            )
        ).id

    private fun createTaskBody(hotelId: UUID): String =
        """{
          "hotelId":"$hotelId",
          "intentType":"MAINTENANCE",
          "source":"MANUAL",
          "title":"Permission test task",
          "description":"Permission test description",
          "priority":"HIGH",
          "slaDeadline":"${Instant.now().plusSeconds(3600)}"
        }"""

    private fun request(method: String, path: String, body: String = "", bearerToken: String? = null): HttpResponse<String> =
        when (method) {
            "GET" -> get(path, bearerToken)
            "POST" -> post(path, body, bearerToken)
            else -> error("Unsupported method $method")
        }

    private fun get(path: String, bearerToken: String? = null): HttpResponse<String> {
        val builder = HttpRequest.newBuilder().uri(URI.create("http://localhost:$port$path"))
        bearerToken?.let { builder.header("Authorization", "Bearer $it") }
        return httpClient.send(builder.GET().build(), HttpResponse.BodyHandlers.ofString())
    }

    private fun post(path: String, body: String, bearerToken: String? = null): HttpResponse<String> {
        val builder = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$port$path"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
        bearerToken?.let { builder.header("Authorization", "Bearer $it") }
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString())
    }

    private fun json(value: String): JsonNode = objectMapper.readTree(value)

    private data class TestUser(
        val hotel: Hotel,
        val role: Role,
        val email: String
    )

    private data class LoginSnapshot(
        val accessToken: String,
        val refreshToken: String,
        val hotelId: UUID,
        val permissions: Set<String>
    )

    private data class RequestCheck(
        val method: String,
        val path: String,
        val body: String = ""
    )

    companion object {
        private const val PASSWORD = "permission123"
        private const val ASSIGN_BODY = """{"assigneeType":"TEAM","assigneeId":"MAINTENANCE","displayName":"Maintenance"}"""
        private const val ATTACHMENT_BODY =
            """{"type":"IMAGE","originalFileName":"sink.png","mimeType":"image/png","sizeBytes":1000,"widthPx":100,"heightPx":100}"""
        private const val DEV_PMS_EVENT_BODY =
            """{"eventId":"evt-1","type":"TEST","subject":"Denied","description":null,"entityType":null,"entityId":null,"roomId":null,"occurredAt":null,"metadata":{}}"""

        private val MANAGER_PERMISSIONS = setOf(
            PermissionCodes.AUTH_VIEW,
            PermissionCodes.TASK_READ,
            PermissionCodes.TASK_CREATE,
            PermissionCodes.TASK_ASSIGN,
            PermissionCodes.TASK_START,
            PermissionCodes.TASK_PAUSE,
            PermissionCodes.TASK_RESUME,
            PermissionCodes.TASK_COMPLETE,
            PermissionCodes.TASK_CANCEL,
            PermissionCodes.TASK_MARK_OVERDUE,
            PermissionCodes.TASK_ATTACHMENT_READ,
            PermissionCodes.ASSISTANT_USE,
            PermissionCodes.ASSISTANT_CONFIRM_TASK,
            PermissionCodes.ASSISTANT_ATTACHMENT_REGISTER,
            PermissionCodes.ASSISTANT_VISION_IMPORT,
            PermissionCodes.NOTIFICATION_READ,
            PermissionCodes.NOTIFICATION_MARK_READ,
            PermissionCodes.DASHBOARD_READ,
            PermissionCodes.REPORT_READ
        )

        private val FRONT_DESK_PERMISSIONS = setOf(
            PermissionCodes.AUTH_VIEW,
            PermissionCodes.TASK_READ,
            PermissionCodes.TASK_CREATE,
            PermissionCodes.TASK_ASSIGN,
            PermissionCodes.TASK_ATTACHMENT_READ,
            PermissionCodes.ASSISTANT_USE,
            PermissionCodes.ASSISTANT_CONFIRM_TASK,
            PermissionCodes.ASSISTANT_ATTACHMENT_REGISTER,
            PermissionCodes.ASSISTANT_VISION_IMPORT,
            PermissionCodes.NOTIFICATION_READ,
            PermissionCodes.NOTIFICATION_MARK_READ,
            PermissionCodes.DASHBOARD_READ
        )

        private val MAINTENANCE_PERMISSIONS = setOf(
            PermissionCodes.AUTH_VIEW,
            PermissionCodes.TASK_READ,
            PermissionCodes.TASK_CREATE,
            PermissionCodes.TASK_START,
            PermissionCodes.TASK_PAUSE,
            PermissionCodes.TASK_RESUME,
            PermissionCodes.TASK_COMPLETE,
            PermissionCodes.TASK_ATTACHMENT_READ,
            PermissionCodes.ASSISTANT_USE,
            PermissionCodes.ASSISTANT_CONFIRM_TASK,
            PermissionCodes.ASSISTANT_ATTACHMENT_REGISTER,
            PermissionCodes.ASSISTANT_VISION_IMPORT,
            PermissionCodes.NOTIFICATION_READ,
            PermissionCodes.NOTIFICATION_MARK_READ
        )

        private val HOUSEKEEPING_PERMISSIONS = MAINTENANCE_PERMISSIONS

        private val STAFF_PERMISSIONS = setOf(
            PermissionCodes.AUTH_VIEW,
            PermissionCodes.TASK_READ,
            PermissionCodes.TASK_START,
            PermissionCodes.TASK_PAUSE,
            PermissionCodes.TASK_RESUME,
            PermissionCodes.TASK_COMPLETE,
            PermissionCodes.TASK_ATTACHMENT_READ,
            PermissionCodes.ASSISTANT_USE,
            PermissionCodes.ASSISTANT_ATTACHMENT_REGISTER,
            PermissionCodes.NOTIFICATION_READ,
            PermissionCodes.NOTIFICATION_MARK_READ
        )
    }
}
