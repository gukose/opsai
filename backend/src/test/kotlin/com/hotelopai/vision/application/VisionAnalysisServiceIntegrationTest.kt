package com.hotelopai.vision.application

import com.hotelopai.assistant.application.AssistantAttachmentRegistrationService
import com.hotelopai.assistant.application.ConversationNotFoundException
import com.hotelopai.assistant.application.ConversationRepository
import com.hotelopai.assistant.application.RegisterAssistantAttachmentCommand
import com.hotelopai.assistant.domain.AttachmentType
import com.hotelopai.assistant.domain.Conversation
import com.hotelopai.shared.kernel.UuidV7Generator
import com.hotelopai.support.PostgresIntegrationTestSupport
import com.hotelopai.vision.domain.VisionAnalysisProviderMode
import com.hotelopai.vision.domain.VisionAnalysisStatus
import io.micrometer.core.instrument.MeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@SpringBootTest
@ActiveProfiles("test")
class VisionAnalysisServiceIntegrationTest : PostgresIntegrationTestSupport() {
    @Autowired
    private lateinit var conversationRepository: ConversationRepository

    @Autowired
    private lateinit var attachmentRegistrationService: AssistantAttachmentRegistrationService

    @Autowired
    private lateinit var visionAnalysisService: VisionAnalysisService

    @Autowired
    private lateinit var visionAnalysisRepository: VisionAnalysisRepository

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var meterRegistry: MeterRegistry

    @Test
    fun `deterministic fixture analysis persists completed tenant scoped result without changing conversation or tasks`() {
        val scope = createScope()
        val attachment = registerAttachment(scope, AttachmentType.IMAGE, "fixture.jpg", "image/jpeg")
        val beforeConversation = conversationRepository.findById(scope.conversationId)
        val beforeTaskCount = taskCount()

        val analysis = visionAnalysisService.analyze(
            command(scope, attachment.id, VisionAnalysisProviderMode.DETERMINISTIC_FIXTURE, "idem-complete", "leaking-sink")
        )

        assertThat(analysis.status).isEqualTo(VisionAnalysisStatus.COMPLETED)
        assertThat(analysis.attachmentId).isEqualTo(attachment.id)
        assertThat(analysis.conversationId).isEqualTo(scope.conversationId)
        assertThat(analysis.hotelId).isEqualTo(scope.hotelId)
        assertThat(analysis.userId).isEqualTo(scope.userId)
        assertThat(analysis.providerId).isEqualTo("deterministic-local")
        assertThat(analysis.providerModel).isEqualTo("deterministic-fixture")
        assertThat(analysis.providerVersion).isNotBlank()
        assertThat(analysis.confidence).isBetween(BigDecimal.ZERO, BigDecimal.ONE)
        assertThat(analysis.observations.map { it.order }).containsExactly(0, 1)
        assertThat(analysis.detectedIssueCategory).isEqualTo("MAINTENANCE")
        assertThat(analysis.detectedLocationHint).isEqualTo("sink")
        assertThat(analysis.providerMetadata).containsKey("fixtureKey")
        assertThat(analysis.providerMetadata.keys).doesNotContain("apiKey", "providerSecret", "rawPayload", "storageReference")
        assertThat(analysis.completedAt).isNotNull()
        assertPersistencePrecision(analysis.completedAt)
        assertPersistencePrecision(analysis.requestedAt)
        assertPersistencePrecision(analysis.updatedAt)
        assertThat(analysis.failedAt).isNull()
        assertThat(taskCount()).isEqualTo(beforeTaskCount)
        assertThat(conversationRepository.findById(scope.conversationId)).isEqualTo(beforeConversation)

        val persisted = visionAnalysisRepository.findById(analysis.id)
        assertThat(persisted).isEqualTo(analysis)
        assertThat(
            counter(
                "hotelopai.vision.analysis.total",
                "operation" to "analyze",
                "outcome" to "completed",
                "provider" to "deterministic",
                "confidence_bucket" to "high",
                "reason_code" to "none"
            )
        ).isGreaterThanOrEqualTo(1.0)
        assertThat(meterRegistry.find("hotelopai.vision.analysis.duration").timer()?.count() ?: 0)
            .isGreaterThanOrEqualTo(1)
    }

