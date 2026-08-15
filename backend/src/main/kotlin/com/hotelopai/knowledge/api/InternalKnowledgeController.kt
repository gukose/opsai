package com.hotelopai.knowledge.api

import com.hotelopai.knowledge.application.KnowledgeBaseService
import com.hotelopai.knowledge.application.KnowledgeAnswer
import com.hotelopai.knowledge.application.KnowledgeAnswerFeedback
import com.hotelopai.knowledge.application.KnowledgeAnswerFeedbackType
import com.hotelopai.knowledge.application.KnowledgeAnswerProviderDiagnosticPage
import com.hotelopai.knowledge.application.KnowledgeAnswerProviderDiagnosticRecord
import com.hotelopai.knowledge.application.KnowledgeAnswerProviderReadiness
import com.hotelopai.knowledge.application.KnowledgeAnswerProviderSummary
import com.hotelopai.knowledge.application.KnowledgeAnswerFeedbackAnalytics
import com.hotelopai.knowledge.application.KnowledgeAnswerOperationsDashboard
import com.hotelopai.knowledge.application.KnowledgeAnswerQuotaUsage
import com.hotelopai.knowledge.application.KnowledgeAnswerRequest
import com.hotelopai.knowledge.application.KnowledgeAnswerRequestLifecycle
import com.hotelopai.knowledge.application.KnowledgeAnswerService
import com.hotelopai.knowledge.application.KnowledgeAnswerStatusCounts
import com.hotelopai.knowledge.application.KnowledgeAnswerSmokeFixtureMode
import com.hotelopai.knowledge.application.KnowledgeAnswerSmokeTestResult
import com.hotelopai.knowledge.application.KnowledgeCitation
import com.hotelopai.knowledge.application.KnowledgeContextAssembler
import com.hotelopai.knowledge.application.KnowledgeContextAssemblyRequest
import com.hotelopai.knowledge.application.KnowledgeContextAssemblyResult
import com.hotelopai.knowledge.application.KnowledgeContextItem
import com.hotelopai.knowledge.application.KnowledgeCuratedDatasetValidationReport
import com.hotelopai.knowledge.application.KnowledgeDocumentFilter
import com.hotelopai.knowledge.application.KnowledgeDocumentNotFoundException
import com.hotelopai.knowledge.application.KnowledgeDocumentPage
import com.hotelopai.knowledge.application.KnowledgeEmbeddingBatchSummary
import com.hotelopai.knowledge.application.KnowledgeEmbeddingRecord
import com.hotelopai.knowledge.application.KnowledgeEmbeddingDiagnosticRecord
import com.hotelopai.knowledge.application.KnowledgeEmbeddingProviderSummary
import com.hotelopai.knowledge.application.KnowledgeEmbeddingScheduleStatus
import com.hotelopai.knowledge.application.KnowledgeEmbeddingService
import com.hotelopai.knowledge.application.KnowledgeEmbeddingStatusSummary
import com.hotelopai.knowledge.application.KnowledgeRetrievalReadinessSummary
import com.hotelopai.knowledge.application.KnowledgeRetrievalEvaluationCase
import com.hotelopai.knowledge.application.KnowledgeRetrievalEvaluationRequest
import com.hotelopai.knowledge.application.KnowledgeRetrievalEvaluationRun
import com.hotelopai.knowledge.application.KnowledgeRetrievalEvaluationService
import com.hotelopai.knowledge.application.KnowledgeRetrievalMetricSummary
import com.hotelopai.knowledge.application.KnowledgeRetrievalQualityGateService
import com.hotelopai.knowledge.application.KnowledgeRetrievalQualityModeReport
import com.hotelopai.knowledge.application.KnowledgeRetrievalQualityReport
import com.hotelopai.knowledge.application.KnowledgeImportCommand
import com.hotelopai.knowledge.application.KnowledgeImportContentType
import com.hotelopai.knowledge.application.KnowledgeSearchMode
import com.hotelopai.knowledge.application.KnowledgeSearchPage
import com.hotelopai.knowledge.application.KnowledgeSearchQuery
import com.hotelopai.knowledge.application.KnowledgeSearchResult
import com.hotelopai.knowledge.application.KnowledgeSemanticSearchUnavailableException
import com.hotelopai.knowledge.domain.KnowledgeCategory
import com.hotelopai.knowledge.domain.KnowledgeChunk
import com.hotelopai.knowledge.domain.KnowledgeDocument
import com.hotelopai.knowledge.domain.KnowledgeDocumentId
import com.hotelopai.knowledge.domain.KnowledgeMetadata
import com.hotelopai.knowledge.domain.KnowledgeSource
import com.hotelopai.shared.security.PermissionExpressions
import com.hotelopai.shared.security.CurrentUserContextResolver
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
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
@RequestMapping("/api/v1/internal/knowledge")
class InternalKnowledgeController(
    private val service: KnowledgeBaseService,
    private val embeddingService: KnowledgeEmbeddingService,
    private val evaluationService: KnowledgeRetrievalEvaluationService,
    private val qualityGateService: KnowledgeRetrievalQualityGateService,
    private val contextAssembler: KnowledgeContextAssembler,
    private val answerService: KnowledgeAnswerService,
    private val currentUserContextResolver: CurrentUserContextResolver
) {
    @PostMapping("/documents/import")
    @PreAuthorize(PermissionExpressions.KNOWLEDGE_OPERATIONS)
    fun importDocument(@RequestBody request: KnowledgeImportRequest): KnowledgeDocumentResponse =
        safely {
            service.importDocument(
                KnowledgeImportCommand(
                    hotelId = currentUserContextResolver.current().hotelId,
                    title = request.title,
                    category = request.category,
                    source = request.source,
                    language = request.language,
                    content = request.content,
                    contentType = request.contentType,
                    metadata = KnowledgeMetadata(request.tags.toSet(), request.metadata)
                )
            ).toResponse(includeChunks = true)
        }

    @GetMapping("/documents")
    @PreAuthorize(PermissionExpressions.KNOWLEDGE_OPERATIONS)
    fun documents(
        @RequestParam(required = false) category: KnowledgeCategory?,
        @RequestParam(required = false) source: KnowledgeSource?,
        @RequestParam(required = false) tag: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): KnowledgeDocumentPageResponse =
        service.documents(KnowledgeDocumentFilter(hotelId = currentUserContextResolver.current().hotelId, category = category, source = source, tag = tag, page = page, size = size)).toResponse()

    @GetMapping("/documents/{documentId}")
    @PreAuthorize(PermissionExpressions.KNOWLEDGE_OPERATIONS)
    fun document(@PathVariable documentId: UUID): KnowledgeDocumentResponse =
        safely { service.detail(KnowledgeDocumentId(documentId), currentUserContextResolver.current().hotelId).toResponse(includeChunks = true) }

    @DeleteMapping("/documents/{documentId}")
    @PreAuthorize(PermissionExpressions.KNOWLEDGE_OPERATIONS)
    fun delete(@PathVariable documentId: UUID): KnowledgeDeleteResponse =
        KnowledgeDeleteResponse(service.delete(KnowledgeDocumentId(documentId), currentUserContextResolver.current().hotelId))

    @PostMapping("/documents/{documentId}/rechunk")
    @PreAuthorize(PermissionExpressions.KNOWLEDGE_OPERATIONS)
    fun rechunk(@PathVariable documentId: UUID): KnowledgeDocumentResponse =
        safely { service.rechunk(KnowledgeDocumentId(documentId), currentUserContextResolver.current().hotelId).toResponse(includeChunks = true) }

    @GetMapping("/search")
    @PreAuthorize(PermissionExpressions.KNOWLEDGE_OPERATIONS)
    fun search(
        @RequestParam query: String,
        @RequestParam(required = false) category: KnowledgeCategory?,
        @RequestParam(required = false) tag: String?,
        @RequestParam(defaultValue = "KEYWORD") mode: KnowledgeSearchMode,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): KnowledgeSearchPageResponse =
        safely { service.search(KnowledgeSearchQuery(query = query, hotelId = currentUserContextResolver.current().hotelId, category = category, tag = tag, mode = mode, page = page, size = size)).toResponse() }

    @GetMapping("/embeddings/status")
    @PreAuthorize(PermissionExpressions.KNOWLEDGE_OPERATIONS)
    fun embeddingStatus(): KnowledgeEmbeddingStatusResponse =
        embeddingService.status().toResponse()

    @GetMapping("/embeddings/providers")
    @PreAuthorize(PermissionExpressions.KNOWLEDGE_OPERATIONS)
    fun embeddingProviders(): List<KnowledgeEmbeddingProviderResponse> =
        embeddingService.providers().map { it.toResponse() }

    @GetMapping("/embeddings/retrieval-readiness")
    @PreAuthorize(PermissionExpressions.KNOWLEDGE_OPERATIONS)
    fun retrievalReadiness(): KnowledgeRetrievalReadinessResponse =
        embeddingService.retrievalReadiness().toResponse()

    @GetMapping("/retrieval/readiness-report")
    @PreAuthorize(PermissionExpressions.KNOWLEDGE_OPERATIONS)
    fun retrievalReadinessReport(): KnowledgeRetrievalReadinessResponse =
        evaluationService.readinessReport().toResponse()

    @PostMapping("/retrieval/evaluations/run")
    @PreAuthorize(PermissionExpressions.KNOWLEDGE_OPERATIONS)
    fun runRetrievalEvaluation(@RequestBody request: KnowledgeRetrievalEvaluationRunRequest): KnowledgeRetrievalEvaluationRunResponse =
        safely { evaluationService.runEvaluation(request.toCommand(currentUserContextResolver.current().hotelId)).toResponse() }

    @PostMapping("/retrieval/benchmark")
    @PreAuthorize(PermissionExpressions.KNOWLEDGE_OPERATIONS)
    fun runRetrievalBenchmark(@RequestBody request: KnowledgeRetrievalEvaluationRunRequest): KnowledgeRetrievalEvaluationRunResponse =
        safely { evaluationService.benchmark(request.toCommand(currentUserContextResolver.current().hotelId)).toResponse() }

    @GetMapping("/retrieval/evaluations")
    @PreAuthorize(PermissionExpressions.KNOWLEDGE_OPERATIONS)
    fun retrievalEvaluationHistory(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): List<KnowledgeRetrievalEvaluationRunResponse> =
        evaluationService.history(page, size).map { it.toResponse() }

    @GetMapping("/retrieval/evaluations/{runId}")
    @PreAuthorize(PermissionExpressions.KNOWLEDGE_OPERATIONS)
    fun retrievalEvaluationDetail(@PathVariable runId: UUID): KnowledgeRetrievalEvaluationRunResponse =
        evaluationService.detail(runId)?.toResponse()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Knowledge retrieval evaluation run not found.")

    @GetMapping("/retrieval/curated-dataset/validate")
    @PreAuthorize(PermissionExpressions.KNOWLEDGE_OPERATIONS)
    fun validateCuratedRetrievalDataset(): KnowledgeCuratedDatasetValidationResponse =
        qualityGateService.validateCuratedDataset().toResponse()

    @PostMapping("/retrieval/quality-gate/run")
    @PreAuthorize(PermissionExpressions.KNOWLEDGE_OPERATIONS)
    fun runRetrievalQualityGate(): KnowledgeRetrievalQualityReportResponse =
        safely { qualityGateService.executeQualityGate(currentUserContextResolver.current().hotelId).toResponse() }

    @GetMapping("/retrieval/quality-gate/latest")
    @PreAuthorize(PermissionExpressions.KNOWLEDGE_OPERATIONS)
    fun latestRetrievalQualityGate(): KnowledgeRetrievalQualityReportResponse =
        qualityGateService.latestQualityReport()?.toResponse()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Knowledge retrieval quality report not found.")

    @PostMapping("/retrieval/context/assemble")
    @PreAuthorize(PermissionExpressions.KNOWLEDGE_OPERATIONS)
    fun assembleRagContext(@RequestBody request: KnowledgeContextAssemblyRequestBody): KnowledgeContextAssemblyResponse =
        safely {
            contextAssembler.assemble(
                KnowledgeContextAssemblyRequest(
                    query = request.query,
                    hotelId = currentUserContextResolver.current().hotelId,
                    mode = request.mode,
                    categories = request.categories,
                    language = request.language,
                    limit = request.limit
                )
            ).toResponse()
        }

    @PostMapping("/answers/test-query")
    @PreAuthorize(PermissionExpressions.KNOWLEDGE_OPERATIONS)
    fun answerTestQuery(@RequestBody request: KnowledgeAnswerRequestBody): KnowledgeAnswerResponse =
        safely {
            val current = currentUserContextResolver.current()
            answerService.answer(
                KnowledgeAnswerRequest(
                    query = request.query,
                    hotelId = current.hotelId,
                    actorUserId = current.userId,
                    retrievalMode = request.retrievalMode,
                    categories = request.categories,
                    language = request.language,
                    contextLimit = request.contextLimit
                )
            ).toResponse()
        }

    @GetMapping("/answers/dashboard")
    @PreAuthorize(PermissionExpressions.KNOWLEDGE_OPERATIONS)
    fun answerDashboard(): KnowledgeAnswerDashboardResponse {
        val current = currentUserContextResolver.current()
        return answerService.dashboard(current.hotelId, current.userId).toResponse()
    }

    @GetMapping("/answers/requests/active")
    @PreAuthorize(PermissionExpressions.KNOWLEDGE_OPERATIONS)
    fun activeAnswerRequests(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): List<KnowledgeAnswerRequestLifecycleResponse> =
        answerService.activeRequests(currentUserContextResolver.current().hotelId, page, size).map { it.toResponse() }

    @GetMapping("/answers/requests/{requestId}")
    @PreAuthorize(PermissionExpressions.KNOWLEDGE_OPERATIONS)
    fun answerRequestDetail(@PathVariable requestId: UUID): KnowledgeAnswerRequestLifecycleResponse =
        answerService.requestDetail(requestId, currentUserContextResolver.current().hotelId)?.toResponse()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Knowledge answer request not found.")

    @PostMapping("/answers/requests/{requestId}/cancel")
    @PreAuthorize(PermissionExpressions.KNOWLEDGE_OPERATIONS)
    fun cancelAnswerRequest(@PathVariable requestId: UUID): KnowledgeAnswerRequestLifecycleResponse {
        val current = currentUserContextResolver.current()
        return safely { answerService.cancelRequest(requestId, current.hotelId, current.userId).toResponse() }
    }

    @PostMapping("/answers/requests/recover-abandoned")
    @PreAuthorize(PermissionExpressions.KNOWLEDGE_OPERATIONS)
    fun recoverAbandonedAnswerRequests(): KnowledgeEmbeddingCleanupResponse =
        KnowledgeEmbeddingCleanupResponse(answerService.recoverAbandonedRequests())

    @GetMapping("/answers/feedback/analytics")
    @PreAuthorize(PermissionExpressions.KNOWLEDGE_OPERATIONS)
    fun answerFeedbackAnalytics(): KnowledgeAnswerFeedbackAnalyticsResponse {
        val current = currentUserContextResolver.current()
        return answerService.dashboard(current.hotelId, current.userId).feedbackAnalytics.toResponse()
    }

    @GetMapping("/answers")
    @PreAuthorize(PermissionExpressions.KNOWLEDGE_OPERATIONS)
    fun answerHistory(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): List<KnowledgeAnswerResponse> =
        answerService.history(currentUserContextResolver.current().hotelId, page, size).map { it.toResponse() }

    @GetMapping("/answers/{answerId}")
    @PreAuthorize(PermissionExpressions.KNOWLEDGE_OPERATIONS)
    fun answerDetail(@PathVariable answerId: UUID): KnowledgeAnswerResponse =
        answerService.detail(answerId, currentUserContextResolver.current().hotelId)?.toResponse()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Knowledge answer not found.")

    @PostMapping("/answers/{answerId}/retry")
    @PreAuthorize(PermissionExpressions.KNOWLEDGE_OPERATIONS)
    fun retryAnswer(@PathVariable answerId: UUID): KnowledgeAnswerResponse =
        safely { answerService.retry(answerId, currentUserContextResolver.current().hotelId).toResponse() }

    @PostMapping("/answers/{answerId}/feedback")
    @PreAuthorize(PermissionExpressions.KNOWLEDGE_OPERATIONS)
    fun submitAnswerFeedback(
        @PathVariable answerId: UUID,
        @RequestBody request: KnowledgeAnswerFeedbackRequest
    ): KnowledgeAnswerFeedbackResponse =
        safely {
            val current = currentUserContextResolver.current()
            answerService.submitFeedback(answerId, current.hotelId, current.userId, request.feedbackType).toResponse()
        }

    @GetMapping("/answers/{answerId}/feedback")
    @PreAuthorize(PermissionExpressions.KNOWLEDGE_OPERATIONS)
    fun answerFeedback(@PathVariable answerId: UUID): List<KnowledgeAnswerFeedbackResponse> =
        safely { answerService.feedback(answerId, currentUserContextResolver.current().hotelId).map { it.toResponse() } }

    @PostMapping("/answers/cleanup")
    @PreAuthorize(PermissionExpressions.KNOWLEDGE_OPERATIONS)
    fun cleanupAnswers(): KnowledgeEmbeddingCleanupResponse =
        KnowledgeEmbeddingCleanupResponse(answerService.cleanup())

    @GetMapping("/answers/provider-readiness")
    @PreAuthorize(PermissionExpressions.KNOWLEDGE_OPERATIONS)
    fun answerProviderReadiness(): KnowledgeAnswerProviderReadinessResponse =
        KnowledgeAnswerProviderReadinessResponse(answerService.providerReadiness().name)

    @GetMapping("/answers/providers")
    @PreAuthorize(PermissionExpressions.KNOWLEDGE_OPERATIONS)
    fun answerProviders(): List<KnowledgeAnswerProviderSummaryResponse> =
        answerService.providerSummaries().map { it.toResponse() }

    @GetMapping("/answers/providers/{providerId}/readiness")
    @PreAuthorize(PermissionExpressions.KNOWLEDGE_OPERATIONS)
    fun answerProviderReadiness(@PathVariable providerId: String): KnowledgeAnswerProviderReadinessDetailResponse =
        answerService.providerReadiness(providerId).toResponse()

    @PostMapping("/answers/providers/{providerId}/smoke-test")
    @PreAuthorize(PermissionExpressions.KNOWLEDGE_OPERATIONS)
    fun smokeTestAnswerProvider(
        @PathVariable providerId: String,
        @RequestBody request: KnowledgeAnswerSmokeTestRequest
    ): KnowledgeAnswerSmokeTestResponse =
        safely { answerService.smokeTest(providerId, request.fixtureMode, currentUserContextResolver.current().userId).toResponse() }

    @GetMapping("/answers/providers/diagnostics")
    @PreAuthorize(PermissionExpressions.KNOWLEDGE_OPERATIONS)
    fun answerProviderDiagnostics(
        @RequestParam(required = false) providerId: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): KnowledgeAnswerProviderDiagnosticPageResponse =
        answerService.diagnostics(providerId, page, size).toResponse()

    @GetMapping("/answers/providers/diagnostics/{diagnosticId}")
    @PreAuthorize(PermissionExpressions.KNOWLEDGE_OPERATIONS)
    fun answerProviderDiagnostic(@PathVariable diagnosticId: UUID): KnowledgeAnswerProviderDiagnosticResponse =
        answerService.diagnostic(diagnosticId)?.toResponse()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Knowledge answer provider diagnostic not found.")

    @PostMapping("/answers/providers/diagnostics/cleanup")
    @PreAuthorize(PermissionExpressions.KNOWLEDGE_OPERATIONS)
    fun cleanupAnswerProviderDiagnostics(): KnowledgeEmbeddingCleanupResponse =
        KnowledgeEmbeddingCleanupResponse(answerService.cleanupDiagnostics())

    @PostMapping("/embeddings/generate")
    @PreAuthorize(PermissionExpressions.KNOWLEDGE_OPERATIONS)
    fun generateEmbeddings(@RequestBody request: KnowledgeEmbeddingBatchRequest): KnowledgeEmbeddingBatchResponse =
        safely { embeddingService.generateBatch(request.limit ?: 20).toResponse() }

    @PostMapping("/documents/{documentId}/embeddings/regenerate")
    @PreAuthorize(PermissionExpressions.KNOWLEDGE_OPERATIONS)
    fun regenerateDocumentEmbeddings(@PathVariable documentId: UUID): KnowledgeEmbeddingBatchResponse =
        safely { embeddingService.regenerateDocument(KnowledgeDocumentId(documentId)).toResponse() }

    @GetMapping("/embeddings/failures")
    @PreAuthorize(PermissionExpressions.KNOWLEDGE_OPERATIONS)
    fun failedEmbeddings(@RequestParam(defaultValue = "20") limit: Int): List<KnowledgeEmbeddingFailureResponse> =
        safely { embeddingService.failed(limit).map { it.toResponse() } }

    @PostMapping("/embeddings/retry")
    @PreAuthorize(PermissionExpressions.KNOWLEDGE_OPERATIONS)
    fun retryEmbeddings(@RequestBody request: KnowledgeEmbeddingBatchRequest): KnowledgeEmbeddingBatchResponse =
        safely { embeddingService.retryFailures(request.limit ?: 20).toResponse() }

    @GetMapping("/embeddings/diagnostics")
    @PreAuthorize(PermissionExpressions.KNOWLEDGE_OPERATIONS)
    fun embeddingDiagnostics(
        @RequestParam(required = false) providerId: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): List<KnowledgeEmbeddingDiagnosticResponse> =
        embeddingService.diagnostics(providerId, page, size).map { it.toResponse() }

    @GetMapping("/embeddings/diagnostics/{diagnosticId}")
    @PreAuthorize(PermissionExpressions.KNOWLEDGE_OPERATIONS)
    fun embeddingDiagnostic(@PathVariable diagnosticId: UUID): KnowledgeEmbeddingDiagnosticResponse =
        embeddingService.diagnostic(diagnosticId)?.toResponse()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Knowledge embedding diagnostic not found.")

    @PostMapping("/embeddings/diagnostics/cleanup")
    @PreAuthorize(PermissionExpressions.KNOWLEDGE_OPERATIONS)
    fun cleanupEmbeddingDiagnostics(): KnowledgeEmbeddingCleanupResponse =
        KnowledgeEmbeddingCleanupResponse(embeddingService.cleanupDiagnostics())

    @GetMapping("/embeddings/schedule")
    @PreAuthorize(PermissionExpressions.KNOWLEDGE_OPERATIONS)
    fun embeddingScheduleStatus(): KnowledgeEmbeddingScheduleStatusResponse =
        embeddingService.scheduleStatus().toResponse()

    @PostMapping("/embeddings/schedule/run-now")
    @PreAuthorize(PermissionExpressions.KNOWLEDGE_OPERATIONS)
    fun runEmbeddingScheduleNow(): KnowledgeEmbeddingBatchResponse =
        safely { embeddingService.runScheduledNow().toResponse() }

    @PostMapping("/embeddings/schedule/pause")
    @PreAuthorize(PermissionExpressions.KNOWLEDGE_OPERATIONS)
    fun pauseEmbeddingSchedule(): KnowledgeEmbeddingScheduleStatusResponse =
        embeddingService.pauseSchedule().toResponse()

    @PostMapping("/embeddings/schedule/resume")
    @PreAuthorize(PermissionExpressions.KNOWLEDGE_OPERATIONS)
    fun resumeEmbeddingSchedule(): KnowledgeEmbeddingScheduleStatusResponse =
        embeddingService.resumeSchedule().toResponse()

    private fun <T> safely(block: () -> T): T =
        try {
            block()
        } catch (exception: KnowledgeDocumentNotFoundException) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Knowledge document not found.", exception)
        } catch (exception: IllegalArgumentException) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid knowledge request.", exception)
        } catch (exception: KnowledgeSemanticSearchUnavailableException) {
            throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Knowledge semantic search is unavailable.", exception)
        }
}

