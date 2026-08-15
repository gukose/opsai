package com.hotelopai.knowledge.infrastructure.persistence

import com.hotelopai.knowledge.application.KnowledgeBaseService
import com.hotelopai.knowledge.application.KnowledgeAnswerRequest
import com.hotelopai.knowledge.application.KnowledgeAnswerService
import com.hotelopai.knowledge.application.KnowledgeAnswerFeedbackType
import com.hotelopai.knowledge.application.KnowledgeAnswerProviderDiagnosticOutcome
import com.hotelopai.knowledge.application.KnowledgeAnswerProviderReadinessStatus
import com.hotelopai.knowledge.application.KnowledgeAnswerRepository
import com.hotelopai.knowledge.application.KnowledgeAnswerRequestStatus
import com.hotelopai.knowledge.application.KnowledgeAnswerSmokeFixtureMode
import com.hotelopai.knowledge.application.KnowledgeAnswerStatus
import com.hotelopai.knowledge.application.KnowledgeEmbeddingService
import com.hotelopai.knowledge.application.KnowledgeEmbeddingStatus
import com.hotelopai.knowledge.application.KnowledgeDocumentFilter
import com.hotelopai.knowledge.application.KnowledgeImportCommand
import com.hotelopai.knowledge.application.KnowledgeImportContentType
import com.hotelopai.knowledge.application.KnowledgeRetrievalEvaluationCase
import com.hotelopai.knowledge.application.KnowledgeRetrievalEvaluationRequest
import com.hotelopai.knowledge.application.KnowledgeRetrievalEvaluationService
import com.hotelopai.knowledge.application.KnowledgeSearchMode
import com.hotelopai.knowledge.application.KnowledgeSearchQuery
import com.hotelopai.knowledge.domain.KnowledgeCategory
import com.hotelopai.knowledge.domain.KnowledgeMetadata
import com.hotelopai.knowledge.domain.KnowledgeSource
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
import java.time.Clock
import java.util.UUID

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(
    properties = [
        "ops.ai.knowledge.semantic-search.enabled=true",
        "ops.ai.knowledge.semantic-search.allowed-profiles[0]=test",
        "ops.ai.knowledge.semantic-search.vector-dimension=16",
        "ops.ai.knowledge.semantic-search.batch-size=10",
        "ops.ai.knowledge.semantic-search.keyword-fallback-enabled=true",
        "ops.ai.knowledge.answers.enabled=true",
        "ops.ai.knowledge.answers.providers.openai.enabled=true",
        "ops.ai.knowledge.answers.providers.openai.endpoint=http://localhost/openai-fixture",
        "ops.ai.knowledge.answers.providers.openai.credential-reference=OPENAI_API_KEY",
        "ops.ai.knowledge.answers.providers.openai.allowed-profiles[0]=test",
        "ops.ai.knowledge.answers.providers.openai.smoke-test-enabled=true",
        "ops.ai.knowledge.answers.providers.openai.fixture-mode-enabled=true"
    ]
)
class KnowledgePersistenceIntegrationTest : PostgresIntegrationTestSupport() {
    @Autowired
    private lateinit var service: KnowledgeBaseService

    @Autowired
    private lateinit var embeddingService: KnowledgeEmbeddingService

    @Autowired
    private lateinit var evaluationService: KnowledgeRetrievalEvaluationService

    @Autowired
    private lateinit var answerService: KnowledgeAnswerService

    @Autowired
    private lateinit var answerRepository: KnowledgeAnswerRepository

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var clock: Clock

    @BeforeEach
    fun cleanKnowledgeTables() {
        jdbcTemplate.execute("delete from knowledge_answer_request_lifecycle")
        jdbcTemplate.execute("delete from knowledge_answer_inflight_scope")
        jdbcTemplate.execute("delete from knowledge_answer_provider_diagnostic")
        jdbcTemplate.execute("delete from knowledge_answer_feedback")
        jdbcTemplate.execute("delete from knowledge_answer_history")
        jdbcTemplate.execute("delete from knowledge_retrieval_evaluation_run")
        jdbcTemplate.execute("delete from knowledge_document")
    }

