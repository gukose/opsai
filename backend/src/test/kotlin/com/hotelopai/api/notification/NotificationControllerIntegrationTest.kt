package com.hotelopai.api.notification

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.hotelopai.hotel.application.HotelRepository
import com.hotelopai.hotel.domain.Hotel
import com.hotelopai.notification.application.NotificationRepository
import com.hotelopai.notification.domain.Notification
import com.hotelopai.notification.domain.NotificationRecipient
import com.hotelopai.notification.domain.NotificationStatus
import com.hotelopai.notification.domain.NotificationType
import com.hotelopai.shared.kernel.UuidV7Generator
import com.hotelopai.support.PostgresIntegrationTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Instant
import java.util.UUID

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class NotificationControllerIntegrationTest : PostgresIntegrationTestSupport() {
    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var hotelRepository: HotelRepository

    @Autowired
    private lateinit var notificationRepository: NotificationRepository

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    private val httpClient = HttpClient.newHttpClient()

    @Test
    fun `notification endpoints require authentication`() {
        assertThat(get("/api/v1/notifications").statusCode()).isEqualTo(401)
        assertThat(post("/api/v1/notifications/${UuidV7Generator.generate()}/read", "", null).statusCode())
            .isEqualTo(401)
    }

    @Test
    fun `authenticated listing returns only accessible hotel scoped notifications`() {
        val login = login()
        val currentUser = notificationRepository.save(
            notification(
                hotelId = login.hotelId,
                recipient = NotificationRecipient.User(login.userId),
                title = "Current user notification"
            )
        )
        val adminRole = notificationRepository.save(
            notification(
                hotelId = login.hotelId,
                recipient = NotificationRecipient.Role("ADMIN"),
                title = "Admin role notification"
            )
        )
        val otherUser = notificationRepository.save(
            notification(
                hotelId = login.hotelId,
                recipient = NotificationRecipient.User(UuidV7Generator.generate()),
                title = "Other user notification"
            )
        )
        val managerRole = notificationRepository.save(
            notification(
                hotelId = login.hotelId,
                recipient = NotificationRecipient.Role("MANAGER"),
                title = "Manager role notification"
            )
        )
        val otherHotel = hotelRepository.save(
            Hotel(code = "notification-other-${UuidV7Generator.generate()}", name = "Other Hotel")
        )
        val otherHotelNotification = notificationRepository.save(
            notification(
                hotelId = otherHotel.id,
                recipient = NotificationRecipient.Role("ADMIN"),
                title = "Other hotel notification"
            )
        )

        val response = get("/api/v1/notifications", login.accessToken)

        assertThat(response.statusCode()).isEqualTo(200)
        val ids = json(response.body()).map { it.path("id").asText() }.toSet()
        assertThat(ids).contains(currentUser.id.toString(), adminRole.id.toString())
        assertThat(ids).doesNotContain(
            otherUser.id.toString(),
            managerRole.id.toString(),
            otherHotelNotification.id.toString()
        )
    }

    @Test
    fun `mark read is idempotent for accessible notification`() {
        val login = login()
        val title = "Readable notification ${UuidV7Generator.generate()}"
        val notification = notificationRepository.save(
            notification(
                hotelId = login.hotelId,
                recipient = NotificationRecipient.User(login.userId),
                title = title
            )
        )
        val unrelated = notificationRepository.save(
            notification(
                hotelId = login.hotelId,
                recipient = NotificationRecipient.User(login.userId),
                title = "Unrelated readable notification ${UuidV7Generator.generate()}"
            )
        )

        val first = post("/api/v1/notifications/${notification.id}/read", "", login.accessToken)
        val persistedAfterFirst = notificationRepository.findById(notification.id)
            ?: error("notification should exist after first mark-read")
        val second = post("/api/v1/notifications/${notification.id}/read", "", login.accessToken)
        val persistedAfterSecond = notificationRepository.findById(notification.id)
            ?: error("notification should exist after second mark-read")

        assertThat(first.statusCode()).isEqualTo(200)
        assertThat(second.statusCode()).isEqualTo(200)
        val firstBody = json(first.body())
        val secondBody = json(second.body())
        assertThat(firstBody.path("status").asText()).isEqualTo("READ")
        assertThat(secondBody.path("status").asText()).isEqualTo("READ")
        val firstReadAt = Instant.parse(firstBody.path("readAt").asText())
        val secondReadAt = Instant.parse(secondBody.path("readAt").asText())
        assertThat(secondReadAt).isEqualTo(firstReadAt)
        assertThat(persistedAfterFirst.status).isEqualTo(NotificationStatus.READ)
        assertThat(persistedAfterFirst.readAt).isEqualTo(firstReadAt)
        assertThat(persistedAfterSecond.status).isEqualTo(NotificationStatus.READ)
        assertThat(persistedAfterSecond.readAt).isEqualTo(firstReadAt)
        assertThat(notificationRepository.findById(unrelated.id)?.status).isEqualTo(NotificationStatus.UNREAD)
        assertThat(
            jdbcTemplate.queryForObject(
                "select count(*) from notifications where id = ?",
                Long::class.java,
                notification.id
            )
        ).isEqualTo(1L)
    }

    @Test
    fun `mark read does not expose inaccessible notifications`() {
        val login = login()
        val otherHotel = hotelRepository.save(
            Hotel(code = "notification-hidden-${UuidV7Generator.generate()}", name = "Hidden Hotel")
        )
        val hidden = notificationRepository.save(
            notification(
                hotelId = otherHotel.id,
                recipient = NotificationRecipient.Role("ADMIN"),
                title = "Hidden notification"
            )
        )

        val response = post("/api/v1/notifications/${hidden.id}/read", "", login.accessToken)

        assertThat(response.statusCode()).isEqualTo(404)
    }

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
        val response = post(
            "/api/v1/auth/login",
            """{
              "hotelCode":"hotel-opai-demo",
              "email":"admin@hotelopai.local",
              "password":"admin123"
            }""",
            null
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

    private fun post(path: String, body: String, bearerToken: String?): HttpResponse<String> {
        val builder = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$port$path"))
            .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .POST(HttpRequest.BodyPublishers.ofString(body))
        if (bearerToken != null) {
            builder.header("Authorization", "Bearer $bearerToken")
        }
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString())
    }

    private fun json(value: String): JsonNode = objectMapper.readTree(value)

    private data class LoginSnapshot(
        val accessToken: String,
        val userId: UUID,
        val hotelId: UUID
    )
}
