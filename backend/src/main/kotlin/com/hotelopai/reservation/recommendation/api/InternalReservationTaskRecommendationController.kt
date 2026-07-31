package com.hotelopai.reservation.recommendation.api

import com.hotelopai.reservation.recommendation.RecommendationCategory
import com.hotelopai.reservation.recommendation.RecommendationConfidence
import com.hotelopai.reservation.recommendation.RecommendationEndpointClassification
import com.hotelopai.reservation.recommendation.ExternalRecommendationProviderSmokeService
import com.hotelopai.reservation.recommendation.ExternalRecommendationPilotService
import com.hotelopai.reservation.recommendation.RecommendationFilter
import com.hotelopai.reservation.recommendation.RecommendationGenerationRun
import com.hotelopai.reservation.recommendation.RecommendationGenerationRunFilter
import com.hotelopai.reservation.recommendation.RecommendationGenerationRunId
import com.hotelopai.reservation.recommendation.RecommendationGenerationRunPage
import com.hotelopai.reservation.recommendation.RecommendationGenerationRunStatus
import com.hotelopai.reservation.recommendation.RecommendationGenerationSummary
import com.hotelopai.reservation.recommendation.RecommendationGenerationTrigger
import com.hotelopai.reservation.recommendation.RecommendationId
import com.hotelopai.reservation.recommendation.RecommendationPilotAnalytics
import com.hotelopai.reservation.recommendation.RecommendationPilotAnalyticsFilter
import com.hotelopai.reservation.recommendation.RecommendationPilotBreakdown
import com.hotelopai.reservation.recommendation.RecommendationPage
import com.hotelopai.reservation.recommendation.RecommendationPilotBudgetStatus
import com.hotelopai.reservation.recommendation.RecommendationPilotReadiness
import com.hotelopai.reservation.recommendation.RecommendationPilotRun
import com.hotelopai.reservation.recommendation.RecommendationPilotRunFilter
import com.hotelopai.reservation.recommendation.RecommendationPilotRunId
import com.hotelopai.reservation.recommendation.RecommendationPilotRunPage
import com.hotelopai.reservation.recommendation.RecommendationPilotRunStatus
import com.hotelopai.reservation.recommendation.RecommendationPilotRunSummary
import com.hotelopai.reservation.recommendation.RecommendationPilotScheduleStatus
import com.hotelopai.reservation.recommendation.RecommendationPilotState
import com.hotelopai.reservation.recommendation.RecommendationPilotTrigger
import com.hotelopai.reservation.recommendation.RecommendationProviderDiagnostic
import com.hotelopai.reservation.recommendation.RecommendationProviderDiagnosticFilter
import com.hotelopai.reservation.recommendation.RecommendationProviderDiagnosticId
import com.hotelopai.reservation.recommendation.RecommendationProviderDiagnosticOutcome
import com.hotelopai.reservation.recommendation.RecommendationProviderDiagnosticPage
import com.hotelopai.reservation.recommendation.RecommendationProviderId
import com.hotelopai.reservation.recommendation.RecommendationProviderReadiness
import com.hotelopai.reservation.recommendation.RecommendationProviderSummary
import com.hotelopai.reservation.recommendation.RecommendationScheduleStatus
import com.hotelopai.reservation.recommendation.RecommendationSmokeFixtureMode
import com.hotelopai.reservation.recommendation.RecommendationSmokeTestResult
import com.hotelopai.reservation.recommendation.RecommendationStatus
import com.hotelopai.reservation.recommendation.ReservationTaskRecommendation
import com.hotelopai.reservation.recommendation.ReservationTaskRecommendationNotFoundException
import com.hotelopai.reservation.recommendation.ReservationTaskRecommendationRejectedException
import com.hotelopai.reservation.recommendation.ReservationTaskRecommendationService
import com.hotelopai.shared.security.CurrentUserContextResolver
import com.hotelopai.shared.security.PermissionExpressions
import com.hotelopai.task.domain.TaskIntentType
import com.hotelopai.task.domain.TaskPriority
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/api/v1/internal/reservations/task-recommendations")
class InternalReservationTaskRecommendationController(
    private val service: ReservationTaskRecommendationService,
    private val smokeService: ExternalRecommendationProviderSmokeService,
    private val pilotService: ExternalRecommendationPilotService,
    private val currentUserContextResolver: CurrentUserContextResolver
) {
    @GetMapping
    @PreAuthorize(PermissionExpressions.RESERVATION_SYNC_OPERATIONS)
    fun list(
        @RequestParam(required = false) status: RecommendationStatus?,
        @RequestParam(required = false) category: RecommendationCategory?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): RecommendationPageResponse =
        service.list(RecommendationFilter(status = status, category = category, page = page, size = size)).toResponse()

    @GetMapping("/{recommendationId}")
    @PreAuthorize(PermissionExpressions.RESERVATION_SYNC_OPERATIONS)
    fun detail(@PathVariable recommendationId: UUID): RecommendationResponse =
        safely { service.detail(RecommendationId(recommendationId)).toResponse() }

    @PostMapping("/generate-batch")
    @PreAuthorize(PermissionExpressions.RESERVATION_SYNC_OPERATIONS)
    fun generateBatch(): RecommendationGenerationSummaryResponse =
        safely { service.generateBatch(actorUserId()).toResponse() }

    @GetMapping("/providers")
    @PreAuthorize(PermissionExpressions.RESERVATION_SYNC_OPERATIONS)
    fun providers(): List<RecommendationProviderResponse> =
        safely { service.providerStatus(actorUserId()).map { it.toResponse() } }

    @GetMapping("/providers/readiness")
    @PreAuthorize(PermissionExpressions.RESERVATION_SYNC_OPERATIONS)
    fun providerReadiness(@RequestParam(required = false) providerId: String?): List<RecommendationProviderReadinessResponse> =
        safely {
            if (providerId.isNullOrBlank()) {
                smokeService.readiness(actorUserId()).map { it.toResponse() }
            } else {
                listOf(smokeService.readiness(RecommendationProviderId(providerId), actorUserId()).toResponse())
            }
        }

    @PostMapping("/providers/smoke-test")
    @PreAuthorize(PermissionExpressions.RESERVATION_SYNC_OPERATIONS)
    fun smokeTest(@RequestBody request: RecommendationSmokeTestRequest): RecommendationSmokeTestResponse =
        safely {
            smokeService.runSmokeTest(
                providerId = RecommendationProviderId(request.providerId),
                fixtureMode = request.fixtureMode,
                actorUserId = actorUserId()
            ).toResponse(request.expectedOutcome)
        }

    @GetMapping("/providers/diagnostics")
    @PreAuthorize(PermissionExpressions.RESERVATION_SYNC_OPERATIONS)
    fun providerDiagnostics(
        @RequestParam(required = false) providerId: String?,
        @RequestParam(required = false) outcome: RecommendationProviderDiagnosticOutcome?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): RecommendationProviderDiagnosticPageResponse =
        safely {
            smokeService.diagnostics(
                RecommendationProviderDiagnosticFilter(
                    providerId = providerId?.takeIf { it.isNotBlank() }?.let(::RecommendationProviderId),
                    outcome = outcome,
                    page = page,
                    size = size
                ),
                actorUserId()
            ).toResponse()
        }

    @GetMapping("/providers/diagnostics/{diagnosticId}")
    @PreAuthorize(PermissionExpressions.RESERVATION_SYNC_OPERATIONS)
    fun providerDiagnostic(@PathVariable diagnosticId: UUID): RecommendationProviderDiagnosticResponse =
        safely { smokeService.diagnostic(RecommendationProviderDiagnosticId(diagnosticId), actorUserId()).toResponse() }

    @PostMapping("/providers/diagnostics/cleanup")
    @PreAuthorize(PermissionExpressions.RESERVATION_SYNC_OPERATIONS)
    fun cleanupProviderDiagnostics(): RecommendationMaintenanceResponse =
        safely { RecommendationMaintenanceResponse(smokeService.cleanupDiagnostics(actorUserId())) }

    @GetMapping("/pilot/readiness")
    @PreAuthorize(PermissionExpressions.RESERVATION_SYNC_OPERATIONS)
    fun pilotReadiness(@RequestParam(required = false) providerId: String?): RecommendationPilotReadinessResponse =
        safely { pilotService.readiness(providerId?.let(::RecommendationProviderId) ?: RecommendationProviderId("openai"), actorUserId()).toResponse() }

    @PostMapping("/pilot/run")
    @PreAuthorize(PermissionExpressions.RESERVATION_SYNC_OPERATIONS)
    fun runPilot(@RequestBody request: RecommendationPilotRunRequest): RecommendationPilotRunSummaryResponse =
        safely { pilotService.runPilot(RecommendationProviderId(request.providerId), actorUserId()).toResponse() }

    @GetMapping("/pilot/runs")
    @PreAuthorize(PermissionExpressions.RESERVATION_SYNC_OPERATIONS)
    fun pilotRuns(
        @RequestParam(required = false) status: RecommendationPilotRunStatus?,
        @RequestParam(required = false) trigger: RecommendationPilotTrigger?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): RecommendationPilotRunPageResponse =
        safely { pilotService.runs(RecommendationPilotRunFilter(status, trigger, page, size), actorUserId()).toResponse() }

    @GetMapping("/pilot/runs/{runId}")
    @PreAuthorize(PermissionExpressions.RESERVATION_SYNC_OPERATIONS)
    fun pilotRun(@PathVariable runId: UUID): RecommendationPilotRunResponse =
        safely { pilotService.run(RecommendationPilotRunId(runId), actorUserId()).toResponse() }

    @GetMapping("/pilot/budget")
    @PreAuthorize(PermissionExpressions.RESERVATION_SYNC_OPERATIONS)
    fun pilotBudget(@RequestParam(required = false) providerId: String?): RecommendationPilotBudgetResponse =
        safely { pilotService.budgetStatus(providerId?.let(::RecommendationProviderId) ?: RecommendationProviderId("openai"), actorUserId()).toResponse() }

    @GetMapping("/pilot/schedule")
    @PreAuthorize(PermissionExpressions.RESERVATION_SYNC_OPERATIONS)
    fun pilotScheduleStatus(): RecommendationPilotScheduleStatusResponse =
        safely { pilotService.scheduleStatus(actorUserId()).toResponse() }

    @PostMapping("/pilot/schedule/run-now")
    @PreAuthorize(PermissionExpressions.RESERVATION_SYNC_OPERATIONS)
    fun runPilotScheduleNow(): RecommendationPilotRunSummaryResponse =
        safely { pilotService.runPilotScheduleNow(actorUserId()).toResponse() }

    @PostMapping("/pilot/schedule/pause")
    @PreAuthorize(PermissionExpressions.RESERVATION_SYNC_OPERATIONS)
    fun pausePilotSchedule(): RecommendationPilotScheduleStatusResponse =
        safely { pilotService.pauseSchedule(actorUserId()).toResponse() }

    @PostMapping("/pilot/schedule/resume")
    @PreAuthorize(PermissionExpressions.RESERVATION_SYNC_OPERATIONS)
    fun resumePilotSchedule(): RecommendationPilotScheduleStatusResponse =
        safely { pilotService.resumeSchedule(actorUserId()).toResponse() }

    @GetMapping("/pilot/analytics")
    @PreAuthorize(PermissionExpressions.RESERVATION_SYNC_OPERATIONS)
    fun pilotAnalytics(
        @RequestParam(required = false) generatedFrom: Instant?,
        @RequestParam(required = false) generatedTo: Instant?,
        @RequestParam(required = false) providerId: String?,
        @RequestParam(required = false) modelIdentifier: String?,
        @RequestParam(required = false) category: RecommendationCategory?,
        @RequestParam(required = false) confidence: RecommendationConfidence?,
        @RequestParam(required = false) status: RecommendationStatus?,
        @RequestParam(required = false) pilotRunId: UUID?
    ): RecommendationPilotAnalyticsResponse =
        safely {
            pilotService.analytics(
                RecommendationPilotAnalyticsFilter(
                    generatedFrom = generatedFrom,
                    generatedTo = generatedTo,
                    providerId = providerId?.takeIf { it.isNotBlank() }?.let(::RecommendationProviderId),
                    modelIdentifier = modelIdentifier?.takeIf { it.isNotBlank() },
                    category = category,
                    confidence = confidence,
                    status = status,
                    pilotRunId = pilotRunId?.let(::RecommendationPilotRunId)
                ),
                actorUserId()
            ).toResponse()
        }

    @GetMapping("/pilot/analytics/review-outcomes")
    @PreAuthorize(PermissionExpressions.RESERVATION_SYNC_OPERATIONS)
    fun pilotReviewOutcomeBreakdown(): List<RecommendationPilotBreakdownResponse> =
        safely { pilotService.analytics(RecommendationPilotAnalyticsFilter(), actorUserId()).reviewOutcomes.map { it.toResponse() } }

    @GetMapping("/pilot/analytics/confidence-category")
    @PreAuthorize(PermissionExpressions.RESERVATION_SYNC_OPERATIONS)
    fun pilotConfidenceCategoryBreakdown(): RecommendationPilotConfidenceCategoryResponse =
        safely {
            val analytics = pilotService.analytics(RecommendationPilotAnalyticsFilter(), actorUserId())
            RecommendationPilotConfidenceCategoryResponse(
                confidenceDistribution = analytics.confidenceDistribution.map { it.toResponse() },
                categoryDistribution = analytics.categoryDistribution.map { it.toResponse() }
            )
        }

    @GetMapping("/pilot/analytics/runs")
    @PreAuthorize(PermissionExpressions.RESERVATION_SYNC_OPERATIONS)
    fun pilotRunAnalytics(@RequestParam pilotRunId: UUID): RecommendationPilotAnalyticsResponse =
        safely { pilotService.analytics(RecommendationPilotAnalyticsFilter(pilotRunId = RecommendationPilotRunId(pilotRunId)), actorUserId()).toResponse() }

    @PostMapping("/pilot/disable")
    @PreAuthorize(PermissionExpressions.RESERVATION_SYNC_OPERATIONS)
    fun disablePilot(): RecommendationPilotStateResponse =
        safely { pilotService.disableFutureRuns(actorUserId()).toResponse() }

    @PostMapping("/pilot/rollback-to-internal-demo")
    @PreAuthorize(PermissionExpressions.RESERVATION_SYNC_OPERATIONS)
    fun rollbackPilot(): RecommendationPilotStateResponse =
        safely { pilotService.rollbackToInternalDemo(actorUserId()).toResponse() }

    @PostMapping("/pilot/recommendations/expire")
    @PreAuthorize(PermissionExpressions.RESERVATION_SYNC_OPERATIONS)
    fun expirePilotRecommendations(): RecommendationMaintenanceResponse =
        safely { RecommendationMaintenanceResponse(pilotService.expirePilotRecommendations(actorUserId())) }

    @PostMapping("/pilot/cleanup")
    @PreAuthorize(PermissionExpressions.RESERVATION_SYNC_OPERATIONS)
    fun cleanupPilotRetention(): RecommendationMaintenanceResponse =
        safely { RecommendationMaintenanceResponse(pilotService.cleanupPilotRetention(actorUserId())) }

    @GetMapping("/schedule")
    @PreAuthorize(PermissionExpressions.RESERVATION_SYNC_OPERATIONS)
    fun schedulerStatus(): RecommendationScheduleStatusResponse =
        safely { service.schedulerStatus(actorUserId()).toResponse() }

    @PostMapping("/schedule/run-now")
    @PreAuthorize(PermissionExpressions.RESERVATION_SYNC_OPERATIONS)
    fun runScheduleNow(): RecommendationGenerationRunResponse =
        safely { service.runScheduleNow(actorUserId()).toResponse() }

    @PostMapping("/schedule/pause")
    @PreAuthorize(PermissionExpressions.RESERVATION_SYNC_OPERATIONS)
    fun pauseSchedule(): RecommendationScheduleStatusResponse =
        safely { service.pauseScheduler(actorUserId()).toResponse() }

    @PostMapping("/schedule/resume")
    @PreAuthorize(PermissionExpressions.RESERVATION_SYNC_OPERATIONS)
    fun resumeSchedule(): RecommendationScheduleStatusResponse =
        safely { service.resumeScheduler(actorUserId()).toResponse() }

    @GetMapping("/generation-runs")
    @PreAuthorize(PermissionExpressions.RESERVATION_SYNC_OPERATIONS)
    fun generationRuns(
        @RequestParam(required = false) status: RecommendationGenerationRunStatus?,
        @RequestParam(required = false) trigger: RecommendationGenerationTrigger?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): RecommendationGenerationRunPageResponse =
        safely { service.generationRuns(RecommendationGenerationRunFilter(status, trigger, page, size)).toResponse() }

    @GetMapping("/generation-runs/{runId}")
    @PreAuthorize(PermissionExpressions.RESERVATION_SYNC_OPERATIONS)
    fun generationRun(@PathVariable runId: UUID): RecommendationGenerationRunResponse =
        safely { service.generationRun(RecommendationGenerationRunId(runId)).toResponse() }

    @PostMapping("/expire")
    @PreAuthorize(PermissionExpressions.RESERVATION_SYNC_OPERATIONS)
    fun expireEligible(): RecommendationMaintenanceResponse =
        safely { RecommendationMaintenanceResponse(service.expireEligible(actorUserId())) }

    @PostMapping("/cleanup")
    @PreAuthorize(PermissionExpressions.RESERVATION_SYNC_OPERATIONS)
    fun cleanup(): RecommendationMaintenanceResponse =
        safely { RecommendationMaintenanceResponse(service.cleanupRetention(actorUserId())) }

    @PostMapping("/{recommendationId}/approve")
    @PreAuthorize(PermissionExpressions.RESERVATION_SYNC_OPERATIONS)
    fun approve(@PathVariable recommendationId: UUID): RecommendationResponse =
        safely { service.approve(RecommendationId(recommendationId), actorUserId()).toResponse() }

    @PostMapping("/{recommendationId}/reject")
    @PreAuthorize(PermissionExpressions.RESERVATION_SYNC_OPERATIONS)
    fun reject(@PathVariable recommendationId: UUID): RecommendationResponse =
        safely { service.reject(RecommendationId(recommendationId), actorUserId()).toResponse() }

    @PostMapping("/{recommendationId}/expire")
    @PreAuthorize(PermissionExpressions.RESERVATION_SYNC_OPERATIONS)
    fun expire(@PathVariable recommendationId: UUID): RecommendationResponse =
        safely { service.expire(RecommendationId(recommendationId), actorUserId()).toResponse() }

    @PostMapping("/{recommendationId}/apply")
    @PreAuthorize(PermissionExpressions.RESERVATION_SYNC_OPERATIONS)
    fun apply(@PathVariable recommendationId: UUID): RecommendationResponse =
        safely { service.apply(RecommendationId(recommendationId), actorUserId()).toResponse() }

    @PostMapping("/{recommendationId}/retry")
    @PreAuthorize(PermissionExpressions.RESERVATION_SYNC_OPERATIONS)
    fun retry(@PathVariable recommendationId: UUID): RecommendationResponse =
        safely { service.retry(RecommendationId(recommendationId), actorUserId()).toResponse() }

    private fun actorUserId(): UUID =
        currentUserContextResolver.current().userId

    private fun <T> safely(block: () -> T): T =
        try {
            block()
        } catch (exception: ReservationTaskRecommendationNotFoundException) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Reservation task recommendation not found.", exception)
        } catch (exception: ReservationTaskRecommendationRejectedException) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Reservation task recommendation operation was rejected.", exception)
        } catch (exception: IllegalArgumentException) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid reservation task recommendation request.", exception)
        }
}