    @Test
    fun `imports markdown persists metadata chunks and searchable content`() {
        val document = service.importDocument(
            KnowledgeImportCommand(
                title = "Valve maintenance SOP",
                category = KnowledgeCategory.MAINTENANCE,
                source = KnowledgeSource.IMPORTED_MARKDOWN,
                language = "en",
                content = """
                    # Valve Checks

                    Inspect service valves before reopening the panel.

                    Escalate pressure drift to maintenance leadership.
                """.trimIndent(),
                contentType = KnowledgeImportContentType.MARKDOWN,
                metadata = KnowledgeMetadata(tags = setOf("Maintenance", "Valve"), attributes = mapOf("owner" to "ops"))
            )
        )

        val loaded = service.detail(document.id)
        val search = service.search(KnowledgeSearchQuery(query = "valve maintenance"))

        assertThat(loaded.metadata.tags).containsExactly("maintenance", "valve")
        assertThat(loaded.chunks).isNotEmpty
        assertThat(loaded.chunks.first().heading).isEqualTo("Valve Checks")
        assertThat(search.content.first().documentId).isEqualTo(document.id)
        assertThat(search.content.first().snippet).contains("Inspect service valves")
        assertThat(search.content.first().tags).contains("valve")
    }

    @Test
    fun `embedding lifecycle is idempotent and semantic search ranks matching chunks`() {
        val valve = service.importDocument(
            KnowledgeImportCommand(
                title = "Valve maintenance",
                category = KnowledgeCategory.MAINTENANCE,
                source = KnowledgeSource.IMPORTED_TEXT,
                content = "Inspect valve pressure and isolate the service panel before repair.",
                contentType = KnowledgeImportContentType.PLAIN_TEXT,
                metadata = KnowledgeMetadata(tags = setOf("valve"))
            )
        )
        service.importDocument(
            KnowledgeImportCommand(
                title = "Front desk greeting",
                category = KnowledgeCategory.FRONT_DESK,
                source = KnowledgeSource.IMPORTED_TEXT,
                content = "Welcome arriving guests and confirm the daily handoff summary.",
                contentType = KnowledgeImportContentType.PLAIN_TEXT,
                metadata = KnowledgeMetadata(tags = setOf("handoff"))
            )
        )

        val first = embeddingService.generateBatch(10)
        val second = embeddingService.generateBatch(10)
        val semantic = service.search(KnowledgeSearchQuery(query = "valve pressure repair", mode = KnowledgeSearchMode.SEMANTIC))
        val hybrid = service.search(KnowledgeSearchQuery(query = "valve pressure repair", mode = KnowledgeSearchMode.HYBRID))
        val status = embeddingService.status()
        val diagnostics = embeddingService.diagnostics(providerId = null, page = 0, size = 10)
        val readiness = embeddingService.retrievalReadiness()

        assertThat(first.embedded).isGreaterThanOrEqualTo(2)
        assertThat(second.embedded).isZero()
        assertThat(status.readyCount).isGreaterThanOrEqualTo(2)
        assertThat(status.failedCount).isZero()
        assertThat(diagnostics).isNotEmpty
        assertThat(readiness.state.name).isIn("READY", "PARTIALLY_INDEXED")
        assertThat(semantic.content.first().documentId).isEqualTo(valve.id)
        assertThat(semantic.content.first().score?.semanticScore).isGreaterThan(0.0)
        assertThat(hybrid.content.first().score?.combinedScore).isGreaterThan(0.0)
    }

    @Test
    fun `scheduler state diagnostics cleanup and provider summaries are safe`() {
        service.importDocument(
            KnowledgeImportCommand(
                title = "Pump service",
                category = KnowledgeCategory.MAINTENANCE,
                source = KnowledgeSource.IMPORTED_TEXT,
                content = "Inspect pump casing and verify safe isolation.",
                contentType = KnowledgeImportContentType.PLAIN_TEXT
            )
        )

        val providers = embeddingService.providers()
        val paused = embeddingService.pauseSchedule()
        val resumed = embeddingService.resumeSchedule()
        val runNow = embeddingService.runScheduledNow()
        val status = embeddingService.scheduleStatus()
        val cleanup = embeddingService.cleanupDiagnostics()

        assertThat(providers.map { it.providerId }).contains("deterministic-local", "openai")
        assertThat(providers.first { it.providerId == "openai" }.readiness.name).isEqualTo("DISABLED")
        assertThat(paused.paused).isTrue()
        assertThat(resumed.paused).isFalse()
        assertThat(runNow.failureCategory).isNull()
        assertThat(status.configuredEnabled).isFalse()
        assertThat(cleanup).isGreaterThanOrEqualTo(0)
    }

