package com.hotelopai.knowledge.application

import com.hotelopai.knowledge.domain.KnowledgeChunkId
import com.hotelopai.knowledge.domain.KnowledgeDocumentId
import java.time.Instant
import java.util.UUID

@JvmInline
value class KnowledgeEmbeddingProviderId(val value: String) {
    init {
        require(value.isNotBlank()) { "knowledge embedding provider id must not be blank" }
        require(value.length <= 80) { "knowledge embedding provider id must be bounded" }
    }
}

enum class KnowledgeEmbeddingProviderReadiness {
    READY,
    DISABLED,
    MISCONFIGURED,
    UNAVAILABLE
}

enum class KnowledgeEmbeddingProviderType {
    INTERNAL,
    EXTERNAL
}

enum class KnowledgeEmbeddingProviderLifecycle {
    REGISTERED,
    AVAILABLE,
    DISABLED,
    MISCONFIGURED,
    UNAVAILABLE
}

enum class KnowledgeEmbeddingFailureCategory {
    PROVIDER_DISABLED,
    PROVIDER_UNAVAILABLE,
    TIMEOUT,
    RATE_LIMITED,
    AUTHENTICATION_FAILURE,
    INVALID_RESPONSE,
    DIMENSION_MISMATCH,
    STALE_CHUNK,
    CONFIGURATION_ERROR
}

enum class KnowledgeEmbeddingStatus {
    READY,
    FAILED,
    STALE,
    SKIPPED
}

enum class KnowledgeRetrievalReadiness {
    DISABLED,
    NOT_INDEXED,
    READY,
    READY_WITH_WARNINGS,
    NOT_EVALUATED,
    QUALITY_GATE_FAILED,
    DEGRADED,
    PARTIALLY_INDEXED,
    INDEXING,
    MISCONFIGURED,
    PROVIDER_UNAVAILABLE
}

enum class KnowledgeEmbeddingDiagnosticType {
    BATCH,
    SMOKE,
    SCHEDULED_REFRESH,
    OPERATOR_REFRESH,
    CLEANUP
}

enum class KnowledgeEmbeddingDiagnosticOutcome {
    SUCCEEDED,
    FAILED,
    SKIPPED
}

enum class KnowledgeSearchMode {
    KEYWORD,
    SEMANTIC,
    HYBRID
}

data class KnowledgeEmbeddingRequest(
    val chunkId: KnowledgeChunkId,
    val text: String,
    val contentFingerprint: String
)

data class KnowledgeEmbeddingVector(
    val values: List<Double>
) {
    init {
        require(values.isNotEmpty()) { "knowledge embedding vector must not be empty" }
        require(values.all { it.isFinite() }) { "knowledge embedding vector values must be finite" }
    }
}

data class KnowledgeEmbeddingResponse(
    val chunkId: KnowledgeChunkId,
    val vector: KnowledgeEmbeddingVector,
    val contentFingerprint: String
)

interface KnowledgeEmbeddingProvider {
    val providerId: KnowledgeEmbeddingProviderId
    val providerType: KnowledgeEmbeddingProviderType
    val modelIdentifier: String
    val embeddingDimension: Int
    fun readiness(): KnowledgeEmbeddingProviderReadiness
    fun embed(requests: List<KnowledgeEmbeddingRequest>): List<KnowledgeEmbeddingResponse>
}

interface ExternalKnowledgeEmbeddingProvider : KnowledgeEmbeddingProvider

data class KnowledgeEmbeddingRecord(
    val chunkId: KnowledgeChunkId,
    val providerId: KnowledgeEmbeddingProviderId,
    val modelId: String,
    val dimension: Int,
    val vector: KnowledgeEmbeddingVector?,
    val contentFingerprint: String,
    val generatedAt: Instant?,
    val status: KnowledgeEmbeddingStatus,
    val failureCategory: KnowledgeEmbeddingFailureCategory? = null,
    val attemptCount: Int = 0,
    val nextAttemptAt: Instant? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
    val version: Long = 0
)

