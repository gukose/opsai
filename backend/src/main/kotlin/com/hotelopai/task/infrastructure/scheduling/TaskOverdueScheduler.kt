package com.hotelopai.task.infrastructure.scheduling

import com.hotelopai.scheduler.application.DistributedScheduledJobRunner
import com.hotelopai.task.application.TaskOverdueService
import com.hotelopai.task.config.TaskSchedulerProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Profile
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@Profile("local", "prod")
@EnableConfigurationProperties(TaskSchedulerProperties::class)
class TaskOverdueScheduler(
    private val taskOverdueService: TaskOverdueService,
    private val scheduledJobRunner: DistributedScheduledJobRunner,
    private val properties: TaskSchedulerProperties
) {
    @Scheduled(fixedDelayString = "\${ops.ai.task.overdue-check-interval-ms:300000}")
    fun run() {
        if (properties.enabled) {
            scheduledJobRunner.runSingleton(OVERDUE_JOB_NAME, properties.normalizedLockTimeout()) {
                taskOverdueService.markOverdueTasks()
            }
        }
    }

    companion object {
        const val OVERDUE_JOB_NAME = "task_overdue_scheduler"
    }
}
