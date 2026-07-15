package com.hotelopai.vision.application

import com.hotelopai.assistant.application.AssistantAttachmentRepository
import com.hotelopai.assistant.application.ConversationNotFoundException
import com.hotelopai.assistant.application.ConversationRepository
import com.hotelopai.assistant.domain.AttachmentStorageStatus
import com.hotelopai.assistant.domain.AttachmentType
import com.hotelopai.shared.kernel.UuidV7Generator
import com.hotelopai.vision.domain.VisionAnalysis
import com.hotelopai.vision.domain.VisionAnalysisProviderMode
import com.hotelopai.vision.domain.VisionAnalysisStatus
import com.hotelopai.vision.domain.VisionProviderIds
import org.springframework.core.env.Environment
import org.springframework.core.env.Profiles
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class VisionAnalysisService(
    private val conversationRepository: ConversationRepository,
    private val attachmentRepository: AssistantAttachmentRepository,
    private val analysisRepository: VisionAnalysisRepository,
    private val visionAnalysisPort: VisionAnalysisPort,
    private val properties: VisionAnalysisProperties,
    private val environment: Environment
) {
    @Transactional
    fun analyze(command: RequestVisionAnalysisCommand): VisionAnalysis {
        require(properties.enabled) { "Vision analysis is disabled" }
        val existing = analysisRepository.findByIdempotencyScope(
            attachmentId = command.attachmentId,
            conversationId = command.conversationId,
            hotelId = command.hotelId,
            userId = command.userId,
            idempotencyKey = command.idempotencyKey
        )
        if (existing != null) {
            return existing
        }

        val pending = createPending(command)
        analysisRepository.save(pending)
        return process(pending, command)
    }

    @Transactional
    fun retryFailed(command: RequestVisionAnalysisCommand): VisionAnalysis {
        require(properties.enabled) { "Vision analysis is disabled" }
        val existing = analysisRepository.findByIdempotencyScope(
            attachmentId = command.attachmentId,
            conversationId = command.conversationId,
            hotelId = command.hotelId,
            userId = command.userId,
            idempotencyKey = command.idempotencyKey
        ) ?: throw VisionAnalysisNotFoundException("Vision analysis not found for retry")

        val retry = existing.retry()
        analysisRepository.save(retry)
        return process(retry, command)
    }

    private fun createPending(command: RequestVisionAnalysisCommand): VisionAnalysis {
        ensureConversationOwned(command.conversationId, command.hotelId, command.userId)
        val attachment = attachmentRepository.findByIdAndConversationIdAndHotelIdAndUserId(
            id = command.attachmentId,
            conversationId = command.conversationId,
            hotelId = command.hotelId,
            userId = command.userId
        ) ?: throw VisionAnalysisNotFoundException("Attachment not found")

        require(attachment.type == AttachmentType.IMAGE) { "Only IMAGE attachments can be analyzed" }

        val now = Instant.now()
        return VisionAnalysis(
            id = UuidV7Generator.generate(now),
            attachmentId = attachment.id,
            conversationId = attachment.conversationId,
            hotelId = attachment.hotelId,
            userId = attachment.userId,
            status = VisionAnalysisStatus.PENDING,
            providerId = providerIdFor(command.providerMode),
            idempotencyKey = command.idempotencyKey,
            requestedAt = now,
            createdAt = now,
            updatedAt = now
        )
    }

    private fun process(analysis: VisionAnalysis, command: RequestVisionAnalysisCommand): VisionAnalysis {
        val attachment = attachmentRepository.findByIdAndConversationIdAndHotelIdAndUserId(
            id = command.attachmentId,
            conversationId = command.conversationId,
            hotelId = command.hotelId,
            userId = command.userId
        ) ?: throw VisionAnalysisNotFoundException("Attachment not found")

        val request = VisionAnalysisRequest(
            analysisId = analysis.id,
            attachmentId = attachment.id,
            conversationId = attachment.conversationId,
            hotelId = attachment.hotelId,
            userId = attachment.userId,
            providerMode = command.providerMode,
            idempotencyKey = command.idempotencyKey,
            requestedAt = analysis.requestedAt,
            storageReference = attachment.storageReference,
            fixtureKey = command.fixtureKey
        )

        val finalAnalysis = when (command.providerMode) {
            VisionAnalysisProviderMode.DETERMINISTIC_FIXTURE -> analyzeDeterministic(analysis, request)
            VisionAnalysisProviderMode.REAL_PROVIDER -> analyzeRealProvider(analysis, request, attachment.storageStatus)
        }

        return analysisRepository.save(finalAnalysis)
    }

    private fun analyzeDeterministic(analysis: VisionAnalysis, request: VisionAnalysisRequest): VisionAnalysis {
        if (!deterministicFixturesAllowed()) {
            return analysis.markIneligible(
                code = "DETERMINISTIC_FIXTURES_DISABLED",
                message = "Deterministic vision fixtures are not enabled"
            )
        }

        return runCatching {
            visionAnalysisPort.analyze(request)
        }.fold(
            onSuccess = { result -> analysis.complete(result) },
            onFailure = { failure ->
                analysis.fail(
                    code = "PROVIDER_FAILURE",
                    message = failure.message?.take(500) ?: "Vision provider failed"
                )
            }
        )
    }

    private fun analyzeRealProvider(
        analysis: VisionAnalysis,
        request: VisionAnalysisRequest,
        storageStatus: AttachmentStorageStatus
    ): VisionAnalysis {
        if (storageStatus == AttachmentStorageStatus.REGISTERED || request.storageReference == null) {
            return analysis.markIneligible(
                code = "PROVIDER_MEDIA_UNAVAILABLE",
                message = "Attachment is registered metadata only and has no provider-accessible media"
            )
        }

        return runCatching {
            visionAnalysisPort.analyze(request)
        }.fold(
            onSuccess = { result -> analysis.complete(result) },
            onFailure = { failure ->
                val code = if (failure is VisionAnalysisProviderUnavailableException) {
                    "PROVIDER_UNAVAILABLE"
                } else {
                    "PROVIDER_FAILURE"
                }
                analysis.fail(code = code, message = failure.message?.take(500) ?: "Vision provider failed")
            }
        )
    }

    private fun ensureConversationOwned(conversationId: String, hotelId: String, userId: String) {
        conversationRepository.findByIdAndHotelIdAndUserId(conversationId, hotelId, userId)
            ?: throw ConversationNotFoundException(conversationId)
    }

    private fun deterministicFixturesAllowed(): Boolean =
        properties.deterministicFixturesEnabled || environment.acceptsProfiles(Profiles.of("test"))

    private fun providerIdFor(providerMode: VisionAnalysisProviderMode): String =
        when (providerMode) {
            VisionAnalysisProviderMode.DETERMINISTIC_FIXTURE -> VisionProviderIds.DETERMINISTIC_LOCAL
            VisionAnalysisProviderMode.REAL_PROVIDER -> properties.normalizedProvider().name.lowercase()
        }
}

data class RequestVisionAnalysisCommand(
    val attachmentId: UUID,
    val conversationId: String,
    val hotelId: String,
    val userId: String,
    val providerMode: VisionAnalysisProviderMode,
    val idempotencyKey: String,
    val fixtureKey: String? = null
) {
    init {
        require(conversationId.isNotBlank()) { "conversationId must not be blank" }
        require(hotelId.isNotBlank()) { "hotelId must not be blank" }
        require(userId.isNotBlank()) { "userId must not be blank" }
        require(idempotencyKey.isNotBlank()) { "idempotencyKey must not be blank" }
        require(fixtureKey == null || fixtureKey.isNotBlank()) { "fixtureKey must not be blank" }
    }
}

class VisionAnalysisNotFoundException(message: String) : RuntimeException(message)
