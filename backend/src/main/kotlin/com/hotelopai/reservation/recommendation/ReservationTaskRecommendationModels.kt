package com.hotelopai.reservation.recommendation

import com.hotelopai.shared.kernel.UuidV7Generator
import com.hotelopai.task.domain.TaskIntentType
import com.hotelopai.task.domain.TaskPriority
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

const val RECOMMENDATION_CONTEXT_SCHEMA_VERSION = "reservation-task-recommendation-context-v1"

@JvmInline
value class RecommendationId(val value: UUID) {
    companion object {
        fun generate(): RecommendationId = RecommendationId(UuidV7Generator.generate())
    }
}

@JvmInline
value class RecommendationProviderId(val value: String) {
    init {
        require(value.isNotBlank()) { "recommendation provider id must not be blank" }
    }
}

@JvmInline
value class RecommendationGenerationRunId(val value: UUID) {
    companion object {
        fun generate(): RecommendationGenerationRunId = RecommendationGenerationRunId(UuidV7Generator.generate())
    }
}

@JvmInline
value class RecommendationProviderDiagnosticId(val value: UUID) {
    companion object {
        fun generate(): RecommendationProviderDiagnosticId = RecommendationProviderDiagnosticId(UuidV7Generator.generate())
    }
}

@JvmInline
value class RecommendationPilotRunId(val value: UUID) {
    companion object {
        fun generate(): RecommendationPilotRunId = RecommendationPilotRunId(UuidV7Generator.generate())
    }
}

enum class RecommendationSource {
    INTERNAL_DEMO_AI,
    EXTERNAL_LLM
}

enum class RecommendationStatus {
    GENERATED,
    REVIEW_REQUIRED,
    APPROVED,
    REJECTED,
    EXPIRED,
    APPLIED,
    FAILED
}

enum class RecommendationConfidence {
    LOW,
    MEDIUM,
    HIGH
}

enum class RecommendationCategory {
    ARRIVAL_RISK_REVIEW,
    DEPARTURE_FOLLOW_UP,
    OCCUPANCY_REVIEW,
    ROOM_ASSIGNMENT_REVIEW
}

enum class RecommendationOutcome {
    GENERATED,
    DUPLICATE,
    SKIPPED,
    FAILED,
    APPLIED,
    REJECTED,
    EXPIRED
}

enum class RecommendationFailureCategory {
    FEATURE_DISABLED,
    RESERVATION_NOT_FOUND,
    PROVIDER_FAILURE,
    VALIDATION,
    DUPLICATE,
    NOT_ELIGIBLE,
    TASK_CREATION_FAILED,
    TRANSIENT_FAILURE,
    PROVIDER_UNAVAILABLE,
    PROVIDER_TIMEOUT,
    INVALID_PROVIDER_RESPONSE,
    CAPABILITY_UNSUPPORTED,
    CONFIGURATION_ERROR,
    RATE_LIMITED,
    PERMANENT_VALIDATION_FAILURE,
    NETWORK_ERROR,
    TIMEOUT,
    RATE_LIMIT,
    AUTHENTICATION,
    AUTHORIZATION,
    INVALID_RESPONSE,
    INVALID_CONFIGURATION,
    INTERNAL_PROVIDER_ERROR
}

enum class RecommendationProviderCapability {
    BATCH_GENERATION,
    STRUCTURED_EXPLANATIONS,
    CONFIDENCE_SCORING,
    RETRYABLE_EXECUTION,
    MODEL_METADATA,
    DETERMINISTIC_OUTPUT
}

enum class RecommendationProviderType {
    INTERNAL,
    EXTERNAL
}

enum class RecommendationProviderLifecycle {
    REGISTERED,
    AVAILABLE,
    DISABLED,
    MISCONFIGURED,
    UNAVAILABLE
}

enum class RecommendationProviderStatus {
    ENABLED,
    DISABLED,
    UNAVAILABLE,
    MISCONFIGURED
}

enum class ExternalRecommendationActivationMode {
    SMOKE_TEST_ONLY,
    RUNTIME_GENERATION
}

enum class RecommendationProviderReadinessStatus {
    NOT_CONFIGURED,
    DISABLED,
    BLOCKED_BY_ENVIRONMENT,
    MISCONFIGURED,
    READY_FOR_LOCAL_SMOKE,
    READY_FOR_NON_PRODUCTION,
    TEMPORARILY_UNAVAILABLE,
    PRODUCTION_BLOCKED
}

enum class RecommendationEndpointClassification {
    LOCAL_STUB,
    EXTERNAL_HTTPS,
    EXTERNAL_HTTP,
    INVALID
}

