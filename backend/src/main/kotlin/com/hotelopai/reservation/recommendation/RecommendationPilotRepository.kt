package com.hotelopai.reservation.recommendation

import java.time.Instant
import java.time.LocalDate

interface RecommendationPilotRepository {
    fun saveRun(run: RecommendationPilotRun): RecommendationPilotRun
    fun findRun(id: RecommendationPilotRunId): RecommendationPilotRun?
    fun findRuns(filter: RecommendationPilotRunFilter): RecommendationPilotRunPage
    fun budgetStatus(
        providerId: RecommendationProviderId,
        budgetDate: LocalDate,
        requestLimit: Int,
        recommendationLimit: Int,
        tokenLimit: Long?
    ): RecommendationPilotBudgetStatus
    fun reserveRequest(
        providerId: RecommendationProviderId,
        budgetDate: LocalDate,
        requestLimit: Int,
        tokenLimit: Long?,
        expectedTokens: Long,
        now: Instant
    ): Boolean
    fun recordGeneratedRecommendations(providerId: RecommendationProviderId, budgetDate: LocalDate, count: Int, now: Instant)
    fun releaseFailedRequest(providerId: RecommendationProviderId, budgetDate: LocalDate, now: Instant)
    fun getOrCreateState(stateId: String, now: Instant): RecommendationPilotState
    fun disable(stateId: String, now: Instant): RecommendationPilotState
    fun rollback(stateId: String, now: Instant): RecommendationPilotState
    fun pauseSchedule(stateId: String, now: Instant): RecommendationPilotState
    fun resumeSchedule(stateId: String, now: Instant): RecommendationPilotState
    fun recordScheduleAttempt(stateId: String, run: RecommendationPilotRun?, budgetRejections: Int, now: Instant): RecommendationPilotState
    fun scheduledRunCount(providerId: RecommendationProviderId, budgetDate: LocalDate): Long
    fun cleanupPilotRuns(completedBefore: Instant, limit: Int): Int
    fun analytics(filter: RecommendationPilotAnalyticsFilter, now: Instant): RecommendationPilotAnalytics
}