    @Test
    fun `unknown fixture persists controlled low confidence no result`() {
        val scope = createScope()
        val attachment = registerAttachment(scope, AttachmentType.IMAGE, "unknown.jpg", "image/jpeg")

        val analysis = visionAnalysisService.analyze(
            command(scope, attachment.id, VisionAnalysisProviderMode.DETERMINISTIC_FIXTURE, "idem-unknown", "unsupported-fixture")
        )

        assertThat(analysis.status).isEqualTo(VisionAnalysisStatus.COMPLETED)
        assertThat(analysis.confidence).isLessThan(BigDecimal("0.20"))
        assertThat(analysis.observations).isEmpty()
        assertThat(analysis.detectedIssueCategory).isNull()
        assertThat(analysis.detectedLocationHint).isNull()
    }

    @Test
    fun `real provider path marks registered metadata only attachment ineligible without provider call`() {
        val scope = createScope()
        val attachment = registerAttachment(scope, AttachmentType.IMAGE, "metadata-only.jpg", "image/jpeg")

        val analysis = visionAnalysisService.analyze(
            command(scope, attachment.id, VisionAnalysisProviderMode.REAL_PROVIDER, "idem-real-provider", fixtureKey = null)
        )

        assertThat(analysis.status).isEqualTo(VisionAnalysisStatus.INELIGIBLE)
        assertThat(analysis.failureCode).isEqualTo("PROVIDER_MEDIA_UNAVAILABLE")
        assertThat(analysis.failureMessage).contains("registered metadata only")
        assertThat(analysis.observations).isEmpty()
        assertThat(analysis.confidence).isNull()
        assertThat(
            counter(
                "hotelopai.vision.analysis.total",
                "operation" to "analyze",
                "outcome" to "ineligible",
                "provider" to "other",
                "confidence_bucket" to "none",
                "reason_code" to "provider_media_unavailable"
            )
        ).isGreaterThanOrEqualTo(1.0)
    }

    @Test
    fun `non image missing cross conversation cross user and cross hotel attachments are rejected`() {
        val scope = createScope()
        val pdf = registerAttachment(scope, AttachmentType.PDF, "report.pdf", "application/pdf")
        val otherConversation = createScope(hotelId = scope.hotelId, userId = scope.userId)
        val otherUserScope = Scope(scope.conversationId, scope.hotelId, "other-user")
        val otherHotelScope = Scope(scope.conversationId, "other-hotel", scope.userId)

        assertThrows(IllegalArgumentException::class.java) {
            visionAnalysisService.analyze(
                command(scope, pdf.id, VisionAnalysisProviderMode.DETERMINISTIC_FIXTURE, "idem-pdf", "leaking-sink")
            )
        }
        assertThrows(VisionAnalysisNotFoundException::class.java) {
            visionAnalysisService.analyze(
                command(scope, UUID.randomUUID(), VisionAnalysisProviderMode.DETERMINISTIC_FIXTURE, "idem-missing", "leaking-sink")
            )
        }
        assertThrows(VisionAnalysisNotFoundException::class.java) {
            visionAnalysisService.analyze(
                command(otherConversation, pdf.id, VisionAnalysisProviderMode.DETERMINISTIC_FIXTURE, "idem-cross-conv", "leaking-sink")
            )
        }
        assertThrows(ConversationNotFoundException::class.java) {
            visionAnalysisService.analyze(
                command(otherUserScope, pdf.id, VisionAnalysisProviderMode.DETERMINISTIC_FIXTURE, "idem-cross-user", "leaking-sink")
            )
        }
        assertThrows(ConversationNotFoundException::class.java) {
            visionAnalysisService.analyze(
                command(otherHotelScope, pdf.id, VisionAnalysisProviderMode.DETERMINISTIC_FIXTURE, "idem-cross-hotel", "leaking-sink")
            )
        }
    }