enum class RecommendationSmokeFixtureMode {
    SUCCESS,
    EMPTY_SUCCESS,
    MALFORMED_RESPONSE,
    TIMEOUT,
    RATE_LIMITED,
    AUTHENTICATION_FAILURE,
    PROVIDER_UNAVAILABLE
}

enum class RecommendationProviderDiagnosticType {
    SMOKE_TEST
}

enum class RecommendationProviderDiagnosticTrigger {
    OPERATOR
}

enum class RecommendationProviderDiagnosticOutcome {
    SUCCEEDED,
    FAILED,
    REJECTED
}

enum class RecommendationPilotRunStatus {
    REQUESTED,
    RUNNING,
    SUCCEEDED,
    PARTIALLY_SUCCEEDED,
    FAILED,
    REJECTED,
    BUDGET_EXHAUSTED
}

enum class RecommendationPilotTrigger {
    OPERATOR,
    SCHEDULED
}

enum class RecommendationPilotReadinessStatus {
    DISABLED,
    BLOCKED,
    READY
}

enum class RecommendationResponseValidationOutcome {
    NOT_APPLICABLE,
    VALID,
    INVALID
}

enum class RecommendationGenerationTrigger {
    OPERATOR,
    SCHEDULED
}

enum class RecommendationGenerationRunStatus {
    REQUESTED,
    RUNNING,
    SUCCEEDED,
    PARTIALLY_SUCCEEDED,
    FAILED,
    REJECTED
}

data class RecommendationExplanation(
    val situation: String,
    val rationale: String,
    val supportingSignals: List<String>
) {
    init {
        require(situation.isNotBlank()) { "recommendation situation must not be blank" }
        require(rationale.isNotBlank()) { "recommendation rationale must not be blank" }
        require(supportingSignals.all { it.isNotBlank() }) { "recommendation supporting signals must not be blank" }
    }
}

data class SanitizedReservationRecommendationContext(
    val contextSchemaVersion: String = RECOMMENDATION_CONTEXT_SCHEMA_VERSION,
    val reservationId: UUID,
    val reservationStatus: String,
    val stayStatus: String,
    val arrivalDate: LocalDate,
    val departureDate: LocalDate,
    val nights: Long,
    val adultOccupancy: Int,
    val childOccupancy: Int,
    val roomAssigned: Boolean,
    val deterministicTaskCreated: Boolean,
    val deterministicAutomationOutcomes: Set<String>,
    val taskBacklogBand: String,
    val openTaskCountBand: String = "unknown",
    val overdueTaskCountBand: String = "unknown",
    val unresolvedAutomationFailure: Boolean = false,
    val activeRecommendationCountBand: String = "none",
    val roomAssignmentCompleteness: String = if (roomAssigned) "assigned" else "unassigned",
    val stayProximityBand: String = "unknown",
    val lifecycleChangeRecencyBand: String = "unknown",
    val propertyCapabilityFlags: Set<String> = emptySet(),
    val now: Instant
)

data class RecommendationTaskProposal(
    val category: RecommendationCategory,
    val confidence: RecommendationConfidence,
    val explanation: RecommendationExplanation,
    val intentType: TaskIntentType,
    val title: String,
    val description: String,
    val priority: TaskPriority,
    val dueAt: Instant,
    val deduplicationKey: String
) {
    init {
        require(title.isNotBlank()) { "recommendation task title must not be blank" }
        require(description.isNotBlank()) { "recommendation task description must not be blank" }
        require(deduplicationKey.isNotBlank()) { "recommendation deduplication key must not be blank" }
    }
}

data class ReservationTaskRecommendation(
    val id: RecommendationId = RecommendationId.generate(),
    val reservationId: UUID,
    val source: RecommendationSource,
    val providerName: String,
    val modelIdentifier: String?,
    val promptVersion: String,
    val contextSchemaVersion: String = RECOMMENDATION_CONTEXT_SCHEMA_VERSION,
    val category: RecommendationCategory,
    val confidence: RecommendationConfidence,
    val explanation: RecommendationExplanation,
    val intentType: TaskIntentType,
    val title: String,
    val description: String,
    val priority: TaskPriority,
    val dueAt: Instant,
    val deduplicationKey: String,
    val status: RecommendationStatus,
    val reviewedBy: UUID? = null,
    val reviewedAt: Instant? = null,
    val appliedTaskId: UUID? = null,
    val pilotRunId: RecommendationPilotRunId? = null,
    val attemptCount: Int = 0,
    val nextAttemptAt: Instant? = null,
    val failureCategory: RecommendationFailureCategory? = null,
    val createdAt: Instant,
    val updatedAt: Instant = createdAt,
    val expiresAt: Instant? = null,
    val version: Long = 0
)