data class KnowledgeImportRequest(
    val title: String,
    val category: KnowledgeCategory,
    val source: KnowledgeSource,
    val language: String = "en",
    val content: String,
    val contentType: KnowledgeImportContentType,
    val tags: List<String> = emptyList(),
    val metadata: Map<String, String> = emptyMap()
)

data class KnowledgeDocumentResponse(
    val id: UUID,
    val title: String,
    val category: KnowledgeCategory,
    val source: KnowledgeSource,
    val language: String,
    val tags: Set<String>,
    val metadataKeys: Set<String>,
    val chunkCount: Int,
    val chunks: List<KnowledgeChunkResponse>,
    val createdAt: Instant,
    val updatedAt: Instant
)

data class KnowledgeChunkResponse(
    val id: UUID,
    val order: Int,
    val heading: String?,
    val text: String
)

data class KnowledgeDocumentPageResponse(
    val content: List<KnowledgeDocumentResponse>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int
)

data class KnowledgeSearchResultResponse(
    val documentId: UUID,
    val chunkId: UUID,
    val title: String,
    val category: KnowledgeCategory,
    val source: KnowledgeSource,
    val language: String,
    val heading: String?,
    val snippet: String,
    val chunkOrder: Int,
    val rank: Int,
    val score: KnowledgeSearchScoreResponse?,
    val tags: Set<String>,
    val updatedAt: Instant
)

