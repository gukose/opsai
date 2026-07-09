package com.hotelopai.task.infrastructure.persistence

import com.hotelopai.task.application.TaskLogEntry
import com.hotelopai.task.application.TaskLogOutcome
import com.hotelopai.task.application.TaskStateHistoryEntry
import com.hotelopai.task.domain.Task
import com.hotelopai.task.domain.TaskAssignment
import com.hotelopai.task.domain.TaskTransition
import com.hotelopai.shared.kernel.UuidV7Generator

internal object TaskPersistenceMapper {
    fun toDomain(entity: TaskJpaEntity): Task =
        Task(
            id = requireNotNull(entity.id) { "task.id must not be null" },
            hotelId = requireNotNull(entity.hotelId) { "task.hotelId must not be null" },
            intentType = entity.intentType,
            source = entity.source,
            title = entity.title,
            description = entity.description,
            roomNumber = entity.roomNumber?.trim()?.takeIf { it.isNotBlank() },
            priority = entity.priority,
            slaDeadline = requireNotNull(entity.slaDeadline) { "task.slaDeadline must not be null" },
            status = entity.status,
            assignment = if (
                entity.assigneeType != null &&
                entity.assigneeId != null &&
                entity.assigneeDisplayName != null &&
                entity.assignedAt != null
            ) {
                TaskAssignment(
                    assigneeType = entity.assigneeType!!,
                    assigneeId = entity.assigneeId!!,
                    displayName = entity.assigneeDisplayName!!,
                    assignedAt = entity.assignedAt!!
                )
            } else {
                null
            },
            createdAt = requireNotNull(entity.createdAt) { "task.createdAt must not be null" },
            updatedAt = requireNotNull(entity.updatedAt) { "task.updatedAt must not be null" },
            startedAt = entity.startedAt,
            completedAt = entity.completedAt,
            cancelledAt = entity.cancelledAt,
            overdueAt = entity.overdueAt
        )

    fun toEntity(domain: Task): TaskJpaEntity =
        TaskJpaEntity().apply {
            id = domain.id
            updateFrom(domain)
        }

    fun updateEntity(entity: TaskJpaEntity, domain: Task): TaskJpaEntity =
        entity.apply { updateFrom(domain) }

    private fun TaskJpaEntity.updateFrom(domain: Task) {
        hotelId = domain.hotelId
        intentType = domain.intentType
        source = domain.source
        title = domain.title
        description = domain.description
        roomNumber = domain.roomNumber?.trim()?.takeIf { it.isNotBlank() }
        priority = domain.priority
        status = domain.status
        slaDeadline = domain.slaDeadline
        assigneeType = domain.assignment?.assigneeType
        assigneeId = domain.assignment?.assigneeId
        assigneeDisplayName = domain.assignment?.displayName
        assignedAt = domain.assignment?.assignedAt
        startedAt = domain.startedAt
        completedAt = domain.completedAt
        cancelledAt = domain.cancelledAt
        overdueAt = domain.overdueAt
        createdAt = domain.createdAt
        updatedAt = domain.updatedAt
    }
}

internal object TaskStateHistoryPersistenceMapper {
    fun toEntity(entry: TaskStateHistoryEntry): TaskStateHistoryJpaEntity =
        TaskStateHistoryJpaEntity().apply {
            id = UuidV7Generator.generate(entry.occurredAt)
            taskId = entry.taskId
            hotelId = entry.hotelId
            fromStatus = entry.fromStatus
            toStatus = entry.toStatus
            operation = entry.operation
            note = entry.note
            correlationId = entry.correlationId
            createdAt = entry.occurredAt
            updatedAt = entry.occurredAt
        }
}

internal object TaskLogPersistenceMapper {
    fun toEntity(entry: TaskLogEntry): TaskLogJpaEntity =
        TaskLogJpaEntity().apply {
            id = UuidV7Generator.generate(entry.occurredAt)
            taskId = entry.taskId
            hotelId = entry.hotelId
            operation = entry.operation
            outcome = if (entry.outcome == TaskLogOutcome.SUCCESS) {
                TaskLogOutcomeJpa.SUCCESS
            } else {
                TaskLogOutcomeJpa.FAILED
            }
            message = entry.message
            fromStatus = entry.fromStatus
            toStatus = entry.toStatus
            correlationId = entry.correlationId
            createdAt = entry.occurredAt
            updatedAt = entry.occurredAt
        }
}