data class RecommendationPilotReadiness(
    val status: RecommendationPilotReadinessStatus,
    val providerId: RecommendationProviderId,
    val providerReadiness: RecommendationProviderReadinessStatus?,
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
    val budget: RecommendationPilotBudgetStatus
)

data class RecommendationPilotRun(
    val id: RecommendationPilotRunId = RecommendationPilotRunId.generate(),
    val providerId: RecommendationProviderId,
    val trigger: RecommendationPilotTrigger,
    val status: RecommendationPilotRunStatus,
    val startedAt: Instant,
    val completedAt: Instant? = null,
    val candidatesSelected: Int = 0,
    val candidatesProcessed: Int = 0,
    val providerCalls: Int = 0,
    val recommendationsGenerated: Int = 0,
    val duplicatesPrevented: Int = 0,
    val skippedCount: Int = 0,
    val failedCount: Int = 0,
    val requestBudgetUsed: Int = 0,
    val recommendationBudgetUsed: Int = 0,
    val tokenBudgetUsed: Long = 0,
    val modelIdentifier: String?,
    val promptVersion: String,
    val contextSchemaVersion: String = RECOMMENDATION_CONTEXT_SCHEMA_VERSION,
    val failureCategory: RecommendationFailureCategory? = null,
    val createdAt: Instant = startedAt,
    val updatedAt: Instant = startedAt,
    val version: Long = 0
)

data class RecommendationPilotRunFilter(
    val status: RecommendationPilotRunStatus? = null,
    val trigger: RecommendationPilotTrigger? = null,
    val page: Int = 0,
    val size: Int = 20
)

data class RecommendationPilotRunPage(
    val content: List<RecommendationPilotRun>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int
)

data class RecommendationPilotBudgetStatus(
    val providerId: RecommendationProviderId,
    val budgetDate: LocalDate,
    val requestLimit: Int,
    val requestUsed: Int,
    val recommendationLimit: Int,
    val recommendationUsed: Int,
    val tokenLimit: Long?,
    val tokenUsed: Long,
    val exhausted: Boolean
)

data class RecommendationPilotRunSummary(
    val run: RecommendationPilotRun,
    val readiness: RecommendationPilotReadiness
)

data class RecommendationPilotState(
    val stateId: String,
    val disabled: Boolean,
    val disabledAt: Instant?,
    val lastRollbackAt: Instant?,
    val schedulePaused: Boolean = false,
    val schedulePausedAt: Instant? = null,
    val scheduleResumedAt: Instant? = null,
    val lastScheduleAttemptedAt: Instant? = null,
    val lastScheduleSuccessfulAt: Instant? = null,
    val lastScheduleOutcome: RecommendationPilotRunStatus? = null,
    val lastSelectedCandidateCount: Int = 0,
    val lastGeneratedRecommendationCount: Int = 0,
    val lastBudgetRejectionCount: Int = 0,
    val lastScheduleFailureCategory: RecommendationFailureCategory? = null,
    val updatedAt: Instant
)

data class RecommendationPilotScheduleStatus(
    val scheduleId: String,
    val configuredEnabled: Boolean,
    val effectiveEnabled: Boolean,
    val paused: Boolean,
    val scheduleSummary: String,
    val providerId: RecommendationProviderId,
    val batchSize: Int,
    val maxRunsPerDay: Int,
    val lastAttemptedAt: Instant?,
    val lastSuccessfulAt: Instant?,
    val nextExpectedExecutionAt: Instant?,
    val lastRunOutcome: RecommendationPilotRunStatus?,
    val lastSelectedCandidateCount: Int,
    val lastGeneratedRecommendationCount: Int,
    val lastBudgetRejectionCount: Int,
    val lastFailureCategory: RecommendationFailureCategory?,
    val leaseState: com.hotelopai.reservation.application.ReservationSyncScheduleLeaseState,
    val dailyRunLimitReached: Boolean,
    val readiness: RecommendationPilotReadiness
)

data class RecommendationPilotAnalyticsFilter(
    val generatedFrom: Instant? = null,
    val generatedTo: Instant? = null,
    val providerId: RecommendationProviderId? = null,
    val modelIdentifier: String? = null,
    val category: RecommendationCategory? = null,
    val confidence: RecommendationConfidence? = null,
    val status: RecommendationStatus? = null,
    val pilotRunId: RecommendationPilotRunId? = null
)