data class KnowledgeEmbeddingBatchSummary(
    val providerId: String,
    val modelId: String,
    val considered: Int,
    val embedded: Int,
    val skippedUnchanged: Int,
    val failed: Int,
    val failureCategory: KnowledgeEmbeddingFailureCategory? = null
)

data class KnowledgeEmbeddingStatusSummary(
    val semanticEnabled: Boolean,
    val providerId: String,
    val modelId: String,
    val dimension: Int,
    val readiness: KnowledgeEmbeddingProviderReadiness,
    val readyCount: Long,
    val failedCount: Long,
    val staleCount: Long
)

data class KnowledgeEmbeddingProviderSummary(
    val providerId: String,
    val providerType: KnowledgeEmbeddingProviderType,
    val lifecycle: KnowledgeEmbeddingProviderLifecycle,
    val active: Boolean,
    val modelPresent: Boolean,
    val dimension: Int,
    val readiness: KnowledgeEmbeddingProviderReadiness,
    val failureCategory: KnowledgeEmbeddingFailureCategory? = null
)

data class KnowledgeEmbeddingDiagnosticRecord(
    val id: UUID,
    val providerId: String,
    val modelId: String,
    val diagnosticType: KnowledgeEmbeddingDiagnosticType,
    val outcome: KnowledgeEmbeddingDiagnosticOutcome,
    val readiness: KnowledgeEmbeddingProviderReadiness,
    val failureCategory: KnowledgeEmbeddingFailureCategory?,
    val latencyBand: String,
    val batchSize: Int,
    val generatedAt: Instant,
    val createdAt: Instant
)

data class KnowledgeRetrievalReadinessSummary(
    val state: KnowledgeRetrievalReadiness,
    val semanticEnabled: Boolean,
    val providerId: String,
    val readiness: KnowledgeEmbeddingProviderReadiness,
    val embeddedPercentage: Int,
    val totalChunks: Long,
    val readyCount: Long,
    val failedCount: Long,
    val staleCount: Long,
    val schedulerPaused: Boolean,
    val blockingReasons: List<String>,
    val evaluationStatus: String? = null,
    val latestEvaluationRunId: UUID? = null,
    val qualityGateStatus: String? = null
)

enum class KnowledgeRetrievalEvaluationStatus {
    SUCCEEDED,
    PARTIALLY_SUCCEEDED,
    FAILED
}

data class KnowledgeRetrievalEvaluationCase(
    val query: String,
    val expectedDocumentIds: Set<KnowledgeDocumentId> = emptySet(),
    val expectedChunkIds: Set<KnowledgeChunkId> = emptySet(),
    val relevanceScore: Double? = null
) {
    init {
        require(query.trim().length in 2..200) { "knowledge retrieval evaluation query length must be between 2 and 200" }
        require(expectedDocumentIds.isNotEmpty() || expectedChunkIds.isNotEmpty()) {
            "knowledge retrieval evaluation case must declare expected documents or chunks"
        }
        relevanceScore?.let { require(it in 0.0..1.0) { "knowledge retrieval evaluation relevance score must be between 0 and 1" } }
    }
}

data class KnowledgeRetrievalEvaluationRequest(
    val name: String,
    val hotelId: UUID?,
    val cases: List<KnowledgeRetrievalEvaluationCase>,
    val modes: Set<KnowledgeSearchMode> = setOf(KnowledgeSearchMode.KEYWORD, KnowledgeSearchMode.SEMANTIC, KnowledgeSearchMode.HYBRID),
    val k: Int = 5
) {
    init {
        require(name.isNotBlank()) { "knowledge retrieval evaluation name must not be blank" }
        require(name.length <= 120) { "knowledge retrieval evaluation name must be bounded" }
        require(cases.isNotEmpty()) { "knowledge retrieval evaluation must include at least one case" }
        require(cases.size <= 100) { "knowledge retrieval evaluation cases must be bounded" }
        require(modes.isNotEmpty()) { "knowledge retrieval evaluation modes must not be empty" }
        require(k in 1..50) { "knowledge retrieval evaluation k must be between 1 and 50" }
    }
}

