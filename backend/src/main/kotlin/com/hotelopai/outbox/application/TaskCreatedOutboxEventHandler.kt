package com.hotelopai.outbox.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.hotelopai.notification.application.NotificationService
import com.hotelopai.outbox.domain.OperationalOutboxAggregateTypes
import com.hotelopai.outbox.domain.OperationalOutboxEvent
import com.hotelopai.outbox.domain.OperationalOutboxEventTypes
import com.hotelopai.outbox.domain.TaskCreatedOutboxPayload
import com.hotelopai.task.application.TaskLifecycleService
import com.hotelopai.task.application.TaskNotFoundException
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Component
class TaskCreatedOutboxEventHandler(
    private val objectMapper: ObjectMapper,
    private val taskLifecycleService: TaskLifecycleService,
    private val notificationService: NotificationService
) {
    @Transactional
    fun handle(event: OperationalOutboxEvent): TaskCreatedHandleOutcome {
        if (event.eventType != OperationalOutboxEventTypes.TASK_CREATED ||
            event.aggregateType != OperationalOutboxAggregateTypes.TASK
        ) {
            throw OutboxEventHandlingException("unsupported_event")
        }

        val payload = parsePayload(event)
        if (payload.payloadVersion != TaskCreatedOutboxPayload.VERSION) {
            throw OutboxEventHandlingException("unknown_payload_version")
        }
        if (payload.taskId != event.aggregateId || payload.hotelId != event.hotelId) {
            throw OutboxEventHandlingException("payload_mismatch")
        }

        val task = try {
            taskLifecycleService.getTaskForHotel(payload.taskId.toString(), payload.hotelId)
        } catch (_: TaskNotFoundException) {
            throw OutboxEventHandlingException("task_not_found")
        }

        val delivery = notificationService.createTaskCreatedNotification(
            task = task,
            sourceEventId = event.id,
            now = parseCreatedAt(payload.createdAt)
        )
        return if (delivery.created) TaskCreatedHandleOutcome.SUCCESS else TaskCreatedHandleOutcome.DUPLICATE
    }

    private fun parsePayload(event: OperationalOutboxEvent): TaskCreatedOutboxPayload =
        try {
            objectMapper.readValue(event.payloadJson, TaskCreatedOutboxPayload::class.java)
        } catch (_: Exception) {
            throw OutboxEventHandlingException("malformed_payload")
        }

    private fun parseCreatedAt(value: String): Instant =
        try {
            Instant.parse(value)
        } catch (_: RuntimeException) {
            throw OutboxEventHandlingException("malformed_payload")
        }
}

enum class TaskCreatedHandleOutcome {
    SUCCESS,
    DUPLICATE
}

class OutboxEventHandlingException(
    val reasonCode: String
) : RuntimeException(reasonCode)