data class KnowledgeSearchPageResponse(
    val content: List<KnowledgeSearchResultResponse>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int
)

data class KnowledgeDeleteResponse(val deleted: Boolean)

data class KnowledgeEmbeddingBatchRequest(val limit: Int? = null)

data class KnowledgeRetrievalEvaluationRunRequest(
    val name: String,
    val cases: List<KnowledgeRetrievalEvaluationCaseRequest>,
    val modes: Set<KnowledgeSearchMode> = setOf(KnowledgeSearchMode.KEYWORD, KnowledgeSearchMode.SEMANTIC, KnowledgeSearchMode.HYBRID),
    val k: Int = 5
)

data class KnowledgeRetrievalEvaluationCaseRequest(
    val query: String,
    val expectedDocumentIds: Set<UUID> = emptySet(),
    val expectedChunkIds: Set<UUID> = emptySet(),
    val relevanceScore: Double? = null
)

data class KnowledgeEmbeddingStatusResponse(
    val semanticEnabled: Boolean,
    val providerId: String,
    val modelPresent: Boolean,
    val dimension: Int,
    val readiness: String,
    val readyCount: Long,
    val failedCount: Long,
    val staleCount: Long
)

data class KnowledgeEmbeddingProviderResponse(
    val providerId: String,
    val providerType: String,
    val lifecycle: String,
    val active: Boolean,
    val modelPresent: Boolean,
    val dimension: Int,
    val readiness: String,
    val failureCategory: String?
)

