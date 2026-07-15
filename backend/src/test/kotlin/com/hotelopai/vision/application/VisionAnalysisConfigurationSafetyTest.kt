package com.hotelopai.vision.application

import com.hotelopai.assistant.application.AssistantAttachmentRepository
import com.hotelopai.assistant.application.ConversationRepository
import com.hotelopai.assistant.domain.AttachmentStorageStatus
import com.hotelopai.assistant.domain.AttachmentType
import com.hotelopai.assistant.domain.Conversation
import com.hotelopai.assistant.domain.RegisteredConversationAttachment
import com.hotelopai.vision.domain.VisionAnalysis
import com.hotelopai.vision.domain.VisionAnalysisProviderMode
import com.hotelopai.vision.domain.VisionAnalysisStatus
import com.hotelopai.vision.infrastructure.deterministic.DeterministicVisionAnalysisProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.mock.env.MockEnvironment
import java.time.Instant
import java.util.UUID

class VisionAnalysisConfigurationSafetyTest {
    @Test
    fun `deterministic provider is not enabled by production default configuration`() {
        val fixture = Fixture()
        val service = fixture.service(properties = VisionAnalysisProperties(), environment = MockEnvironment())

        val analysis = service.analyze(fixture.command(VisionAnalysisProviderMode.DETERMINISTIC_FIXTURE, "leaking-sink"))

        assertThat(analysis.status).isEqualTo(VisionAnalysisStatus.INELIGIBLE)
        assertThat(analysis.failureCode).isEqualTo("DETERMINISTIC_FIXTURES_DISABLED")
    }

    @Test
    fun `deterministic provider is available in test profile without OpenAI key`() {
        val fixture = Fixture()
        val service = fixture.service(
            properties = VisionAnalysisProperties(),
            environment = MockEnvironment().apply { setActiveProfiles("test") }
        )

        val analysis = service.analyze(fixture.command(VisionAnalysisProviderMode.DETERMINISTIC_FIXTURE, "leaking-sink"))

        assertThat(analysis.status).isEqualTo(VisionAnalysisStatus.COMPLETED)
        assertThat(analysis.providerId).isEqualTo("deterministic-local")
    }

    @Test
    fun `unknown vision provider configuration fails clearly`() {
        val properties = VisionAnalysisProperties().apply { provider = "mystery-provider" }

        assertThrows(IllegalStateException::class.java) {
            properties.normalizedProvider()
        }
    }

    @Test
    fun `disabled analysis configuration fails safely`() {
        val fixture = Fixture()
        val properties = VisionAnalysisProperties().apply { enabled = false }
        val service = fixture.service(
            properties = properties,
            environment = MockEnvironment().apply { setActiveProfiles("test") }
        )

        assertThrows(IllegalArgumentException::class.java) {
            service.analyze(fixture.command(VisionAnalysisProviderMode.DETERMINISTIC_FIXTURE, "leaking-sink"))
        }
    }

    private class Fixture {
        private val now = Instant.parse("2026-07-15T12:00:00Z")
        private val conversation = Conversation(
            id = "conversation-test",
            hotelId = "hotel-test",
            userId = "user-test"
        )
        private val attachment = RegisteredConversationAttachment(
            id = UUID.fromString("00000000-0000-7000-8000-000000000101"),
            conversationId = conversation.id,
            hotelId = conversation.hotelId,
            userId = conversation.userId,
            type = AttachmentType.IMAGE,
            originalFileName = "fixture.jpg",
            declaredMimeType = "image/jpeg",
            declaredSizeBytes = 100,
            widthPx = 100,
            heightPx = 100,
            storageStatus = AttachmentStorageStatus.REGISTERED,
            storageReference = null,
            createdAt = now,
            updatedAt = now
        )
        private val conversationRepository = object : ConversationRepository {
            override fun save(conversation: Conversation): Conversation = conversation
            override fun findById(id: String): Conversation? = conversation.takeIf { it.id == id }
        }
        private val attachmentRepository = object : AssistantAttachmentRepository {
            override fun save(attachment: RegisteredConversationAttachment): RegisteredConversationAttachment = attachment

            override fun findByIdAndConversationIdAndHotelIdAndUserId(
                id: UUID,
                conversationId: String,
                hotelId: String,
                userId: String
            ): RegisteredConversationAttachment? =
                attachment.takeIf {
                    it.id == id &&
                        it.conversationId == conversationId &&
                        it.hotelId == hotelId &&
                        it.userId == userId
                }
        }
        private val analysisRepository = object : VisionAnalysisRepository {
            private var stored: VisionAnalysis? = null

            override fun save(analysis: VisionAnalysis): VisionAnalysis {
                stored = analysis
                return analysis
            }

            override fun findById(id: UUID): VisionAnalysis? = stored?.takeIf { it.id == id }

            override fun findByIdempotencyScope(
                attachmentId: UUID,
                conversationId: String,
                hotelId: String,
                userId: String,
                idempotencyKey: String
            ): VisionAnalysis? = stored?.takeIf {
                it.attachmentId == attachmentId &&
                    it.conversationId == conversationId &&
                    it.hotelId == hotelId &&
                    it.userId == userId &&
                    it.idempotencyKey == idempotencyKey
            }
        }

        fun service(properties: VisionAnalysisProperties, environment: MockEnvironment): VisionAnalysisService =
            VisionAnalysisService(
                conversationRepository = conversationRepository,
                attachmentRepository = attachmentRepository,
                analysisRepository = analysisRepository,
                visionAnalysisPort = DeterministicVisionAnalysisProvider(),
                properties = properties,
                environment = environment
            )

        fun command(providerMode: VisionAnalysisProviderMode, fixtureKey: String?): RequestVisionAnalysisCommand =
            RequestVisionAnalysisCommand(
                attachmentId = attachment.id,
                conversationId = conversation.id,
                hotelId = conversation.hotelId,
                userId = conversation.userId,
                providerMode = providerMode,
                idempotencyKey = "idem-config-${fixtureKey ?: "none"}",
                fixtureKey = fixtureKey
            )
    }
}
