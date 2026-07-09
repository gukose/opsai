package com.hotelopai.task.api

import com.hotelopai.task.application.AssignTaskCommand
import com.hotelopai.task.application.AssignmentCommand
import com.hotelopai.task.application.CreateTaskCommand
import com.hotelopai.task.domain.Task
import com.hotelopai.task.domain.TaskAssigneeType
import com.hotelopai.task.domain.TaskIntentType
import com.hotelopai.task.domain.TaskPriority
import com.hotelopai.task.domain.TaskSource
import com.hotelopai.task.domain.TaskStatus
import java.time.Instant
import java.util.UUID

data class CreateTaskRequest(
    val hotelId: String,
    val intentType: TaskIntentType,
    val source: TaskSource,
    val title: String,
    val description: String,
    val priority: TaskPriority,
    val slaDeadline: Instant,
    val assignment: AssignmentRequest? = null
) {
    fun toCommand(): CreateTaskCommand =
        CreateTaskCommand(
            hotelId = UUID.fromString(hotelId),
            intentType = intentType,
            source = source,
            title = title,
            description = description,
            priority = priority,
            slaDeadline = slaDeadline,
            assignment = assignment?.toCommand()
        )
}

data class AssignTaskRequest(
    val assigneeType: TaskAssigneeType,
    val assigneeId: String,
    val displayName: String
) {
    fun toCommand(): AssignTaskCommand =
        AssignTaskCommand(
            assignment = AssignmentCommand(
                assigneeType = assigneeType,
                assigneeId = assigneeId,
                displayName = displayName
            )
        )
}

data class AssignmentRequest(
    val assigneeType: TaskAssigneeType,
    val assigneeId: String,
    val displayName: String
) {
    fun toCommand(): AssignmentCommand =
        AssignmentCommand(
            assigneeType = assigneeType,
            assigneeId = assigneeId,
            displayName = displayName
        )
}

data class TaskResponse(
    val id: String,
    val hotelId: String,
    val intentType: String,
    val source: String,
    val title: String,
    val description: String,
    val priority: String,
    val status: String,
    val slaDeadline: Instant,
    val assignment: TaskAssignmentResponse?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val startedAt: Instant?,
    val completedAt: Instant?,
    val cancelledAt: Instant?,
    val overdueAt: Instant?
) {
    companion object {
        fun from(task: Task): TaskResponse =
            TaskResponse(
                id = task.id.toString(),
                hotelId = task.hotelId.toString(),
                intentType = task.intentType.name,
                source = task.source.name,
                title = task.title,
                description = task.description,
                priority = task.priority.name,
                status = task.status.name,
                slaDeadline = task.slaDeadline,
                assignment = task.assignment?.let { TaskAssignmentResponse.from(it) },
                createdAt = task.createdAt,
                updatedAt = task.updatedAt,
                startedAt = task.startedAt,
                completedAt = task.completedAt,
                cancelledAt = task.cancelledAt,
                overdueAt = task.overdueAt
            )
    }
}

data class TaskAssignmentResponse(
    val assigneeType: String,
    val assigneeId: String,
    val displayName: String,
    val assignedAt: Instant
) {
    companion object {
        fun from(assignment: com.hotelopai.task.domain.TaskAssignment): TaskAssignmentResponse =
            TaskAssignmentResponse(
                assigneeType = assignment.assigneeType.name,
                assigneeId = assignment.assigneeId,
                displayName = assignment.displayName,
                assignedAt = assignment.assignedAt
            )
    }
}