data class RecommendationResponse(
    val id: UUID,
    val source: String,
    val providerName: String,
    val modelIdentifierPresent: Boolean,
    val promptVersion: String,
    val category: RecommendationCategory,
    val confidence: RecommendationConfidence,
    val situation: String,
    val rationale: String,
    val supportingSignals: List<String>,
    val taskIntentType: TaskIntentType,
    val taskTitle: String,
    val taskPriority: TaskPriority,
    val taskDueAt: Instant,
    val status: RecommendationStatus,
    val pilotGenerated: Boolean,
    val pilotRunId: UUID?,
    val reviewed: Boolean,
    val applied: Boolean,
    val attemptCount: Int,
    val nextAttemptAt: Instant?,
    val failureCategory: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val expiresAt: Instant?
)

data class RecommendationPageResponse(
    val content: List<RecommendationResponse>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int
)

data class RecommendationGenerationSummaryResponse(
    val processedReservations: Int,
    val generated: Int,
    val duplicates: Int,
    val skipped: Int,
    val failed: Int
)

data class RecommendationProviderResponse(
    val providerId: String,
    val displayName: String,
    val providerType: String,
    val lifecycle: String,
    val status: String,
    val active: Boolean,
    val capabilities: List<String>,
    val modelIdentifierPresent: Boolean,
    val activeModel: String?,
    val promptVersion: String,
    val failureCategory: String?,
    val lastSuccessfulGenerationAt: Instant?,
    val lastProviderFailureCategory: String?,
    val averageResponseTimeBand: String,
    val retryStatistics: String
)

