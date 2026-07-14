package com.hotelopai.notification.infrastructure.persistence

import com.hotelopai.notification.domain.Notification
import com.hotelopai.notification.domain.NotificationRecipient

object NotificationPersistenceMapper {
    fun toEntity(notification: Notification): NotificationJpaEntity =
        NotificationJpaEntity().also { entity ->
            updateEntity(entity, notification)
        }

    fun updateEntity(entity: NotificationJpaEntity, notification: Notification): NotificationJpaEntity =
        entity.apply {
            id = notification.id
            hotelId = notification.hotelId
            when (val recipient = notification.recipient) {
                is NotificationRecipient.User -> {
                    recipientUserId = recipient.userId
                    recipientRoleCode = null
                }
                is NotificationRecipient.Role -> {
                    recipientUserId = null
                    recipientRoleCode = recipient.roleCode
                }
            }
            type = notification.type
            status = notification.status
            title = notification.title
            body = notification.body
            sourceTaskId = notification.sourceTaskId
            readAt = notification.readAt
            createdAt = notification.createdAt
            createdBy = notification.createdBy
            updatedAt = notification.updatedAt
            updatedBy = notification.updatedBy
        }

    fun toDomain(entity: NotificationJpaEntity): Notification =
        Notification(
            id = requireNotNull(entity.id) { "notification id is required" },
            hotelId = requireNotNull(entity.hotelId) { "notification hotelId is required" },
            recipient = entity.recipientUserId?.let(NotificationRecipient::User)
                ?: NotificationRecipient.Role(requireNotNull(entity.recipientRoleCode) { "notification recipient is required" }),
            type = entity.type,
            status = entity.status,
            title = entity.title,
            body = entity.body,
            sourceTaskId = entity.sourceTaskId,
            readAt = entity.readAt,
            version = entity.version ?: 0,
            createdAt = requireNotNull(entity.createdAt) { "notification createdAt is required" },
            createdBy = entity.createdBy,
            updatedAt = requireNotNull(entity.updatedAt) { "notification updatedAt is required" },
            updatedBy = entity.updatedBy
        )
}
