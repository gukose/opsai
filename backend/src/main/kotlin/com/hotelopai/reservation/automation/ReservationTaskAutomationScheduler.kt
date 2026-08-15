package com.hotelopai.reservation.automation

import com.hotelopai.scheduler.application.DistributedScheduledJobRunner
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Profile
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@Profile("local", "demo", "prod")
@EnableConfigurationProperties(ReservationTaskAutomationProperties::class)
class ReservationTaskAutomationScheduler(
    private val automationService: ReservationTaskAutomationService,
    private val scheduledJobRunner: DistributedScheduledJobRunner,
    private val properties: ReservationTaskAutomationProperties
) {
    @Scheduled(
        initialDelayString = "\${ops.ai.reservation.task-automation.schedule.startup-delay:PT2M}",
        fixedDelayString = "\${ops.ai.reservation.task-automation.schedule.execution-interval:PT1M}"
    )
    fun run() {
        if (!properties.schedule.enabled) return
        scheduledJobRunner.runSingleton(ReservationTaskAutomationService.SCHEDULE_JOB_NAME, properties.schedule.lockTimeout) {
            automationService.processScheduledBatch()
        }
    }
}