data class RecommendationProviderReadinessResponse(
    val providerId: String,
    val readiness: String,
    val lifecycle: String,
    val active: Boolean,
    val enabled: Boolean,
    val endpointClassification: RecommendationEndpointClassification,
    val environmentClass: String,
    val fallbackConfigured: Boolean,
    val productionUseBlocked: Boolean,
    val lastSmokeOutcome: String?,
    val lastSmokeAt: Instant?,
    val lastSuccessfulSmokeAt: Instant?,
    val consecutiveFailureBand: String,
    val latencyBand: String,
    val validationOutcome: String,
    val failureCategory: String?,
    val blockingReasons: List<String>,
    val capabilities: List<String>,
    val activeModel: String?,
    val promptVersion: String
)

data class RecommendationSmokeTestRequest(
    val providerId: String,
    val fixtureMode: RecommendationSmokeFixtureMode? = null,
    val expectedOutcome: RecommendationProviderDiagnosticOutcome? = null
)

data class RecommendationSmokeTestResponse(
    val diagnostic: RecommendationProviderDiagnosticResponse,
    val readiness: RecommendationProviderReadinessResponse,
    val recommendationCount: Int,
    val expectedOutcomeMatched: Boolean?
)

data class RecommendationPilotRunRequest(
    val providerId: String = "openai"
)