    @Test
    fun `retrieval evaluation persists aggregate metrics and updates readiness report`() {
        val document = service.importDocument(
            KnowledgeImportCommand(
                title = "Boiler isolation",
                category = KnowledgeCategory.MAINTENANCE,
                source = KnowledgeSource.IMPORTED_TEXT,
                content = "Boiler service requires isolation, lockout, and pressure verification before maintenance.",
                contentType = KnowledgeImportContentType.PLAIN_TEXT
            )
        )
        embeddingService.generateBatch(10)

        val run = evaluationService.runEvaluation(
            KnowledgeRetrievalEvaluationRequest(
                name = "maintenance retrieval smoke",
                hotelId = null,
                cases = listOf(
                    KnowledgeRetrievalEvaluationCase(
                        query = "boiler isolation pressure",
                        expectedDocumentIds = setOf(document.id)
                    )
                ),
                modes = setOf(KnowledgeSearchMode.KEYWORD, KnowledgeSearchMode.SEMANTIC, KnowledgeSearchMode.HYBRID),
                k = 3
            )
        )
        val history = evaluationService.history(page = 0, size = 10)
        val detail = evaluationService.detail(run.id)
        val readiness = evaluationService.readinessReport()

        assertThat(run.status.name).isEqualTo("SUCCEEDED")
        assertThat(run.metrics.map { it.mode }).containsExactly(KnowledgeSearchMode.HYBRID, KnowledgeSearchMode.KEYWORD, KnowledgeSearchMode.SEMANTIC)
        assertThat(run.metrics).allMatch { it.hitRate >= 0.0 && it.hitRate <= 1.0 }
        assertThat(history.map { it.id }).contains(run.id)
        assertThat(detail?.metrics).hasSize(3)
        assertThat(readiness.latestEvaluationRunId).isEqualTo(run.id)
        assertThat(readiness.evaluationStatus).isEqualTo("SUCCEEDED")
    }

    @Test
    fun `rechunk invalidates obsolete embeddings and deleted documents disappear from semantic results`() {
        val document = service.importDocument(
            KnowledgeImportCommand(
                title = "Safety checks",
                category = KnowledgeCategory.SAFETY,
                source = KnowledgeSource.IMPORTED_TEXT,
                content = "Check isolation before servicing equipment.",
                contentType = KnowledgeImportContentType.PLAIN_TEXT
            )
        )
        embeddingService.generateBatch(10)

        val rechunked = service.rechunk(document.id)
        val regenerated = embeddingService.generateBatch(10)
        val failed = embeddingService.failed(10)
        val searchBeforeDelete = service.search(KnowledgeSearchQuery(query = "isolation servicing", mode = KnowledgeSearchMode.HYBRID))
        service.delete(document.id)
        val searchAfterDelete = service.search(KnowledgeSearchQuery(query = "isolation servicing", mode = KnowledgeSearchMode.HYBRID))

        assertThat(rechunked.chunks).isNotEmpty
        assertThat(regenerated.embedded).isGreaterThanOrEqualTo(1)
        assertThat(failed).allMatch { it.status != KnowledgeEmbeddingStatus.READY }
        assertThat(searchBeforeDelete.content).isNotEmpty
        assertThat(searchAfterDelete.content).isEmpty()
    }

    @Test
    fun `answer generation uses assembled knowledge citations and suppresses duplicates`() {
        val document = service.importDocument(
            KnowledgeImportCommand(
                title = "Housekeeping release",
                category = KnowledgeCategory.HOUSEKEEPING,
                source = KnowledgeSource.SOP,
                content = "Housekeeping release requires inspected amenities, clean surfaces, linen restock, and supervisor approval before assignment.",
                contentType = KnowledgeImportContentType.PLAIN_TEXT
            )
        )
        embeddingService.generateBatch(10)

        val request = KnowledgeAnswerRequest(
            query = "What is needed before housekeeping releases a room?",
            hotelId = null,
            retrievalMode = KnowledgeSearchMode.HYBRID,
            categories = setOf(KnowledgeCategory.HOUSEKEEPING)
        )
        val answer = answerService.answer(request)
        val duplicate = answerService.answer(request)

        assertThat(answer.status).isEqualTo(KnowledgeAnswerStatus.ANSWERED)
        assertThat(answer.answerText).contains("Housekeeping release")
        assertThat(answer.citations).hasSize(1)
        assertThat(answer.citations.first().documentReference).isEqualTo(document.id.value)
        assertThat(answer.citations.first().excerpt).contains("Housekeeping release requires")
        assertThat(duplicate.id).isEqualTo(answer.id)
        assertThat(answerService.history(null, 0, 10).map { it.id }).contains(answer.id)
    }