data class KnowledgeRetrievalReadinessResponse(
    val state: String,
    val semanticEnabled: Boolean,
    val providerId: String,
    val providerReadiness: String,
    val embeddedPercentage: Int,
    val totalChunks: Long,
    val readyCount: Long,
    val failedCount: Long,
    val staleCount: Long,
    val schedulerPaused: Boolean,
    val blockingReasons: List<String>,
    val evaluationStatus: String?,
    val latestEvaluationRunId: UUID?,
    val qualityGateStatus: String?
)

data class KnowledgeRetrievalEvaluationRunResponse(
    val id: UUID,
    val name: String,
    val status: String,
    val caseCount: Int,
    val k: Int,
    val modes: Set<String>,
    val startedAt: Instant,
    val completedAt: Instant,
    val failureCategory: String?,
    val metrics: List<KnowledgeRetrievalMetricResponse>
)

data class KnowledgeRetrievalMetricResponse(
    val mode: String,
    val precisionAtK: Double,
    val recallAtK: Double,
    val meanReciprocalRank: Double,
    val normalizedDiscountedCumulativeGain: Double,
    val hitRate: Double,
    val averageLatencyMillis: Long,
    val averageRetrievedChunks: Double,
    val scoreBand: String
)

data class KnowledgeCuratedDatasetValidationResponse(
    val datasetVersion: String,
    val valid: Boolean,
    val documentCount: Int,
    val caseCount: Int,
    val failureReasons: List<String>
)