data class KnowledgeRetrievalMetricSummary(
    val mode: KnowledgeSearchMode,
    val precisionAtK: Double,
    val recallAtK: Double,
    val meanReciprocalRank: Double,
    val normalizedDiscountedCumulativeGain: Double,
    val hitRate: Double,
    val averageLatencyMillis: Long,
    val averageRetrievedChunks: Double,
    val scoreBand: String
)

data class KnowledgeRetrievalEvaluationRun(
    val id: UUID,
    val name: String,
    val status: KnowledgeRetrievalEvaluationStatus,
    val caseCount: Int,
    val k: Int,
    val modes: Set<KnowledgeSearchMode>,
    val startedAt: Instant,
    val completedAt: Instant,
    val failureCategory: KnowledgeEmbeddingFailureCategory?,
    val metrics: List<KnowledgeRetrievalMetricSummary>
)

interface KnowledgeRetrievalEvaluationRepository {
    fun saveEvaluationRun(run: KnowledgeRetrievalEvaluationRun): KnowledgeRetrievalEvaluationRun
    fun evaluationRuns(limit: Int, offset: Int): List<KnowledgeRetrievalEvaluationRun>
    fun evaluationRun(id: UUID): KnowledgeRetrievalEvaluationRun?
    fun latestEvaluationRun(): KnowledgeRetrievalEvaluationRun?
}

enum class KnowledgeRetrievalQualityOutcome {
    PASS,
    PASS_WITH_WARNINGS,
    FAIL
}

data class KnowledgeCuratedDocumentFixture(
    val reference: String,
    val title: String,
    val category: com.hotelopai.knowledge.domain.KnowledgeCategory,
    val source: com.hotelopai.knowledge.domain.KnowledgeSource,
    val language: String,
    val tags: Set<String>,
    val content: String
)

data class KnowledgeCuratedEvaluationCase(
    val caseId: String,
    val query: String,
    val expectedDocumentReferences: Set<String>,
    val expectedChunkReferences: Set<String> = emptySet(),
    val relevanceLevels: Map<String, Int> = emptyMap(),
    val modes: Set<KnowledgeSearchMode> = setOf(KnowledgeSearchMode.KEYWORD, KnowledgeSearchMode.SEMANTIC, KnowledgeSearchMode.HYBRID),
    val category: com.hotelopai.knowledge.domain.KnowledgeCategory? = null,
    val language: String = "en"
)

data class KnowledgeCuratedEvaluationDataset(
    val version: String,
    val documents: List<KnowledgeCuratedDocumentFixture>,
    val cases: List<KnowledgeCuratedEvaluationCase>
)

data class KnowledgeCuratedDatasetValidationReport(
    val datasetVersion: String,
    val valid: Boolean,
    val documentCount: Int,
    val caseCount: Int,
    val failureReasons: List<String>
)

data class KnowledgeRetrievalQualityThresholds(
    val minimumPrecisionAtK: Double,
    val minimumRecallAtK: Double,
    val minimumMrr: Double,
    val minimumNdcg: Double,
    val minimumHitRate: Double,
    val maximumAverageLatencyMillis: Long,
    val minimumEvaluatedQueryCount: Int
)

data class KnowledgeRetrievalQualityModeReport(
    val mode: KnowledgeSearchMode,
    val metrics: KnowledgeRetrievalMetricSummary?,
    val outcome: KnowledgeRetrievalQualityOutcome,
    val failedThresholds: List<String>
)

data class KnowledgeRetrievalQualityReport(
    val datasetVersion: String,
    val runId: UUID?,
    val outcome: KnowledgeRetrievalQualityOutcome,
    val evaluatedQueryCount: Int,
    val k: Int,
    val modeReports: List<KnowledgeRetrievalQualityModeReport>,
    val failedThresholds: List<String>
)

data class KnowledgeContextAssemblyRequest(
    val query: String,
    val hotelId: UUID?,
    val mode: KnowledgeSearchMode = KnowledgeSearchMode.HYBRID,
    val categories: Set<com.hotelopai.knowledge.domain.KnowledgeCategory> = emptySet(),
    val language: String = "en",
    val limit: Int? = null
)

