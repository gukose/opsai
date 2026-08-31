package com.hotelopai.task.application

import com.hotelopai.task.domain.Task
import com.hotelopai.task.domain.TaskIntentType

/** Central decision for work that must be accepted by an authorized inspector. */
object TaskCompletionInspectionPolicy {
    fun requiresInspection(task: Task, hasLinkedWorkflow: Boolean): Boolean =
        hasLinkedWorkflow && task.intentType == TaskIntentType.HOUSEKEEPING
}