data class KnowledgeRetrievalQualityReportResponse(
    val datasetVersion: String,
    val runId: UUID?,
    val outcome: String,
    val evaluatedQueryCount: Int,
    val k: Int,
    val modeReports: List<KnowledgeRetrievalQualityModeReportResponse>,
    val failedThresholds: List<String>
)

data class KnowledgeRetrievalQualityModeReportResponse(
    val mode: String,
    val outcome: String,
    val failedThresholds: List<String>,
    val metrics: KnowledgeRetrievalMetricResponse?
)

data class KnowledgeContextAssemblyRequestBody(
    val query: String,
    val mode: KnowledgeSearchMode = KnowledgeSearchMode.HYBRID,
    val categories: Set<KnowledgeCategory> = emptySet(),
    val language: String = "en",
    val limit: Int? = null
)

data class KnowledgeContextAssemblyResponse(
    val mode: String,
    val itemCount: Int,
    val totalCharacters: Int,
    val duplicateCount: Int,
    val items: List<KnowledgeContextItemResponse>
)

data class KnowledgeContextItemResponse(
    val documentReference: UUID,
    val chunkReference: UUID,
    val title: String,
    val category: KnowledgeCategory,
    val chunkPosition: Int,
    val selectedText: String,
    val retrievalMode: String,
    val score: KnowledgeSearchScoreResponse,
    val contentFingerprint: String
)

data class KnowledgeAnswerRequestBody(
    val query: String,
    val retrievalMode: KnowledgeSearchMode = KnowledgeSearchMode.HYBRID,
    val categories: Set<KnowledgeCategory> = emptySet(),
    val language: String = "en",
    val contextLimit: Int? = null
)

data class KnowledgeAnswerFeedbackRequest(
    val feedbackType: KnowledgeAnswerFeedbackType
)

data class KnowledgeAnswerResponse(
    val id: UUID,
    val providerId: String,
    val modelPresent: Boolean,
    val promptTemplateId: String,
    val promptVersion: String,
    val retrievalMode: String,
    val contextSchemaVersion: String,
    val status: String,
    val confidence: String?,
    val answerText: String?,
    val citations: List<KnowledgeAnswerCitationResponse>,
    val failureCategory: String?,
    val createdAt: Instant,
    val updatedAt: Instant
)

data class KnowledgeAnswerCitationResponse(
    val citationId: String,
    val documentReference: UUID,
    val chunkReference: UUID,
    val title: String,
    val category: KnowledgeCategory,
    val chunkPosition: Int,
    val retrievalScore: Double,
    val contentFingerprint: String,
    val excerpt: String?
)

data class KnowledgeAnswerProviderReadinessResponse(val readiness: String)

data class KnowledgeAnswerProviderSummaryResponse(
    val providerId: String,
    val providerType: String,
    val lifecycle: String,
    val active: Boolean,
    val enabled: Boolean,
    val modelPresent: Boolean,
    val promptTemplateId: String,
    val promptVersion: String,
    val readiness: String
)

data class KnowledgeAnswerProviderReadinessDetailResponse(
    val providerId: String,
    val readiness: String,
    val lifecycle: String,
    val active: Boolean,
    val enabled: Boolean,
    val endpointClassification: String,
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
    val modelPresent: Boolean,
    val promptTemplateId: String,
    val promptVersion: String
)

