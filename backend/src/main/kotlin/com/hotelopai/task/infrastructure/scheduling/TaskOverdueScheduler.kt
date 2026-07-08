package com.hotelopai.task.infrastructure.scheduling

import com.hotelopai.task.application.TaskOverdueService
import org.springframework.context.annotation.Profile
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@Profile("local", "prod")
class TaskOverdueScheduler(
    private val taskOverdueService: TaskOverdueService
) {
    @Scheduled(fixedDelayString = "\${ops.ai.task.overdue-check-interval-ms:300000}")
    fun run() {
        taskOverdueService.markOverdueTasks()
    }
}
