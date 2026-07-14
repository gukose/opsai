package com.hotelopai.notification.application

import com.hotelopai.notification.domain.Notification
import com.hotelopai.notification.domain.NotificationRecipient
import com.hotelopai.notification.domain.NotificationType
import com.hotelopai.shared.security.CurrentUserContext
import com.hotelopai.task.application.TaskNotificationPublisher
import com.hotelopai.task.domain.Task
import com.hotelopai.task.domain.TaskAssigneeType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
@Transactional
class NotificationService(
    private val notificationRepository: NotificationRepository
) : TaskNotificationPublisher {
    override fun taskCreated(task: Task, now: Instant) {
        notificationRepository.save(
            Notification(
                hotelId = task.hotelId,
                recipient = recipientFor(task),
                type = NotificationType.TASK_CREATED,
                title = "Task created",
                body = "${task.title} was created.",
                sourceTaskId = task.id,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    @Transactional(readOnly = true)
    fun listAccessible(currentUser: CurrentUserContext): List<Notification> =
        notificationRepository.findAccessible(
            hotelId = currentUser.hotelId,
            userId = currentUser.userId,
            roleCodes = currentUser.roles
        )

    fun markRead(notificationId: UUID, currentUser: CurrentUserContext, now: Instant = Instant.now()): Notification {
        val notification = notificationRepository.findById(notificationId)
            ?.takeIf { it.isAccessibleTo(currentUser) }
            ?: throw NotificationNotFoundException(notificationId)

        return notificationRepository.save(
            notification.markRead(
                now = now,
                updatedBy = currentUser.userId.toString()
            )
        )
    }

    private fun recipientFor(task: Task): NotificationRecipient {
        val assignment = task.assignment
        if (assignment?.assigneeType == TaskAssigneeType.USER) {
            val userId = assignment.assigneeId.toUuidOrNull()
            if (userId != null) {
                return NotificationRecipient.User(userId)
            }
        }

        return NotificationRecipient.Role(DEFAULT_TASK_CREATED_ROLE)
    }

    private fun Notification.isAccessibleTo(currentUser: CurrentUserContext): Boolean =
        hotelId == currentUser.hotelId && when (recipient) {
            is NotificationRecipient.User -> recipient.userId == currentUser.userId
            is NotificationRecipient.Role -> recipient.roleCode in currentUser.roles
        }

    private fun String.toUuidOrNull(): UUID? =
        try {
            UUID.fromString(this)
        } catch (_: IllegalArgumentException) {
            null
        }

    companion object {
        const val DEFAULT_TASK_CREATED_ROLE = "ADMIN"
    }
}

class NotificationNotFoundException(
    notificationId: UUID
) : RuntimeException("Notification $notificationId was not found")
