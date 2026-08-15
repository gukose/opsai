package com.hotelopai.knowledge.application

import com.hotelopai.knowledge.domain.KnowledgeChunkId
import com.hotelopai.knowledge.domain.KnowledgeDocumentId
import com.hotelopai.observability.OperationalObservability
import com.hotelopai.shared.kernel.PersistenceInstant
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.ln

@Service
class KnowledgeRetrievalEvaluationService(
    private val knowledgeBaseService: KnowledgeBaseService,
    private val embeddingService: KnowledgeEmbeddingService,
    private val repository: KnowledgeRetrievalEvaluationRepository,
    private val properties: KnowledgeProperties,
    private val clock: Clock,
    private val observability: OperationalObservability = OperationalObservability.noop()
) {
    @Transactional
    fun runEvaluation(request: KnowledgeRetrievalEvaluationRequest): KnowledgeRetrievalEvaluationRun {
        val startedAt = PersistenceInstant.now(clock)
        val metrics = mutableListOf<KnowledgeRetrievalMetricSummary>()
        var failedModes = 0
        var failure: KnowledgeEmbeddingFailureCategory? = null

        request.modes.sortedBy { it.name }.forEach { mode ->
            val result = runCatching { evaluateMode(request, mode) }
            result.onSuccess {
                metrics += it
                observability.incrementCounter("knowledge_retrieval_evaluations_total", "search_mode" to mode.name.lowercase(), "outcome" to "succeeded", "failure_category" to "none")
            }.onFailure {
                failedModes += 1
                val category = KnowledgeEmbeddingFailureCategory.PROVIDER_UNAVAILABLE
                failure = category
                observability.incrementCounter("knowledge_retrieval_evaluations_total", "search_mode" to mode.name.lowercase(), "outcome" to "failed", "failure_category" to category.name)
            }
        }

        val status = when {
            metrics.isEmpty() -> KnowledgeRetrievalEvaluationStatus.FAILED
            failedModes > 0 -> KnowledgeRetrievalEvaluationStatus.PARTIALLY_SUCCEEDED
            else -> KnowledgeRetrievalEvaluationStatus.SUCCEEDED
        }
        val completedAt = PersistenceInstant.now(clock)
        val run = KnowledgeRetrievalEvaluationRun(
            id = UUID.randomUUID(),
            name = request.name.trim(),
            status = status,
            caseCount = request.cases.size,
            k = request.k,
            modes = request.modes,
            startedAt = startedAt,
            completedAt = completedAt,
            failureCategory = if (status == KnowledgeRetrievalEvaluationStatus.SUCCEEDED) null else failure,
            metrics = metrics.sortedBy { it.mode.name }
        )
        return repository.saveEvaluationRun(run)
    }

    fun benchmark(request: KnowledgeRetrievalEvaluationRequest): KnowledgeRetrievalEvaluationRun =
        runEvaluation(request.copy(modes = setOf(KnowledgeSearchMode.KEYWORD, KnowledgeSearchMode.SEMANTIC, KnowledgeSearchMode.HYBRID)))

    fun history(page: Int, size: Int): List<KnowledgeRetrievalEvaluationRun> =
        repository.evaluationRuns(size.coerceIn(1, 100), page.coerceAtLeast(0) * size.coerceIn(1, 100))

    fun detail(runId: UUID): KnowledgeRetrievalEvaluationRun? =
        repository.evaluationRun(runId)

    fun readinessReport(): KnowledgeRetrievalReadinessSummary {
        val base = embeddingService.retrievalReadiness()
        val latest = repository.latestEvaluationRun()
        if (!base.semanticEnabled) return base.copy(state = KnowledgeRetrievalReadiness.DISABLED, evaluationStatus = latest?.status?.name, latestEvaluationRunId = latest?.id, qualityGateStatus = "DISABLED")
        if (base.totalChunks == 0L) {
            return base.copy(
                state = KnowledgeRetrievalReadiness.NOT_INDEXED,
                blockingReasons = (base.blockingReasons + "knowledge_not_indexed").distinct(),
                evaluationStatus = latest?.status?.name,
                latestEvaluationRunId = latest?.id,
                qualityGateStatus = "NOT_RUN"
            )
        }
        if (latest == null) {
            return base.copy(
                state = KnowledgeRetrievalReadiness.NOT_EVALUATED,
                blockingReasons = (base.blockingReasons + "retrieval_not_evaluated").distinct(),
                evaluationStatus = null,
                latestEvaluationRunId = null,
                qualityGateStatus = "NOT_RUN"
            )
        }
        val bestHitRate = latest.metrics.maxOfOrNull { it.hitRate } ?: 0.0
        val qualityGateFailed = qualityGateFailed(latest)
        val state = when {
            base.state == KnowledgeRetrievalReadiness.MISCONFIGURED -> KnowledgeRetrievalReadiness.MISCONFIGURED
            base.state == KnowledgeRetrievalReadiness.PROVIDER_UNAVAILABLE -> KnowledgeRetrievalReadiness.PROVIDER_UNAVAILABLE
            latest.status == KnowledgeRetrievalEvaluationStatus.FAILED -> KnowledgeRetrievalReadiness.DEGRADED
            qualityGateFailed -> KnowledgeRetrievalReadiness.QUALITY_GATE_FAILED
            bestHitRate < 0.5 -> KnowledgeRetrievalReadiness.READY_WITH_WARNINGS
            base.state == KnowledgeRetrievalReadiness.READY -> KnowledgeRetrievalReadiness.READY
            else -> KnowledgeRetrievalReadiness.READY_WITH_WARNINGS
        }
        return base.copy(
            state = state,
            blockingReasons = when (state) {
                KnowledgeRetrievalReadiness.READY -> base.blockingReasons
                KnowledgeRetrievalReadiness.QUALITY_GATE_FAILED -> (base.blockingReasons + "retrieval_quality_gate_failed").distinct()
                else -> (base.blockingReasons + "retrieval_quality_warning").distinct()
            },
            evaluationStatus = latest.status.name,
            latestEvaluationRunId = latest.id,
            qualityGateStatus = if (qualityGateFailed) "FAIL" else "PASS"
        )
    }

    private fun qualityGateFailed(run: KnowledgeRetrievalEvaluationRun): Boolean {
        if (!properties.retrievalQuality.gatesEnabled && !properties.retrievalQuality.verificationEnabled) return false
        return properties.retrievalQuality.modes.any { mode ->
            val metric = run.metrics.firstOrNull { it.mode == mode } ?: return@any true
            val threshold = when (mode) {
                KnowledgeSearchMode.KEYWORD -> properties.retrievalQuality.keywordThresholds
                KnowledgeSearchMode.SEMANTIC -> properties.retrievalQuality.semanticThresholds
                KnowledgeSearchMode.HYBRID -> properties.retrievalQuality.hybridThresholds
            }
            run.caseCount < threshold.minimumEvaluatedQueryCount ||
                metric.precisionAtK < threshold.minimumPrecisionAtK ||
                metric.recallAtK < threshold.minimumRecallAtK ||
                metric.meanReciprocalRank < threshold.minimumMrr ||
                metric.normalizedDiscountedCumulativeGain < threshold.minimumNdcg ||
                metric.hitRate < threshold.minimumHitRate ||
                metric.averageLatencyMillis > threshold.maximumAverageLatencyMillis
        }
    }

    private fun evaluateMode(request: KnowledgeRetrievalEvaluationRequest, mode: KnowledgeSearchMode): KnowledgeRetrievalMetricSummary {
        var precision = 0.0
        var recall = 0.0
        var reciprocalRank = 0.0
        var ndcg = 0.0
        var hits = 0
        var latency = 0L
        var retrieved = 0

        request.cases.forEach { case ->
            val started = System.nanoTime()
            val results = knowledgeBaseService.search(
                KnowledgeSearchQuery(
                    query = case.query,
                    hotelId = request.hotelId,
                    mode = mode,
                    page = 0,
                    size = request.k
                )
            ).content
            latency += ((System.nanoTime() - started) / 1_000_000L).coerceAtLeast(0L)
            retrieved += results.size
            val expectedDocs = case.expectedDocumentIds
            val expectedChunks = case.expectedChunkIds
            val relevant = results.mapIndexedNotNull { index, result ->
                if (isRelevant(result.documentId, result.chunkId, expectedDocs, expectedChunks)) index + 1 else null
            }
            val relevantCount = relevant.size
            val expectedCount = (expectedDocs.size + expectedChunks.size).coerceAtLeast(1)
            precision += relevantCount.toDouble() / request.k.toDouble()
            recall += relevantCount.toDouble() / expectedCount.toDouble()
            reciprocalRank += relevant.firstOrNull()?.let { 1.0 / it.toDouble() } ?: 0.0
            ndcg += ndcg(relevant, expectedCount, request.k)
            if (relevant.isNotEmpty()) hits += 1
        }
        val count = request.cases.size.toDouble()
        val hitRate = hits.toDouble() / count
        return KnowledgeRetrievalMetricSummary(
            mode = mode,
            precisionAtK = precision / count,
            recallAtK = recall / count,
            meanReciprocalRank = reciprocalRank / count,
            normalizedDiscountedCumulativeGain = ndcg / count,
            hitRate = hitRate,
            averageLatencyMillis = latency / request.cases.size,
            averageRetrievedChunks = retrieved.toDouble() / count,
            scoreBand = scoreBand(hitRate)
        )
    }

    private fun isRelevant(
        documentId: KnowledgeDocumentId,
        chunkId: KnowledgeChunkId,
        expectedDocuments: Set<KnowledgeDocumentId>,
        expectedChunks: Set<KnowledgeChunkId>
    ): Boolean =
        documentId in expectedDocuments || chunkId in expectedChunks

    private fun ndcg(relevantRanks: List<Int>, expectedCount: Int, k: Int): Double {
        if (relevantRanks.isEmpty()) return 0.0
        val dcg = relevantRanks.sumOf { rank -> 1.0 / log2(rank + 1.0) }
        val idealHits = expectedCount.coerceAtMost(k)
        val idcg = (1..idealHits).sumOf { rank -> 1.0 / log2(rank + 1.0) }
        return if (idcg == 0.0) 0.0 else dcg / idcg
    }

    private fun log2(value: Double): Double =
        ln(value) / ln(2.0)

    private fun scoreBand(value: Double): String =
        when {
            value >= 0.9 -> "excellent"
            value >= 0.7 -> "good"
            value >= 0.5 -> "warning"
            else -> "poor"
        }
}