data class KnowledgeSourceCitation(
    val documentReference: UUID,
    val chunkReference: UUID,
    val title: String,
    val category: com.hotelopai.knowledge.domain.KnowledgeCategory,
    val chunkPosition: Int,
    val retrievalScore: Double,
    val contentFingerprint: String
)

data class KnowledgeContextScore(
    val keywordScore: Double,
    val semanticScore: Double,
    val combinedScore: Double,
    val fallbackUsed: Boolean
)

data class KnowledgeContextItem(
    val citation: KnowledgeSourceCitation,
    val selectedText: String,
    val retrievalMode: KnowledgeSearchMode,
    val score: KnowledgeContextScore
)

data class KnowledgeContextAssemblyResult(
    val mode: KnowledgeSearchMode,
    val itemCount: Int,
    val totalCharacters: Int,
    val duplicateCount: Int,
    val items: List<KnowledgeContextItem>
)

data class KnowledgeOperationsAuditEvent(
    val action: String,
    val outcome: String,
    val occurredAt: Instant,
    val safeCategory: String? = null
)

interface KnowledgeOperationsAuditSink {
    fun record(event: KnowledgeOperationsAuditEvent)
}

object NoOpKnowledgeOperationsAuditSink : KnowledgeOperationsAuditSink {
    override fun record(event: KnowledgeOperationsAuditEvent) = Unit
}

@JvmInline
value class KnowledgeAnswerId(val value: UUID)

enum class KnowledgeAnswerStatus {
    ANSWERED,
    INSUFFICIENT_CONTEXT,
    PROVIDER_DISABLED,
    FAILED_VALIDATION,
    PROVIDER_FAILURE
}

enum class KnowledgeAnswerConfidence {
    LOW,
    MEDIUM,
    HIGH
}

enum class KnowledgeAnswerFailureCategory {
    PROVIDER_DISABLED,
    INSUFFICIENT_CONTEXT,
    PRIVACY_REJECTED,
    PROMPT_TOO_LARGE,
    UNKNOWN_CITATION,
    MISSING_CITATION,
    SENSITIVE_OUTPUT,
    UNSUPPORTED_ACTION_DIRECTIVE,
    INVALID_RESPONSE,
    PROVIDER_TIMEOUT,
    PROVIDER_UNAVAILABLE,
    RATE_LIMITED,
    AUTHENTICATION_FAILURE,
    AUTHORIZATION_FAILURE,
    CONFIGURATION_ERROR,
    QUOTA_EXCEEDED,
    IN_FLIGHT_LIMIT_EXCEEDED,
    CANCELLED,
    ABANDONED,
    DUPLICATE_SUPPRESSED
}

enum class KnowledgeAnswerRequestStatus {
    REQUESTED,
    RETRIEVING,
    GENERATING,
    VALIDATING,
    COMPLETED,
    INSUFFICIENT_CONTEXT,
    FAILED,
    REJECTED,
    ABANDONED
}

data class KnowledgeAnswerRequest(
    val query: String,
    val hotelId: UUID?,
    val actorUserId: UUID? = null,
    val retrievalMode: KnowledgeSearchMode = KnowledgeSearchMode.HYBRID,
    val categories: Set<com.hotelopai.knowledge.domain.KnowledgeCategory> = emptySet(),
    val language: String = "en",
    val contextLimit: Int? = null
)

data class KnowledgeCitation(
    val citationId: String,
    val documentReference: UUID,
    val chunkReference: UUID,
    val title: String,
    val category: com.hotelopai.knowledge.domain.KnowledgeCategory,
    val chunkPosition: Int,
    val retrievalScore: Double,
    val contentFingerprint: String,
    val excerpt: String? = null
)