data class KnowledgeAnswerSmokeTestRequest(
    val fixtureMode: KnowledgeAnswerSmokeFixtureMode? = null
)

data class KnowledgeAnswerSmokeTestResponse(
    val diagnostic: KnowledgeAnswerProviderDiagnosticResponse,
    val readiness: KnowledgeAnswerProviderReadinessDetailResponse,
    val answerCount: Int
)

data class KnowledgeAnswerProviderDiagnosticPageResponse(
    val content: List<KnowledgeAnswerProviderDiagnosticResponse>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int
)

data class KnowledgeAnswerProviderDiagnosticResponse(
    val id: UUID,
    val providerId: String,
    val diagnosticType: String,
    val triggerType: String,
    val startedAt: Instant,
    val completedAt: Instant,
    val outcome: String,
    val failureCategory: String?,
    val latencyBand: String,
    val retryCount: Int,
    val responseValidationOutcome: String,
    val promptTemplateId: String,
    val promptVersion: String,
    val modelPresent: Boolean,
    val environmentClass: String,
    val createdAt: Instant
)

data class KnowledgeAnswerFeedbackResponse(
    val feedbackType: String,
    val createdAt: Instant
)

data class KnowledgeAnswerRequestLifecycleResponse(
    val requestId: UUID,
    val answerIdPresent: Boolean,
    val originalRequestIdPresent: Boolean,
    val providerId: String,
    val modelPresent: Boolean,
    val retrievalMode: String,
    val status: String,
    val requestedAt: Instant,
    val completedAt: Instant?,
    val updatedAt: Instant,
    val failureCategory: String?,
    val citationCountBand: String,
    val latencyBand: String
)

data class KnowledgeAnswerQuotaUsageResponse(
    val hourlyLimit: Int,
    val hourlyUsed: Long,
    val dailyLimit: Int,
    val dailyUsed: Long,
    val inFlightLimit: Int,
    val inFlightUsed: Long
)

data class KnowledgeAnswerStatusCountsResponse(
    val answered: Long,
    val insufficientContext: Long,
    val failed: Long
)

data class KnowledgeAnswerFeedbackAnalyticsResponse(
    val counts: Map<String, Long>,
    val rates: Map<String, Double>,
    val providerBreakdown: Map<String, Map<String, Long>>,
    val retrievalModeBreakdown: Map<String, Map<String, Long>>,
    val citationCountBandBreakdown: Map<String, Map<String, Long>>
)

data class KnowledgeAnswerDashboardResponse(
    val providerReadiness: KnowledgeAnswerProviderReadinessDetailResponse,
    val retrievalReadiness: KnowledgeRetrievalReadinessResponse,
    val recentAnswerCount: Long,
    val statusCounts: KnowledgeAnswerStatusCountsResponse,
    val quotaUsage: KnowledgeAnswerQuotaUsageResponse,
    val activeInFlightCount: Long,
    val abandonedRequestCount: Long,
    val feedbackAnalytics: KnowledgeAnswerFeedbackAnalyticsResponse,
    val citationCountBands: Map<String, Long>,
    val latencyBands: Map<String, Long>,
    val recentFailureCategories: Map<String, Long>
)

data class KnowledgeEmbeddingBatchResponse(
    val providerId: String,
    val modelPresent: Boolean,
    val considered: Int,
    val embedded: Int,
    val skippedUnchanged: Int,
    val failed: Int,
    val failureCategory: String?
)

data class KnowledgeEmbeddingFailureResponse(
    val chunkId: UUID,
    val providerId: String,
    val modelPresent: Boolean,
    val dimension: Int,
    val status: String,
    val failureCategory: String?,
    val attemptCount: Int,
    val updatedAt: Instant
)

data class KnowledgeEmbeddingDiagnosticResponse(
    val id: UUID,
    val providerId: String,
    val modelPresent: Boolean,
    val diagnosticType: String,
    val outcome: String,
    val readiness: String,
    val failureCategory: String?,
    val latencyBand: String,
    val batchSize: Int,
    val generatedAt: Instant,
    val createdAt: Instant
)

data class KnowledgeEmbeddingScheduleStatusResponse(
    val configuredEnabled: Boolean,
    val effectiveEnabled: Boolean,
    val paused: Boolean,
    val scheduleSummary: String,
    val batchSize: Int,
    val lastAttemptedAt: Instant?,
    val lastSuccessfulAt: Instant?,
    val lastEmbeddedCount: Int,
    val lastFailureCategory: String?,
    val leaseState: String
)

data class KnowledgeEmbeddingCleanupResponse(val deleted: Int)

data class KnowledgeSearchScoreResponse(
    val keywordScore: Double,
    val semanticScore: Double,
    val combinedScore: Double,
    val fallbackUsed: Boolean
)

private fun KnowledgeDocument.toResponse(includeChunks: Boolean): KnowledgeDocumentResponse =
    KnowledgeDocumentResponse(
        id = id.value,
        title = title,
        category = category,
        source = source,
        language = language,
        tags = metadata.tags,
        metadataKeys = metadata.attributes.keys,
        chunkCount = chunks.size,
        chunks = if (includeChunks) chunks.map { it.toResponse() } else emptyList(),
        createdAt = createdAt,
        updatedAt = updatedAt
    )

private fun KnowledgeChunk.toResponse(): KnowledgeChunkResponse =
    KnowledgeChunkResponse(id = id.value, order = order, heading = heading, text = text)

private fun KnowledgeDocumentPage.toResponse(): KnowledgeDocumentPageResponse =
    KnowledgeDocumentPageResponse(content.map { it.toResponse(includeChunks = false) }, page, size, totalElements, totalPages)

private fun KnowledgeSearchPage.toResponse(): KnowledgeSearchPageResponse =
    KnowledgeSearchPageResponse(content.map { it.toResponse() }, page, size, totalElements, totalPages)

private fun KnowledgeSearchResult.toResponse(): KnowledgeSearchResultResponse =
    KnowledgeSearchResultResponse(
        documentId = documentId.value,
        chunkId = chunkId.value,
        title = title,
        category = category,
        source = source,
        language = language,
        heading = heading,
        snippet = snippet,
        chunkOrder = chunkOrder,
        rank = rank,
        score = score?.let { KnowledgeSearchScoreResponse(it.keywordScore, it.semanticScore, it.combinedScore, it.fallbackUsed) },
        tags = tags,
        updatedAt = updatedAt
    )