data class RecommendationPilotReadinessResponse(
    val status: String,
    val providerId: String,
    val providerReadiness: String?,
    val activeProvider: Boolean,
    val allowedProfile: Boolean,
    val productionBlocked: Boolean,
    val smokeFresh: Boolean,
    val withinPilotWindow: Boolean,
    val approvalModeRequired: Boolean,
    val budgetAvailable: Boolean,
    val blockingReasons: List<String>,
    val modelIdentifierPresent: Boolean,
    val promptVersion: String,
    val budget: RecommendationPilotBudgetResponse
)

data class RecommendationPilotRunSummaryResponse(
    val run: RecommendationPilotRunResponse,
    val readiness: RecommendationPilotReadinessResponse
)

data class RecommendationPilotRunResponse(
    val runId: UUID,
    val providerId: String,
    val trigger: String,
    val status: String,
    val startedAt: Instant,
    val completedAt: Instant?,
    val candidatesSelected: Int,
    val candidatesProcessed: Int,
    val providerCalls: Int,
    val recommendationsGenerated: Int,
    val duplicatesPrevented: Int,
    val skippedCount: Int,
    val failedCount: Int,
    val requestBudgetUsed: Int,
    val recommendationBudgetUsed: Int,
    val tokenBudgetUsed: Long,
    val modelIdentifierPresent: Boolean,
    val promptVersion: String,
    val contextSchemaVersion: String,
    val failureCategory: String?
)

