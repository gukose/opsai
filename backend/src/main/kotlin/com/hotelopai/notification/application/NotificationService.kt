package com.hotelopai.notification.application

import com.hotelopai.notification.domain.Notification
import com.hotelopai.notification.domain.NotificationRecipient
import com.hotelopai.notification.domain.NotificationType
import com.hotelopai.observability.OperationalObservability
import com.hotelopai.shared.kernel.toPersistencePrecision
import com.hotelopai.shared.security.CurrentUserContext
import com.hotelopai.task.domain.Task
import com.hotelopai.task.domain.TaskAssigneeType
import org.slf4j.LoggerFactory
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
@Transactional
class NotificationService(
    private val notificationRepository: NotificationRepository,
    private val observability: OperationalObservability = OperationalObservability.noop()
) {
    fun createTaskCreatedNotification(
        task: Task,
        sourceEventId: UUID?,
        now: Instant
    ): TaskCreatedNotificationDelivery {
        sourceEventId?.let { eventId ->
            notificationRepository.findBySourceEventId(eventId)?.let {
                return TaskCreatedNotificationDelivery(notification = it, created = false)
            }
        }
        notificationRepository.findTaskCreatedBySourceTaskId(task.id)?.let {
            return TaskCreatedNotificationDelivery(notification = it, created = false)
        }

        try {
            val notification = notificationRepository.save(
                Notification(
                    hotelId = task.hotelId,
                    recipient = recipientFor(task),
                    type = NotificationType.TASK_CREATED,
                    title = "Task created",
                    body = "${task.title} was created.",
                    sourceTaskId = task.id,
                    sourceEventId = sourceEventId,
                    createdAt = now,
                    updatedAt = now
                )
            )
            recordNotification("create", "success", "none")
            return TaskCreatedNotificationDelivery(notification = notification, created = true)
        } catch (_: DuplicateKeyException) {
            val existing = sourceEventId?.let(notificationRepository::findBySourceEventId)
                ?: notificationRepository.findTaskCreatedBySourceTaskId(task.id)
            if (existing != null) {
                recordNotification("create", "success", "idempotent_reuse")
                return TaskCreatedNotificationDelivery(notification = existing, created = false)
            }
            recordNotification("create", "failure", "duplicate_without_existing_notification")
            logger.warn("event=notification operation=create outcome=failure reasonCode=duplicate_without_existing_notification")
            throw DuplicateNotificationDeliveryException()
        } catch (exception: RuntimeException) {
            recordNotification("create", "failure", "operation_failed")
            logger.warn("event=notification operation=create outcome=failure reasonCode=operation_failed")
            throw exception
        }
    }

    @Transactional(readOnly = true)
    fun listAccessible(currentUser: CurrentUserContext): List<Notification> {
        val timer = observability.startTimer()
        var outcome = "failure"
        return try {
            notificationRepository.findAccessible(
                hotelId = currentUser.hotelId,
                userId = currentUser.userId,
                roleCodes = currentUser.roles
            ).also {
                outcome = "success"
                recordNotification("list", outcome, "none")
            }
        } finally {
            observability.stopTimer(
                timer,
                "hotelopai.notification.list.duration",
                "operation" to "list",
                "outcome" to outcome
            )
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(NotificationService::class.java)

        const val DEFAULT_TASK_CREATED_ROLE = "ADMIN"
    }

    fun markRead(notificationId: UUID, currentUser: CurrentUserContext, now: Instant = Instant.now()): Notification {
        try {
            val notification = notificationRepository.findById(notificationId)
                ?.takeIf { it.isAccessibleTo(currentUser) }
                ?: run {
                    recordNotification("mark_read", "not_found", "notification_not_found")
                    throw NotificationNotFoundException(notificationId)
                }

            return notificationRepository.save(
                notification.markRead(
                    now = now.toPersistencePrecision(),
                    updatedBy = currentUser.userId.toString()
                )
            ).also {
                recordNotification("mark_read", "success", "none")
            }
        } catch (exception: NotificationNotFoundException) {
            throw exception
        } catch (exception: RuntimeException) {
            recordNotification("mark_read", "failure", "operation_failed")
            throw exception
        }
    }

    private fun recordNotification(operation: String, outcome: String, reasonCode: String) {
        observability.incrementCounter(
            "hotelopai.notification.operation.total",
            "operation" to operation,
            "outcome" to outcome,
            "reason_code" to reasonCode
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

}

data class TaskCreatedNotificationDelivery(
    val notification: Notification,
    val created: Boolean
)

class DuplicateNotificationDeliveryException : RuntimeException("Duplicate notification delivery could not be resolved")

class NotificationNotFoundException(
    notificationId: UUID
) : RuntimeException("Notification $notificationId was not found")
