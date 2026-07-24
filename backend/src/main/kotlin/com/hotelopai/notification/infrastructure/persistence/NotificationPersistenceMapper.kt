package com.hotelopai.notification.infrastructure.persistence

import com.hotelopai.notification.domain.Notification
import com.hotelopai.notification.domain.NotificationRecipient
import com.hotelopai.shared.kernel.PersistenceInstant

object NotificationPersistenceMapper {
    fun toEntity(notification: Notification): NotificationJpaEntity =
        NotificationJpaEntity().also { entity ->
            updateEntity(entity, notification)
        }

    fun updateEntity(entity: NotificationJpaEntity, notification: Notification): NotificationJpaEntity =
        entity.apply {
            val normalized = notification.normalizedForPersistence()
            id = normalized.id
            hotelId = normalized.hotelId
            when (val recipient = normalized.recipient) {
                is NotificationRecipient.User -> {
                    recipientUserId = recipient.userId
                    recipientRoleCode = null
                }
                is NotificationRecipient.Role -> {
                    recipientUserId = null
                    recipientRoleCode = recipient.roleCode
                }
            }
            type = normalized.type
            status = normalized.status
            title = normalized.title
            body = normalized.body
            sourceTaskId = normalized.sourceTaskId
            sourceEventId = normalized.sourceEventId
            readAt = normalized.readAt
            createdAt = normalized.createdAt
            createdBy = normalized.createdBy
            updatedAt = normalized.updatedAt
            updatedBy = normalized.updatedBy
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
            sourceEventId = entity.sourceEventId,
            readAt = entity.readAt,
            version = entity.version ?: 0,
            createdAt = requireNotNull(entity.createdAt) { "notification createdAt is required" },
            createdBy = entity.createdBy,
            updatedAt = requireNotNull(entity.updatedAt) { "notification updatedAt is required" },
            updatedBy = entity.updatedBy
        )

    private fun Notification.normalizedForPersistence(): Notification =
        copy(
            readAt = PersistenceInstant.toPersistencePrecisionOrNull(readAt),
            createdAt = PersistenceInstant.toPersistencePrecision(createdAt),
            updatedAt = PersistenceInstant.toPersistencePrecision(updatedAt)
        )
}
