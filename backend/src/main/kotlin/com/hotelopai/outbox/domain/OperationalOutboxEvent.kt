package com.hotelopai.outbox.domain

import com.hotelopai.shared.kernel.UuidV7Generator
import java.time.Instant
import java.util.UUID

data class OperationalOutboxEvent(
    val id: UUID = UuidV7Generator.generate(),
    val eventType: String,
    val aggregateType: String,
    val aggregateId: UUID,
    val hotelId: UUID,
    val payloadJson: String,
    val status: OperationalOutboxStatus = OperationalOutboxStatus.PENDING,
    val attemptCount: Int = 0,
    val nextAttemptAt: Instant,
    val lockedAt: Instant? = null,
    val lockedBy: String? = null,
    val processedAt: Instant? = null,
    val lastFailureCode: String? = null,
    val lastFailureMessage: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant = createdAt
) {
    init {
        require(eventType.isNotBlank()) { "eventType must not be blank" }
        require(aggregateType.isNotBlank()) { "aggregateType must not be blank" }
        require(attemptCount >= 0) { "attemptCount must not be negative" }
        require(status != OperationalOutboxStatus.COMPLETED || processedAt != null) {
            "completed outbox events must have processedAt"
        }
    }
}

enum class OperationalOutboxStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED
}

object OperationalOutboxEventTypes {
    const val TASK_CREATED = "TASK_CREATED"
}

object OperationalOutboxAggregateTypes {
    const val TASK = "TASK"
}

data class TaskCreatedOutboxPayload(
    val payloadVersion: Int,
    val taskId: UUID,
    val hotelId: UUID,
    val createdAt: String
) {
    companion object {
        const val VERSION = 1
    }
}
