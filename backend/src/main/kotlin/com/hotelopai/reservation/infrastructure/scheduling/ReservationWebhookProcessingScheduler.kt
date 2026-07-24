package com.hotelopai.reservation.infrastructure.scheduling

import com.hotelopai.reservation.application.ReservationWebhookProcessingService
import com.hotelopai.reservation.application.ReservationWebhookProcessingService.Companion.WEBHOOK_CLEANUP_JOB_NAME
import com.hotelopai.reservation.application.ReservationWebhookProcessingService.Companion.WEBHOOK_PROCESSING_JOB_NAME
import com.hotelopai.reservation.application.ReservationWebhookScheduleProperties
import com.hotelopai.scheduler.application.DistributedScheduledJobRunner
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Profile
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@Profile("local", "prod")
@EnableConfigurationProperties(ReservationWebhookScheduleProperties::class)
class ReservationWebhookProcessingScheduler(
    private val processingService: ReservationWebhookProcessingService,
    private val scheduledJobRunner: DistributedScheduledJobRunner,
    private val properties: ReservationWebhookScheduleProperties
) {
    @Scheduled(
        initialDelayString = "\${ops.ai.reservation.webhooks.schedule.startup-delay:PT2M}",
        fixedDelayString = "\${ops.ai.reservation.webhooks.schedule.execution-interval:PT1M}"
    )
    fun run() {
        if (!properties.enabled) return
        scheduledJobRunner.runSingleton(WEBHOOK_PROCESSING_JOB_NAME, properties.lockTimeout) {
            processingService.processScheduledBatch()
        }
        if (properties.retentionCleanupEnabled) {
            scheduledJobRunner.runSingleton(WEBHOOK_CLEANUP_JOB_NAME, properties.cleanupLockTimeout) {
                processingService.cleanupScheduled()
            }
        }
    }
}