private fun KnowledgeEmbeddingStatusSummary.toResponse(): KnowledgeEmbeddingStatusResponse =
    KnowledgeEmbeddingStatusResponse(
        semanticEnabled = semanticEnabled,
        providerId = providerId,
        modelPresent = modelId.isNotBlank(),
        dimension = dimension,
        readiness = readiness.name,
        readyCount = readyCount,
        failedCount = failedCount,
        staleCount = staleCount
    )

private fun KnowledgeEmbeddingProviderSummary.toResponse(): KnowledgeEmbeddingProviderResponse =
    KnowledgeEmbeddingProviderResponse(
        providerId = providerId,
        providerType = providerType.name,
        lifecycle = lifecycle.name,
        active = active,
        modelPresent = modelPresent,
        dimension = dimension,
        readiness = readiness.name,
        failureCategory = failureCategory?.name
    )

private fun KnowledgeRetrievalReadinessSummary.toResponse(): KnowledgeRetrievalReadinessResponse =
    KnowledgeRetrievalReadinessResponse(
        state = state.name,
        semanticEnabled = semanticEnabled,
        providerId = providerId,
        providerReadiness = readiness.name,
        embeddedPercentage = embeddedPercentage,
        totalChunks = totalChunks,
        readyCount = readyCount,
        failedCount = failedCount,
        staleCount = staleCount,
        schedulerPaused = schedulerPaused,
        blockingReasons = blockingReasons,
        evaluationStatus = evaluationStatus,
        latestEvaluationRunId = latestEvaluationRunId,
        qualityGateStatus = qualityGateStatus
    )

private fun KnowledgeEmbeddingBatchSummary.toResponse(): KnowledgeEmbeddingBatchResponse =
    KnowledgeEmbeddingBatchResponse(providerId, modelId.isNotBlank(), considered, embedded, skippedUnchanged, failed, failureCategory?.name)

private fun KnowledgeEmbeddingRecord.toResponse(): KnowledgeEmbeddingFailureResponse =
    KnowledgeEmbeddingFailureResponse(chunkId.value, providerId.value, modelId.isNotBlank(), dimension, status.name, failureCategory?.name, attemptCount, updatedAt)

private fun KnowledgeEmbeddingDiagnosticRecord.toResponse(): KnowledgeEmbeddingDiagnosticResponse =
    KnowledgeEmbeddingDiagnosticResponse(id, providerId, modelId.isNotBlank(), diagnosticType.name, outcome.name, readiness.name, failureCategory?.name, latencyBand, batchSize, generatedAt, createdAt)

private fun KnowledgeEmbeddingScheduleStatus.toResponse(): KnowledgeEmbeddingScheduleStatusResponse =
    KnowledgeEmbeddingScheduleStatusResponse(configuredEnabled, effectiveEnabled, paused, scheduleSummary, batchSize, lastAttemptedAt, lastSuccessfulAt, lastEmbeddedCount, lastFailureCategory?.name, leaseState)

private fun KnowledgeRetrievalEvaluationRunRequest.toCommand(hotelId: UUID): KnowledgeRetrievalEvaluationRequest =
    KnowledgeRetrievalEvaluationRequest(
        name = name,
        hotelId = hotelId,
        cases = cases.map {
            KnowledgeRetrievalEvaluationCase(
                query = it.query,
                expectedDocumentIds = it.expectedDocumentIds.map(::KnowledgeDocumentId).toSet(),
                expectedChunkIds = it.expectedChunkIds.map { chunkId -> com.hotelopai.knowledge.domain.KnowledgeChunkId(chunkId) }.toSet(),
                relevanceScore = it.relevanceScore
            )
        },
        modes = modes,
        k = k
    )

private fun KnowledgeRetrievalEvaluationRun.toResponse(): KnowledgeRetrievalEvaluationRunResponse =
    KnowledgeRetrievalEvaluationRunResponse(
        id = id,
        name = name,
        status = status.name,
        caseCount = caseCount,
        k = k,
        modes = modes.map { it.name }.toSortedSet(),
        startedAt = startedAt,
        completedAt = completedAt,
        failureCategory = failureCategory?.name,
        metrics = metrics.map { it.toResponse() }
    )

private fun KnowledgeRetrievalMetricSummary.toResponse(): KnowledgeRetrievalMetricResponse =
    KnowledgeRetrievalMetricResponse(
        mode = mode.name,
        precisionAtK = precisionAtK,
        recallAtK = recallAtK,
        meanReciprocalRank = meanReciprocalRank,
        normalizedDiscountedCumulativeGain = normalizedDiscountedCumulativeGain,
        hitRate = hitRate,
        averageLatencyMillis = averageLatencyMillis,
        averageRetrievedChunks = averageRetrievedChunks,
        scoreBand = scoreBand
    )

private fun KnowledgeCuratedDatasetValidationReport.toResponse(): KnowledgeCuratedDatasetValidationResponse =
    KnowledgeCuratedDatasetValidationResponse(datasetVersion, valid, documentCount, caseCount, failureReasons)

private fun KnowledgeRetrievalQualityReport.toResponse(): KnowledgeRetrievalQualityReportResponse =
    KnowledgeRetrievalQualityReportResponse(
        datasetVersion = datasetVersion,
        runId = runId,
        outcome = outcome.name,
        evaluatedQueryCount = evaluatedQueryCount,
        k = k,
        modeReports = modeReports.map { it.toResponse() },
        failedThresholds = failedThresholds
    )

private fun KnowledgeRetrievalQualityModeReport.toResponse(): KnowledgeRetrievalQualityModeReportResponse =
    KnowledgeRetrievalQualityModeReportResponse(
        mode = mode.name,
        outcome = outcome.name,
        failedThresholds = failedThresholds,
        metrics = metrics?.toResponse()
    )

private fun KnowledgeContextAssemblyResult.toResponse(): KnowledgeContextAssemblyResponse =
    KnowledgeContextAssemblyResponse(
        mode = mode.name,
        itemCount = itemCount,
        totalCharacters = totalCharacters,
        duplicateCount = duplicateCount,
        items = items.map { it.toResponse() }
    )

