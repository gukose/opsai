package com.hotelopai.reservation.automation

import com.hotelopai.outbox.domain.OperationalOutboxEvent
import java.time.Instant
import java.util.UUID

interface ReservationTaskAutomationRepository {
    fun insertExecution(execution: ReservationTaskAutomationExecution): ReservationTaskAutomationInsertResult
    fun saveExecution(execution: ReservationTaskAutomationExecution): ReservationTaskAutomationExecution
    fun findExecution(id: ReservationTaskAutomationExecutionId): ReservationTaskAutomationExecution?
    fun findExecutions(filter: ReservationTaskAutomationExecutionFilter): ReservationTaskAutomationExecutionPage
    fun findExecutionByDeduplicationKey(deduplicationKey: String): ReservationTaskAutomationExecution?
    fun claimReservationEvents(now: Instant, batchSize: Int, processorId: String): List<OperationalOutboxEvent>
    fun markOutboxCompleted(id: UUID, now: Instant)
    fun markOutboxRetryable(id: UUID, attemptCount: Int, nextAttemptAt: Instant, failureCode: String, now: Instant)
    fun markOutboxFailed(id: UUID, attemptCount: Int, failureCode: String, now: Instant)
    fun retryExecution(id: ReservationTaskAutomationExecutionId, now: Instant): ReservationTaskAutomationExecution
    fun backlogCount(now: Instant): Long
    fun executionCount(outcomes: Set<ReservationTaskAutomationOutcome>): Long
}

sealed class ReservationTaskAutomationInsertResult {
    data class Inserted(val execution: ReservationTaskAutomationExecution) : ReservationTaskAutomationInsertResult()
    data class Duplicate(val existing: ReservationTaskAutomationExecution) : ReservationTaskAutomationInsertResult()
}

interface ReservationTaskAutomationScheduleStateRepository {
    fun getOrCreate(scheduleId: String, now: Instant): ReservationTaskAutomationScheduleState
    fun markPaused(scheduleId: String, now: Instant): ReservationTaskAutomationScheduleState
    fun markResumed(scheduleId: String, now: Instant): ReservationTaskAutomationScheduleState
    fun recordAttempt(
        scheduleId: String,
        summary: ReservationTaskAutomationBatchSummary?,
        now: Instant,
        failureCategory: ReservationTaskAutomationSkipReason?
    ): ReservationTaskAutomationScheduleState
}