data class RecommendationPilotRunPageResponse(
    val content: List<RecommendationPilotRunResponse>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int
)

data class RecommendationPilotBudgetResponse(
    val providerId: String,
    val budgetDate: String,
    val requestLimit: Int,
    val requestUsed: Int,
    val recommendationLimit: Int,
    val recommendationUsed: Int,
    val tokenLimitConfigured: Boolean,
    val tokenUsedBand: String,
    val exhausted: Boolean
)

data class RecommendationPilotStateResponse(
    val stateId: String,
    val disabled: Boolean,
    val disabledAt: Instant?,
    val lastRollbackAt: Instant?,
    val schedulePaused: Boolean,
    val schedulePausedAt: Instant?,
    val scheduleResumedAt: Instant?,
    val rollbackTargetProviderId: String,
    val updatedAt: Instant
)

data class RecommendationPilotScheduleStatusResponse(
    val scheduleId: String,
    val configuredEnabled: Boolean,
    val effectiveEnabled: Boolean,
    val paused: Boolean,
    val scheduleSummary: String,
    val providerId: String,
    val batchSize: Int,
    val maxRunsPerDay: Int,
    val lastAttemptedAt: Instant?,
    val lastSuccessfulAt: Instant?,
    val nextExpectedExecutionAt: Instant?,
    val lastRunOutcome: String?,
    val lastSelectedCandidateCount: Int,
    val lastGeneratedRecommendationCount: Int,
    val lastBudgetRejectionCount: Int,
    val lastFailureCategory: String?,
    val leaseState: String,
    val dailyRunLimitReached: Boolean,
    val readiness: RecommendationPilotReadinessResponse
)

