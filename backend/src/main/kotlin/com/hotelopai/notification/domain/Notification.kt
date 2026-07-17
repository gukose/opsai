package com.hotelopai.notification.domain

import com.hotelopai.shared.kernel.AuditedRecord
import com.hotelopai.shared.kernel.UuidV7Generator
import java.time.Instant
import java.util.UUID

data class Notification(
    override val id: UUID = UuidV7Generator.generate(),
    val hotelId: UUID,
    val recipient: NotificationRecipient,
    val type: NotificationType,
    val status: NotificationStatus = NotificationStatus.UNREAD,
    val title: String,
    val body: String,
    val sourceTaskId: UUID? = null,
    val sourceEventId: UUID? = null,
    val readAt: Instant? = null,
    override val version: Long = 0,
    override val createdAt: Instant = Instant.now(),
    override val createdBy: String? = null,
    override val updatedAt: Instant = createdAt,
    override val updatedBy: String? = null
) : AuditedRecord {
    init {
        require(hotelId != UUID(0L, 0L)) { "hotelId must not be empty" }
        require(title.isNotBlank()) { "title must not be blank" }
        require(body.isNotBlank()) { "body must not be blank" }
        require((status == NotificationStatus.READ) == (readAt != null)) {
            "read notifications must have readAt and unread notifications must not"
        }
    }

    fun markRead(now: Instant = Instant.now(), updatedBy: String? = null): Notification =
        if (status == NotificationStatus.READ) {
            this
        } else {
            copy(
                status = NotificationStatus.READ,
                readAt = now,
                updatedAt = now,
                updatedBy = updatedBy,
                version = version + 1
            )
        }
}

sealed interface NotificationRecipient {
    data class User(val userId: UUID) : NotificationRecipient {
        init {
            require(userId != UUID(0L, 0L)) { "recipient userId must not be empty" }
        }
    }

    data class Role(val roleCode: String) : NotificationRecipient {
        init {
            require(roleCode.isNotBlank()) { "recipient roleCode must not be blank" }
        }
    }
}
