package com.hotelopai.knowledge.application

import com.hotelopai.knowledge.domain.KnowledgeMetadata
import com.hotelopai.support.PostgresIntegrationTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import java.time.Instant
import java.util.UUID

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(
    properties = [
        "ops.ai.knowledge.semantic-search.enabled=true",
        "ops.ai.knowledge.semantic-search.allowed-profiles[0]=test",
        "ops.ai.knowledge.semantic-search.vector-dimension=16",
        "ops.ai.knowledge.semantic-search.batch-size=50",
        "ops.ai.knowledge.semantic-search.keyword-fallback-enabled=true",
        "ops.ai.knowledge.retrieval-quality.verification-enabled=true",
        "ops.ai.knowledge.retrieval-quality.k=5",
        "ops.ai.knowledge.retrieval-quality.keyword-thresholds.minimum-hit-rate=0.7",
        "ops.ai.knowledge.retrieval-quality.semantic-thresholds.minimum-hit-rate=0.0",
        "ops.ai.knowledge.retrieval-quality.hybrid-thresholds.minimum-hit-rate=0.7"
    ]
)
class KnowledgeRetrievalQualityVerificationTest : PostgresIntegrationTestSupport() {
    @Autowired
    private lateinit var knowledgeBaseService: KnowledgeBaseService

    @Autowired
    private lateinit var embeddingService: KnowledgeEmbeddingService

    @Autowired
    private lateinit var datasetService: KnowledgeCuratedRetrievalDatasetService

    @Autowired
    private lateinit var qualityGateService: KnowledgeRetrievalQualityGateService

    @Autowired
    private lateinit var contextAssembler: KnowledgeContextAssembler

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun cleanKnowledgeTables() {
        jdbcTemplate.execute("delete from knowledge_retrieval_evaluation_run")
        jdbcTemplate.execute("delete from knowledge_document")
    }

    @Test
    fun `curated retrieval quality gate passes for deterministic local fixtures`() {
        importCuratedFixtures()
        embeddingService.generateBatch(50)

        val validation = qualityGateService.validateCuratedDataset()
        val report = qualityGateService.executeQualityGate(hotelId = null)
        val readiness = qualityGateService.latestQualityReport()

        assertThat(validation.valid).isTrue()
        assertThat(validation.caseCount).isGreaterThanOrEqualTo(7)
        assertThat(report.outcome).withFailMessage(report.toString()).isEqualTo(KnowledgeRetrievalQualityOutcome.PASS)
        assertThat(report.modeReports.map { it.mode }).containsExactly(KnowledgeSearchMode.HYBRID, KnowledgeSearchMode.KEYWORD, KnowledgeSearchMode.SEMANTIC)
        assertThat(report.failedThresholds).isEmpty()
        assertThat(readiness?.runId).isEqualTo(report.runId)
    }

    @Test
    fun `context assembly is bounded attributed and free of vectors`() {
        importCuratedFixtures()
        embeddingService.generateBatch(50)

        val result = contextAssembler.assemble(
            KnowledgeContextAssemblyRequest(
                query = "thermostat blocked vents target temperature",
                hotelId = null,
                mode = KnowledgeSearchMode.HYBRID,
                categories = setOf(com.hotelopai.knowledge.domain.KnowledgeCategory.MAINTENANCE),
                limit = 4
            )
        )

        assertThat(result.itemCount).isBetween(1, 4)
        assertThat(result.totalCharacters).isLessThanOrEqualTo(4_000)
        assertThat(result.items.map { it.citation.category }).containsOnly(com.hotelopai.knowledge.domain.KnowledgeCategory.MAINTENANCE)
        assertThat(result.items.first().citation.title).contains("Thermostat")
        assertThat(result.items.first().citation.contentFingerprint).hasSize(64)
    }

    @Test
    fun `quality report preserves failing modes and warning latency thresholds`() {
        val run = KnowledgeRetrievalEvaluationRun(
            id = UUID.randomUUID(),
            name = "synthetic quality report",
            status = KnowledgeRetrievalEvaluationStatus.SUCCEEDED,
            caseCount = 2,
            k = 5,
            modes = setOf(KnowledgeSearchMode.KEYWORD, KnowledgeSearchMode.SEMANTIC),
            startedAt = Instant.parse("2026-01-01T00:00:00Z"),
            completedAt = Instant.parse("2026-01-01T00:00:01Z"),
            failureCategory = null,
            metrics = listOf(
                KnowledgeRetrievalMetricSummary(KnowledgeSearchMode.KEYWORD, 0.0, 0.0, 0.0, 0.0, 0.0, 1, 0.0, "poor"),
                KnowledgeRetrievalMetricSummary(KnowledgeSearchMode.SEMANTIC, 1.0, 1.0, 1.0, 1.0, 1.0, 9_999, 1.0, "excellent")
            )
        )
        val report = qualityGateService.qualityReport(
            run,
            KnowledgeRetrievalQualityProperties(
                modes = setOf(KnowledgeSearchMode.KEYWORD, KnowledgeSearchMode.SEMANTIC),
                keywordThresholds = KnowledgeRetrievalQualityThresholdProperties(minimumHitRate = 0.5),
                semanticThresholds = KnowledgeRetrievalQualityThresholdProperties(maximumAverageLatencyMillis = 10)
            )
        )

        assertThat(report.outcome).isEqualTo(KnowledgeRetrievalQualityOutcome.FAIL)
        assertThat(report.modeReports.first { it.mode == KnowledgeSearchMode.KEYWORD }.outcome).isEqualTo(KnowledgeRetrievalQualityOutcome.FAIL)
        assertThat(report.modeReports.first { it.mode == KnowledgeSearchMode.SEMANTIC }.outcome).isEqualTo(KnowledgeRetrievalQualityOutcome.PASS_WITH_WARNINGS)
        assertThat(report.failedThresholds).anyMatch { it.contains("KEYWORD:minimum_hit_rate") }
        assertThat(report.failedThresholds).anyMatch { it.contains("SEMANTIC:maximum_average_latency_millis") }
    }

    private fun importCuratedFixtures() {
        datasetService.dataset().documents.forEach { fixture ->
            knowledgeBaseService.importDocument(
                KnowledgeImportCommand(
                    title = fixture.title,
                    category = fixture.category,
                    source = fixture.source,
                    language = fixture.language,
                    content = fixture.content,
                    contentType = KnowledgeImportContentType.PLAIN_TEXT,
                    metadata = KnowledgeMetadata(tags = fixture.tags, attributes = mapOf("curated_ref" to fixture.reference))
                )
            )
        }
    }
}