data class RecommendationPilotAnalyticsSummary(
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

data class RecommendationPilotBreakdown(
    val key: String,
    val count: Long
)

data class RecommendationPilotAnalytics(
    val summary: RecommendationPilotAnalyticsSummary,
    val reviewOutcomes: List<RecommendationPilotBreakdown>,
    val confidenceDistribution: List<RecommendationPilotBreakdown>,
    val categoryDistribution: List<RecommendationPilotBreakdown>,
    val providerModelDistribution: List<RecommendationPilotBreakdown>,
    val recommendationAgeBands: List<RecommendationPilotBreakdown>
)

data class RecommendationProviderSummary(
    val providerId: RecommendationProviderId,
    val displayName: String,
    val providerType: RecommendationProviderType,
    val lifecycle: RecommendationProviderLifecycle,
    val status: RecommendationProviderStatus,
    val active: Boolean,
    val capabilities: Set<RecommendationProviderCapability>,
    val modelIdentifierPresent: Boolean,
    val activeModel: String?,
    val promptVersion: String,
    val failureCategory: RecommendationFailureCategory? = null,
    val lastSuccessfulGenerationAt: Instant? = null,
    val lastProviderFailureCategory: RecommendationFailureCategory? = null,
    val averageResponseTimeBand: String = "unknown",
    val retryStatistics: String = "not_recorded"
)

data class RecommendationProviderReadiness(
    val providerId: RecommendationProviderId,
    val readiness: RecommendationProviderReadinessStatus,
    val lifecycle: RecommendationProviderLifecycle,
    val active: Boolean,
    val enabled: Boolean,
    val endpointClassification: RecommendationEndpointClassification,
    val environmentClass: String,
    val fallbackConfigured: Boolean,
    val productionUseBlocked: Boolean,
    val lastSmokeOutcome: RecommendationProviderDiagnosticOutcome?,
    val lastSmokeAt: Instant?,
    val lastSuccessfulSmokeAt: Instant?,
    val consecutiveFailureBand: String,
    val latencyBand: String,
    val validationOutcome: RecommendationResponseValidationOutcome,
    val failureCategory: RecommendationFailureCategory?,
    val blockingReasons: List<String>,
    val capabilities: Set<RecommendationProviderCapability>,
    val activeModel: String?,
    val promptVersion: String
)

data class RecommendationProviderDiagnostic(
    val id: RecommendationProviderDiagnosticId = RecommendationProviderDiagnosticId.generate(),
    val providerId: RecommendationProviderId,
    val diagnosticType: RecommendationProviderDiagnosticType,
    val triggerType: RecommendationProviderDiagnosticTrigger,
    val startedAt: Instant,
    val completedAt: Instant?,
    val outcome: RecommendationProviderDiagnosticOutcome,
    val failureCategory: RecommendationFailureCategory?,
    val latencyBand: String,
    val retryCount: Int,
    val responseValidationOutcome: RecommendationResponseValidationOutcome,
    val promptVersion: String,
    val modelIdentifier: String?,
    val environmentClass: String,
    val endpointClassification: RecommendationEndpointClassification,
    val createdAt: Instant = startedAt
)

data class RecommendationProviderDiagnosticFilter(
    val providerId: RecommendationProviderId? = null,
    val outcome: RecommendationProviderDiagnosticOutcome? = null,
    val page: Int = 0,
    val size: Int = 20
)

data class RecommendationProviderDiagnosticPage(
    val content: List<RecommendationProviderDiagnostic>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int
)

data class RecommendationGenerationRun(
    val id: RecommendationGenerationRunId = RecommendationGenerationRunId.generate(),
    val trigger: RecommendationGenerationTrigger,
    val providerId: RecommendationProviderId,
    val status: RecommendationGenerationRunStatus,
    val startedAt: Instant,
    val completedAt: Instant? = null,
    val candidatesSelected: Int = 0,
    val candidatesProcessed: Int = 0,
    val recommendationsGenerated: Int = 0,
    val duplicatesPrevented: Int = 0,
    val skippedCount: Int = 0,
    val failedCount: Int = 0,
    val failureCategory: RecommendationFailureCategory? = null,
    val createdAt: Instant = startedAt,
    val updatedAt: Instant = startedAt,
    val version: Long = 0
)

data class RecommendationGenerationRunFilter(
    val status: RecommendationGenerationRunStatus? = null,
    val trigger: RecommendationGenerationTrigger? = null,
    val page: Int = 0,
    val size: Int = 20
)

data class RecommendationGenerationRunPage(
    val content: List<RecommendationGenerationRun>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int
)

data class RecommendationScheduleState(
    val scheduleId: String,
    val paused: Boolean,
    val pausedAt: Instant? = null,
    val resumedAt: Instant? = null,
    val lastAttemptedAt: Instant? = null,
    val lastSuccessfulAt: Instant? = null,
    val lastProcessedCandidateCount: Int = 0,
    val lastGeneratedRecommendationCount: Int = 0,
    val lastFailureCategory: RecommendationFailureCategory? = null,
    val updatedAt: Instant
)

data class RecommendationScheduleStatus(
    val scheduleId: String,
    val configuredEnabled: Boolean,
    val effectiveEnabled: Boolean,
    val paused: Boolean,
    val scheduleSummary: String,
    val batchSize: Int,
    val maxReservationsPerExecution: Int,
    val enabledProviderId: RecommendationProviderId,
    val lastAttemptedAt: Instant?,
    val lastSuccessfulAt: Instant?,
    val nextExpectedExecutionAt: Instant?,
    val lastProcessedCandidateCount: Int,
    val lastGeneratedRecommendationCount: Int,
    val lastFailureCategory: RecommendationFailureCategory?,
    val leaseState: com.hotelopai.reservation.application.ReservationSyncScheduleLeaseState,
    val eligibleCandidateBacklogCount: Long,
    val failedRunCount: Long
)

data class RecommendationGenerationSummary(
    val processedReservations: Int,
    val generated: Int,
    val duplicates: Int,
    val skipped: Int,
    val failed: Int
)

data class RecommendationFilter(
    val status: RecommendationStatus? = null,
    val category: RecommendationCategory? = null,
    val page: Int = 0,
    val size: Int = 20
)

data class RecommendationPage(
    val content: List<ReservationTaskRecommendation>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int
)

interface TaskRecommendationProvider {
    val providerName: String
    val modelIdentifier: String?
    val promptVersion: String
    val id: RecommendationProviderId
        get() = RecommendationProviderId(providerName)
    val displayName: String
        get() = providerName
    val providerType: RecommendationProviderType
        get() = RecommendationProviderType.INTERNAL
    val capabilities: Set<RecommendationProviderCapability>
        get() = setOf(
            RecommendationProviderCapability.BATCH_GENERATION,
            RecommendationProviderCapability.STRUCTURED_EXPLANATIONS,
            RecommendationProviderCapability.CONFIDENCE_SCORING,
            RecommendationProviderCapability.RETRYABLE_EXECUTION
        )
    fun recommend(context: SanitizedReservationRecommendationContext): List<RecommendationTaskProposal>
}

data class RecommendationPrompt(
    val templateId: String,
    val version: String,
    val systemInstructions: String,
    val recommendationTemplate: String,
    val context: OutboundRecommendationContext,
    val outputSchema: RecommendationOutputSchema
) {
    init {
        require(templateId.isNotBlank()) { "recommendation prompt template id must not be blank" }
        require(version.isNotBlank()) { "recommendation prompt version must not be blank" }
        require(systemInstructions.isNotBlank()) { "recommendation prompt system instructions must not be blank" }
        require(recommendationTemplate.isNotBlank()) { "recommendation prompt template must not be blank" }
    }
}

data class OutboundRecommendationContext(
    val schemaVersion: String,
    val reservationStatus: String,
    val stayStatus: String,
    val stayTimingBand: String,
    val nightsBand: String,
    val occupancyBand: String,
    val roomAssignmentCompleteness: String,
    val deterministicAutomationOutcomes: Set<String>,
    val taskBacklogBand: String,
    val activeRecommendationCountBand: String,
    val unresolvedAutomationFailure: Boolean,
    val propertyCapabilityFlags: Set<String>
)

data class RecommendationOutputSchema(
    val schemaId: String = "hotelopai.reservation-task-recommendation.v1",
    val maxRecommendations: Int
) {
    init {
        require(maxRecommendations in 1..20) { "recommendation output schema max recommendations must be between 1 and 20" }
    }
}

data class StructuredRecommendationResponse(
    val recommendations: List<StructuredRecommendationItem>
)

data class StructuredRecommendationItem(
    val category: RecommendationCategory,
    val priority: TaskPriority,
    val confidence: RecommendationConfidence,
    val explanation: RecommendationExplanation,
    val intentType: TaskIntentType,
    val proposedTaskTitle: String,
    val proposedTaskSummary: String
)

interface ExternalLlmRecommendationProvider : TaskRecommendationProvider
