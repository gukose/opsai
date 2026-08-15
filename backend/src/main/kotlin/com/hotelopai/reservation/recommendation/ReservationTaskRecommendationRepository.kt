package com.hotelopai.reservation.recommendation

import java.time.Instant
import java.util.UUID

interface ReservationTaskRecommendationRepository {
    fun insert(recommendation: ReservationTaskRecommendation): ReservationTaskRecommendationInsertResult
    fun save(recommendation: ReservationTaskRecommendation): ReservationTaskRecommendation
    fun find(id: RecommendationId): ReservationTaskRecommendation?
    fun find(filter: RecommendationFilter): RecommendationPage
    fun findPilotReviewQueue(filter: RecommendationPilotReviewQueueFilter, now: Instant): RecommendationPage
    fun claimEligibleAutomationExecutions(now: Instant, batchSize: Int, createdAfter: Instant): List<RecommendationSourceExecution>
    fun retry(id: RecommendationId, now: Instant): ReservationTaskRecommendation
    fun saveRun(run: RecommendationGenerationRun): RecommendationGenerationRun
    fun findRun(id: RecommendationGenerationRunId): RecommendationGenerationRun?
    fun findRuns(filter: RecommendationGenerationRunFilter): RecommendationGenerationRunPage
    fun runCount(statuses: Set<RecommendationGenerationRunStatus>): Long
    fun getOrCreateScheduleState(scheduleId: String, now: Instant): RecommendationScheduleState
    fun markSchedulePaused(scheduleId: String, now: Instant): RecommendationScheduleState
    fun markScheduleResumed(scheduleId: String, now: Instant): RecommendationScheduleState
    fun recordScheduleAttempt(
        scheduleId: String,
        run: RecommendationGenerationRun?,
        now: Instant,
        failureCategory: RecommendationFailureCategory?
    ): RecommendationScheduleState
    fun eligibleCandidateBacklogCount(now: Instant, createdAfter: Instant): Long
    fun activeRecommendationCount(reservationId: UUID): Long
    fun unresolvedAutomationFailureExists(reservationId: UUID): Boolean
    fun expireEligibleRecommendations(now: Instant, olderThan: Instant, limit: Int): Int
    fun expirePilotRecommendations(now: Instant, limit: Int): Int
    fun cleanupTerminalRecords(
        runOlderThan: Instant,
        recommendationOlderThan: Instant,
        appliedOlderThan: Instant,
        limit: Int
    ): Int
}

sealed class ReservationTaskRecommendationInsertResult {
    data class Inserted(val recommendation: ReservationTaskRecommendation) : ReservationTaskRecommendationInsertResult()
    data class Duplicate(val existing: ReservationTaskRecommendation) : ReservationTaskRecommendationInsertResult()
}

data class RecommendationSourceExecution(
    val outboxEventId: UUID,
    val reservationId: UUID,
    val triggerEventType: String,
    val automationOutcome: String,
    val taskCreated: Boolean,
    val createdAt: Instant
)
