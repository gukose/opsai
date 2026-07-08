package com.hotelopai.auth.api

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.hotelopai.auth.application.RoleRepository
import com.hotelopai.auth.application.UserRepository
import com.hotelopai.auth.domain.EmailAddress
import com.hotelopai.auth.domain.Role
import com.hotelopai.auth.domain.User
import com.hotelopai.auth.domain.UserStatus
import com.hotelopai.hotel.application.HotelRepository
import com.hotelopai.hotel.domain.Hotel
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
import java.util.UUID

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AuthControllerIntegrationTest : PostgresIntegrationTestSupport() {
    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var hotelRepository: HotelRepository

    @Autowired
    private lateinit var roleRepository: RoleRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var passwordEncoder: BCryptPasswordEncoder

    private val httpClient = HttpClient.newHttpClient()

    @Test
    fun `successful login returns tokens and current user`() {
        val response = post(
            "/api/v1/auth/login",
            """{
              "hotelCode":"hotel-opai-demo",
              "email":"admin@hotelopai.local",
              "password":"admin123",
              "deviceId":"device-1",
              "deviceName":"iPhone"
            }"""
        )

        assertThat(response.statusCode()).isEqualTo(200)
        val body = json(response.body())
        assertThat(body.path("tokenType").asText()).isEqualTo("Bearer")
        assertThat(body.path("accessToken").asText()).isNotBlank()
        assertThat(body.path("refreshToken").asText()).isNotBlank()
        assertThat(body.path("user").path("email").asText()).isEqualTo("admin@hotelopai.local")
        assertThat(body.path("user").path("hotelName").asText()).isEqualTo("Hotel OpAI Demo")
        assertThat(body.path("user").path("roles").size()).isGreaterThan(0)
        assertThat(body.path("user").path("permissions").size()).isGreaterThan(0)
    }

    @Test
    fun `failed login rejects invalid credentials`() {
        val response = post(
            "/api/v1/auth/login",
            """{
              "hotelCode":"hotel-opai-demo",
              "email":"admin@hotelopai.local",
              "password":"wrong-password"
            }"""
        )

        assertThat(response.statusCode()).isEqualTo(401)
        assertThat(json(response.body()).path("type").asText()).contains("invalid-credentials")
    }

    @Test
    fun `access token protects me and returns current user`() {
        val login = login()

        val unauthorized = get("/api/v1/auth/me")
        assertThat(unauthorized.statusCode()).isEqualTo(401)

        val me = get("/api/v1/auth/me", login.accessToken)
        assertThat(me.statusCode()).isEqualTo(200)
        val body = json(me.body())
        assertThat(body.path("userId").asText()).isEqualTo(login.userId)
        assertThat(body.path("hotelName").asText()).isEqualTo("Hotel OpAI Demo")
        assertThat(body.path("roles").size()).isGreaterThan(0)
        assertThat(body.path("permissions").size()).isGreaterThan(0)
    }

    @Test
    fun `authenticated user without permission gets forbidden`() {
        val limited = createLimitedAccessUser()
        val response = get("/api/v1/auth/me", limited.accessToken)

        assertThat(response.statusCode()).isEqualTo(403)
        assertThat(json(response.body()).path("type").asText()).contains("forbidden")
    }

    @Test
    fun `hotel id is present in security context`() {
        val login = login()
        val response = get("/api/v1/auth/context", login.accessToken)

        assertThat(response.statusCode()).isEqualTo(200)
        val body = json(response.body())
        assertThat(body.path("hotelId").asText()).isEqualTo(login.hotelId)
    }

    @Test
    fun `refresh rotates token and rejects reused token`() {
        val login = login()
        val refreshed = refresh(login.refreshToken)

        assertThat(refreshed.statusCode()).isEqualTo(200)
        val refreshedBody = json(refreshed.body())
        assertThat(refreshedBody.path("refreshToken").asText()).isNotEqualTo(login.refreshToken)

        val reused = refresh(login.refreshToken)
        assertThat(reused.statusCode()).isEqualTo(401)
        assertThat(json(reused.body()).path("type").asText()).contains("invalid-refresh-token")
    }

    @Test
    fun `logout revokes session and refresh rejects revoked token`() {
        val login = login()
        val refreshed = refresh(login.refreshToken)
        val refreshedBody = json(refreshed.body())
        val latestRefreshToken = refreshedBody.path("refreshToken").asText()
        val latestAccessToken = refreshedBody.path("accessToken").asText()

        val logout = post("/api/v1/auth/logout", "", latestAccessToken)
        assertThat(logout.statusCode()).isEqualTo(200)

        val afterLogout = refresh(latestRefreshToken)
        assertThat(afterLogout.statusCode()).isEqualTo(401)
        assertThat(json(afterLogout.body()).path("type").asText()).contains("revoked-refresh-token")
    }

    @Test
    fun `disabled user cannot login`() {
        val hotel = hotelRepository.save(
            Hotel(
                code = "disabled-hotel",
                name = "Disabled Hotel"
            )
        )
        val role = roleRepository.save(
            Role(
                hotelId = hotel.id,
                code = "disabled-role",
                name = "Disabled Role"
            )
        )
        userRepository.save(
            User(
                hotelId = hotel.id,
                email = EmailAddress.of("disabled@hotelopai.local"),
                displayName = "Disabled User",
                passwordHash = requireNotNull(passwordEncoder.encode("disabled123")),
                roleIds = setOf(role.id),
                status = UserStatus.DISABLED
            )
        )

        val response = post(
            "/api/v1/auth/login",
            """{
              "hotelCode":"disabled-hotel",
              "email":"disabled@hotelopai.local",
              "password":"disabled123"
            }"""
        )

        assertThat(response.statusCode()).isEqualTo(403)
        assertThat(json(response.body()).path("type").asText()).contains("user-disabled")
    }

    private data class LoginSnapshot(
        val accessToken: String,
        val refreshToken: String,
        val userId: String,
        val hotelId: String
    )

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
            refreshToken = body.path("refreshToken").asText(),
            userId = body.path("user").path("userId").asText(),
            hotelId = body.path("user").path("hotelId").asText()
        )
    }

    private fun createLimitedAccessUser(): LoginSnapshot {
        val email = "limited-${UUID.randomUUID().toString().take(8)}@hotelopai.local"
        val hotel = hotelRepository.save(
            Hotel(
                code = "limited-${UUID.randomUUID().toString().take(8)}",
                name = "Limited Hotel"
            )
        )
        val role = roleRepository.save(
            Role(
                hotelId = hotel.id,
                code = "limited-${UUID.randomUUID().toString().take(8)}",
                name = "Limited Role"
            )
        )
        userRepository.save(
            User(
                hotelId = hotel.id,
                email = EmailAddress.of(email),
                displayName = "Limited User",
                passwordHash = requireNotNull(passwordEncoder.encode("limited123")),
                roleIds = setOf(role.id),
                status = UserStatus.ACTIVE
            )
        )

        val loginResponse = post(
            "/api/v1/auth/login",
            """{
              "hotelCode":"${hotel.code}",
              "email":"$email",
              "password":"limited123"
            }"""
        )

        assertThat(loginResponse.statusCode()).isEqualTo(200)
        val body = json(loginResponse.body())
        return LoginSnapshot(
            accessToken = body.path("accessToken").asText(),
            refreshToken = body.path("refreshToken").asText(),
            userId = body.path("user").path("userId").asText(),
            hotelId = body.path("user").path("hotelId").asText()
        )
    }

    private fun refresh(refreshToken: String): HttpResponse<String> =
        post(
            "/api/v1/auth/refresh",
            """{"refreshToken":"$refreshToken"}"""
        )

    private fun post(path: String, body: String, bearerToken: String? = null): HttpResponse<String> {
        val builder = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$port$path"))
            .header("Content-Type", "application/json")
        if (bearerToken != null) {
            builder.header("Authorization", "Bearer $bearerToken")
        }
        builder.POST(HttpRequest.BodyPublishers.ofString(body))
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString())
    }

    private fun get(path: String, bearerToken: String? = null): HttpResponse<String> {
        val builder = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$port$path"))
        if (bearerToken != null) {
            builder.header("Authorization", "Bearer $bearerToken")
        }
        builder.GET()
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString())
    }

    private fun json(value: String): JsonNode =
        objectMapper.readTree(value)
}
