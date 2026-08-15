package com.hotelopai.knowledge.application

import com.hotelopai.knowledge.domain.KnowledgeDocumentId
import com.hotelopai.observability.OperationalObservability
import com.hotelopai.shared.kernel.PersistenceInstant
import org.springframework.core.env.Environment
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.time.Clock
import java.time.Duration
import java.util.UUID
import kotlin.math.sqrt

@Service
class KnowledgeEmbeddingService(
    providers: List<KnowledgeEmbeddingProvider>,
    private val embeddingRepository: KnowledgeEmbeddingRepository,
    private val chunkRepository: KnowledgeChunkRepository,
    private val properties: KnowledgeProperties,
    private val clock: Clock,
    private val environment: Environment? = null,
    private val observability: OperationalObservability = OperationalObservability.noop()
) {
    companion object {
        const val SCHEDULE_ID = "knowledge_embedding_refresh_default"
        const val SCHEDULE_JOB_NAME = "knowledge_embedding_refresh_default"

        fun fingerprint(text: String): String =
            MessageDigest.getInstance("SHA-256")
                .digest(text.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
    }

    private val providerById = providers.associateBy { it.providerId.value }

    init {
        require(providerById.size == providers.size) { "knowledge embedding provider ids must be unique" }
        if (properties.semanticSearch.enabled) validateEnabledConfiguration()
    }

    fun status(): KnowledgeEmbeddingStatusSummary {
        val provider = activeProviderOrNull()
        val counts = if (provider == null) emptyMap() else embeddingRepository.counts(provider.providerId, provider.modelIdentifier)
        return KnowledgeEmbeddingStatusSummary(
            semanticEnabled = properties.semanticSearch.enabled,
            providerId = properties.semanticSearch.activeProvider,
            modelId = properties.semanticSearch.model,
            dimension = properties.semanticSearch.vectorDimension,
            readiness = if (!properties.semanticSearch.enabled) KnowledgeEmbeddingProviderReadiness.DISABLED else provider?.readiness() ?: KnowledgeEmbeddingProviderReadiness.DISABLED,
            readyCount = counts[KnowledgeEmbeddingStatus.READY] ?: 0,
            failedCount = counts[KnowledgeEmbeddingStatus.FAILED] ?: 0,
            staleCount = counts[KnowledgeEmbeddingStatus.STALE] ?: 0
        )
    }

    fun providers(): List<KnowledgeEmbeddingProviderSummary> =
        providerById.values.sortedBy { it.providerId.value }.map { provider ->
            val readiness = if (provider.providerId.value == "openai" && !properties.semanticSearch.externalProviders.openai.enabled) {
                KnowledgeEmbeddingProviderReadiness.DISABLED
            } else {
                provider.readiness()
            }
            KnowledgeEmbeddingProviderSummary(
                providerId = provider.providerId.value,
                providerType = provider.providerType,
                lifecycle = when (readiness) {
                    KnowledgeEmbeddingProviderReadiness.READY -> KnowledgeEmbeddingProviderLifecycle.AVAILABLE
                    KnowledgeEmbeddingProviderReadiness.DISABLED -> KnowledgeEmbeddingProviderLifecycle.DISABLED
                    KnowledgeEmbeddingProviderReadiness.MISCONFIGURED -> KnowledgeEmbeddingProviderLifecycle.MISCONFIGURED
                    KnowledgeEmbeddingProviderReadiness.UNAVAILABLE -> KnowledgeEmbeddingProviderLifecycle.UNAVAILABLE
                },
                active = provider.providerId.value == properties.semanticSearch.activeProvider,
                modelPresent = provider.modelIdentifier.isNotBlank(),
                dimension = provider.embeddingDimension,
                readiness = readiness
            )
        }

    fun retrievalReadiness(): KnowledgeRetrievalReadinessSummary {
        val status = status()
        val now = PersistenceInstant.now(clock)
        val state = embeddingRepository.getOrCreateScheduleState(SCHEDULE_ID, now)
        val totalChunks = embeddingRepository.countChunks()
        val embedded = status.readyCount
        val percentage = if (totalChunks == 0L) 100 else ((embedded * 100) / totalChunks).toInt()
        val reasons = mutableListOf<String>()
        val readiness = when {
            !status.semanticEnabled -> KnowledgeRetrievalReadiness.DISABLED.also { reasons += "semantic_search_disabled" }
            status.readiness == KnowledgeEmbeddingProviderReadiness.MISCONFIGURED -> KnowledgeRetrievalReadiness.MISCONFIGURED.also { reasons += "provider_misconfigured" }
            status.readiness == KnowledgeEmbeddingProviderReadiness.UNAVAILABLE -> KnowledgeRetrievalReadiness.PROVIDER_UNAVAILABLE.also { reasons += "provider_unavailable" }
            status.staleCount > 0 -> KnowledgeRetrievalReadiness.INDEXING.also { reasons += "stale_embeddings_present" }
            status.failedCount > 0 -> KnowledgeRetrievalReadiness.PARTIALLY_INDEXED.also { reasons += "failed_embeddings_present" }
            totalChunks > 0 && embedded < totalChunks -> KnowledgeRetrievalReadiness.PARTIALLY_INDEXED.also { reasons += "missing_embeddings_present" }
            else -> KnowledgeRetrievalReadiness.READY
        }
        return KnowledgeRetrievalReadinessSummary(
            state = readiness,
            semanticEnabled = status.semanticEnabled,
            providerId = status.providerId,
            readiness = status.readiness,
            embeddedPercentage = percentage,
            totalChunks = totalChunks,
            readyCount = status.readyCount,
            failedCount = status.failedCount,
            staleCount = status.staleCount,
            schedulerPaused = state.paused,
            blockingReasons = reasons
        )
    }

    @Transactional
    fun generateBatch(limit: Int = properties.semanticSearch.batchSize): KnowledgeEmbeddingBatchSummary {
        val provider = activeProvider()
        val now = PersistenceInstant.now(clock)
        val requests = embeddingRepository.findChunksNeedingEmbeddings(provider.providerId, provider.modelIdentifier, limit.coerceIn(1, properties.semanticSearch.batchSize))
        if (requests.isEmpty()) {
            return summary(provider, considered = 0, embedded = 0, skipped = 0, failed = 0).also {
                recordDiagnostic(provider, KnowledgeEmbeddingDiagnosticType.BATCH, KnowledgeEmbeddingDiagnosticOutcome.SKIPPED, 0, null, Duration.ZERO)
            }
        }
        val current = requests.filterNot {
            embeddingRepository.findEmbedding(it.chunkId, provider.providerId, provider.modelIdentifier)?.contentFingerprint == it.contentFingerprint
        }
        val skipped = requests.size - current.size
        if (current.isEmpty()) return summary(provider, requests.size, 0, skipped, 0)
        val startedAt = PersistenceInstant.now(clock)
        return runCatching {
            provider.embed(current)
        }.fold(
            onSuccess = { responses ->
                var embedded = 0
                var failed = 0
                responses.forEach { response ->
                    if (response.vector.values.size != provider.embeddingDimension) {
                        failed += 1
                        embeddingRepository.markFailed(failedRecord(provider, response.chunkId, response.contentFingerprint, KnowledgeEmbeddingFailureCategory.DIMENSION_MISMATCH, now))
                    } else {
                        embeddingRepository.upsertReady(
                            KnowledgeEmbeddingRecord(
                                chunkId = response.chunkId,
                                providerId = provider.providerId,
                                modelId = provider.modelIdentifier,
                                dimension = provider.embeddingDimension,
                                vector = response.vector,
                                contentFingerprint = response.contentFingerprint,
                                generatedAt = now,
                                status = KnowledgeEmbeddingStatus.READY,
                                createdAt = now,
                                updatedAt = now
                            )
                        )
                        embedded += 1
                    }
                }
                recordBatch(provider, embedded, skipped, failed, null)
                recordDiagnostic(provider, KnowledgeEmbeddingDiagnosticType.BATCH, if (failed > 0) KnowledgeEmbeddingDiagnosticOutcome.FAILED else KnowledgeEmbeddingDiagnosticOutcome.SUCCEEDED, requests.size, if (failed > 0) KnowledgeEmbeddingFailureCategory.DIMENSION_MISMATCH else null, Duration.between(startedAt, PersistenceInstant.now(clock)))
                summary(provider, requests.size, embedded, skipped, failed, if (failed > 0) KnowledgeEmbeddingFailureCategory.DIMENSION_MISMATCH else null)
            },
            onFailure = { exception ->
                val category = classify(exception)
                current.forEach {
                    embeddingRepository.markFailed(failedRecord(provider, it.chunkId, it.contentFingerprint, category, now))
                }
                recordBatch(provider, 0, skipped, current.size, category)
                recordDiagnostic(provider, KnowledgeEmbeddingDiagnosticType.BATCH, KnowledgeEmbeddingDiagnosticOutcome.FAILED, requests.size, category, Duration.between(startedAt, PersistenceInstant.now(clock)))
                summary(provider, requests.size, 0, skipped, current.size, category)
            }
        )
    }

    @Transactional
    fun regenerateDocument(documentId: KnowledgeDocumentId): KnowledgeEmbeddingBatchSummary {
        val provider = activeProvider()
        markDocumentStale(documentId)
        return generateBatch(properties.semanticSearch.batchSize)
    }

    fun markDocumentStale(documentId: KnowledgeDocumentId): Int {
        val provider = activeProviderOrNull() ?: return 0
        return embeddingRepository.markDocumentStale(documentId, provider.providerId, provider.modelIdentifier, PersistenceInstant.now(clock))
    }

    fun failed(limit: Int): List<KnowledgeEmbeddingRecord> {
        val provider = activeProviderOrNull() ?: return emptyList()
        return embeddingRepository.failed(provider.providerId, provider.modelIdentifier, limit.coerceIn(1, 100))
    }

    fun retryFailures(limit: Int): KnowledgeEmbeddingBatchSummary =
        generateBatch(limit)

    fun scheduleStatus(): KnowledgeEmbeddingScheduleStatus {
        val now = PersistenceInstant.now(clock)
        val state = embeddingRepository.getOrCreateScheduleState(SCHEDULE_ID, now)
        return KnowledgeEmbeddingScheduleStatus(
            configuredEnabled = properties.semanticSearch.schedule.enabled,
            effectiveEnabled = properties.semanticSearch.schedule.enabled && !state.paused,
            paused = state.paused,
            scheduleSummary = "fixedDelay=${properties.semanticSearch.schedule.executionInterval}",
            batchSize = properties.semanticSearch.schedule.batchSize,
            lastAttemptedAt = state.lastAttemptedAt,
            lastSuccessfulAt = state.lastSuccessfulAt,
            lastEmbeddedCount = state.lastEmbeddedCount,
            lastFailureCategory = state.lastFailureCategory,
            leaseState = "not_exposed"
        )
    }

    @Transactional
    fun processScheduledBatch(): KnowledgeEmbeddingBatchSummary {
        val now = PersistenceInstant.now(clock)
        val state = embeddingRepository.getOrCreateScheduleState(SCHEDULE_ID, now)
        if (!properties.semanticSearch.schedule.enabled || state.paused) {
            val provider = activeProviderOrNull()
            return if (provider == null) {
                KnowledgeEmbeddingBatchSummary(properties.semanticSearch.activeProvider, properties.semanticSearch.model, 0, 0, 0, 0, KnowledgeEmbeddingFailureCategory.PROVIDER_DISABLED)
            } else {
                summary(provider, 0, 0, 0, 0, KnowledgeEmbeddingFailureCategory.PROVIDER_DISABLED)
            }
        }
        val summary = runCatching { generateBatch(properties.semanticSearch.schedule.batchSize) }.getOrElse { exception ->
            val provider = activeProviderOrNull()
            val category = classify(exception)
            if (provider == null) {
                KnowledgeEmbeddingBatchSummary(properties.semanticSearch.activeProvider, properties.semanticSearch.model, 0, 0, 0, 0, category)
            } else {
                summary(provider, 0, 0, 0, 0, category)
            }
        }
        embeddingRepository.recordScheduleAttempt(SCHEDULE_ID, summary, now, summary.failureCategory)
        observability.incrementCounter("knowledge_embedding_scheduler_total", "trigger" to "scheduled", "outcome" to if (summary.failureCategory == null) "succeeded" else "failed", "failure_category" to (summary.failureCategory?.name ?: "none"))
        return summary
    }

    fun runScheduledNow(): KnowledgeEmbeddingBatchSummary =
        generateBatch(properties.semanticSearch.schedule.batchSize)

    fun pauseSchedule(): KnowledgeEmbeddingScheduleStatus {
        embeddingRepository.markSchedulePaused(SCHEDULE_ID, PersistenceInstant.now(clock))
        observability.incrementCounter("knowledge_embedding_scheduler_total", "trigger" to "operator", "outcome" to "paused", "failure_category" to "none")
        return scheduleStatus()
    }

    fun resumeSchedule(): KnowledgeEmbeddingScheduleStatus {
        embeddingRepository.markScheduleResumed(SCHEDULE_ID, PersistenceInstant.now(clock))
        observability.incrementCounter("knowledge_embedding_scheduler_total", "trigger" to "operator", "outcome" to "resumed", "failure_category" to "none")
        return scheduleStatus()
    }

    fun diagnostics(providerId: String?, page: Int, size: Int): List<KnowledgeEmbeddingDiagnosticRecord> =
        embeddingRepository.diagnostics(providerId, size.coerceIn(1, 100), page.coerceAtLeast(0) * size.coerceIn(1, 100))

    fun diagnostic(id: UUID): KnowledgeEmbeddingDiagnosticRecord? =
        embeddingRepository.findDiagnostic(id)

    fun cleanupDiagnostics(): Int {
        val cutoff = PersistenceInstant.now(clock).minus(properties.semanticSearch.diagnosticsRetention)
        val deleted = embeddingRepository.cleanupDiagnostics(cutoff, properties.semanticSearch.diagnosticsCleanupBatchSize)
        observability.incrementCounter("knowledge_embedding_diagnostics_cleanup_total", deleted.toDouble(), "outcome" to "deleted")
        return deleted
    }

    fun semanticSearch(query: KnowledgeSearchQuery, strict: Boolean): KnowledgeSearchPage {
        val provider = activeProviderOrNull()
        if (!properties.semanticSearch.enabled || provider == null || provider.readiness() != KnowledgeEmbeddingProviderReadiness.READY) {
            if (!strict && properties.semanticSearch.keywordFallbackEnabled) {
                observability.incrementCounter("knowledge_keyword_fallback_total", "search_mode" to query.mode.name.lowercase(), "outcome" to "fallback")
                return chunkRepository.search(query.copy(mode = KnowledgeSearchMode.KEYWORD))
            }
            throw KnowledgeSemanticSearchUnavailableException("Knowledge semantic search is unavailable.")
        }
        val queryEmbedding = provider.embed(listOf(KnowledgeEmbeddingRequest(com.hotelopai.knowledge.domain.KnowledgeChunkId(java.util.UUID(0L, 0L)), query.query, fingerprint(query.query)))).single().vector
        val candidates = embeddingRepository.semanticCandidates(provider.providerId, provider.modelIdentifier, query.hotelId, queryEmbedding, properties.semanticSearch.semanticResultLimit)
            .filter { it.similarity >= properties.semanticSearch.similarityThreshold }
            .filter { query.category == null || it.category == query.category }
        val rows = candidates.map {
            KnowledgeSearchResult(
                documentId = it.documentId,
                chunkId = it.chunkId,
                title = it.title,
                category = it.category,
                source = it.source,
                language = it.language,
                heading = it.heading,
                snippet = it.snippet,
                chunkOrder = it.chunkOrder,
                rank = (it.similarity * 1_000_000).toInt(),
                tags = it.tags,
                updatedAt = it.updatedAt,
                score = KnowledgeSearchScore(0.0, it.similarity, it.similarity)
            )
        }.sortedWith(compareByDescending<KnowledgeSearchResult> { it.score?.combinedScore ?: 0.0 }.thenByDescending { it.updatedAt }.thenBy { it.documentId.value }.thenBy { it.chunkId.value })
        return page(rows, query.page, query.size)
    }

    fun hybridSearch(query: KnowledgeSearchQuery): KnowledgeSearchPage {
        val keyword = chunkRepository.search(query.copy(mode = KnowledgeSearchMode.KEYWORD, size = 100)).content
        val semantic = runCatching { semanticSearch(query, strict = false).content }.getOrElse {
            if (properties.semanticSearch.keywordFallbackEnabled) return page(keyword.map { it.copy(score = KnowledgeSearchScore(it.rank.toDouble(), 0.0, 1.0, fallbackUsed = true)) }, query.page, query.size)
            throw it
        }
        val maxKeyword = keyword.maxOfOrNull { it.rank }?.coerceAtLeast(1) ?: 1
        val byChunk = linkedMapOf<com.hotelopai.knowledge.domain.KnowledgeChunkId, KnowledgeSearchResult>()
        keyword.forEach {
            byChunk[it.chunkId] = it.copy(score = KnowledgeSearchScore(it.rank.toDouble() / maxKeyword.toDouble(), 0.0, 0.0))
        }
        semantic.forEach { semanticResult ->
            val existing = byChunk[semanticResult.chunkId]
            val semanticScore = semanticResult.score?.semanticScore ?: 0.0
            val keywordScore = existing?.score?.keywordScore ?: 0.0
            val combined = normalizedHybrid(keywordScore, semanticScore)
            byChunk[semanticResult.chunkId] = (existing ?: semanticResult).copy(score = KnowledgeSearchScore(keywordScore, semanticScore, combined))
        }
        val rows = byChunk.values.map {
            if (it.score?.combinedScore == 0.0) it.copy(score = it.score.copy(combinedScore = normalizedHybrid(it.score.keywordScore, it.score.semanticScore))) else it
        }.sortedWith(compareByDescending<KnowledgeSearchResult> { it.score?.combinedScore ?: 0.0 }.thenByDescending { it.updatedAt }.thenBy { it.documentId.value }.thenBy { it.chunkId.value })
        return page(rows, query.page, query.size)
    }

    private fun activeProvider(): KnowledgeEmbeddingProvider {
        if (!properties.semanticSearch.enabled) {
            throw KnowledgeSemanticSearchUnavailableException("Knowledge semantic search is disabled.")
        }
        validateEnabledConfiguration()
        val provider = activeProviderOrNull() ?: throw KnowledgeSemanticSearchUnavailableException("Knowledge embedding provider is not registered.")
        if (provider.readiness() != KnowledgeEmbeddingProviderReadiness.READY) {
            throw KnowledgeSemanticSearchUnavailableException("Knowledge embedding provider is not ready.")
        }
        return provider
    }

    private fun activeProviderOrNull(): KnowledgeEmbeddingProvider? =
        providerById[properties.semanticSearch.activeProvider]

    private fun validateEnabledConfiguration() {
        val activeProfiles = environment?.activeProfiles?.toSet().orEmpty()
        val allowed = properties.semanticSearch.allowedProfiles
        if (allowed.isNotEmpty() && activeProfiles.intersect(allowed.toSet()).isEmpty()) {
            throw IllegalStateException("knowledge semantic search profile is not allowed")
        }
        val provider = activeProviderOrNull() ?: throw IllegalStateException("knowledge semantic search active provider is not registered")
        if (provider.embeddingDimension != properties.semanticSearch.vectorDimension) {
            throw IllegalStateException("knowledge semantic search vector dimension does not match provider")
        }
        if (properties.semanticSearch.schedule.enabled) {
            val allowedScheduleProfiles = properties.semanticSearch.schedule.allowedProfiles
            if (allowedScheduleProfiles.isNotEmpty() && activeProfiles.intersect(allowedScheduleProfiles.toSet()).isEmpty()) {
                throw IllegalStateException("knowledge embedding refresh schedule profile is not allowed")
            }
        }
    }

    private fun failedRecord(
        provider: KnowledgeEmbeddingProvider,
        chunkId: com.hotelopai.knowledge.domain.KnowledgeChunkId,
        fingerprint: String,
        category: KnowledgeEmbeddingFailureCategory,
        now: java.time.Instant
    ): KnowledgeEmbeddingRecord =
        KnowledgeEmbeddingRecord(
            chunkId = chunkId,
            providerId = provider.providerId,
            modelId = provider.modelIdentifier,
            dimension = provider.embeddingDimension,
            vector = null,
            contentFingerprint = fingerprint,
            generatedAt = null,
            status = KnowledgeEmbeddingStatus.FAILED,
            failureCategory = category,
            attemptCount = 1,
            createdAt = now,
            updatedAt = now
        )

    private fun classify(exception: Throwable): KnowledgeEmbeddingFailureCategory =
        when (exception) {
            is KnowledgeEmbeddingProviderException -> exception.category
            is KnowledgeSemanticSearchUnavailableException -> KnowledgeEmbeddingFailureCategory.PROVIDER_UNAVAILABLE
            is IllegalArgumentException -> KnowledgeEmbeddingFailureCategory.INVALID_RESPONSE
            else -> KnowledgeEmbeddingFailureCategory.PROVIDER_UNAVAILABLE
        }

    private fun summary(provider: KnowledgeEmbeddingProvider, considered: Int, embedded: Int, skipped: Int, failed: Int, failure: KnowledgeEmbeddingFailureCategory? = null) =
        KnowledgeEmbeddingBatchSummary(provider.providerId.value, provider.modelIdentifier, considered, embedded, skipped, failed, failure)

    private fun recordBatch(provider: KnowledgeEmbeddingProvider, embedded: Int, skipped: Int, failed: Int, failure: KnowledgeEmbeddingFailureCategory?) {
        observability.incrementCounter("knowledge_embedding_batches_total", "provider" to provider.providerId.value, "model" to provider.modelIdentifier, "outcome" to if (failed > 0) "failed" else "succeeded", "failure_category" to (failure?.name ?: "none"))
        observability.incrementCounter("knowledge_chunks_embedded_total", embedded.toDouble(), "provider" to provider.providerId.value, "model" to provider.modelIdentifier, "outcome" to "embedded")
        observability.incrementCounter("knowledge_embedding_skipped_total", skipped.toDouble(), "provider" to provider.providerId.value, "model" to provider.modelIdentifier, "outcome" to "unchanged")
    }

    private fun recordDiagnostic(
        provider: KnowledgeEmbeddingProvider,
        type: KnowledgeEmbeddingDiagnosticType,
        outcome: KnowledgeEmbeddingDiagnosticOutcome,
        batchSize: Int,
        failure: KnowledgeEmbeddingFailureCategory?,
        elapsed: Duration
    ) {
        val now = PersistenceInstant.now(clock)
        embeddingRepository.saveDiagnostic(
            KnowledgeEmbeddingDiagnosticRecord(
                id = UUID.randomUUID(),
                providerId = provider.providerId.value,
                modelId = provider.modelIdentifier,
                diagnosticType = type,
                outcome = outcome,
                readiness = provider.readiness(),
                failureCategory = failure,
                latencyBand = latencyBand(elapsed),
                batchSize = batchSize,
                generatedAt = now,
                createdAt = now
            )
        )
        observability.incrementCounter("knowledge_embedding_provider_diagnostics_total", "provider" to provider.providerId.value, "outcome" to outcome.name.lowercase(), "failure_category" to (failure?.name ?: "none"))
    }

    private fun latencyBand(duration: Duration): String =
        when {
            duration.toMillis() < 100 -> "under_100ms"
            duration.toMillis() < 500 -> "100_500ms"
            duration.toMillis() < 2_000 -> "500ms_2s"
            else -> "over_2s"
        }

    private fun normalizedHybrid(keyword: Double, semantic: Double): Double {
        val weights = properties.semanticSearch
        val total = weights.hybridKeywordWeight + weights.hybridSemanticWeight
        return (keyword * weights.hybridKeywordWeight + semantic * weights.hybridSemanticWeight) / total
    }

    private fun page(rows: List<KnowledgeSearchResult>, page: Int, size: Int): KnowledgeSearchPage {
        val normalizedPage = page.coerceAtLeast(0)
        val normalizedSize = size.coerceIn(1, 100)
        val total = rows.size.toLong()
        return KnowledgeSearchPage(rows.drop(normalizedPage * normalizedSize).take(normalizedSize), normalizedPage, normalizedSize, total, if (total == 0L) 0 else kotlin.math.ceil(total.toDouble() / normalizedSize.toDouble()).toInt())
    }

}