data class KnowledgeAnswer(
    val id: KnowledgeAnswerId,
    val hotelId: UUID?,
    val providerId: String,
    val modelId: String,
    val promptTemplateId: String,
    val promptVersion: String,
    val retrievalMode: KnowledgeSearchMode,
    val contextSchemaVersion: String,
    val status: KnowledgeAnswerStatus,
    val confidence: KnowledgeAnswerConfidence?,
    val answerText: String?,
    val citations: List<KnowledgeCitation>,
    val requestFingerprint: String,
    val failureCategory: KnowledgeAnswerFailureCategory?,
    val actorUserId: UUID? = null,
    val createdAt: Instant,
    val updatedAt: Instant
)

data class KnowledgePromptContextItem(
    val citationId: String,
    val text: String,
    val citation: KnowledgeCitation
)

data class KnowledgePrompt(
    val templateId: String,
    val version: String,
    val systemInstructions: String,
    val operatorQuery: String,
    val contextItems: List<KnowledgePromptContextItem>,
    val outputSchema: String
) {
    val size: Int
        get() = systemInstructions.length + operatorQuery.length + outputSchema.length + contextItems.sumOf { it.text.length + it.citationId.length }
}

data class KnowledgeAnswerProviderResponse(
    val status: KnowledgeAnswerStatus,
    val answerText: String?,
    val confidence: KnowledgeAnswerConfidence?,
    val citationIds: List<String>,
    val failureCategory: KnowledgeAnswerFailureCategory? = null
)

interface KnowledgeAnswerProvider {
    val providerId: String
    val modelId: String
    val providerType: KnowledgeEmbeddingProviderType
        get() = KnowledgeEmbeddingProviderType.INTERNAL
    fun readiness(): KnowledgeEmbeddingProviderReadiness
    fun generate(prompt: KnowledgePrompt): KnowledgeAnswerProviderResponse
}

interface ExternalKnowledgeAnswerProvider : KnowledgeAnswerProvider {
    override val providerType: KnowledgeEmbeddingProviderType
        get() = KnowledgeEmbeddingProviderType.EXTERNAL
}

enum class KnowledgeAnswerProviderReadinessStatus {
    NOT_CONFIGURED,
    DISABLED,
    READY,
    READY_FOR_LOCAL_SMOKE,
    READY_FOR_NON_PRODUCTION,
    BLOCKED_BY_ENVIRONMENT,
    MISCONFIGURED,
    TEMPORARILY_UNAVAILABLE,
    PRODUCTION_BLOCKED
}

enum class KnowledgeAnswerEndpointClassification {
    LOCAL_STUB,
    EXTERNAL_HTTPS,
    EXTERNAL_HTTP,
    INVALID
}

enum class KnowledgeAnswerSmokeFixtureMode {
    SUCCESS,
    EMPTY_SUCCESS,
    MALFORMED_RESPONSE,
    TIMEOUT,
    RATE_LIMITED,
    AUTHENTICATION_FAILURE,
    PROVIDER_UNAVAILABLE
}

enum class KnowledgeAnswerProviderDiagnosticType {
    SMOKE_TEST
}

enum class KnowledgeAnswerProviderDiagnosticTrigger {
    OPERATOR
}

enum class KnowledgeAnswerProviderDiagnosticOutcome {
    SUCCEEDED,
    FAILED,
    REJECTED
}

enum class KnowledgeAnswerResponseValidationOutcome {
    NOT_APPLICABLE,
    VALID,
    INVALID
}

enum class KnowledgeAnswerFeedbackType {
    HELPFUL,
    NOT_HELPFUL,
    INSUFFICIENT,
    INCORRECT_SOURCE
}

data class KnowledgeAnswerProviderSummary(
    val providerId: String,
    val providerType: KnowledgeEmbeddingProviderType,
    val lifecycle: KnowledgeEmbeddingProviderLifecycle,
    val active: Boolean,
    val enabled: Boolean,
    val modelPresent: Boolean,
    val promptTemplateId: String,
    val promptVersion: String,
    val readiness: KnowledgeEmbeddingProviderReadiness
)