    @Test
    fun `answer feedback persists without exposing actor in operational response model`() {
        val actor = UUID.randomUUID()
        service.importDocument(
            KnowledgeImportCommand(
                title = "Checkout guide",
                category = KnowledgeCategory.FRONT_DESK,
                source = KnowledgeSource.SOP,
                content = "Checkout follow-up requires bill review and key return confirmation.",
                contentType = KnowledgeImportContentType.PLAIN_TEXT
            )
        )
        embeddingService.generateBatch(10)
        val answer = answerService.answer(
            KnowledgeAnswerRequest(
                query = "What does checkout follow-up require?",
                hotelId = null,
                actorUserId = actor,
                retrievalMode = KnowledgeSearchMode.HYBRID
            )
        )

        val feedback = answerService.submitFeedback(answer.id.value, null, actor, KnowledgeAnswerFeedbackType.HELPFUL)
        val loaded = answerService.feedback(answer.id.value, null)

        assertThat(feedback.feedbackType).isEqualTo(KnowledgeAnswerFeedbackType.HELPFUL)
        assertThat(loaded).hasSize(1)
        assertThat(loaded.first().actorUserId).isEqualTo(actor)
    }

    @Test
    fun `answer request lifecycle enforces durable in flight capacity and releases terminal requests`() {
        val hotel = UUID.randomUUID()
        val actor = UUID.randomUUID()
        val now = Instant.parse("2026-08-01T10:00:00Z")

        val first = answerRepository.acquireAnswerRequestLifecycle(
            hotelId = hotel,
            actorUserId = actor,
            providerId = "internal-demo",
            modelId = "internal-demo",
            retrievalMode = KnowledgeSearchMode.HYBRID,
            requestFingerprint = "fingerprint-one",
            inFlightLimit = 1,
            abandonedBefore = now.minusSeconds(600),
            now = now
        )
        val rejected = answerRepository.acquireAnswerRequestLifecycle(
            hotelId = hotel,
            actorUserId = actor,
            providerId = "internal-demo",
            modelId = "internal-demo",
            retrievalMode = KnowledgeSearchMode.HYBRID,
            requestFingerprint = "fingerprint-two",
            inFlightLimit = 1,
            abandonedBefore = now.minusSeconds(600),
            now = now.plusSeconds(1)
        )

        assertThat(first).isNotNull
        assertThat(rejected).isNull()
        assertThat(answerRepository.countActiveAnswerRequests(hotel, actor)).isEqualTo(1)

        answerRepository.transitionAnswerRequest(
            requestId = first!!.requestId,
            status = KnowledgeAnswerRequestStatus.COMPLETED,
            now = now.plusSeconds(2),
            citationCountBand = "one",
            latencyBand = "under_1_second"
        )

        val next = answerRepository.acquireAnswerRequestLifecycle(
            hotelId = hotel,
            actorUserId = actor,
            providerId = "internal-demo",
            modelId = "internal-demo",
            retrievalMode = KnowledgeSearchMode.HYBRID,
            requestFingerprint = "fingerprint-three",
            inFlightLimit = 1,
            abandonedBefore = now.minusSeconds(600),
            now = now.plusSeconds(3)
        )

        assertThat(next).isNotNull
        assertThat(answerRepository.countActiveAnswerRequests(hotel, actor)).isEqualTo(1)
    }

