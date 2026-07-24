package com.hotelopai.reservation.application

import com.hotelopai.pms.application.PmsFailureCategory
import java.time.Instant

data class ReservationWebhookScheduleStatus(
    val scheduleId: String,
    val configuredEnabled: Boolean,
    val effectiveEnabled: Boolean,
    val paused: Boolean,
    val scheduleSummary: String,
    val batchSize: Int,
    val maxRecordsPerExecution: Int,
    val lastAttemptedAt: Instant?,
    val lastSuccessfulAt: Instant?,
    val nextExpectedExecutionAt: Instant?,
    val lastProcessedCount: Int,
    val lastFailureCategory: PmsFailureCategory?,
    val leaseState: ReservationSyncScheduleLeaseState,
    val backlogCounts: ReservationWebhookBacklogCounts
)
