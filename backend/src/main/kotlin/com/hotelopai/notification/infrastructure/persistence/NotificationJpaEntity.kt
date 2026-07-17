package com.hotelopai.notification.infrastructure.persistence

import com.hotelopai.infrastructure.persistence.AuditedJpaEntity
import com.hotelopai.notification.domain.NotificationStatus
import com.hotelopai.notification.domain.NotificationType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "notifications")
class NotificationJpaEntity : AuditedJpaEntity() {
    @Column(name = "hotel_id", nullable = false, columnDefinition = "uuid")
    var hotelId: UUID? = null

    @Column(name = "recipient_user_id", columnDefinition = "uuid")
    var recipientUserId: UUID? = null

    @Column(name = "recipient_role_code")
    var recipientRoleCode: String? = null

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    var type: NotificationType = NotificationType.TASK_CREATED

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: NotificationStatus = NotificationStatus.UNREAD

    @Column(name = "title", nullable = false)
    var title: String = ""

    @Column(name = "body", nullable = false)
    var body: String = ""

    @Column(name = "source_task_id", columnDefinition = "uuid")
    var sourceTaskId: UUID? = null

    @Column(name = "source_event_id", columnDefinition = "uuid")
    var sourceEventId: UUID? = null

    @Column(name = "read_at")
    var readAt: Instant? = null
}
