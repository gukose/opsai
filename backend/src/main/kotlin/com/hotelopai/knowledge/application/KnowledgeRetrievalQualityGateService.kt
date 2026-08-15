package com.hotelopai.knowledge.application

import com.hotelopai.knowledge.domain.KnowledgeDocumentId
import com.hotelopai.observability.OperationalObservability
import com.hotelopai.shared.kernel.PersistenceInstant
import org.springframework.stereotype.Service
import java.time.Clock
import java.util.UUID

@Service
class KnowledgeRetrievalQualityGateService(
    private val datasetService: KnowledgeCuratedRetrievalDatasetService,
    private val knowledgeBaseService: KnowledgeBaseService,
    private val evaluationService: KnowledgeRetrievalEvaluationService,
    private val properties: KnowledgeProperties,
    private val clock: Clock,
    private val auditSink: KnowledgeOperationsAuditSink = NoOpKnowledgeOperationsAuditSink,
    private val observability: OperationalObservability = OperationalObservability.noop()
) {
    fun validateCuratedDataset(): KnowledgeCuratedDatasetValidationReport {
        val validation = datasetService.validate()
        auditSink.record(KnowledgeOperationsAuditEvent("curated_dataset_validated", if (validation.valid) "valid" else "invalid", PersistenceInstant.now(clock), properties.retrievalQuality.datasetVersion))
        observability.incrementCounter("knowledge_retrieval_dataset_validation_total", "dataset_version" to validation.datasetVersion, "outcome" to if (validation.valid) "valid" else "invalid")
        return validation
    }

    fun executeQualityGate(hotelId: UUID?): KnowledgeRetrievalQualityReport {
        auditSink.record(KnowledgeOperationsAuditEvent("quality_gate_requested", "requested", PersistenceInstant.now(clock), properties.retrievalQuality.datasetVersion))
        val validation = datasetService.validate()
        require(validation.valid) { "curated knowledge retrieval dataset is invalid: ${validation.failureReasons.joinToString(",")}" }
        val request = curatedRequest(hotelId)
        val run = evaluationService.runEvaluation(request)
        val report = qualityReport(run, properties.retrievalQuality)
        observability.incrementCounter(
            "knowledge_retrieval_quality_gates_total",
            "dataset_version" to report.datasetVersion,
            "retrieval_mode" to "all",
            "outcome" to report.outcome.name.lowercase(),
            "failure_category" to if (report.outcome == KnowledgeRetrievalQualityOutcome.FAIL) "threshold" else "none"
        )
        observability.incrementCounter(
            "knowledge_retrieval_quality_gate_failed_thresholds_total",
            report.failedThresholds.size.toDouble(),
            "dataset_version" to report.datasetVersion,
            "retrieval_mode" to "all",
            "outcome" to report.outcome.name.lowercase()
        )
        auditSink.record(KnowledgeOperationsAuditEvent("quality_gate_completed", report.outcome.name.lowercase(), PersistenceInstant.now(clock), properties.retrievalQuality.datasetVersion))
        return report
    }
 
    fun latestQualityReport(): KnowledgeRetrievalQualityReport? =
        evaluationService.history(0, 1).firstOrNull()?.let { qualityReport(it, properties.retrievalQuality) }

    fun qualityReport(run: KnowledgeRetrievalEvaluationRun, quality: KnowledgeRetrievalQualityProperties = properties.retrievalQuality): KnowledgeRetrievalQualityReport {
        val reports = quality.modes.sortedBy { it.name }.map { mode ->
            val metric = run.metrics.firstOrNull { it.mode == mode }
            val failures = if (metric == null) {
                listOf("missing_mode:${mode.name}")
            } else {
                failedThresholds(mode, metric, run.caseCount, thresholdsFor(mode, quality))
            }
            KnowledgeRetrievalQualityModeReport(
                mode = mode,
                metrics = metric,
                outcome = when {
                    failures.isEmpty() -> KnowledgeRetrievalQualityOutcome.PASS
                    failures.any { it.startsWith("missing_mode") || it.startsWith("minimum_") } -> KnowledgeRetrievalQualityOutcome.FAIL
                    else -> KnowledgeRetrievalQualityOutcome.PASS_WITH_WARNINGS
                },
                failedThresholds = failures
            )
        }
        val failures = reports.flatMap { modeReport -> modeReport.failedThresholds.map { "${modeReport.mode.name}:$it" } }
        val outcome = when {
            reports.any { it.outcome == KnowledgeRetrievalQualityOutcome.FAIL } -> KnowledgeRetrievalQualityOutcome.FAIL
            reports.any { it.outcome == KnowledgeRetrievalQualityOutcome.PASS_WITH_WARNINGS } -> KnowledgeRetrievalQualityOutcome.PASS_WITH_WARNINGS
            else -> KnowledgeRetrievalQualityOutcome.PASS
        }
        return KnowledgeRetrievalQualityReport(
            datasetVersion = quality.datasetVersion,
            runId = run.id,
            outcome = outcome,
            evaluatedQueryCount = run.caseCount,
            k = run.k,
            modeReports = reports,
            failedThresholds = failures
        )
    }

    private fun curatedRequest(hotelId: UUID?): KnowledgeRetrievalEvaluationRequest {
        val dataset = datasetService.dataset()
        val documents = knowledgeBaseService.documents(KnowledgeDocumentFilter(hotelId = hotelId, size = 100)).content
        val byReference = documents
            .filter { it.metadata.attributes["curated_ref"] != null }
            .associateBy { requireNotNull(it.metadata.attributes["curated_ref"]) }
        val missing = dataset.cases.flatMap { it.expectedDocumentReferences }.distinct().filterNot { it in byReference }
        require(missing.isEmpty()) { "curated knowledge retrieval fixtures are not imported for references: ${missing.joinToString(",")}" }
        val cases = dataset.cases.map { case ->
            KnowledgeRetrievalEvaluationCase(
                query = case.query,
                expectedDocumentIds = case.expectedDocumentReferences.map { reference -> KnowledgeDocumentId(byReference.getValue(reference).id.value) }.toSet()
            )
        }
        return KnowledgeRetrievalEvaluationRequest(
            name = "curated ${dataset.version}",
            hotelId = hotelId,
            cases = cases,
            modes = properties.retrievalQuality.modes,
            k = properties.retrievalQuality.k
        )
    }

    private fun thresholdsFor(mode: KnowledgeSearchMode, quality: KnowledgeRetrievalQualityProperties): KnowledgeRetrievalQualityThresholds {
        val configured = when (mode) {
            KnowledgeSearchMode.KEYWORD -> quality.keywordThresholds
            KnowledgeSearchMode.SEMANTIC -> quality.semanticThresholds
            KnowledgeSearchMode.HYBRID -> quality.hybridThresholds
        }
        return KnowledgeRetrievalQualityThresholds(
            minimumPrecisionAtK = configured.minimumPrecisionAtK,
            minimumRecallAtK = configured.minimumRecallAtK,
            minimumMrr = configured.minimumMrr,
            minimumNdcg = configured.minimumNdcg,
            minimumHitRate = configured.minimumHitRate,
            maximumAverageLatencyMillis = configured.maximumAverageLatencyMillis,
            minimumEvaluatedQueryCount = configured.minimumEvaluatedQueryCount
        )
    }

    private fun failedThresholds(
        mode: KnowledgeSearchMode,
        metric: KnowledgeRetrievalMetricSummary,
        queryCount: Int,
        thresholds: KnowledgeRetrievalQualityThresholds
    ): List<String> =
        buildList {
            if (queryCount < thresholds.minimumEvaluatedQueryCount) add("minimum_evaluated_query_count:${queryCount}<${thresholds.minimumEvaluatedQueryCount}")
            if (metric.precisionAtK < thresholds.minimumPrecisionAtK) add("minimum_precision_at_k:${metric.precisionAtK}<${thresholds.minimumPrecisionAtK}")
            if (metric.recallAtK < thresholds.minimumRecallAtK) add("minimum_recall_at_k:${metric.recallAtK}<${thresholds.minimumRecallAtK}")
            if (metric.meanReciprocalRank < thresholds.minimumMrr) add("minimum_mrr:${metric.meanReciprocalRank}<${thresholds.minimumMrr}")
            if (metric.normalizedDiscountedCumulativeGain < thresholds.minimumNdcg) add("minimum_ndcg:${metric.normalizedDiscountedCumulativeGain}<${thresholds.minimumNdcg}")
            if (metric.hitRate < thresholds.minimumHitRate) add("minimum_hit_rate:${metric.hitRate}<${thresholds.minimumHitRate}")
            if (metric.averageLatencyMillis > thresholds.maximumAverageLatencyMillis) add("maximum_average_latency_millis:${metric.averageLatencyMillis}>${thresholds.maximumAverageLatencyMillis}")
        }.sorted()
}
