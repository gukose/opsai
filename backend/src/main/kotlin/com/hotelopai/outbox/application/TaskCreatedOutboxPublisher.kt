package com.hotelopai.outbox.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.hotelopai.observability.OperationalObservability
import com.hotelopai.outbox.domain.OperationalOutboxAggregateTypes
import com.hotelopai.outbox.domain.OperationalOutboxEvent
import com.hotelopai.outbox.domain.OperationalOutboxEventTypes
import com.hotelopai.outbox.domain.TaskCreatedOutboxPayload
import com.hotelopai.task.application.TaskNotificationPublisher
import com.hotelopai.task.domain.Task
import org.slf4j.LoggerFactory
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class TaskCreatedOutboxPublisher(
    private val outboxRepository: OperationalOutboxRepository,
    private val objectMapper: ObjectMapper,
    private val observability: OperationalObservability = OperationalObservability.noop()
) : TaskNotificationPublisher {
    override fun taskCreated(task: Task, now: Instant) {
        val payload = TaskCreatedOutboxPayload(
            payloadVersion = TaskCreatedOutboxPayload.VERSION,
            taskId = task.id,
            hotelId = task.hotelId,
            createdAt = now.toString()
        )
        val event = OperationalOutboxEvent(
            eventType = OperationalOutboxEventTypes.TASK_CREATED,
            aggregateType = OperationalOutboxAggregateTypes.TASK,
            aggregateId = task.id,
            hotelId = task.hotelId,
            payloadJson = objectMapper.writeValueAsString(payload),
            nextAttemptAt = now,
            createdAt = now,
            updatedAt = now
        )

        try {
            outboxRepository.save(event)
            recordOutbox(operation = "enqueue", outcome = "success", reasonCode = "none")
        } catch (_: DuplicateKeyException) {
            val existing = outboxRepository.findByEventAggregate(
                eventType = OperationalOutboxEventTypes.TASK_CREATED,
                aggregateType = OperationalOutboxAggregateTypes.TASK,
                aggregateId = task.id
            )
            if (existing != null) {
                recordOutbox(operation = "enqueue", outcome = "duplicate", reasonCode = "event_already_exists")
                return
            }
            recordOutbox(operation = "enqueue", outcome = "failed", reasonCode = "duplicate_without_existing_event")
            logger.warn("event=outbox_enqueue operation=enqueue outcome=failed reasonCode=duplicate_without_existing_event")
            throw DuplicateOutboxEventException()
        }
    }

    private fun recordOutbox(operation: String, outcome: String, reasonCode: String) {
        observability.incrementCounter(
            "hotelopai.outbox.event.total",
            "operation" to operation,
            "event_type" to "task_created",
            "outcome" to outcome,
            "reason_code" to reasonCode
        )
    }

    companion object {
        private val logger = LoggerFactory.getLogger(TaskCreatedOutboxPublisher::class.java)
    }
}

class DuplicateOutboxEventException : RuntimeException("Duplicate outbox event could not be resolved")