data class KnowledgeAnswerProviderReadiness(
    val providerId: String,
    val readiness: KnowledgeAnswerProviderReadinessStatus,
    val lifecycle: KnowledgeEmbeddingProviderLifecycle,
    val active: Boolean,
    val enabled: Boolean,
    val endpointClassification: KnowledgeAnswerEndpointClassification,
    val environmentClass: String,
    val fallbackConfigured: Boolean,
    val productionUseBlocked: Boolean,
    val lastSmokeOutcome: KnowledgeAnswerProviderDiagnosticOutcome?,
    val lastSmokeAt: Instant?,
    val lastSuccessfulSmokeAt: Instant?,
    val consecutiveFailureBand: String,
    val latencyBand: String,
    val validationOutcome: KnowledgeAnswerResponseValidationOutcome,
    val failureCategory: KnowledgeAnswerFailureCategory?,
    val blockingReasons: List<String>,
    val modelPresent: Boolean,
    val promptTemplateId: String,
    val promptVersion: String
)

data class KnowledgeAnswerProviderDiagnosticRecord(
    val id: UUID,
    val providerId: String,
    val diagnosticType: KnowledgeAnswerProviderDiagnosticType,
    val triggerType: KnowledgeAnswerProviderDiagnosticTrigger,
    val startedAt: Instant,
    val completedAt: Instant,
    val outcome: KnowledgeAnswerProviderDiagnosticOutcome,
    val failureCategory: KnowledgeAnswerFailureCategory?,
    val latencyBand: String,
    val retryCount: Int,
    val responseValidationOutcome: KnowledgeAnswerResponseValidationOutcome,
    val promptTemplateId: String,
    val promptVersion: String,
    val modelId: String,
    val environmentClass: String,
    val createdAt: Instant
)

data class KnowledgeAnswerProviderDiagnosticPage(
    val content: List<KnowledgeAnswerProviderDiagnosticRecord>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int
)

data class KnowledgeAnswerFeedback(
    val answerId: KnowledgeAnswerId,
    val feedbackType: KnowledgeAnswerFeedbackType,
    val actorUserId: UUID?,
    val createdAt: Instant
)

data class KnowledgeAnswerRequestLifecycle(
    val requestId: UUID,
    val answerId: KnowledgeAnswerId?,
    val originalRequestId: UUID?,
    val hotelId: UUID?,
    val actorUserId: UUID?,
    val providerId: String,
    val modelId: String,
    val retrievalMode: KnowledgeSearchMode,
    val requestFingerprint: String,
    val status: KnowledgeAnswerRequestStatus,
    val requestedAt: Instant,
    val completedAt: Instant?,
    val updatedAt: Instant,
    val failureCategory: KnowledgeAnswerFailureCategory?,
    val citationCountBand: String,
    val latencyBand: String
)

data class KnowledgeAnswerQuotaUsage(
    val hourlyLimit: Int,
    val hourlyUsed: Long,
    val dailyLimit: Int,
    val dailyUsed: Long,
    val inFlightLimit: Int,
    val inFlightUsed: Long
)

data class KnowledgeAnswerStatusCounts(
    val answered: Long,
    val insufficientContext: Long,
    val failed: Long
)

data class KnowledgeAnswerFeedbackAnalytics(
    val counts: Map<KnowledgeAnswerFeedbackType, Long>,
    val rates: Map<KnowledgeAnswerFeedbackType, Double>,
    val providerBreakdown: Map<String, Map<KnowledgeAnswerFeedbackType, Long>>,
    val retrievalModeBreakdown: Map<KnowledgeSearchMode, Map<KnowledgeAnswerFeedbackType, Long>>,
    val citationCountBandBreakdown: Map<String, Map<KnowledgeAnswerFeedbackType, Long>>
)

data class KnowledgeAnswerOperationsDashboard(
    val providerReadiness: KnowledgeAnswerProviderReadiness,
    val retrievalReadiness: KnowledgeRetrievalReadinessSummary,
    val recentAnswerCount: Long,
    val statusCounts: KnowledgeAnswerStatusCounts,
    val quotaUsage: KnowledgeAnswerQuotaUsage,
    val activeInFlightCount: Long,
    val abandonedRequestCount: Long,
    val feedbackAnalytics: KnowledgeAnswerFeedbackAnalytics,
    val citationCountBands: Map<String, Long>,
    val latencyBands: Map<String, Long>,
    val recentFailureCategories: Map<KnowledgeAnswerFailureCategory, Long>
)