    @Test
    fun `dashboard cancellation and abandoned recovery expose only safe aggregate metadata`() {
        val hotel = UUID.randomUUID()
        val actor = UUID.randomUUID()
        val now = clock.instant()
        service.importDocument(
            KnowledgeImportCommand(
                hotelId = hotel,
                title = "Front desk safety",
                category = KnowledgeCategory.FRONT_DESK,
                source = KnowledgeSource.SOP,
                content = "Front desk safety checks require key inventory, lobby inspection, and escalation readiness.",
                contentType = KnowledgeImportContentType.PLAIN_TEXT
            )
        )
        embeddingService.generateBatch(10)
        val answer = answerService.answer(
            KnowledgeAnswerRequest(
                query = "What should front desk safety checks include?",
                hotelId = hotel,
                actorUserId = actor,
                retrievalMode = KnowledgeSearchMode.HYBRID
            )
        )
        answerService.submitFeedback(answer.id.value, hotel, actor, KnowledgeAnswerFeedbackType.HELPFUL)
        val cancellable = answerRepository.acquireAnswerRequestLifecycle(
            hotelId = hotel,
            actorUserId = actor,
            providerId = "internal-demo",
            modelId = "internal-demo",
            retrievalMode = KnowledgeSearchMode.HYBRID,
            requestFingerprint = "cancel-fingerprint",
            inFlightLimit = 2,
            abandonedBefore = now.minusSeconds(600),
            now = now
        )
        val abandoned = answerRepository.acquireAnswerRequestLifecycle(
            hotelId = hotel,
            actorUserId = actor,
            providerId = "internal-demo",
            modelId = "internal-demo",
            retrievalMode = KnowledgeSearchMode.KEYWORD,
            requestFingerprint = "abandoned-fingerprint",
            inFlightLimit = 3,
            abandonedBefore = now.minusSeconds(86_400),
            now = now.minusSeconds(7_200)
        )

        val cancelled = answerService.cancelRequest(cancellable!!.requestId, hotel, actor)
        val recovered = answerService.recoverAbandonedRequests()
        val dashboard = answerService.dashboard(hotel, actor)

        assertThat(abandoned).isNotNull
        assertThat(cancelled.status).isEqualTo(KnowledgeAnswerRequestStatus.REJECTED)
        assertThat(recovered).isGreaterThanOrEqualTo(1)
        assertThat(dashboard.statusCounts.answered).isGreaterThanOrEqualTo(1)
        assertThat(dashboard.statusCounts.failed).isGreaterThanOrEqualTo(1)
        assertThat(dashboard.quotaUsage.hourlyUsed).isGreaterThanOrEqualTo(1)
        assertThat(dashboard.quotaUsage.inFlightUsed).isZero()
        assertThat(dashboard.abandonedRequestCount).isGreaterThanOrEqualTo(1)
        assertThat(dashboard.feedbackAnalytics.counts[KnowledgeAnswerFeedbackType.HELPFUL]).isEqualTo(1)
        assertThat(dashboard.recentFailureCategories.keys.map { it.name }).contains("CANCELLED")
    }

    @Test
    fun `OpenAI answer smoke test uses fixture transport and persists sanitized diagnostics only`() {
        val readiness = answerService.providerReadiness("openai")

        val result = answerService.smokeTest("openai", KnowledgeAnswerSmokeFixtureMode.SUCCESS, UUID.randomUUID())
        val diagnostics = answerService.diagnostics("openai", 0, 10)
        val historyCount = jdbcTemplate.queryForObject("select count(*) from knowledge_answer_history", Long::class.java)

        assertThat(readiness.readiness).isEqualTo(KnowledgeAnswerProviderReadinessStatus.READY_FOR_LOCAL_SMOKE)
        assertThat(result.diagnostic.outcome).isEqualTo(KnowledgeAnswerProviderDiagnosticOutcome.SUCCEEDED)
        assertThat(result.answerCount).isEqualTo(1)
        assertThat(diagnostics.content).hasSize(1)
        assertThat(diagnostics.content.first().modelId).isNotBlank()
        assertThat(diagnostics.content.joinToString()).doesNotContain("Bearer", "Synthetic context:")
        assertThat(historyCount).isZero()
    }

    @Test
    fun `answer privacy rejection persists safe failure without prompt or query text`() {
        val answer = answerService.answer(
            KnowledgeAnswerRequest(
                query = "Use bearer token secret-token to answer this",
                hotelId = null,
                retrievalMode = KnowledgeSearchMode.KEYWORD
            )
        )

        assertThat(answer.status).isEqualTo(KnowledgeAnswerStatus.FAILED_VALIDATION)
        assertThat(answer.failureCategory?.name).isEqualTo("PRIVACY_REJECTED")
        assertThat(answer.answerText).isNull()
        assertThat(answer.citations).isEmpty()
    }

    @Test
    fun `list filter delete and rechunk remain deterministic`() {
        val document = service.importDocument(
            KnowledgeImportCommand(
                title = "Front desk handoff",
                category = KnowledgeCategory.FRONT_DESK,
                source = KnowledgeSource.IMPORTED_TEXT,
                content = "Morning handoff includes arrivals, departures, and unresolved operational notes.",
                contentType = KnowledgeImportContentType.PLAIN_TEXT,
                metadata = KnowledgeMetadata(tags = setOf("handoff"))
            )
        )

        val filtered = service.documents(KnowledgeDocumentFilter(category = KnowledgeCategory.FRONT_DESK, tag = "handoff"))
        val rechunked = service.rechunk(document.id)
        val deleted = service.delete(document.id)

        assertThat(filtered.totalElements).isEqualTo(1)
        assertThat(rechunked.chunks.map { it.order }).containsExactlyElementsOf(rechunked.chunks.indices.toList())
        assertThat(deleted).isTrue()
        assertThat(service.documents(KnowledgeDocumentFilter()).totalElements).isZero()
    }
}