    @Test
    fun `local metadata only and client controlled storage references cannot be analyzed`() {
        val scope = createScope()

        assertThrows(VisionAnalysisNotFoundException::class.java) {
            visionAnalysisService.analyze(
                command(
                    scope = scope,
                    attachmentId = UUID.randomUUID(),
                    providerMode = VisionAnalysisProviderMode.REAL_PROVIDER,
                    idempotencyKey = "idem-local-only",
                    fixtureKey = null
                )
            )
        }

        val columns = jdbcTemplate.queryForList(
            """
            select column_name
            from information_schema.columns
            where table_name = 'vision_analysis'
            """.trimIndent(),
            String::class.java
        )
        assertThat(columns).doesNotContain("local_reference", "local_uri", "file_uri", "media_url", "image_url", "base64", "raw_binary")
    }

    @Test
    fun `duplicate completed idempotency request returns existing record without duplicate completion`() {
        val scope = createScope()
        val attachment = registerAttachment(scope, AttachmentType.IMAGE, "fixture.jpg", "image/jpeg")
        val request = command(scope, attachment.id, VisionAnalysisProviderMode.DETERMINISTIC_FIXTURE, "idem-duplicate", "dirty-room")

        val first = visionAnalysisService.analyze(request)
        val second = visionAnalysisService.analyze(request)

        assertThat(second).isEqualTo(first)
        assertPersistencePrecision(first.completedAt)
        assertPersistencePrecision(first.requestedAt)
        assertPersistencePrecision(first.updatedAt)
        assertThat(
            jdbcTemplate.queryForObject(
                "select count(*) from vision_analysis where idempotency_key = 'idem-duplicate'",
                Int::class.java
            )
        ).isEqualTo(1)
    }

    @Test
    fun `provider failure persists failed and explicit retry increments attempt count`() {
        val scope = createScope()
        val attachment = registerAttachment(scope, AttachmentType.IMAGE, "fixture.jpg", "image/jpeg")
        val failingRequest = command(
            scope,
            attachment.id,
            VisionAnalysisProviderMode.DETERMINISTIC_FIXTURE,
            "idem-retry",
            fixtureKey = null
        )

        val failed = visionAnalysisService.analyze(failingRequest)
        assertThat(failed.status).isEqualTo(VisionAnalysisStatus.FAILED)
        assertThat(failed.failureCode).isEqualTo("PROVIDER_FAILURE")
        assertThat(failed.failedAt).isNotNull()
        assertPersistencePrecision(failed.failedAt)
        assertPersistencePrecision(failed.requestedAt)
        assertPersistencePrecision(failed.updatedAt)

        val duplicateFailed = visionAnalysisService.analyze(failingRequest)
        assertThat(duplicateFailed).isEqualTo(failed)
        assertThat(
            counter(
                "hotelopai.vision.analysis.total",
                "operation" to "analyze",
                "outcome" to "idempotent_reuse",
                "provider" to "deterministic",
                "confidence_bucket" to "none",
                "reason_code" to "none"
            )
        ).isGreaterThanOrEqualTo(1.0)

        val retried = visionAnalysisService.retryFailed(
            command(scope, attachment.id, VisionAnalysisProviderMode.DETERMINISTIC_FIXTURE, "idem-retry", "broken-window")
        )
        assertThat(retried.id).isEqualTo(failed.id)
        assertThat(retried.status).isEqualTo(VisionAnalysisStatus.COMPLETED)
        assertThat(retried.attemptCount).isEqualTo(2)
        assertThat(retried.detectedLocationHint).isEqualTo("window")
        assertPersistencePrecision(retried.completedAt)
        assertPersistencePrecision(retried.requestedAt)
        assertPersistencePrecision(retried.updatedAt)
        assertThat(
            counter(
                "hotelopai.vision.analysis.total",
                "operation" to "retry",
                "outcome" to "completed",
                "provider" to "deterministic",
                "confidence_bucket" to "high",
                "reason_code" to "none"
            )
        ).isGreaterThanOrEqualTo(1.0)
    }

