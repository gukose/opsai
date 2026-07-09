package com.hotelopai.task.domain

import java.time.Instant

data class TaskAssignment(
    val assigneeType: TaskAssigneeType,
    val assigneeId: String,
    val displayName: String,
    val assignedAt: Instant
) {
    init {
        require(assigneeId.isNotBlank()) { "assigneeId must not be blank" }
        require(displayName.isNotBlank()) { "displayName must not be blank" }
    }
}
