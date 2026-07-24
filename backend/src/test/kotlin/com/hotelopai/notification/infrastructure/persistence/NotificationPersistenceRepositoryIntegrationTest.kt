package com.hotelopai.notification.infrastructure.persistence

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
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class NotificationPersistenceRepositoryIntegrationTest : PostgresIntegrationTestSupport() {
    @Autowired
    private lateinit var hotelRepository: HotelRepository

    @Autowired
    private lateinit var notificationRepository: NotificationRepository

    @Test
    fun `persists and loads role notification`() {
        val hotel = hotelRepository.save(
            Hotel(code = "notification-hotel-${UuidV7Generator.generate()}", name = "Notification Hotel")
        )
        val createdAt = Instant.parse("2026-07-14T10:00:00Z")
        val notification = Notification(
            hotelId = hotel.id,
            recipient = NotificationRecipient.Role("ADMIN"),
            type = NotificationType.TASK_CREATED,
            title = "Task created",
            body = "AC not working was created.",
            createdAt = createdAt,
            updatedAt = createdAt
        )

        val saved = notificationRepository.save(notification)

        assertThat(notificationRepository.findById(saved.id)).isEqualTo(saved)
        assertThat(
            notificationRepository.findAccessible(
                hotelId = hotel.id,
                userId = UuidV7Generator.generate(),
                roleCodes = setOf("ADMIN")
            )
        ).containsExactly(saved)
    }

    @Test
    fun `persists and loads user notification and read status`() {
        val hotel = hotelRepository.save(
            Hotel(code = "notification-user-${UuidV7Generator.generate()}", name = "Notification User Hotel")
        )
        val userId = UuidV7Generator.generate()
        val createdAt = Instant.parse("2026-07-14T11:00:00Z")
        val readAt = Instant.parse("2026-07-14T11:05:00Z")
        val notification = Notification(
            hotelId = hotel.id,
            recipient = NotificationRecipient.User(userId),
            type = NotificationType.TASK_CREATED,
            status = NotificationStatus.READ,
            title = "Task created",
            body = "Extra towels was created.",
            readAt = readAt,
            createdAt = createdAt,
            updatedAt = readAt
        )

        val saved = notificationRepository.save(notification)

        assertThat(notificationRepository.findById(saved.id)).isEqualTo(saved)
        assertThat(
            notificationRepository.findAccessible(
                hotelId = hotel.id,
                userId = userId,
                roleCodes = emptySet()
            )
        ).containsExactly(saved)
    }

    @Test
    fun `save returns notification timestamps at persistence precision`() {
        val hotel = hotelRepository.save(
            Hotel(code = "notification-precision-${UuidV7Generator.generate()}", name = "Notification Precision Hotel")
        )
        val createdAt = Instant.parse("2026-07-14T12:00:00.123456789Z")
        val readAt = Instant.parse("2026-07-14T12:05:00.987654321Z")
        val notification = Notification(
            hotelId = hotel.id,
            recipient = NotificationRecipient.Role("ADMIN"),
            type = NotificationType.TASK_CREATED,
            status = NotificationStatus.READ,
            title = "Task created",
            body = "Precision task was created.",
            readAt = readAt,
            createdAt = createdAt,
            updatedAt = readAt
        )

        val saved = notificationRepository.save(notification)

        assertThat(saved.createdAt).isEqualTo(Instant.parse("2026-07-14T12:00:00.123456Z"))
        assertThat(saved.readAt).isEqualTo(Instant.parse("2026-07-14T12:05:00.987654Z"))
        assertThat(saved.updatedAt).isEqualTo(Instant.parse("2026-07-14T12:05:00.987654Z"))
        assertThat(notificationRepository.findById(saved.id)).isEqualTo(saved)
    }
}