data class KnowledgeAnswerSmokeTestResult(
    val diagnostic: KnowledgeAnswerProviderDiagnosticRecord,
    val readiness: KnowledgeAnswerProviderReadiness,
    val answerCount: Int
)

interface KnowledgeAnswerRepository {
    fun findDuplicate(hotelId: UUID?, requestFingerprint: String, since: Instant): KnowledgeAnswer?
    fun saveAnswer(answer: KnowledgeAnswer): KnowledgeAnswer
    fun answer(id: KnowledgeAnswerId, hotelId: UUID?): KnowledgeAnswer?
    fun answers(hotelId: UUID?, limit: Int, offset: Int): List<KnowledgeAnswer>
    fun cleanupAnswers(before: Instant, limit: Int): Int
    fun countAnswers(hotelId: UUID?, actorUserId: UUID?, since: Instant): Long
    fun saveFeedback(feedback: KnowledgeAnswerFeedback): KnowledgeAnswerFeedback
    fun feedbackFor(answerId: KnowledgeAnswerId): List<KnowledgeAnswerFeedback>
    fun acquireAnswerRequestLifecycle(
        hotelId: UUID?,
        actorUserId: UUID?,
        providerId: String,
        modelId: String,
        retrievalMode: KnowledgeSearchMode,
        requestFingerprint: String,
        inFlightLimit: Int,
        abandonedBefore: Instant,
        now: Instant
    ): KnowledgeAnswerRequestLifecycle?
    fun transitionAnswerRequest(
        requestId: UUID,
        status: KnowledgeAnswerRequestStatus,
        now: Instant,
        answerId: KnowledgeAnswerId? = null,
        failureCategory: KnowledgeAnswerFailureCategory? = null,
        citationCountBand: String? = null,
        latencyBand: String? = null
    ): KnowledgeAnswerRequestLifecycle?
    fun answerRequest(requestId: UUID, hotelId: UUID?): KnowledgeAnswerRequestLifecycle?
    fun activeAnswerRequests(hotelId: UUID?, limit: Int, offset: Int): List<KnowledgeAnswerRequestLifecycle>
    fun countActiveAnswerRequests(hotelId: UUID?, actorUserId: UUID?): Long
    fun countAbandonedAnswerRequests(hotelId: UUID?): Long
    fun recoverAbandonedAnswerRequests(before: Instant, now: Instant, limit: Int): Int
    fun cleanupAnswerRequestLifecycles(before: Instant, limit: Int): Int
    fun answerStatusCounts(hotelId: UUID?, since: Instant): KnowledgeAnswerStatusCounts
    fun answerCitationBands(hotelId: UUID?, since: Instant): Map<String, Long>
    fun answerLatencyBands(hotelId: UUID?, since: Instant): Map<String, Long>
    fun answerFailureCategories(hotelId: UUID?, since: Instant): Map<KnowledgeAnswerFailureCategory, Long>
    fun feedbackAnalytics(hotelId: UUID?, since: Instant): KnowledgeAnswerFeedbackAnalytics
    fun saveAnswerProviderDiagnostic(record: KnowledgeAnswerProviderDiagnosticRecord): KnowledgeAnswerProviderDiagnosticRecord
    fun answerProviderDiagnostics(providerId: String?, limit: Int, offset: Int): KnowledgeAnswerProviderDiagnosticPage
    fun answerProviderDiagnostic(id: UUID): KnowledgeAnswerProviderDiagnosticRecord?
    fun latestAnswerProviderDiagnostic(providerId: String): KnowledgeAnswerProviderDiagnosticRecord?
    fun latestSuccessfulAnswerProviderDiagnostic(providerId: String): KnowledgeAnswerProviderDiagnosticRecord?
    fun cleanupAnswerProviderDiagnostics(before: Instant, limit: Int): Int
}

