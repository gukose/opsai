package com.hotelopai.task.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Instant
import com.hotelopai.shared.kernel.UuidV7Generator

class TaskLifecycleTest {
    @Test
    fun `task follows valid lifecycle transitions`() {
        val createdAt = Instant.parse("2026-07-08T10:00:00Z")
        val task = Task.create(
            hotelId = UuidV7Generator.generate(createdAt),
            intentType = TaskIntentType.MAINTENANCE,
            source = TaskSource.ASSISTANT,
            title = "AC not working",
            description = "Room 101 AC not working",
            priority = TaskPriority.HIGH,
            slaDeadline = createdAt.plusSeconds(3600),
            createdAt = createdAt
        )

        val assigned = task.assign(
            TaskAssignment(
                assigneeType = TaskAssigneeType.TEAM,
                assigneeId = "maintenance",
                displayName = "Maintenance",
                assignedAt = createdAt
            ),
            createdAt
        )

        assertEquals(TaskStatus.ASSIGNED, assigned.status)

        val started = assigned.start(createdAt.plusSeconds(10))
        assertEquals(TaskStatus.STARTED, started.status)

        val inProgress = started.progress(createdAt.plusSeconds(20))
        assertEquals(TaskStatus.IN_PROGRESS, inProgress.status)

        val waiting = inProgress.wait(createdAt.plusSeconds(30))
        assertEquals(TaskStatus.WAITING, waiting.status)

        val completed = waiting.complete(createdAt.plusSeconds(40))
        assertEquals(TaskStatus.COMPLETED, completed.status)
    }

    @Test
    fun `invalid transitions are rejected`() {
        val createdAt = Instant.parse("2026-07-08T10:00:00Z")
        val task = Task.create(
            hotelId = UuidV7Generator.generate(createdAt),
            intentType = TaskIntentType.GUEST_REQUEST,
            source = TaskSource.MANUAL,
            title = "Extra towels",
            description = "Guest requested extra towels",
            priority = TaskPriority.MEDIUM,
            slaDeadline = createdAt.plusSeconds(3600),
            createdAt = createdAt
        )

        assertThrows(IllegalArgumentException::class.java) {
            task.complete(createdAt.plusSeconds(5))
        }
    }
}
