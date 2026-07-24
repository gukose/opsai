package com.hotelopai.api.reservation

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
import com.hotelopai.shared.kernel.UuidV7Generator
import com.hotelopai.shared.security.PermissionCodes
import com.hotelopai.support.PostgresIntegrationTestSupport
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class InternalReservationSyncOperationsControllerIntegrationTest : PostgresIntegrationTestSupport() {
    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var hotelRepository: HotelRepository

    @Autowired
    private lateinit var roleRepository: RoleRepository

    @Autowired
    private lateinit var permissionRepository: PermissionRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var passwordEncoder: BCryptPasswordEncoder

    private val httpClient = HttpClient.newHttpClient()

    @Test
    fun `reservation sync operations endpoints require permission`() {
        assertThat(get("/api/v1/internal/reservations/sync-runs").statusCode()).isEqualTo(401)

        val noPermission = login(createUser("RES_SYNC_NO_PERMISSION", emptySet()))
        assertThat(get("/api/v1/internal/reservations/sync-runs", noPermission).statusCode()).isEqualTo(403)

        val operator = login(createUser("RES_SYNC_ALLOWED", setOf(PermissionCodes.RESERVATION_SYNC_OPERATIONS)))
        assertThat(get("/api/v1/internal/reservations/sync-runs", operator).statusCode()).isEqualTo(200)
        assertThat(get("/api/v1/internal/reservations/sync-schedule", operator).statusCode()).isEqualTo(200)
        assertThat(get("/api/v1/internal/reservations/webhooks", operator).statusCode()).isEqualTo(200)
        assertThat(get("/api/v1/internal/reservations/webhooks/schedule", operator).statusCode()).isEqualTo(200)
        assertThat(post("/api/v1/internal/reservations/sync-schedule/pause", "{}", noPermission).statusCode()).isEqualTo(403)
        assertThat(post("/api/v1/internal/reservations/webhooks/process-batch", "{}", noPermission).statusCode()).isEqualTo(403)
        assertThat(post("/api/v1/internal/reservations/webhooks/schedule/run-now", "{}", noPermission).statusCode()).isEqualTo(403)
        assertThat(post("/api/v1/internal/reservations/webhooks/schedule/pause", "{}", noPermission).statusCode()).isEqualTo(403)
        assertThat(post("/api/v1/internal/reservations/webhooks/schedule/resume", "{}", noPermission).statusCode()).isEqualTo(403)
    }

    @Test
    fun `manual sync request returns sanitized rejected run with default internal demo provider`() {
        val operator = login(createUser("RES_SYNC_REJECTED", setOf(PermissionCodes.RESERVATION_SYNC_OPERATIONS)))

        val response = post(
            "/api/v1/internal/reservations/sync",
            """{"startDate":"2026-07-24","endDate":"2026-07-26"}""",
            operator
        )

        assertThat(response.statusCode()).isEqualTo(400)
        assertThat(response.body()).doesNotContain("guest-", "RES-", "MUC", "credential", "secret", "notes")
    }

    @Test
    fun `run history and sync state responses are sanitized`() {
        val operator = login(createUser("RES_SYNC_HISTORY", setOf(PermissionCodes.RESERVATION_SYNC_OPERATIONS)))

        val history = get("/api/v1/internal/reservations/sync-runs?page=0&size=5", operator)

        assertThat(history.statusCode()).isEqualTo(200)
        assertThat(json(history.body()).path("content").isArray).isTrue()
        assertThat(history.body()).doesNotContain("credential", "secret", "externalReference", "guest")
    }

    @Test
    fun `scheduler status pause and resume responses are sanitized`() {
        val operator = login(createUser("RES_SYNC_SCHEDULE", setOf(PermissionCodes.RESERVATION_SYNC_OPERATIONS)))

        val paused = post("/api/v1/internal/reservations/sync-schedule/pause", "{}", operator)
        val status = get("/api/v1/internal/reservations/sync-schedule", operator)
        val resumed = post("/api/v1/internal/reservations/sync-schedule/resume", "{}", operator)

        assertThat(paused.statusCode()).isEqualTo(200)
        assertThat(status.statusCode()).isEqualTo(200)
        assertThat(resumed.statusCode()).isEqualTo(200)
        assertThat(json(paused.body()).path("paused").asBoolean()).isTrue()
        assertThat(json(resumed.body()).path("paused").asBoolean()).isFalse()
        assertThat(status.body()).contains("reservation_sync_default")
        assertThat(status.body()).doesNotContain("credential", "secret", "MUC", "guest", "externalReference")
    }

    @Test
    fun `webhook receiver is disabled by default and internal inbox responses are sanitized`() {
        val disabled = post(
            "/api/v1/integrations/pms/apaleo/webhooks?token=test",
            """{"id":"event-1","topic":"reservation","type":"changed","propertyId":"MUC","timestamp":1784896800000}"""
        )
        val operator = login(createUser("RES_WEBHOOK_HISTORY", setOf(PermissionCodes.RESERVATION_SYNC_OPERATIONS)))
        val inbox = get("/api/v1/internal/reservations/webhooks?page=0&size=5", operator)

        assertThat(disabled.statusCode()).isIn(401, 404)
        assertThat(inbox.statusCode()).isEqualTo(200)
        assertThat(inbox.body()).doesNotContain("signature", "secret", "MUC", "guest", "externalReference")
    }

    @Test
    fun `webhook scheduler status and controls are sanitized`() {
        val operator = login(createUser("RES_WEBHOOK_SCHEDULE", setOf(PermissionCodes.RESERVATION_SYNC_OPERATIONS)))

        val paused = post("/api/v1/internal/reservations/webhooks/schedule/pause", "{}", operator)
        val status = get("/api/v1/internal/reservations/webhooks/schedule", operator)
        val resumed = post("/api/v1/internal/reservations/webhooks/schedule/resume", "{}", operator)

        assertThat(paused.statusCode()).isEqualTo(200)
        assertThat(status.statusCode()).isEqualTo(200)
        assertThat(resumed.statusCode()).isEqualTo(200)
        assertThat(json(paused.body()).path("paused").asBoolean()).isTrue()
        assertThat(json(resumed.body()).path("paused").asBoolean()).isFalse()
        assertThat(status.body()).contains("reservation_webhook_processing_default")
        assertThat(status.body()).contains("eligibleBacklogCount")
        assertThat(status.body()).doesNotContain(
            "providerEventId",
            "payloadFingerprint",
            "signature",
            "secret",
            "MUC",
            "externalEntityHash"
        )
    }

    private fun createUser(roleCode: String, permissions: Set<String>): TestUser {
        val suffix = UuidV7Generator.generate().toString().takeLast(12)
        val hotel = hotelRepository.save(Hotel(code = "ressync-$suffix", name = "Reservation Sync Hotel $suffix"))
        val role = roleRepository.save(
            Role(
                hotelId = hotel.id,
                code = roleCode,
                name = "$roleCode Role",
                permissionIds = permissions.map { code ->
                    requireNotNull(permissionRepository.findByCode(code)) { "Missing permission: $code" }.id
                }.toSet()
            )
        )
        val user = userRepository.save(
            User(
                hotelId = hotel.id,
                email = EmailAddress.of("ressync-$suffix@example.test"),
                displayName = "Reservation Sync User $suffix",
                passwordHash = requireNotNull(passwordEncoder.encode(PASSWORD)),
                status = UserStatus.ACTIVE,
                roleIds = setOf(role.id)
            )
        )
        return TestUser(hotel.code, user.email.value)
    }

    private fun login(user: TestUser): String {
        val response = post(
            "/api/v1/auth/login",
            """{"hotelCode":"${user.hotelCode}","email":"${user.email}","password":"$PASSWORD"}"""
        )
        assertThat(response.statusCode()).isEqualTo(200)
        return json(response.body()).path("accessToken").asText()
    }

    private fun get(path: String, token: String? = null): HttpResponse<String> =
        request("GET", path, null, token)

    private fun post(path: String, body: String, token: String? = null): HttpResponse<String> =
        request("POST", path, body, token)

    private fun request(method: String, path: String, body: String?, token: String?): HttpResponse<String> {
        val builder = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$port$path"))
            .method(method, HttpRequest.BodyPublishers.ofString(body ?: ""))
            .header("Content-Type", "application/json")
        if (token != null) {
            builder.header("Authorization", "Bearer $token")
        }
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString())
    }

    private fun json(value: String): JsonNode =
        objectMapper.readTree(value)

    private data class TestUser(
        val hotelCode: String,
        val email: String
    )

    companion object {
        private const val PASSWORD = "reservation-sync-password"
    }
}