data class RecommendationPilotAnalyticsSummaryResponse(
    val generatedCount: Long,
    val approvedCount: Long,
    val rejectedCount: Long,
    val expiredCount: Long,
    val appliedCount: Long,
    val approvalRate: Double,
    val rejectionRate: Double,
    val applyRate: Double,
    val averageReviewTimeBand: String,
    val duplicatePreventionCount: Long,
    val failureCount: Long
)

data class RecommendationPilotBreakdownResponse(
    val key: String,
    val count: Long
)

data class RecommendationPilotAnalyticsResponse(
    val summary: RecommendationPilotAnalyticsSummaryResponse,
    val reviewOutcomes: List<RecommendationPilotBreakdownResponse>,
    val confidenceDistribution: List<RecommendationPilotBreakdownResponse>,
    val categoryDistribution: List<RecommendationPilotBreakdownResponse>,
    val providerModelDistribution: List<RecommendationPilotBreakdownResponse>,
    val recommendationAgeBands: List<RecommendationPilotBreakdownResponse>
)

data class RecommendationPilotConfidenceCategoryResponse(
    val confidenceDistribution: List<RecommendationPilotBreakdownResponse>,
    val categoryDistribution: List<RecommendationPilotBreakdownResponse>
)

data class RecommendationProviderDiagnosticResponse(
    val diagnosticId: UUID,
    val providerId: String,
    val diagnosticType: String,
    val triggerType: String,
    val startedAt: Instant,
    val completedAt: Instant?,
    val outcome: String,
    val failureCategory: String?,
    val latencyBand: String,
    val retryCount: Int,
    val responseValidationOutcome: String,
    val promptVersion: String,
    val modelIdentifierPresent: Boolean,
    val environmentClass: String,
    val endpointClassification: String,
    val createdAt: Instant
)

data class RecommendationProviderDiagnosticPageResponse(
    val content: List<RecommendationProviderDiagnosticResponse>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int
)

data class RecommendationScheduleStatusResponse(
    val scheduleId: String,
    val configuredEnabled: Boolean,
    val effectiveEnabled: Boolean,
    val paused: Boolean,
    val scheduleSummary: String,
    val batchSize: Int,
    val maxReservationsPerExecution: Int,
    val enabledProviderId: String,
    val lastAttemptedAt: Instant?,
    val lastSuccessfulAt: Instant?,
    val nextExpectedExecutionAt: Instant?,
    val lastProcessedCandidateCount: Int,
    val lastGeneratedRecommendationCount: Int,
    val lastFailureCategory: String?,
    val leaseState: String,
    val eligibleCandidateBacklogCount: Long,
    val failedRunCount: Long
)

data class RecommendationGenerationRunResponse(
    val runId: UUID,
    val trigger: String,
    val providerId: String,
    val status: String,
    val startedAt: Instant,
    val completedAt: Instant?,
    val candidatesSelected: Int,
    val candidatesProcessed: Int,
    val recommendationsGenerated: Int,
    val duplicatesPrevented: Int,
    val skippedCount: Int,
    val failedCount: Int,
    val failureCategory: String?
)

data class RecommendationGenerationRunPageResponse(
    val content: List<RecommendationGenerationRunResponse>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int
)

data class RecommendationMaintenanceResponse(
    val affectedCount: Int
)

private fun ReservationTaskRecommendation.toResponse(): RecommendationResponse =
    RecommendationResponse(
        id = id.value,
        source = source.name,
        providerName = providerName,
        modelIdentifierPresent = modelIdentifier != null,
        promptVersion = promptVersion,
        category = category,
        confidence = confidence,
        situation = explanation.situation,
        rationale = explanation.rationale,
        supportingSignals = explanation.supportingSignals,
        taskIntentType = intentType,
        taskTitle = title,
        taskPriority = priority,
        taskDueAt = dueAt,
        status = status,
        pilotGenerated = pilotRunId != null,
        pilotRunId = pilotRunId?.value,
        reviewed = reviewedAt != null,
        applied = appliedTaskId != null,
        attemptCount = attemptCount,
        nextAttemptAt = nextAttemptAt,
        failureCategory = failureCategory?.name,
        createdAt = createdAt,
        updatedAt = updatedAt,
        expiresAt = expiresAt
    )

private fun RecommendationPilotReadiness.toResponse(): RecommendationPilotReadinessResponse =
    RecommendationPilotReadinessResponse(
        status = status.name,
        providerId = providerId.value,
        providerReadiness = providerReadiness?.name,
        activeProvider = activeProvider,
        allowedProfile = allowedProfile,
        productionBlocked = productionBlocked,
        smokeFresh = smokeFresh,
        withinPilotWindow = withinPilotWindow,
        approvalModeRequired = approvalModeRequired,
        budgetAvailable = budgetAvailable,
        blockingReasons = blockingReasons,
        modelIdentifierPresent = modelIdentifierPresent,
        promptVersion = promptVersion,
        budget = budget.toResponse()
    )

