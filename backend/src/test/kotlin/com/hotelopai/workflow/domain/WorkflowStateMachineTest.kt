package com.hotelopai.workflow.domain

import com.hotelopai.task.domain.TaskStatus
import com.hotelopai.workflow.task.TaskWorkflowDefinition
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class WorkflowStateMachineTest {
    @Test
    fun `task workflow allows only configured transitions`() {
        val expectedTransitions = mapOf(
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
            TaskStatus.COMPLETED to emptySet(),
            TaskStatus.CANCELLED to emptySet(),
            TaskStatus.OVERDUE to setOf(
                TaskStatus.ASSIGNED,
                TaskStatus.STARTED,
                TaskStatus.IN_PROGRESS,
                TaskStatus.WAITING,
                TaskStatus.COMPLETED,
                TaskStatus.CANCELLED
            )
        )

        TaskStatus.values().forEach { from ->
            TaskStatus.values().forEach { to ->
                val expected = to in expectedTransitions[from].orEmpty()
                assertEquals(
                    expected,
                    TaskWorkflowDefinition.canTransition(from, to),
                    "Unexpected workflow transition result for $from -> $to"
                )
            }
        }
    }

    @Test
    fun `workflow rejects invalid transitions with a clear error`() {
        val exception = assertThrows(IllegalArgumentException::class.java) {
            TaskWorkflowDefinition.requireTransition(TaskStatus.CREATED, TaskStatus.COMPLETED)
        }

        assertFalse(exception.message.isNullOrBlank())
    }
}
