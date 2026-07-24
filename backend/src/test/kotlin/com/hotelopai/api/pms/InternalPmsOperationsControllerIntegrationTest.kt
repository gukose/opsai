package com.hotelopai.api.pms

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
class InternalPmsOperationsControllerIntegrationTest : PostgresIntegrationTestSupport() {
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
    fun `PMS operations endpoints require operations permission and return sanitized diagnostics`() {
        assertThat(get("/api/v1/internal/pms/providers").statusCode()).isEqualTo(401)

        val noPermission = login(createUser("PMS_OPS_NO_PERMISSION", emptySet()))
        assertThat(get("/api/v1/internal/pms/providers", noPermission).statusCode()).isEqualTo(403)

        val operator = login(createUser("PMS_OPS_ALLOWED", setOf(PermissionCodes.PMS_OPERATIONS_ACCESS)))
        val providers = get("/api/v1/internal/pms/providers", operator)
        val diagnostics = get("/api/v1/internal/pms/providers/internal-demo", operator)
        val health = post("/api/v1/internal/pms/providers/internal-demo/health-check", "", operator)
        val readiness = get("/api/v1/internal/pms/rollout-readiness", operator)
        val refresh = post("/api/v1/internal/pms/providers/internal-demo/credentials/refresh", "", operator)

        listOf(providers, diagnostics, health, readiness, refresh).forEach {
            assertThat(it.statusCode()).isEqualTo(200)
            assertThat(it.body()).doesNotContain("APALEO_CLIENT_SECRET")
            assertThat(it.body()).doesNotContain("client-secret")
            assertThat(it.body()).doesNotContain("credentialReferences")
        }
        assertThat(json(providers.body()).map { it.path("providerId").asText() })
            .contains("internal-demo", "apaleo")
        assertThat(json(diagnostics.body()).path("authenticationMode").asText()).isEqualTo("NONE")
        assertThat(json(health.body()).path("state").asText()).isEqualTo("READY")
        assertThat(json(readiness.body()).path("state").asText()).isEqualTo("READY_FOR_SANDBOX")
    }

    @Test
    fun `unknown PMS provider is rejected clearly`() {
        val operator = login(createUser("PMS_OPS_UNKNOWN", setOf(PermissionCodes.PMS_OPERATIONS_ACCESS)))

        val response = get("/api/v1/internal/pms/providers/missing", operator)

        assertThat(response.statusCode()).isEqualTo(404)
        assertThat(json(response.body()).path("path").asText()).isEqualTo("/api/v1/internal/pms/providers/missing")
    }

    private fun createUser(roleCode: String, permissions: Set<String>): TestUser {
        val suffix = UuidV7Generator.generate().toString().takeLast(12)
        val hotel = hotelRepository.save(Hotel(code = "pmsops-$suffix", name = "PMS Ops Hotel $suffix"))
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
                email = EmailAddress.of("pmsops-$suffix@example.test"),
                displayName = "PMS Ops User $suffix",
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
        private const val PASSWORD = "pms-ops-password"
    }
}
