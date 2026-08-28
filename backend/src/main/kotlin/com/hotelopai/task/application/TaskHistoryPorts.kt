package com.hotelopai.task.application

import com.hotelopai.task.domain.TaskStatus
import com.hotelopai.task.domain.TaskTransition
import com.hotelopai.shared.kernel.CorrelationIdContextHolder
import java.time.Instant
import java.util.UUID

interface TaskStateHistoryRepository {
    fun append(entry: TaskStateHistoryEntry)
    fun findByTaskId(taskId: UUID): List<TaskStateHistoryEntry> = emptyList()
    fun findByTaskIds(taskIds: Collection<UUID>): Map<UUID, List<TaskStateHistoryEntry>> =
        taskIds.associateWith { findByTaskId(it) }
}

data class TaskTiming(val totalPauseDurationSeconds: Long, val actualWorkingDurationSeconds: Long)

object TaskTimingCalculator {
    fun calculate(history: List<TaskStateHistoryEntry>, now: Instant): TaskTiming {
        var pause = 0L; var work = 0L; var productive: Instant? = null; var paused: Instant? = null
        history.sortedBy { it.occurredAt }.forEach { e ->
            when (e.toStatus) {
                TaskStatus.STARTED, TaskStatus.IN_PROGRESS -> { productive = productive ?: e.occurredAt; paused?.let { pause += java.time.Duration.between(it,e.occurredAt).seconds }; paused = null }
                TaskStatus.WAITING -> { productive?.let { work += java.time.Duration.between(it,e.occurredAt).seconds }; productive = null; paused = e.occurredAt }
                TaskStatus.COMPLETED, TaskStatus.CANCELLED -> { productive?.let { work += java.time.Duration.between(it,e.occurredAt).seconds }; productive = null; paused?.let { pause += java.time.Duration.between(it,e.occurredAt).seconds }; paused = null }
                else -> Unit
            }
        }
        productive?.let { work += java.time.Duration.between(it,now).seconds }; paused?.let { pause += java.time.Duration.between(it,now).seconds }
        return TaskTiming(pause, work)
    }
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
