package com.hotelopai.task.domain

import com.hotelopai.task.domain.TaskWorkflowDefinition
import java.time.Instant
import java.util.UUID

data class Task(
    val id: UUID,
    val hotelId: UUID,
    val intentType: TaskIntentType,
    val source: TaskSource,
    val title: String,
    val description: String,
    val roomNumber: String? = null,
    val priority: TaskPriority,
    val slaDeadline: Instant,
    val status: TaskStatus = TaskStatus.CREATED,
    val assignment: TaskAssignment? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = createdAt,
    val startedAt: Instant? = null,
    val completedAt: Instant? = null,
    val cancelledAt: Instant? = null,
    val overdueAt: Instant? = null
) {
    init {
        require(hotelId != UUID(0L, 0L)) { "hotelId must not be empty" }
        require(title.isNotBlank()) { "title must not be blank" }
        require(description.isNotBlank()) { "description must not be blank" }
        require(slaDeadline.isAfter(createdAt)) { "slaDeadline must be after createdAt" }
    }

    fun assign(assignment: TaskAssignment, now: Instant = Instant.now()): Task {
        TaskWorkflowDefinition.requireTransition(status, TaskStatus.ASSIGNED)
        return copy(
            status = TaskStatus.ASSIGNED,
            assignment = assignment.copy(assignedAt = now),
            updatedAt = now
        )
    }

    fun start(now: Instant = Instant.now()): Task {
        TaskWorkflowDefinition.requireTransition(status, TaskStatus.STARTED)
        return copy(
            status = TaskStatus.STARTED,
            startedAt = startedAt ?: now,
            updatedAt = now
        )
    }

    fun progress(now: Instant = Instant.now()): Task {
        TaskWorkflowDefinition.requireTransition(status, TaskStatus.IN_PROGRESS)
        return copy(
            status = TaskStatus.IN_PROGRESS,
            updatedAt = now
        )
    }

    fun wait(now: Instant = Instant.now()): Task {
        TaskWorkflowDefinition.requireTransition(status, TaskStatus.WAITING)
        return copy(
            status = TaskStatus.WAITING,
            updatedAt = now
        )
    }

    fun complete(now: Instant = Instant.now()): Task {
        TaskWorkflowDefinition.requireTransition(status, TaskStatus.COMPLETED)
        return copy(
            status = TaskStatus.COMPLETED,
            completedAt = completedAt ?: now,
            updatedAt = now
        )
    }

    fun cancel(now: Instant = Instant.now()): Task {
        TaskWorkflowDefinition.requireTransition(status, TaskStatus.CANCELLED)
        return copy(
            status = TaskStatus.CANCELLED,
            cancelledAt = cancelledAt ?: now,
            updatedAt = now
        )
    }

    fun markOverdue(now: Instant = Instant.now()): Task {
        require(!isTerminal()) { "Cannot mark terminal task as overdue" }
        require(now.isAfter(slaDeadline) || now == slaDeadline) {
            "Task is not overdue yet"
        }
        TaskWorkflowDefinition.requireTransition(status, TaskStatus.OVERDUE)

        return copy(
            status = TaskStatus.OVERDUE,
            overdueAt = overdueAt ?: now,
            updatedAt = now
        )
    }

    fun isTerminal(): Boolean =
        status == TaskStatus.COMPLETED || status == TaskStatus.CANCELLED

    fun isOverdue(now: Instant = Instant.now()): Boolean =
        !isTerminal() && now.isAfter(slaDeadline)

    companion object {
        fun create(
            hotelId: UUID,
            intentType: TaskIntentType,
            source: TaskSource,
            title: String,
            description: String,
            roomNumber: String? = null,
            priority: TaskPriority,
            slaDeadline: Instant,
            createdAt: Instant = Instant.now(),
            id: UUID = com.hotelopai.shared.kernel.UuidV7Generator.generate(createdAt)
        ): Task =
            Task(
                id = id,
                hotelId = hotelId,
                intentType = intentType,
                source = source,
                title = title,
                description = description,
                roomNumber = roomNumber,
                priority = priority,
                slaDeadline = slaDeadline,
                createdAt = createdAt,
                updatedAt = createdAt
            )
    }
}
