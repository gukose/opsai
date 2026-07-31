package com.hotelopai.reservation.recommendation

import com.hotelopai.scheduler.application.DistributedScheduledJobRunner
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Profile
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@Profile("local", "prod")
@EnableConfigurationProperties(ReservationTaskRecommendationProperties::class)
class ExternalRecommendationPilotScheduler(
    private val pilotService: ExternalRecommendationPilotService,
    private val scheduledJobRunner: DistributedScheduledJobRunner,
    private val properties: ReservationTaskRecommendationProperties
) {
    @Scheduled(
        initialDelayString = "\${ops.ai.reservation.task-recommendations.pilot-schedule.startup-delay:PT2M}",
        fixedDelayString = "\${ops.ai.reservation.task-recommendations.pilot-schedule.execution-interval:PT6H}"
    )
    fun run() {
        if (!properties.pilotSchedule.enabled) return
        scheduledJobRunner.runSingleton(ExternalRecommendationPilotService.PILOT_SCHEDULE_JOB_NAME, properties.pilotSchedule.lockTimeout) {
            pilotService.runScheduledPilot()
        }
    }

    @Scheduled(
        initialDelayString = "\${ops.ai.reservation.task-recommendations.pilot-schedule.startup-delay:PT2M}",
        fixedDelayString = "\${ops.ai.reservation.task-recommendations.pilot-schedule.cleanup-execution-interval:PT24H}"
    )
    fun cleanup() {
        if (!properties.pilotSchedule.retentionCleanupEnabled) return
        scheduledJobRunner.runSingleton(ExternalRecommendationPilotService.PILOT_CLEANUP_JOB_NAME, properties.pilotSchedule.lockTimeout) {
            pilotService.cleanupPilotRetention(actorUserId = null)
        }
    }
}
