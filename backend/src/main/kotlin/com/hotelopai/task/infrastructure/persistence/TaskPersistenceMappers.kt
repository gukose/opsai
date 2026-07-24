package com.hotelopai.task.infrastructure.persistence

import com.hotelopai.task.application.TaskLogEntry
import com.hotelopai.task.application.TaskLogOutcome
import com.hotelopai.task.application.TaskStateHistoryEntry
import com.hotelopai.task.domain.Task
import com.hotelopai.task.domain.TaskAssignment
import com.hotelopai.task.domain.TaskTransition
import com.hotelopai.shared.kernel.PersistenceInstant
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
        val normalized = domain.normalizedForPersistence()
        hotelId = normalized.hotelId
        intentType = normalized.intentType
        source = normalized.source
        title = normalized.title
        description = normalized.description
        roomNumber = normalized.roomNumber?.trim()?.takeIf { it.isNotBlank() }
        priority = normalized.priority
        status = normalized.status
        slaDeadline = domain.slaDeadline
        assigneeType = normalized.assignment?.assigneeType
        assigneeId = normalized.assignment?.assigneeId
        assigneeDisplayName = normalized.assignment?.displayName
        assignedAt = normalized.assignment?.assignedAt
        startedAt = normalized.startedAt
        completedAt = normalized.completedAt
        cancelledAt = normalized.cancelledAt
        overdueAt = normalized.overdueAt
        createdAt = normalized.createdAt
        updatedAt = normalized.updatedAt
    }

    private fun Task.normalizedForPersistence(): Task =
        copy(
            assignment = assignment?.copy(
                assignedAt = PersistenceInstant.toPersistencePrecision(assignment.assignedAt)
            ),
            createdAt = PersistenceInstant.toPersistencePrecision(createdAt),
            updatedAt = PersistenceInstant.toPersistencePrecision(updatedAt),
            startedAt = PersistenceInstant.toPersistencePrecisionOrNull(startedAt),
            completedAt = PersistenceInstant.toPersistencePrecisionOrNull(completedAt),
            cancelledAt = PersistenceInstant.toPersistencePrecisionOrNull(cancelledAt),
            overdueAt = PersistenceInstant.toPersistencePrecisionOrNull(overdueAt)
        )
}

internal object TaskStateHistoryPersistenceMapper {
    fun toEntity(entry: TaskStateHistoryEntry): TaskStateHistoryJpaEntity =
        TaskStateHistoryJpaEntity().apply {
            val occurredAt = PersistenceInstant.toPersistencePrecision(entry.occurredAt)
            id = UuidV7Generator.generate(occurredAt)
            taskId = entry.taskId
            hotelId = entry.hotelId
            fromStatus = entry.fromStatus
            toStatus = entry.toStatus
            operation = entry.operation
            note = entry.note
            correlationId = entry.correlationId
            createdAt = occurredAt
            updatedAt = occurredAt
        }
}

internal object TaskLogPersistenceMapper {
    fun toEntity(entry: TaskLogEntry): TaskLogJpaEntity =
        TaskLogJpaEntity().apply {
            val occurredAt = PersistenceInstant.toPersistencePrecision(entry.occurredAt)
            id = UuidV7Generator.generate(occurredAt)
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
            createdAt = occurredAt
            updatedAt = occurredAt
        }
}