private fun RecommendationPilotRunSummary.toResponse(): RecommendationPilotRunSummaryResponse =
    RecommendationPilotRunSummaryResponse(run.toResponse(), readiness.toResponse())

private fun RecommendationPilotRun.toResponse(): RecommendationPilotRunResponse =
    RecommendationPilotRunResponse(
        runId = id.value,
        providerId = providerId.value,
        trigger = trigger.name,
        status = status.name,
        startedAt = startedAt,
        completedAt = completedAt,
        candidatesSelected = candidatesSelected,
        candidatesProcessed = candidatesProcessed,
        providerCalls = providerCalls,
        recommendationsGenerated = recommendationsGenerated,
        duplicatesPrevented = duplicatesPrevented,
        skippedCount = skippedCount,
        failedCount = failedCount,
        requestBudgetUsed = requestBudgetUsed,
        recommendationBudgetUsed = recommendationBudgetUsed,
        tokenBudgetUsed = tokenBudgetUsed,
        modelIdentifierPresent = modelIdentifier != null,
        promptVersion = promptVersion,
        contextSchemaVersion = contextSchemaVersion,
        failureCategory = failureCategory?.name
    )

private fun RecommendationPilotRunPage.toResponse(): RecommendationPilotRunPageResponse =
    RecommendationPilotRunPageResponse(content.map { it.toResponse() }, page, size, totalElements, totalPages)

private fun RecommendationPilotBudgetStatus.toResponse(): RecommendationPilotBudgetResponse =
    RecommendationPilotBudgetResponse(
        providerId = providerId.value,
        budgetDate = budgetDate.toString(),
        requestLimit = requestLimit,
        requestUsed = requestUsed,
        recommendationLimit = recommendationLimit,
        recommendationUsed = recommendationUsed,
        tokenLimitConfigured = tokenLimit != null,
        tokenUsedBand = when {
            tokenUsed <= 0 -> "none"
            tokenUsed < 10_000 -> "low"
            tokenUsed < 100_000 -> "medium"
            else -> "high"
        },
        exhausted = exhausted
    )

private fun RecommendationPilotState.toResponse(): RecommendationPilotStateResponse =
    RecommendationPilotStateResponse(
        stateId = stateId,
        disabled = disabled,
        disabledAt = disabledAt,
        lastRollbackAt = lastRollbackAt,
        schedulePaused = schedulePaused,
        schedulePausedAt = schedulePausedAt,
        scheduleResumedAt = scheduleResumedAt,
        rollbackTargetProviderId = "internal-demo",
        updatedAt = updatedAt
    )

private fun RecommendationPilotScheduleStatus.toResponse(): RecommendationPilotScheduleStatusResponse =
    RecommendationPilotScheduleStatusResponse(
        scheduleId = scheduleId,
        configuredEnabled = configuredEnabled,
        effectiveEnabled = effectiveEnabled,
        paused = paused,
        scheduleSummary = scheduleSummary,
        providerId = providerId.value,
        batchSize = batchSize,
        maxRunsPerDay = maxRunsPerDay,
        lastAttemptedAt = lastAttemptedAt,
        lastSuccessfulAt = lastSuccessfulAt,
        nextExpectedExecutionAt = nextExpectedExecutionAt,
        lastRunOutcome = lastRunOutcome?.name,
        lastSelectedCandidateCount = lastSelectedCandidateCount,
        lastGeneratedRecommendationCount = lastGeneratedRecommendationCount,
        lastBudgetRejectionCount = lastBudgetRejectionCount,
        lastFailureCategory = lastFailureCategory?.name,
        leaseState = leaseState.name,
        dailyRunLimitReached = dailyRunLimitReached,
        readiness = readiness.toResponse()
    )

private fun RecommendationPilotAnalytics.toResponse(): RecommendationPilotAnalyticsResponse =
    RecommendationPilotAnalyticsResponse(
        summary = RecommendationPilotAnalyticsSummaryResponse(
            generatedCount = summary.generatedCount,
            approvedCount = summary.approvedCount,
            rejectedCount = summary.rejectedCount,
            expiredCount = summary.expiredCount,
            appliedCount = summary.appliedCount,
            approvalRate = summary.approvalRate,
            rejectionRate = summary.rejectionRate,
            applyRate = summary.applyRate,
            averageReviewTimeBand = summary.averageReviewTimeBand,
            duplicatePreventionCount = summary.duplicatePreventionCount,
            failureCount = summary.failureCount
        ),
        reviewOutcomes = reviewOutcomes.map { it.toResponse() },
        confidenceDistribution = confidenceDistribution.map { it.toResponse() },
        categoryDistribution = categoryDistribution.map { it.toResponse() },
        providerModelDistribution = providerModelDistribution.map { it.toResponse() },
        recommendationAgeBands = recommendationAgeBands.map { it.toResponse() }
    )

private fun RecommendationPilotBreakdown.toResponse(): RecommendationPilotBreakdownResponse =
    RecommendationPilotBreakdownResponse(key = key, count = count)

private fun RecommendationPage.toResponse(): RecommendationPageResponse =
    RecommendationPageResponse(content.map { it.toResponse() }, page, size, totalElements, totalPages)

private fun RecommendationGenerationSummary.toResponse(): RecommendationGenerationSummaryResponse =
    RecommendationGenerationSummaryResponse(processedReservations, generated, duplicates, skipped, failed)

