package com.hotelopai.knowledge.application

import com.hotelopai.observability.OperationalObservability
import com.hotelopai.shared.kernel.PersistenceInstant
import org.springframework.core.env.Environment
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.net.URI
import java.security.MessageDigest
import java.time.Clock
import java.time.Duration
import java.util.UUID

@Service
class KnowledgeAnswerService(
    providers: List<KnowledgeAnswerProvider>,
    private val contextAssembler: KnowledgeContextAssembler,
    private val embeddingService: KnowledgeEmbeddingService,
    private val promptAssembler: KnowledgePromptAssembler,
    private val privacyGateway: KnowledgeAnswerPrivacyGateway,
    private val repository: KnowledgeAnswerRepository,
    private val properties: KnowledgeProperties,
    private val clock: Clock,
    private val environment: Environment,
    private val auditSink: KnowledgeOperationsAuditSink = NoOpKnowledgeOperationsAuditSink,
    private val observability: OperationalObservability = OperationalObservability.noop()
) {
    private val providersById = providers.associateBy { it.providerId }

    init {
        require(providersById.size == providers.size) { "knowledge answer provider ids must be unique" }
        if (properties.answers.enabled) {
            require(providersById.containsKey(properties.answers.activeProvider)) { "knowledge answer active provider is not registered" }
            validateActiveProviderConfiguration()
        }
    }

    @Transactional
    fun answer(request: KnowledgeAnswerRequest): KnowledgeAnswer {
        val now = PersistenceInstant.now(clock)
        val normalizedQuery = request.query.trim()
        require(normalizedQuery.length in 2..properties.answers.maxQueryCharacters) { "knowledge answer query length is invalid" }
        val fingerprint = fingerprint(request)
        repository.findDuplicate(request.hotelId, fingerprint, now.minus(properties.answers.duplicateWindow))?.let {
            observability.incrementCounter("knowledge_answer_duplicate_prevention_total", "provider" to it.providerId, "outcome" to "duplicate", "failure_category" to "duplicate_suppressed")
            return it
        }
        enforceQuota(request, fingerprint, now)?.let { return it }
        val lifecycle = if (request.hotelId != null && request.actorUserId != null) {
            repository.acquireAnswerRequestLifecycle(
                hotelId = request.hotelId,
                actorUserId = request.actorUserId,
                providerId = properties.answers.activeProvider,
                modelId = properties.answers.model,
                retrievalMode = request.retrievalMode,
                requestFingerprint = fingerprint,
                inFlightLimit = properties.answers.maximumConcurrentRequests,
                abandonedBefore = now.minus(properties.answers.inFlightAbandonedTimeout),
                now = now
            ) ?: return saveFailure(request, fingerprint, KnowledgeAnswerStatus.FAILED_VALIDATION, KnowledgeAnswerFailureCategory.IN_FLIGHT_LIMIT_EXCEEDED, now)
        } else {
            null
        }
        if (!properties.answers.enabled) {
            return saveFailure(request, fingerprint, KnowledgeAnswerStatus.PROVIDER_DISABLED, KnowledgeAnswerFailureCategory.PROVIDER_DISABLED, now, lifecycle?.requestId)
        }
        val provider = providersById[properties.answers.activeProvider]
            ?: return saveFailure(request, fingerprint, KnowledgeAnswerStatus.PROVIDER_DISABLED, KnowledgeAnswerFailureCategory.PROVIDER_DISABLED, now, lifecycle?.requestId)
        if (provider.readiness() != KnowledgeEmbeddingProviderReadiness.READY) {
            return saveFailure(request, fingerprint, KnowledgeAnswerStatus.PROVIDER_DISABLED, KnowledgeAnswerFailureCategory.PROVIDER_DISABLED, now, lifecycle?.requestId)
        }
        return runCatching {
            privacyGateway.validateQuery(normalizedQuery)
            lifecycle?.let { transition(it.requestId, KnowledgeAnswerRequestStatus.RETRIEVING) }
            val context = contextAssembler.assemble(
                KnowledgeContextAssemblyRequest(
                    query = normalizedQuery,
                    hotelId = request.hotelId,
                    mode = request.retrievalMode,
                    categories = request.categories,
                    language = request.language,
                    limit = request.contextLimit
                )
            )
            if (context.itemCount < properties.answers.minimumContextItems) {
                return saveFailure(request, fingerprint, KnowledgeAnswerStatus.INSUFFICIENT_CONTEXT, KnowledgeAnswerFailureCategory.INSUFFICIENT_CONTEXT, now, lifecycle?.requestId)
            }
            val prompt = promptAssembler.assemble(normalizedQuery, context)
            lifecycle?.let { transition(it.requestId, KnowledgeAnswerRequestStatus.GENERATING) }
            val response = provider.generate(prompt)
            lifecycle?.let { transition(it.requestId, KnowledgeAnswerRequestStatus.VALIDATING) }
            validateGrounding(response, prompt)
            privacyGateway.validateOutput(response.answerText)
            val citations = response.citationIds.map { id -> prompt.contextItems.first { it.citationId == id }.citation }
            saveAnswer(request, fingerprint, provider, response, citations, now, lifecycle?.requestId, lifecycle?.requestedAt)
        }.getOrElse { exception ->
            val failure = classify(exception)
            val status = when (failure) {
                KnowledgeAnswerFailureCategory.INSUFFICIENT_CONTEXT -> KnowledgeAnswerStatus.INSUFFICIENT_CONTEXT
                KnowledgeAnswerFailureCategory.PROVIDER_DISABLED -> KnowledgeAnswerStatus.PROVIDER_DISABLED
                KnowledgeAnswerFailureCategory.PROVIDER_TIMEOUT,
                KnowledgeAnswerFailureCategory.PROVIDER_UNAVAILABLE -> KnowledgeAnswerStatus.PROVIDER_FAILURE
                else -> KnowledgeAnswerStatus.FAILED_VALIDATION
            }
            saveFailure(request, fingerprint, status, failure, now, lifecycle?.requestId)
        }
    }

    fun history(hotelId: UUID?, page: Int, size: Int): List<KnowledgeAnswer> =
        repository.answers(hotelId, size.coerceIn(1, 100), page.coerceAtLeast(0) * size.coerceIn(1, 100))

    fun detail(id: UUID, hotelId: UUID?): KnowledgeAnswer? =
        repository.answer(KnowledgeAnswerId(id), hotelId)

    @Transactional
    fun retry(id: UUID, hotelId: UUID?): KnowledgeAnswer {
        val existing = detail(id, hotelId) ?: throw IllegalArgumentException("knowledge answer record not found")
        require(existing.failureCategory in TRANSIENT_RETRY_FAILURES) { "knowledge answer failure is not retryable" }
        throw IllegalArgumentException("knowledge answer retry requires operator resubmission because query text is not retained")
    }

    @Transactional
    fun cleanup(): Int {
        val deleted = repository.cleanupAnswers(PersistenceInstant.now(clock).minus(properties.answers.historyRetention), properties.answers.historyCleanupBatchSize)
        observability.incrementCounter("knowledge_answer_cleanup_total", deleted.toDouble(), "outcome" to "deleted")
        auditSink.record(KnowledgeOperationsAuditEvent("knowledge_answer_cleanup", "deleted", PersistenceInstant.now(clock), "count_band_${countBand(deleted)}"))
        return deleted
    }

    fun providerReadiness(): KnowledgeEmbeddingProviderReadiness =
        if (!properties.answers.enabled) KnowledgeEmbeddingProviderReadiness.DISABLED
        else providersById[properties.answers.activeProvider]?.readiness() ?: KnowledgeEmbeddingProviderReadiness.DISABLED

    fun providerSummaries(): List<KnowledgeAnswerProviderSummary> =
        providersById.values.sortedBy { it.providerId }.map { provider ->
            val enabled = provider.enabledByConfiguration()
            val readiness = if (!enabled) KnowledgeEmbeddingProviderReadiness.DISABLED else provider.readiness()
            KnowledgeAnswerProviderSummary(
                providerId = provider.providerId,
                providerType = provider.providerType,
                lifecycle = when (readiness) {
                    KnowledgeEmbeddingProviderReadiness.READY -> KnowledgeEmbeddingProviderLifecycle.AVAILABLE
                    KnowledgeEmbeddingProviderReadiness.DISABLED -> KnowledgeEmbeddingProviderLifecycle.DISABLED
                    KnowledgeEmbeddingProviderReadiness.MISCONFIGURED -> KnowledgeEmbeddingProviderLifecycle.MISCONFIGURED
                    KnowledgeEmbeddingProviderReadiness.UNAVAILABLE -> KnowledgeEmbeddingProviderLifecycle.UNAVAILABLE
                },
                active = provider.providerId == properties.answers.activeProvider,
                enabled = enabled,
                modelPresent = provider.modelId.isNotBlank(),
                promptTemplateId = properties.answers.promptTemplateId,
                promptVersion = properties.answers.promptVersion,
                readiness = readiness
            )
        }

    fun providerReadiness(providerId: String): KnowledgeAnswerProviderReadiness =
        readinessInternal(providerId)

    @Transactional
    fun smokeTest(providerId: String, fixtureMode: KnowledgeAnswerSmokeFixtureMode?, actorUserId: UUID?): KnowledgeAnswerSmokeTestResult {
        auditSink.record(KnowledgeOperationsAuditEvent("knowledge_answer_provider_smoke_test", "requested", PersistenceInstant.now(clock)))
        val started = PersistenceInstant.now(clock)
        val provider = providersById[providerId] ?: throw IllegalArgumentException("knowledge answer provider is not registered")
        if (provider.providerType != KnowledgeEmbeddingProviderType.EXTERNAL) {
            throw IllegalArgumentException("knowledge answer smoke tests are supported only for external providers")
        }
        val readiness = readinessInternal(providerId)
        if (readiness.readiness !in setOf(KnowledgeAnswerProviderReadinessStatus.READY_FOR_LOCAL_SMOKE, KnowledgeAnswerProviderReadinessStatus.READY_FOR_NON_PRODUCTION)) {
            val record = saveDiagnostic(provider, started, started, KnowledgeAnswerProviderDiagnosticOutcome.REJECTED, readiness.failureCategory ?: KnowledgeAnswerFailureCategory.CONFIGURATION_ERROR, KnowledgeAnswerResponseValidationOutcome.NOT_APPLICABLE)
            return KnowledgeAnswerSmokeTestResult(record, readinessInternal(providerId), 0)
        }
        if (fixtureMode != null && !properties.answers.providers.openai.fixtureModeEnabled) {
            throw IllegalArgumentException("knowledge answer smoke fixture mode is not enabled")
        }
        return try {
            val prompt = syntheticPrompt()
            val response = KnowledgeAnswerSmokeFixtureScope.withFixture(fixtureMode) { provider.generate(prompt) }
            validateGrounding(response, prompt)
            privacyGateway.validateOutput(response.answerText)
            val completed = PersistenceInstant.now(clock)
            val record = saveDiagnostic(provider, started, completed, KnowledgeAnswerProviderDiagnosticOutcome.SUCCEEDED, null, KnowledgeAnswerResponseValidationOutcome.VALID)
            observability.incrementCounter("knowledge_answer_provider_smoke_tests_total", "provider" to providerId, "fixture_mode" to (fixtureMode?.name ?: "none"), "outcome" to "succeeded", "failure_category" to "none")
            auditSink.record(KnowledgeOperationsAuditEvent("knowledge_answer_provider_smoke_test", "succeeded", completed))
            KnowledgeAnswerSmokeTestResult(record, readinessInternal(providerId), if (response.status == KnowledgeAnswerStatus.ANSWERED) 1 else 0)
        } catch (exception: Throwable) {
            val completed = PersistenceInstant.now(clock)
            val failure = classify(exception)
            val validation = if (failure in setOf(KnowledgeAnswerFailureCategory.INVALID_RESPONSE, KnowledgeAnswerFailureCategory.UNKNOWN_CITATION, KnowledgeAnswerFailureCategory.MISSING_CITATION)) {
                KnowledgeAnswerResponseValidationOutcome.INVALID
            } else {
                KnowledgeAnswerResponseValidationOutcome.NOT_APPLICABLE
            }
            val record = saveDiagnostic(provider, started, completed, KnowledgeAnswerProviderDiagnosticOutcome.FAILED, failure, validation)
            observability.incrementCounter("knowledge_answer_provider_smoke_tests_total", "provider" to providerId, "fixture_mode" to (fixtureMode?.name ?: "none"), "outcome" to "failed", "failure_category" to failure.name)
            auditSink.record(KnowledgeOperationsAuditEvent("knowledge_answer_provider_smoke_test", "failed", completed, failure.name))
            KnowledgeAnswerSmokeTestResult(record, readinessInternal(providerId), 0)
        }
    }

    fun diagnostics(providerId: String?, page: Int, size: Int): KnowledgeAnswerProviderDiagnosticPage {
        auditSink.record(KnowledgeOperationsAuditEvent("knowledge_answer_provider_diagnostics", "inspected", PersistenceInstant.now(clock)))
        return repository.answerProviderDiagnostics(providerId, size.coerceIn(1, 100), page.coerceAtLeast(0) * size.coerceIn(1, 100))
    }

    fun diagnostic(id: UUID): KnowledgeAnswerProviderDiagnosticRecord? =
        repository.answerProviderDiagnostic(id)

    @Transactional
    fun cleanupDiagnostics(): Int {
        val deleted = repository.cleanupAnswerProviderDiagnostics(PersistenceInstant.now(clock).minus(properties.answers.providers.externalPolicy.diagnosticsRetention), properties.answers.providers.externalPolicy.diagnosticsCleanupBatchSize)
        observability.incrementCounter("knowledge_answer_provider_diagnostic_cleanup_total", deleted.toDouble(), "outcome" to "deleted")
        auditSink.record(KnowledgeOperationsAuditEvent("knowledge_answer_provider_diagnostics_cleanup", "deleted", PersistenceInstant.now(clock), "count_band_${countBand(deleted)}"))
        return deleted
    }

    @Transactional
    fun submitFeedback(answerId: UUID, hotelId: UUID?, actorUserId: UUID?, feedbackType: KnowledgeAnswerFeedbackType): KnowledgeAnswerFeedback {
        requireNotNull(actorUserId) { "knowledge answer feedback requires an authenticated actor" }
        val answer = detail(answerId, hotelId) ?: throw IllegalArgumentException("knowledge answer record not found")
        val feedback = repository.saveFeedback(KnowledgeAnswerFeedback(answer.id, feedbackType, actorUserId, PersistenceInstant.now(clock)))
        observability.incrementCounter("knowledge_answer_feedback_total", "provider" to answer.providerId, "outcome" to feedbackType.name.lowercase())
        auditSink.record(KnowledgeOperationsAuditEvent("knowledge_answer_feedback", feedbackType.name.lowercase(), feedback.createdAt))
        return feedback
    }

    fun feedback(answerId: UUID, hotelId: UUID?): List<KnowledgeAnswerFeedback> {
        val answer = detail(answerId, hotelId) ?: throw IllegalArgumentException("knowledge answer record not found")
        return repository.feedbackFor(answer.id)
    }

    fun activeRequests(hotelId: UUID?, page: Int, size: Int): List<KnowledgeAnswerRequestLifecycle> =
        repository.activeAnswerRequests(hotelId, size.coerceIn(1, 100), page.coerceAtLeast(0) * size.coerceIn(1, 100))

    fun requestDetail(requestId: UUID, hotelId: UUID?): KnowledgeAnswerRequestLifecycle? =
        repository.answerRequest(requestId, hotelId)

    @Transactional
    fun cancelRequest(requestId: UUID, hotelId: UUID?, actorUserId: UUID?): KnowledgeAnswerRequestLifecycle {
        val existing = repository.answerRequest(requestId, hotelId) ?: throw IllegalArgumentException("knowledge answer request not found")
        require(existing.status in ACTIVE_REQUEST_STATUSES) { "knowledge answer request cannot be cancelled" }
        auditSink.record(KnowledgeOperationsAuditEvent("knowledge_answer_request_cancelled", "requested", PersistenceInstant.now(clock), KnowledgeAnswerFailureCategory.CANCELLED.name))
        return requireNotNull(
            repository.transitionAnswerRequest(
                requestId = requestId,
                status = KnowledgeAnswerRequestStatus.REJECTED,
                now = PersistenceInstant.now(clock),
                failureCategory = KnowledgeAnswerFailureCategory.CANCELLED,
                latencyBand = latencyBand(Duration.between(existing.requestedAt, PersistenceInstant.now(clock)))
            )
        )
    }

    @Transactional
    fun recoverAbandonedRequests(): Int {
        val recovered = repository.recoverAbandonedAnswerRequests(
            before = PersistenceInstant.now(clock).minus(properties.answers.inFlightAbandonedTimeout),
            now = PersistenceInstant.now(clock),
            limit = properties.answers.historyCleanupBatchSize
        )
        if (recovered > 0) {
            auditSink.record(KnowledgeOperationsAuditEvent("knowledge_answer_abandoned_recovered", "recovered", PersistenceInstant.now(clock), "count_band_${countBand(recovered)}"))
        }
        observability.incrementCounter("knowledge_answer_abandoned_recovery_total", recovered.toDouble(), "outcome" to "recovered")
        return recovered
    }

    fun dashboard(hotelId: UUID, actorUserId: UUID): KnowledgeAnswerOperationsDashboard {
        val since = PersistenceInstant.now(clock).minus(Duration.ofDays(1))
        auditSink.record(KnowledgeOperationsAuditEvent("knowledge_answer_dashboard", "inspected", PersistenceInstant.now(clock)))
        observability.incrementCounter("knowledge_answer_dashboard_requests_total", "outcome" to "inspected")
        return KnowledgeAnswerOperationsDashboard(
            providerReadiness = readinessInternal(properties.answers.activeProvider),
            retrievalReadiness = embeddingService.retrievalReadiness(),
            recentAnswerCount = repository.countAnswers(hotelId, null, since),
            statusCounts = repository.answerStatusCounts(hotelId, since),
            quotaUsage = KnowledgeAnswerQuotaUsage(
                hourlyLimit = properties.answers.hourlyRequestLimit,
                hourlyUsed = repository.countAnswers(hotelId, actorUserId, PersistenceInstant.now(clock).minus(Duration.ofHours(1))),
                dailyLimit = properties.answers.dailyRequestLimit,
                dailyUsed = repository.countAnswers(hotelId, actorUserId, since),
                inFlightLimit = properties.answers.maximumConcurrentRequests,
                inFlightUsed = repository.countActiveAnswerRequests(hotelId, actorUserId)
            ),
            activeInFlightCount = repository.countActiveAnswerRequests(hotelId, null),
            abandonedRequestCount = repository.countAbandonedAnswerRequests(hotelId),
            feedbackAnalytics = repository.feedbackAnalytics(hotelId, since),
            citationCountBands = repository.answerCitationBands(hotelId, since),
            latencyBands = repository.answerLatencyBands(hotelId, since),
            recentFailureCategories = repository.answerFailureCategories(hotelId, since)
        )
    }

    private fun saveAnswer(
        request: KnowledgeAnswerRequest,
        fingerprint: String,
        provider: KnowledgeAnswerProvider,
        response: KnowledgeAnswerProviderResponse,
        citations: List<KnowledgeCitation>,
        now: java.time.Instant,
        requestId: UUID?,
        requestedAt: java.time.Instant?
    ): KnowledgeAnswer {
        val answer = KnowledgeAnswer(
            id = KnowledgeAnswerId(UUID.randomUUID()),
            hotelId = request.hotelId,
            providerId = provider.providerId,
            modelId = provider.modelId,
            promptTemplateId = properties.answers.promptTemplateId,
            promptVersion = properties.answers.promptVersion,
            retrievalMode = request.retrievalMode,
            contextSchemaVersion = properties.answers.contextSchemaVersion,
            status = response.status,
            confidence = response.confidence,
            answerText = response.answerText?.take(properties.answers.maxAnswerCharacters),
            citations = citations,
            requestFingerprint = fingerprint,
            failureCategory = response.failureCategory,
            actorUserId = request.actorUserId,
            createdAt = now,
            updatedAt = now
        )
        val saved = repository.saveAnswer(answer)
        requestId?.let {
            repository.transitionAnswerRequest(
                requestId = it,
                status = KnowledgeAnswerRequestStatus.COMPLETED,
                now = now,
                answerId = saved.id,
                failureCategory = response.failureCategory,
                citationCountBand = countBand(citations.size),
                latencyBand = requestedAt?.let { started -> latencyBand(Duration.between(started, now)) }
            )
        }
        record(saved)
        return saved
    }

    private fun saveFailure(
        request: KnowledgeAnswerRequest,
        fingerprint: String,
        status: KnowledgeAnswerStatus,
        failure: KnowledgeAnswerFailureCategory,
        now: java.time.Instant,
        requestId: UUID? = null
    ): KnowledgeAnswer {
        val answer = KnowledgeAnswer(
            id = KnowledgeAnswerId(UUID.randomUUID()),
            hotelId = request.hotelId,
            providerId = properties.answers.activeProvider,
            modelId = properties.answers.model,
            promptTemplateId = properties.answers.promptTemplateId,
            promptVersion = properties.answers.promptVersion,
            retrievalMode = request.retrievalMode,
            contextSchemaVersion = properties.answers.contextSchemaVersion,
            status = status,
            confidence = null,
            answerText = null,
            citations = emptyList(),
            requestFingerprint = fingerprint,
            failureCategory = failure,
            actorUserId = request.actorUserId,
            createdAt = now,
            updatedAt = now
        )
        val saved = repository.saveAnswer(answer)
        requestId?.let {
            repository.transitionAnswerRequest(
                requestId = it,
                status = when (status) {
                    KnowledgeAnswerStatus.INSUFFICIENT_CONTEXT -> KnowledgeAnswerRequestStatus.INSUFFICIENT_CONTEXT
                    KnowledgeAnswerStatus.PROVIDER_DISABLED -> KnowledgeAnswerRequestStatus.REJECTED
                    KnowledgeAnswerStatus.PROVIDER_FAILURE -> KnowledgeAnswerRequestStatus.FAILED
                    KnowledgeAnswerStatus.FAILED_VALIDATION -> KnowledgeAnswerRequestStatus.FAILED
                    KnowledgeAnswerStatus.ANSWERED -> KnowledgeAnswerRequestStatus.COMPLETED
                },
                now = now,
                answerId = saved.id,
                failureCategory = failure,
                citationCountBand = countBand(0),
                latencyBand = "unknown"
            )
        }
        record(saved)
        return saved
    }

    private fun validateGrounding(response: KnowledgeAnswerProviderResponse, prompt: KnowledgePrompt) {
        if (response.status == KnowledgeAnswerStatus.INSUFFICIENT_CONTEXT) {
            require(response.citationIds.isEmpty()) { "insufficient context answer must not include citations" }
            return
        }
        require(response.status == KnowledgeAnswerStatus.ANSWERED) { "knowledge answer provider returned unsupported status" }
        require(!response.answerText.isNullOrBlank()) { "knowledge answer text is required" }
        require(response.answerText.length <= properties.answers.maxAnswerCharacters) { "knowledge answer text exceeds configured limit" }
        require(!response.answerText.contains(Regex("\\b(create|assign|close|cancel|delete|modify)\\s+(a\\s+)?(task|reservation)\\b", RegexOption.IGNORE_CASE))) {
            "knowledge answer contains unsupported operational action directive"
        }
        require(response.confidence != null) { "knowledge answer confidence is required" }
        require(response.citationIds.isNotEmpty()) { "knowledge answer requires at least one citation" }
        val allowed = prompt.contextItems.map { it.citationId }.toSet()
        require(response.citationIds.all { it in allowed }) { "knowledge answer includes unknown citation" }
    }

    private fun classify(exception: Throwable): KnowledgeAnswerFailureCategory =
        when (exception) {
            is KnowledgeAnswerProviderException -> exception.category
            is KnowledgeEmbeddingProviderException -> when (exception.category) {
                KnowledgeEmbeddingFailureCategory.TIMEOUT -> KnowledgeAnswerFailureCategory.PROVIDER_TIMEOUT
                KnowledgeEmbeddingFailureCategory.RATE_LIMITED -> KnowledgeAnswerFailureCategory.RATE_LIMITED
                KnowledgeEmbeddingFailureCategory.AUTHENTICATION_FAILURE -> KnowledgeAnswerFailureCategory.AUTHENTICATION_FAILURE
                KnowledgeEmbeddingFailureCategory.CONFIGURATION_ERROR -> KnowledgeAnswerFailureCategory.CONFIGURATION_ERROR
                else -> KnowledgeAnswerFailureCategory.PROVIDER_UNAVAILABLE
            }
            else -> when {
            exception.message?.contains("prompt exceeds", ignoreCase = true) == true -> KnowledgeAnswerFailureCategory.PROMPT_TOO_LARGE
            exception.message?.contains("sensitive data", ignoreCase = true) == true -> KnowledgeAnswerFailureCategory.PRIVACY_REJECTED
            exception.message?.contains("sensitive", ignoreCase = true) == true -> KnowledgeAnswerFailureCategory.SENSITIVE_OUTPUT
            exception.message?.contains("unknown citation", ignoreCase = true) == true -> KnowledgeAnswerFailureCategory.UNKNOWN_CITATION
            exception.message?.contains("citation", ignoreCase = true) == true -> KnowledgeAnswerFailureCategory.MISSING_CITATION
            exception.message?.contains("action directive", ignoreCase = true) == true -> KnowledgeAnswerFailureCategory.UNSUPPORTED_ACTION_DIRECTIVE
            else -> KnowledgeAnswerFailureCategory.INVALID_RESPONSE
            }
        }

    private fun record(answer: KnowledgeAnswer) {
        observability.incrementCounter("knowledge_answer_requests_total", "provider" to answer.providerId, "outcome" to answer.status.name.lowercase(), "failure_category" to (answer.failureCategory?.name ?: "none"))
        observability.incrementCounter("knowledge_answer_citations_total", answer.citations.size.toDouble(), "provider" to answer.providerId, "outcome" to countBand(answer.citations.size))
        auditSink.record(KnowledgeOperationsAuditEvent("knowledge_answer_requested", answer.status.name.lowercase(), answer.createdAt, answer.failureCategory?.name))
    }

    private fun fingerprint(request: KnowledgeAnswerRequest): String =
        MessageDigest.getInstance("SHA-256")
            .digest(
                listOf(
                    request.hotelId?.toString().orEmpty(),
                    request.query.trim().lowercase(),
                    request.retrievalMode.name,
                    request.categories.map { it.name }.sorted().joinToString(","),
                    request.language.lowercase(),
                    properties.answers.promptVersion,
                    properties.answers.contextSchemaVersion
                ).joinToString("|").toByteArray(Charsets.UTF_8)
            )
            .joinToString("") { "%02x".format(it) }

    private fun enforceQuota(request: KnowledgeAnswerRequest, fingerprint: String, now: java.time.Instant): KnowledgeAnswer? {
        val actor = request.actorUserId ?: return null
        val hourly = repository.countAnswers(request.hotelId, actor, now.minus(Duration.ofHours(1)))
        val daily = repository.countAnswers(request.hotelId, actor, now.minus(Duration.ofDays(1)))
        return if (hourly >= properties.answers.hourlyRequestLimit || daily >= properties.answers.dailyRequestLimit) {
            observability.incrementCounter("knowledge_answer_rate_limit_rejections_total", "provider" to properties.answers.activeProvider, "outcome" to "quota_exceeded", "failure_category" to KnowledgeAnswerFailureCategory.QUOTA_EXCEEDED.name)
            saveFailure(request, fingerprint, KnowledgeAnswerStatus.FAILED_VALIDATION, KnowledgeAnswerFailureCategory.QUOTA_EXCEEDED, now)
        } else {
            null
        }
    }

    private fun transition(requestId: UUID, status: KnowledgeAnswerRequestStatus) {
        repository.transitionAnswerRequest(requestId, status, PersistenceInstant.now(clock))
        observability.incrementCounter("knowledge_answer_request_lifecycle_total", "status" to status.name.lowercase())
    }

    private fun readinessInternal(providerId: String): KnowledgeAnswerProviderReadiness {
        val provider = providersById[providerId]
        val latest = repository.latestAnswerProviderDiagnostic(providerId)
        val latestSuccessful = repository.latestSuccessfulAnswerProviderDiagnostic(providerId)
        val prodBlocked = productionBlocked()
        if (provider == null) {
            return KnowledgeAnswerProviderReadiness(
                providerId = providerId,
                readiness = KnowledgeAnswerProviderReadinessStatus.NOT_CONFIGURED,
                lifecycle = KnowledgeEmbeddingProviderLifecycle.MISCONFIGURED,
                active = false,
                enabled = false,
                endpointClassification = KnowledgeAnswerEndpointClassification.INVALID,
                environmentClass = environmentClass(),
                fallbackConfigured = false,
                productionUseBlocked = prodBlocked,
                lastSmokeOutcome = latest?.outcome,
                lastSmokeAt = latest?.completedAt,
                lastSuccessfulSmokeAt = latestSuccessful?.completedAt,
                consecutiveFailureBand = "none",
                latencyBand = latest?.latencyBand ?: "unknown",
                validationOutcome = latest?.responseValidationOutcome ?: KnowledgeAnswerResponseValidationOutcome.NOT_APPLICABLE,
                failureCategory = KnowledgeAnswerFailureCategory.CONFIGURATION_ERROR,
                blockingReasons = listOf("provider_not_registered"),
                modelPresent = false,
                promptTemplateId = properties.answers.promptTemplateId,
                promptVersion = properties.answers.promptVersion
            )
        }
        val blocking = mutableListOf<String>()
        val endpoint = endpointClassification(providerId)
        val enabled = provider.enabledByConfiguration()
        if (!enabled) blocking += "provider_disabled"
        if (provider.providerType == KnowledgeEmbeddingProviderType.EXTERNAL) {
            if (prodBlocked) blocking += "production_blocked"
            if (!externalProfileAllowed()) blocking += "profile_not_allowed"
            if (provider.modelId.isBlank()) blocking += "model_not_configured"
            if (properties.answers.providers.openai.credentialReference.isNullOrBlank()) blocking += "credential_reference_not_configured"
            if (endpoint == KnowledgeAnswerEndpointClassification.INVALID) blocking += "endpoint_invalid"
            if (endpoint == KnowledgeAnswerEndpointClassification.EXTERNAL_HTTP && properties.answers.providers.externalPolicy.requireHttpsOutsideLocal) blocking += "endpoint_https_required"
            if (endpoint == KnowledgeAnswerEndpointClassification.LOCAL_STUB && !properties.answers.providers.openai.smokeTestEnabled) blocking += "local_stub_smoke_not_enabled"
        }
        val readiness = when {
            !enabled -> KnowledgeAnswerProviderReadinessStatus.DISABLED
            provider.providerType == KnowledgeEmbeddingProviderType.EXTERNAL && prodBlocked -> KnowledgeAnswerProviderReadinessStatus.PRODUCTION_BLOCKED
            blocking.any { it == "profile_not_allowed" } -> KnowledgeAnswerProviderReadinessStatus.BLOCKED_BY_ENVIRONMENT
            blocking.isNotEmpty() -> KnowledgeAnswerProviderReadinessStatus.MISCONFIGURED
            latest?.outcome == KnowledgeAnswerProviderDiagnosticOutcome.FAILED &&
                latest.failureCategory in setOf(KnowledgeAnswerFailureCategory.PROVIDER_TIMEOUT, KnowledgeAnswerFailureCategory.PROVIDER_UNAVAILABLE, KnowledgeAnswerFailureCategory.RATE_LIMITED) ->
                KnowledgeAnswerProviderReadinessStatus.TEMPORARILY_UNAVAILABLE
            provider.providerType == KnowledgeEmbeddingProviderType.EXTERNAL && endpoint == KnowledgeAnswerEndpointClassification.LOCAL_STUB ->
                KnowledgeAnswerProviderReadinessStatus.READY_FOR_LOCAL_SMOKE
            provider.providerType == KnowledgeEmbeddingProviderType.EXTERNAL && !properties.answers.providers.openai.smokeTestOnly ->
                KnowledgeAnswerProviderReadinessStatus.READY_FOR_NON_PRODUCTION
            provider.providerType == KnowledgeEmbeddingProviderType.EXTERNAL -> KnowledgeAnswerProviderReadinessStatus.READY_FOR_LOCAL_SMOKE
            else -> KnowledgeAnswerProviderReadinessStatus.READY
        }
        val lifecycle = when {
            readiness == KnowledgeAnswerProviderReadinessStatus.DISABLED -> KnowledgeEmbeddingProviderLifecycle.DISABLED
            readiness in setOf(KnowledgeAnswerProviderReadinessStatus.MISCONFIGURED, KnowledgeAnswerProviderReadinessStatus.BLOCKED_BY_ENVIRONMENT, KnowledgeAnswerProviderReadinessStatus.PRODUCTION_BLOCKED) -> KnowledgeEmbeddingProviderLifecycle.MISCONFIGURED
            readiness == KnowledgeAnswerProviderReadinessStatus.TEMPORARILY_UNAVAILABLE -> KnowledgeEmbeddingProviderLifecycle.UNAVAILABLE
            else -> KnowledgeEmbeddingProviderLifecycle.AVAILABLE
        }
        return KnowledgeAnswerProviderReadiness(
            providerId = providerId,
            readiness = readiness,
            lifecycle = lifecycle,
            active = providerId == properties.answers.activeProvider,
            enabled = enabled,
            endpointClassification = endpoint,
            environmentClass = environmentClass(),
            fallbackConfigured = properties.answers.providers.openai.allowFallbackToInternalDemo,
            productionUseBlocked = prodBlocked,
            lastSmokeOutcome = latest?.outcome,
            lastSmokeAt = latest?.completedAt,
            lastSuccessfulSmokeAt = latestSuccessful?.completedAt,
            consecutiveFailureBand = consecutiveFailureBand(providerId),
            latencyBand = latest?.latencyBand ?: "unknown",
            validationOutcome = latest?.responseValidationOutcome ?: KnowledgeAnswerResponseValidationOutcome.NOT_APPLICABLE,
            failureCategory = blocking.takeIf { it.isNotEmpty() }?.let { KnowledgeAnswerFailureCategory.CONFIGURATION_ERROR } ?: latest?.failureCategory,
            blockingReasons = blocking.sorted(),
            modelPresent = provider.modelId.isNotBlank(),
            promptTemplateId = properties.answers.promptTemplateId,
            promptVersion = properties.answers.promptVersion
        )
    }

    private fun saveDiagnostic(
        provider: KnowledgeAnswerProvider,
        started: java.time.Instant,
        completed: java.time.Instant,
        outcome: KnowledgeAnswerProviderDiagnosticOutcome,
        failureCategory: KnowledgeAnswerFailureCategory?,
        validationOutcome: KnowledgeAnswerResponseValidationOutcome
    ): KnowledgeAnswerProviderDiagnosticRecord =
        repository.saveAnswerProviderDiagnostic(
            KnowledgeAnswerProviderDiagnosticRecord(
                id = UUID.randomUUID(),
                providerId = provider.providerId,
                diagnosticType = KnowledgeAnswerProviderDiagnosticType.SMOKE_TEST,
                triggerType = KnowledgeAnswerProviderDiagnosticTrigger.OPERATOR,
                startedAt = started,
                completedAt = completed,
                outcome = outcome,
                failureCategory = failureCategory,
                latencyBand = latencyBand(Duration.between(started, completed)),
                retryCount = 0,
                responseValidationOutcome = validationOutcome,
                promptTemplateId = properties.answers.promptTemplateId,
                promptVersion = properties.answers.promptVersion,
                modelId = provider.modelId,
                environmentClass = environmentClass(),
                createdAt = completed
            )
        )

    private fun syntheticPrompt(): KnowledgePrompt {
        val citation = KnowledgeCitation(
            citationId = "K1",
            documentReference = UUID.nameUUIDFromBytes("synthetic-knowledge-document".toByteArray()),
            chunkReference = UUID.nameUUIDFromBytes("synthetic-knowledge-chunk".toByteArray()),
            title = "Synthetic Knowledge Smoke Context",
            category = com.hotelopai.knowledge.domain.KnowledgeCategory.GENERAL,
            chunkPosition = 1,
            retrievalScore = 1.0,
            contentFingerprint = "synthetic-knowledge-smoke-v1",
            excerpt = "Synthetic context: confirmed procedure, low backlog band, room assigned, normal operating capability flags. This is not real hotel data."
        )
        return KnowledgePrompt(
            templateId = properties.answers.promptTemplateId,
            version = properties.answers.promptVersion,
            systemInstructions = "Answer only from supplied synthetic Hotel OpAI knowledge context. Do not create tasks or operational actions.",
            operatorQuery = "How should an operator use a verified procedure?",
            contextItems = listOf(
                KnowledgePromptContextItem(
                    citationId = "K1",
                    text = "Synthetic context: confirmed procedure, low backlog band, room assigned, normal operating capability flags. This is not real hotel data.",
                    citation = citation
                )
            ),
            outputSchema = "Structured output: status, answer, confidence, citationIds."
        )
    }

    private fun KnowledgeAnswerProvider.enabledByConfiguration(): Boolean =
        when (providerId) {
            "internal-demo" -> properties.answers.providers.internalDemo.enabled
            "openai" -> properties.answers.providers.openai.enabled
            else -> readiness() != KnowledgeEmbeddingProviderReadiness.DISABLED
        }

    private fun endpointClassification(providerId: String): KnowledgeAnswerEndpointClassification {
        if (providerId != "openai") return KnowledgeAnswerEndpointClassification.INVALID
        val endpoint = properties.answers.providers.openai.endpoint
        val uri = runCatching { URI.create(endpoint) }.getOrNull() ?: return KnowledgeAnswerEndpointClassification.INVALID
        val local = properties.answers.providers.externalPolicy.localEndpointAllowlist.any { endpoint.startsWith(it) }
        return when {
            local -> KnowledgeAnswerEndpointClassification.LOCAL_STUB
            uri.scheme == "https" -> KnowledgeAnswerEndpointClassification.EXTERNAL_HTTPS
            uri.scheme == "http" -> KnowledgeAnswerEndpointClassification.EXTERNAL_HTTP
            else -> KnowledgeAnswerEndpointClassification.INVALID
        }
    }

    private fun environmentClass(): String {
        val profiles = environment.activeProfiles.toSet()
        return when {
            profiles.any { it == "prod" || it == "production" } -> "PRODUCTION"
            profiles.any { it == "test" } -> "TEST"
            profiles.any { it == "local" } -> "LOCAL"
            profiles.isEmpty() -> "DEFAULT"
            else -> "NON_PRODUCTION"
        }
    }

    private fun productionBlocked(): Boolean =
        properties.answers.providers.externalPolicy.productionProhibited &&
            environment.activeProfiles.any { it == "prod" || it == "production" }

    private fun externalProfileAllowed(): Boolean {
        val profiles = environment.activeProfiles.toSet()
        val allowed = properties.answers.providers.openai.allowedProfiles.ifEmpty { properties.answers.providers.externalPolicy.allowedProfiles }
        return allowed.isEmpty() || profiles.any { it in allowed }
    }

    private fun validateActiveProviderConfiguration() {
        val provider = providersById[properties.answers.activeProvider] ?: return
        if (provider.providerType == KnowledgeEmbeddingProviderType.EXTERNAL) {
            val readiness = readinessInternal(provider.providerId)
            require(readiness.readiness !in setOf(KnowledgeAnswerProviderReadinessStatus.PRODUCTION_BLOCKED, KnowledgeAnswerProviderReadinessStatus.BLOCKED_BY_ENVIRONMENT, KnowledgeAnswerProviderReadinessStatus.MISCONFIGURED)) {
                "knowledge answer external provider configuration is invalid: ${readiness.blockingReasons.joinToString(",")}"
            }
            require(!properties.answers.providers.openai.smokeTestOnly) {
                "knowledge answer external provider is configured for smoke-test only and cannot be active for runtime generation"
            }
        }
    }

    private fun consecutiveFailureBand(providerId: String): String =
        when (repository.answerProviderDiagnostics(providerId, 10, 0).content.takeWhile { it.outcome == KnowledgeAnswerProviderDiagnosticOutcome.FAILED }.size) {
            0 -> "none"
            1 -> "one"
            2 -> "two"
            in 3..5 -> "three_five"
            else -> "over_five"
        }

    private fun latencyBand(duration: Duration): String =
        when {
            duration.toMillis() < 100 -> "under_100ms"
            duration.toMillis() < 500 -> "100_500ms"
            duration.toMillis() < 2_000 -> "500ms_2s"
            else -> "over_2s"
        }

    private fun countBand(value: Int): String =
        when {
            value == 0 -> "zero"
            value <= 2 -> "one_two"
            value <= 5 -> "three_five"
            else -> "over_five"
        }
}

private val ACTIVE_REQUEST_STATUSES = setOf(
    KnowledgeAnswerRequestStatus.REQUESTED,
    KnowledgeAnswerRequestStatus.RETRIEVING,
    KnowledgeAnswerRequestStatus.GENERATING,
    KnowledgeAnswerRequestStatus.VALIDATING
)

private val TRANSIENT_RETRY_FAILURES = setOf(
    KnowledgeAnswerFailureCategory.PROVIDER_TIMEOUT,
    KnowledgeAnswerFailureCategory.PROVIDER_UNAVAILABLE,
    KnowledgeAnswerFailureCategory.RATE_LIMITED
)
