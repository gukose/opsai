package com.hotelopai.reservation.application

import com.hotelopai.pms.application.PmsFailureCategory
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

enum class ReservationSyncWindowStrategy {
    LOOKBACK_LOOKAHEAD
}

enum class ReservationSyncScheduleLeaseState {
    AVAILABLE,
    HELD,
    HELD_OR_UNKNOWN
}

data class ReservationSyncScheduleState(
    val scheduleId: String,
    val paused: Boolean,
    val pausedAt: Instant? = null,
    val resumedAt: Instant? = null,
    val lastAttemptedAt: Instant? = null,
    val lastSuccessfulAt: Instant? = null,
    val lastFailureCategory: PmsFailureCategory? = null,
    val lastRunId: ReservationSyncRunId? = null,
    val lastProcessedCount: Int = 0,
    val updatedAt: Instant
)

data class ReservationSyncScheduleStatus(
    val scheduleId: String,
    val enabled: Boolean,
    val paused: Boolean,
    val providerId: String,
    val propertyScopeLabel: String,
    val scheduleSummary: String,
    val timezone: ZoneId,
    val windowStartDate: LocalDate,
    val windowEndDate: LocalDate,
    val lastAttemptedAt: Instant?,
    val lastSuccessfulAt: Instant?,
    val nextExpectedExecutionAt: Instant?,
    val leaseState: ReservationSyncScheduleLeaseState,
    val lastFailureCategory: PmsFailureCategory?
)

interface ReservationSyncScheduleStateRepository {
    fun getOrCreate(scheduleId: String, now: Instant): ReservationSyncScheduleState
    fun markPaused(scheduleId: String, now: Instant): ReservationSyncScheduleState
    fun markResumed(scheduleId: String, now: Instant): ReservationSyncScheduleState
    fun recordAttempt(scheduleId: String, run: ReservationSyncRun?, now: Instant, failureCategory: PmsFailureCategory?): ReservationSyncScheduleState
    fun recordProcessingAttempt(scheduleId: String, processedCount: Int, success: Boolean, now: Instant, failureCategory: PmsFailureCategory?): ReservationSyncScheduleState
}

interface ReservationSyncScheduleLeaseStatusRepository {
    fun state(jobName: String, now: Instant): ReservationSyncScheduleLeaseState
}