    @Test
    fun `vision analysis persistence keeps ordered observations and safe metadata only`() {
        val scope = createScope()
        val attachment = registerAttachment(scope, AttachmentType.IMAGE, "fixture.jpg", "image/jpeg")
        val analysis = visionAnalysisService.analyze(
            command(scope, attachment.id, VisionAnalysisProviderMode.DETERMINISTIC_FIXTURE, "idem-persist", "leaking-sink")
        )

        val row = jdbcTemplate.queryForMap("select * from vision_analysis where id = '${analysis.id}'::uuid")
        assertThat(row["attachment_id"]).isEqualTo(attachment.id)
        assertThat(row["conversation_id"]).isEqualTo(scope.conversationId)
        assertThat(row["hotel_id"]).isEqualTo(scope.hotelId)
        assertThat(row["user_id"]).isEqualTo(scope.userId)
        assertThat(row["provider_id"]).isEqualTo("deterministic-local")
        assertThat(row["provider_model"]).isEqualTo("deterministic-fixture")
        assertThat(row["provider_version"]).isNotNull()
        assertThat(row["confidence"]).isNotNull()
        assertThat(row["observations_json"].toString()).contains("\"order\": 0", "\"order\": 1")
        assertThat(row["detected_issue_category"]).isEqualTo("MAINTENANCE")
        assertThat(row["detected_location_hint"]).isEqualTo("sink")
        assertThat(row["provider_metadata_json"].toString()).contains("fixtureKey")
        assertThat(row["provider_metadata_json"].toString()).doesNotContain("secret", "apiKey", "base64", "file://")
        assertThat(row["idempotency_key"]).isEqualTo("idem-persist")
        assertThat(row["attempt_count"]).isEqualTo(1)
        assertThat(row["requested_at"]).isNotNull()
        assertThat(row["completed_at"]).isNotNull()
        assertThat(row["failed_at"]).isNull()
    }

    private fun createScope(
        hotelId: String = "hotel-${UuidV7Generator.generate()}",
        userId: String = "user-${UuidV7Generator.generate()}"
    ): Scope {
        val conversation = conversationRepository.save(
            Conversation(
                id = "conversation-${UuidV7Generator.generate()}",
                hotelId = hotelId,
                userId = userId
            )
        )
        return Scope(conversation.id, hotelId, userId)
    }

    private fun registerAttachment(
        scope: Scope,
        type: AttachmentType,
        fileName: String,
        mimeType: String
    ) = attachmentRegistrationService.register(
        conversationId = scope.conversationId,
        hotelId = scope.hotelId,
        userId = scope.userId,
        command = RegisterAssistantAttachmentCommand(
            type = type,
            originalFileName = fileName,
            mimeType = mimeType,
            sizeBytes = 100,
            widthPx = if (type == AttachmentType.IMAGE) 100 else null,
            heightPx = if (type == AttachmentType.IMAGE) 100 else null
        )
    )

    private fun command(
        scope: Scope,
        attachmentId: UUID,
        providerMode: VisionAnalysisProviderMode,
        idempotencyKey: String,
        fixtureKey: String?
    ): RequestVisionAnalysisCommand =
        RequestVisionAnalysisCommand(
            attachmentId = attachmentId,
            conversationId = scope.conversationId,
            hotelId = scope.hotelId,
            userId = scope.userId,
            providerMode = providerMode,
            idempotencyKey = idempotencyKey,
            fixtureKey = fixtureKey
        )

    private fun taskCount(): Int =
        jdbcTemplate.queryForObject("select count(*) from task", Int::class.java) ?: 0

    private fun counter(name: String, vararg tags: Pair<String, String>): Double =
        meterRegistry.find(name)
            .tags(*tags.flatMap { listOf(it.first, it.second) }.toTypedArray())
            .counter()
            ?.count()
            ?: 0.0

    private fun assertPersistencePrecision(instant: Instant?) {
        assertThat(instant).isNotNull()
        assertThat(requireNotNull(instant).nano % 1_000).isZero()
    }

    private data class Scope(
        val conversationId: String,
        val hotelId: String,
        val userId: String
    )
}
