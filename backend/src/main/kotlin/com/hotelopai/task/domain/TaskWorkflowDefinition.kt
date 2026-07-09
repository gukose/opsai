package com.hotelopai.task.domain

import com.hotelopai.task.domain.TaskStatus
import com.hotelopai.workflow.domain.WorkflowStateMachine

object TaskWorkflowDefinition {
    private val machine = WorkflowStateMachine(
        allowedTransitions = mapOf(
            TaskStatus.CREATED to setOf(
                TaskStatus.ASSIGNED,
                TaskStatus.STARTED,
                TaskStatus.CANCELLED,
                TaskStatus.OVERDUE
            ),
            TaskStatus.ASSIGNED to setOf(
                TaskStatus.STARTED,
                TaskStatus.CANCELLED,
                TaskStatus.OVERDUE
            ),
            TaskStatus.STARTED to setOf(
                TaskStatus.IN_PROGRESS,
                TaskStatus.WAITING,
                TaskStatus.COMPLETED,
                TaskStatus.CANCELLED,
                TaskStatus.OVERDUE
            ),
            TaskStatus.IN_PROGRESS to setOf(
                TaskStatus.WAITING,
                TaskStatus.COMPLETED,
                TaskStatus.CANCELLED,
                TaskStatus.OVERDUE
            ),
            TaskStatus.WAITING to setOf(
                TaskStatus.IN_PROGRESS,
                TaskStatus.COMPLETED,
                TaskStatus.CANCELLED,
                TaskStatus.OVERDUE
            ),
            TaskStatus.OVERDUE to setOf(
                TaskStatus.ASSIGNED,
                TaskStatus.STARTED,
                TaskStatus.IN_PROGRESS,
                TaskStatus.WAITING,
                TaskStatus.COMPLETED,
                TaskStatus.CANCELLED
            ),
            TaskStatus.COMPLETED to emptySet(),
            TaskStatus.CANCELLED to emptySet()
        )
    )

    fun canTransition(from: TaskStatus, to: TaskStatus): Boolean =
        machine.canTransition(from, to)

    fun allowedTargets(from: TaskStatus): Set<TaskStatus> =
        machine.allowedTargets(from)

    fun requireTransition(from: TaskStatus, to: TaskStatus) {
        machine.requireTransition(from, to)
    }
}
