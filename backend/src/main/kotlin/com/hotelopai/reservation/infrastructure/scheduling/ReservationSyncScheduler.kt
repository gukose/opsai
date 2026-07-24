package com.hotelopai.reservation.infrastructure.scheduling

import com.hotelopai.reservation.application.ReservationSyncOperationsService
import com.hotelopai.reservation.application.ReservationSyncOperationsService.Companion.SCHEDULED_SYNC_JOB_NAME
import com.hotelopai.reservation.application.ReservationSyncScheduleProperties
import com.hotelopai.scheduler.application.DistributedScheduledJobRunner
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Profile
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@Profile("local", "prod")
@EnableConfigurationProperties(ReservationSyncScheduleProperties::class)
class ReservationSyncScheduler(
    private val operationsService: ReservationSyncOperationsService,
    private val scheduledJobRunner: DistributedScheduledJobRunner,
    private val properties: ReservationSyncScheduleProperties
) {
    @Scheduled(
        initialDelayString = "\${ops.ai.reservation.sync.schedule.startup-delay:PT2M}",
        fixedDelayString = "\${ops.ai.reservation.sync.schedule.execution-interval:PT30M}"
    )
    fun run() {
        if (!properties.enabled) return
        scheduledJobRunner.runSingleton(SCHEDULED_SYNC_JOB_NAME, properties.lockTimeout) {
            repeat(properties.maxRunsPerExecution) {
                operationsService.runScheduledPolicy()
            }
            if (properties.retentionCleanupEnabled) {
                operationsService.cleanupCompletedRuns(actorUserId = null, limit = properties.retentionCleanupMaxRuns)
            }
        }
    }
}