data class KnowledgeEmbeddingScheduleState(
    val scheduleId: String,
    val paused: Boolean,
    val pausedAt: Instant? = null,
    val resumedAt: Instant? = null,
    val lastAttemptedAt: Instant? = null,
    val lastSuccessfulAt: Instant? = null,
    val lastEmbeddedCount: Int = 0,
    val lastFailureCategory: KnowledgeEmbeddingFailureCategory? = null,
    val updatedAt: Instant
)

data class KnowledgeEmbeddingScheduleStatus(
    val configuredEnabled: Boolean,
    val effectiveEnabled: Boolean,
    val paused: Boolean,
    val scheduleSummary: String,
    val batchSize: Int,
    val lastAttemptedAt: Instant?,
    val lastSuccessfulAt: Instant?,
    val lastEmbeddedCount: Int,
    val lastFailureCategory: KnowledgeEmbeddingFailureCategory?,
    val leaseState: String
)

data class KnowledgeSemanticCandidate(
    val chunkId: KnowledgeChunkId,
    val documentId: KnowledgeDocumentId,
    val title: String,
    val category: com.hotelopai.knowledge.domain.KnowledgeCategory,
    val source: com.hotelopai.knowledge.domain.KnowledgeSource,
    val language: String,
    val heading: String?,
    val snippet: String,
    val chunkOrder: Int,
    val tags: Set<String>,
    val updatedAt: Instant,
    val similarity: Double
)

data class KnowledgeSearchScore(
    val keywordScore: Double,
    val semanticScore: Double,
    val combinedScore: Double,
    val fallbackUsed: Boolean = false
)

interface KnowledgeEmbeddingRepository {
    fun findChunksNeedingEmbeddings(providerId: KnowledgeEmbeddingProviderId, modelId: String, limit: Int): List<KnowledgeEmbeddingRequest>
    fun findEmbedding(chunkId: KnowledgeChunkId, providerId: KnowledgeEmbeddingProviderId, modelId: String): KnowledgeEmbeddingRecord?
    fun upsertReady(record: KnowledgeEmbeddingRecord): KnowledgeEmbeddingRecord
    fun markFailed(record: KnowledgeEmbeddingRecord): KnowledgeEmbeddingRecord
    fun markDocumentStale(documentId: KnowledgeDocumentId, providerId: KnowledgeEmbeddingProviderId, modelId: String, now: Instant): Int
    fun semanticCandidates(providerId: KnowledgeEmbeddingProviderId, modelId: String, hotelId: UUID?, queryVector: KnowledgeEmbeddingVector, limit: Int): List<KnowledgeSemanticCandidate>
    fun failed(providerId: KnowledgeEmbeddingProviderId, modelId: String, limit: Int): List<KnowledgeEmbeddingRecord>
    fun counts(providerId: KnowledgeEmbeddingProviderId, modelId: String): Map<KnowledgeEmbeddingStatus, Long>
    fun countChunks(): Long
    fun saveDiagnostic(record: KnowledgeEmbeddingDiagnosticRecord): KnowledgeEmbeddingDiagnosticRecord
    fun diagnostics(providerId: String?, limit: Int, offset: Int): List<KnowledgeEmbeddingDiagnosticRecord>
    fun findDiagnostic(id: UUID): KnowledgeEmbeddingDiagnosticRecord?
    fun cleanupDiagnostics(before: Instant, limit: Int): Int
    fun getOrCreateScheduleState(scheduleId: String, now: Instant): KnowledgeEmbeddingScheduleState
    fun markSchedulePaused(scheduleId: String, now: Instant): KnowledgeEmbeddingScheduleState
    fun markScheduleResumed(scheduleId: String, now: Instant): KnowledgeEmbeddingScheduleState
    fun recordScheduleAttempt(scheduleId: String, summary: KnowledgeEmbeddingBatchSummary, now: Instant, failure: KnowledgeEmbeddingFailureCategory?): KnowledgeEmbeddingScheduleState
}

class KnowledgeSemanticSearchUnavailableException(message: String) : RuntimeException(message)
