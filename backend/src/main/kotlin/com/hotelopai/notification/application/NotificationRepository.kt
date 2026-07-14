package com.hotelopai.notification.application

import com.hotelopai.notification.domain.Notification
import java.util.UUID

interface NotificationRepository {
    fun save(notification: Notification): Notification

    fun findById(id: UUID): Notification?

    fun findAccessible(
        hotelId: UUID,
        userId: UUID,
        roleCodes: Set<String>
    ): List<Notification>

    fun countBySourceTaskId(sourceTaskId: UUID): Long
}
