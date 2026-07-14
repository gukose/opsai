package com.hotelopai.notification.api

import com.hotelopai.notification.domain.Notification
import com.hotelopai.notification.domain.NotificationRecipient
import java.time.Instant

data class NotificationResponse(
    val id: String,
    val hotelId: String,
    val recipient: NotificationRecipientResponse,
    val type: String,
    val status: String,
    val title: String,
    val body: String,
    val sourceTaskId: String?,
    val readAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    companion object {
        fun from(notification: Notification): NotificationResponse =
            NotificationResponse(
                id = notification.id.toString(),
                hotelId = notification.hotelId.toString(),
                recipient = NotificationRecipientResponse.from(notification.recipient),
                type = notification.type.name,
                status = notification.status.name,
                title = notification.title,
                body = notification.body,
                sourceTaskId = notification.sourceTaskId?.toString(),
                readAt = notification.readAt,
                createdAt = notification.createdAt,
                updatedAt = notification.updatedAt
            )
    }
}

data class NotificationRecipientResponse(
    val type: String,
    val userId: String?,
    val roleCode: String?
) {
    companion object {
        fun from(recipient: NotificationRecipient): NotificationRecipientResponse =
            when (recipient) {
                is NotificationRecipient.User -> NotificationRecipientResponse(
                    type = "USER",
                    userId = recipient.userId.toString(),
                    roleCode = null
                )
                is NotificationRecipient.Role -> NotificationRecipientResponse(
                    type = "ROLE",
                    userId = null,
                    roleCode = recipient.roleCode
                )
            }
    }
}
