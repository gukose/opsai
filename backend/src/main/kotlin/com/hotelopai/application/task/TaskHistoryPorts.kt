package com.hotelopai.task.application

import com.hotelopai.task.domain.TaskStatus
import com.hotelopai.task.domain.TaskTransition
import com.hotelopai.shared.kernel.CorrelationIdContextHolder
import java.time.Instant
import java.util.UUID

interface TaskStateHistoryRepository {
    fun append(entry: TaskStateHistoryEntry)
}

interface TaskLogRepository {
    fun append(entry: TaskLogEntry)
}

enum class TaskLogOutcome {
    SUCCESS,
    FAILED
}

data class TaskStateHistoryEntry(
    val taskId: UUID,
    val hotelId: UUID,
    val fromStatus: TaskStatus?,
    val toStatus: TaskStatus,
    val operation: TaskTransition,
    val note: String? = null,
    val correlationId: String? = CorrelationIdContextHolder.current(),
    val occurredAt: Instant = Instant.now()
)

data class TaskLogEntry(
    val taskId: UUID,
    val hotelId: UUID,
    val operation: TaskTransition,
    val outcome: TaskLogOutcome,
    val message: String,
    val fromStatus: TaskStatus? = null,
    val toStatus: TaskStatus? = null,
    val correlationId: String? = CorrelationIdContextHolder.current(),
    val occurredAt: Instant = Instant.now()
)
