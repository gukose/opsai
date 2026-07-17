package com.hotelopai.notification.infrastructure.persistence

import com.hotelopai.notification.application.NotificationRepository
import com.hotelopai.notification.domain.Notification
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Repository
@Transactional
class NotificationPersistenceRepository(
    private val notificationJpaRepository: NotificationJpaRepository
) : NotificationRepository {
    override fun save(notification: Notification): Notification =
        NotificationPersistenceMapper.toDomain(
            notificationJpaRepository.saveAndFlush(
                notificationJpaRepository.findById(notification.id).orElse(null)?.let {
                    NotificationPersistenceMapper.updateEntity(it, notification)
                } ?: NotificationPersistenceMapper.toEntity(notification)
            )
        )

    override fun findById(id: UUID): Notification? =
        notificationJpaRepository.findById(id).orElse(null)?.let(NotificationPersistenceMapper::toDomain)

    override fun findBySourceEventId(sourceEventId: UUID): Notification? =
        notificationJpaRepository.findBySourceEventId(sourceEventId)?.let(NotificationPersistenceMapper::toDomain)

    override fun findTaskCreatedBySourceTaskId(sourceTaskId: UUID): Notification? =
        notificationJpaRepository.findBySourceTaskIdAndType(
            sourceTaskId,
            com.hotelopai.notification.domain.NotificationType.TASK_CREATED
        )?.let(NotificationPersistenceMapper::toDomain)

    override fun findAccessible(
        hotelId: UUID,
        userId: UUID,
        roleCodes: Set<String>
    ): List<Notification> =
        if (roleCodes.isEmpty()) {
            notificationJpaRepository.findAccessibleForUserOnly(hotelId, userId)
        } else {
            notificationJpaRepository.findAccessible(hotelId, userId, roleCodes)
        }.map(NotificationPersistenceMapper::toDomain)

    override fun countBySourceTaskId(sourceTaskId: UUID): Long =
        notificationJpaRepository.countBySourceTaskId(sourceTaskId)

    override fun countBySourceEventId(sourceEventId: UUID): Long =
        notificationJpaRepository.countBySourceEventId(sourceEventId)
}