private fun RecommendationProviderSummary.toResponse(): RecommendationProviderResponse =
    RecommendationProviderResponse(
        providerId = providerId.value,
        displayName = displayName,
        providerType = providerType.name,
        lifecycle = lifecycle.name,
        status = status.name,
        active = active,
        capabilities = capabilities.map { it.name }.sorted(),
        modelIdentifierPresent = modelIdentifierPresent,
        activeModel = activeModel,
        promptVersion = promptVersion,
        failureCategory = failureCategory?.name,
        lastSuccessfulGenerationAt = lastSuccessfulGenerationAt,
        lastProviderFailureCategory = lastProviderFailureCategory?.name,
        averageResponseTimeBand = averageResponseTimeBand,
        retryStatistics = retryStatistics
    )

private fun RecommendationProviderReadiness.toResponse(): RecommendationProviderReadinessResponse =
    RecommendationProviderReadinessResponse(
        providerId = providerId.value,
        readiness = readiness.name,
        lifecycle = lifecycle.name,
        active = active,
        enabled = enabled,
        endpointClassification = endpointClassification,
        environmentClass = environmentClass,
        fallbackConfigured = fallbackConfigured,
        productionUseBlocked = productionUseBlocked,
        lastSmokeOutcome = lastSmokeOutcome?.name,
        lastSmokeAt = lastSmokeAt,
        lastSuccessfulSmokeAt = lastSuccessfulSmokeAt,
        consecutiveFailureBand = consecutiveFailureBand,
        latencyBand = latencyBand,
        validationOutcome = validationOutcome.name,
        failureCategory = failureCategory?.name,
        blockingReasons = blockingReasons,
        capabilities = capabilities.map { it.name }.sorted(),
        activeModel = activeModel,
        promptVersion = promptVersion
    )

private fun RecommendationSmokeTestResult.toResponse(expectedOutcome: RecommendationProviderDiagnosticOutcome?): RecommendationSmokeTestResponse =
    RecommendationSmokeTestResponse(
        diagnostic = diagnostic.toResponse(),
        readiness = readiness.toResponse(),
        recommendationCount = recommendationCount,
        expectedOutcomeMatched = expectedOutcome?.let { it == diagnostic.outcome }
    )

private fun RecommendationProviderDiagnostic.toResponse(): RecommendationProviderDiagnosticResponse =
    RecommendationProviderDiagnosticResponse(
        diagnosticId = id.value,
        providerId = providerId.value,
        diagnosticType = diagnosticType.name,
        triggerType = triggerType.name,
        startedAt = startedAt,
        completedAt = completedAt,
        outcome = outcome.name,
        failureCategory = failureCategory?.name,
        latencyBand = latencyBand,
        retryCount = retryCount,
        responseValidationOutcome = responseValidationOutcome.name,
        promptVersion = promptVersion,
        modelIdentifierPresent = modelIdentifier != null,
        environmentClass = environmentClass,
        endpointClassification = endpointClassification.name,
        createdAt = createdAt
    )

private fun RecommendationProviderDiagnosticPage.toResponse(): RecommendationProviderDiagnosticPageResponse =
    RecommendationProviderDiagnosticPageResponse(content.map { it.toResponse() }, page, size, totalElements, totalPages)

private fun RecommendationScheduleStatus.toResponse(): RecommendationScheduleStatusResponse =
    RecommendationScheduleStatusResponse(
        scheduleId = scheduleId,
        configuredEnabled = configuredEnabled,
        effectiveEnabled = effectiveEnabled,
        paused = paused,
        scheduleSummary = scheduleSummary,
        batchSize = batchSize,
        maxReservationsPerExecution = maxReservationsPerExecution,
        enabledProviderId = enabledProviderId.value,
        lastAttemptedAt = lastAttemptedAt,
        lastSuccessfulAt = lastSuccessfulAt,
        nextExpectedExecutionAt = nextExpectedExecutionAt,
        lastProcessedCandidateCount = lastProcessedCandidateCount,
        lastGeneratedRecommendationCount = lastGeneratedRecommendationCount,
        lastFailureCategory = lastFailureCategory?.name,
        leaseState = leaseState.name,
        eligibleCandidateBacklogCount = eligibleCandidateBacklogCount,
        failedRunCount = failedRunCount
    )

private fun RecommendationGenerationRun.toResponse(): RecommendationGenerationRunResponse =
    RecommendationGenerationRunResponse(
        runId = id.value,
        trigger = trigger.name,
        providerId = providerId.value,
        status = status.name,
        startedAt = startedAt,
        completedAt = completedAt,
        candidatesSelected = candidatesSelected,
        candidatesProcessed = candidatesProcessed,
        recommendationsGenerated = recommendationsGenerated,
        duplicatesPrevented = duplicatesPrevented,
        skippedCount = skippedCount,
        failedCount = failedCount,
        failureCategory = failureCategory?.name
    )

private fun RecommendationGenerationRunPage.toResponse(): RecommendationGenerationRunPageResponse =
    RecommendationGenerationRunPageResponse(content.map { it.toResponse() }, page, size, totalElements, totalPages)
