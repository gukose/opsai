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
    const val RESERVATION_IMPORTED = "RESERVATION_IMPORTED"
    const val RESERVATION_UPDATED = "RESERVATION_UPDATED"
    const val RESERVATION_STATUS_CHANGED = "RESERVATION_STATUS_CHANGED"
    const val GUEST_CHECKED_IN = "GUEST_CHECKED_IN"
    const val GUEST_CHECKED_OUT = "GUEST_CHECKED_OUT"
    const val RESERVATION_CANCELLED = "RESERVATION_CANCELLED"
    const val RESERVATION_MARKED_NO_SHOW = "RESERVATION_MARKED_NO_SHOW"
    const val ROOM_ASSIGNMENT_CHANGED = "ROOM_ASSIGNMENT_CHANGED"
}

object OperationalOutboxAggregateTypes {
    const val TASK = "TASK"
    const val RESERVATION = "RESERVATION"
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
