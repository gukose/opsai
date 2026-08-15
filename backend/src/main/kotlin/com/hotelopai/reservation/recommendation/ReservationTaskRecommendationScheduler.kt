package com.hotelopai.reservation.recommendation

import com.hotelopai.scheduler.application.DistributedScheduledJobRunner
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Profile
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@Profile("local", "demo", "prod")
@EnableConfigurationProperties(ReservationTaskRecommendationProperties::class)
class ReservationTaskRecommendationScheduler(
    private val recommendationService: ReservationTaskRecommendationService,
    private val scheduledJobRunner: DistributedScheduledJobRunner,
    private val properties: ReservationTaskRecommendationProperties
) {
    @Scheduled(
        initialDelayString = "\${ops.ai.reservation.task-recommendations.schedule.startup-delay:PT2M}",
        fixedDelayString = "\${ops.ai.reservation.task-recommendations.schedule.execution-interval:PT5M}"
    )
    fun run() {
        if (!properties.schedule.enabled) return
        scheduledJobRunner.runSingleton(ReservationTaskRecommendationService.SCHEDULE_JOB_NAME, properties.schedule.lockTimeout) {
            recommendationService.processScheduledBatch()
        }
    }

    @Scheduled(
        initialDelayString = "\${ops.ai.reservation.task-recommendations.schedule.startup-delay:PT2M}",
        fixedDelayString = "\${ops.ai.reservation.task-recommendations.schedule.cleanup-execution-interval:PT12H}"
    )
    fun cleanup() {
        if (!properties.schedule.retentionCleanupEnabled) return
        scheduledJobRunner.runSingleton(ReservationTaskRecommendationService.CLEANUP_JOB_NAME, properties.schedule.lockTimeout) {
            recommendationService.cleanupRetention(null)
        }
    }
}