private fun KnowledgeContextItem.toResponse(): KnowledgeContextItemResponse =
    KnowledgeContextItemResponse(
        documentReference = citation.documentReference,
        chunkReference = citation.chunkReference,
        title = citation.title,
        category = citation.category,
        chunkPosition = citation.chunkPosition,
        selectedText = selectedText,
        retrievalMode = retrievalMode.name,
        score = KnowledgeSearchScoreResponse(score.keywordScore, score.semanticScore, score.combinedScore, score.fallbackUsed),
        contentFingerprint = citation.contentFingerprint
    )

private fun KnowledgeAnswer.toResponse(): KnowledgeAnswerResponse =
    KnowledgeAnswerResponse(
        id = id.value,
        providerId = providerId,
        modelPresent = modelId.isNotBlank(),
        promptTemplateId = promptTemplateId,
        promptVersion = promptVersion,
        retrievalMode = retrievalMode.name,
        contextSchemaVersion = contextSchemaVersion,
        status = status.name,
        confidence = confidence?.name,
        answerText = answerText,
        citations = citations.map { it.toResponse() },
        failureCategory = failureCategory?.name,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

private fun KnowledgeCitation.toResponse(): KnowledgeAnswerCitationResponse =
    KnowledgeAnswerCitationResponse(citationId, documentReference, chunkReference, title, category, chunkPosition, retrievalScore, contentFingerprint, excerpt)

private fun KnowledgeAnswerProviderSummary.toResponse(): KnowledgeAnswerProviderSummaryResponse =
    KnowledgeAnswerProviderSummaryResponse(
        providerId = providerId,
        providerType = providerType.name,
        lifecycle = lifecycle.name,
        active = active,
        enabled = enabled,
        modelPresent = modelPresent,
        promptTemplateId = promptTemplateId,
        promptVersion = promptVersion,
        readiness = readiness.name
    )

private fun KnowledgeAnswerProviderReadiness.toResponse(): KnowledgeAnswerProviderReadinessDetailResponse =
    KnowledgeAnswerProviderReadinessDetailResponse(
        providerId = providerId,
        readiness = readiness.name,
        lifecycle = lifecycle.name,
        active = active,
        enabled = enabled,
        endpointClassification = endpointClassification.name,
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
        modelPresent = modelPresent,
        promptTemplateId = promptTemplateId,
        promptVersion = promptVersion
    )

private fun KnowledgeAnswerSmokeTestResult.toResponse(): KnowledgeAnswerSmokeTestResponse =
    KnowledgeAnswerSmokeTestResponse(
        diagnostic = diagnostic.toResponse(),
        readiness = readiness.toResponse(),
        answerCount = answerCount
    )

private fun KnowledgeAnswerProviderDiagnosticPage.toResponse(): KnowledgeAnswerProviderDiagnosticPageResponse =
    KnowledgeAnswerProviderDiagnosticPageResponse(
        content = content.map { it.toResponse() },
        page = page,
        size = size,
        totalElements = totalElements,
        totalPages = totalPages
    )

private fun KnowledgeAnswerProviderDiagnosticRecord.toResponse(): KnowledgeAnswerProviderDiagnosticResponse =
    KnowledgeAnswerProviderDiagnosticResponse(
        id = id,
        providerId = providerId,
        diagnosticType = diagnosticType.name,
        triggerType = triggerType.name,
        startedAt = startedAt,
        completedAt = completedAt,
        outcome = outcome.name,
        failureCategory = failureCategory?.name,
        latencyBand = latencyBand,
        retryCount = retryCount,
        responseValidationOutcome = responseValidationOutcome.name,
        promptTemplateId = promptTemplateId,
        promptVersion = promptVersion,
        modelPresent = modelId.isNotBlank(),
        environmentClass = environmentClass,
        createdAt = createdAt
    )

private fun KnowledgeAnswerFeedback.toResponse(): KnowledgeAnswerFeedbackResponse =
    KnowledgeAnswerFeedbackResponse(feedbackType.name, createdAt)

private fun KnowledgeAnswerRequestLifecycle.toResponse(): KnowledgeAnswerRequestLifecycleResponse =
    KnowledgeAnswerRequestLifecycleResponse(
        requestId = requestId,
        answerIdPresent = answerId != null,
        originalRequestIdPresent = originalRequestId != null,
        providerId = providerId,
        modelPresent = modelId.isNotBlank(),
        retrievalMode = retrievalMode.name,
        status = status.name,
        requestedAt = requestedAt,
        completedAt = completedAt,
        updatedAt = updatedAt,
        failureCategory = failureCategory?.name,
        citationCountBand = citationCountBand,
        latencyBand = latencyBand
    )

private fun KnowledgeAnswerOperationsDashboard.toResponse(): KnowledgeAnswerDashboardResponse =
    KnowledgeAnswerDashboardResponse(
        providerReadiness = providerReadiness.toResponse(),
        retrievalReadiness = retrievalReadiness.toResponse(),
        recentAnswerCount = recentAnswerCount,
        statusCounts = statusCounts.toResponse(),
        quotaUsage = quotaUsage.toResponse(),
        activeInFlightCount = activeInFlightCount,
        abandonedRequestCount = abandonedRequestCount,
        feedbackAnalytics = feedbackAnalytics.toResponse(),
        citationCountBands = citationCountBands,
        latencyBands = latencyBands,
        recentFailureCategories = recentFailureCategories.mapKeys { it.key.name }
    )

private fun KnowledgeAnswerStatusCounts.toResponse(): KnowledgeAnswerStatusCountsResponse =
    KnowledgeAnswerStatusCountsResponse(answered, insufficientContext, failed)

private fun KnowledgeAnswerQuotaUsage.toResponse(): KnowledgeAnswerQuotaUsageResponse =
    KnowledgeAnswerQuotaUsageResponse(hourlyLimit, hourlyUsed, dailyLimit, dailyUsed, inFlightLimit, inFlightUsed)

private fun KnowledgeAnswerFeedbackAnalytics.toResponse(): KnowledgeAnswerFeedbackAnalyticsResponse =
    KnowledgeAnswerFeedbackAnalyticsResponse(
        counts = counts.mapKeys { it.key.name },
        rates = rates.mapKeys { it.key.name },
        providerBreakdown = providerBreakdown.mapValues { (_, value) -> value.mapKeys { it.key.name } },
        retrievalModeBreakdown = retrievalModeBreakdown.mapKeys { it.key.name }.mapValues { (_, value) -> value.mapKeys { it.key.name } },
        citationCountBandBreakdown = citationCountBandBreakdown.mapValues { (_, value) -> value.mapKeys { it.key.name } }
    )
