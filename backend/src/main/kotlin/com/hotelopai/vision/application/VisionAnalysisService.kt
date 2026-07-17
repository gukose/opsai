package com.hotelopai.vision.application

import com.hotelopai.assistant.application.AssistantAttachmentRepository
import com.hotelopai.assistant.application.ConversationNotFoundException
import com.hotelopai.assistant.application.ConversationRepository
import com.hotelopai.assistant.domain.AttachmentStorageStatus
import com.hotelopai.assistant.domain.AttachmentType
import com.hotelopai.observability.OperationalObservability
import com.hotelopai.shared.kernel.UuidV7Generator
import com.hotelopai.shared.kernel.toPersistencePrecision
import com.hotelopai.vision.domain.VisionAnalysis
import com.hotelopai.vision.domain.VisionAnalysisProviderMode
import com.hotelopai.vision.domain.VisionAnalysisStatus
import com.hotelopai.vision.domain.VisionProviderIds
import org.slf4j.LoggerFactory
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
    private val environment: Environment,
    private val observability: OperationalObservability = OperationalObservability.noop()
) {
    @Transactional
    fun analyze(command: RequestVisionAnalysisCommand): VisionAnalysis {
        val timer = observability.startTimer()
        var outcome = "failure"
        var reasonCode = "operation_failed"
        var confidenceBucket = "none"
        val provider = observability.provider(providerIdFor(command.providerMode))
        try {
        require(properties.enabled) { "Vision analysis is disabled" }
        val existing = analysisRepository.findByIdempotencyScope(
            attachmentId = command.attachmentId,
            conversationId = command.conversationId,
            hotelId = command.hotelId,
            userId = command.userId,
            idempotencyKey = command.idempotencyKey
        )
        if (existing != null) {
            outcome = "idempotent_reuse"
            reasonCode = "none"
            confidenceBucket = observability.confidenceBucket(existing.confidence)
            recordAnalysis("analyze", outcome, provider, confidenceBucket, reasonCode)
            return existing
        }

        val pending = createPending(command)
        analysisRepository.save(pending)
        val finalAnalysis = process(pending, command)
        outcome = analysisOutcome(finalAnalysis)
        reasonCode = finalAnalysis.failureCode?.lowercase() ?: "none"
        confidenceBucket = observability.confidenceBucket(finalAnalysis.confidence)
        recordAnalysis("analyze", outcome, provider, confidenceBucket, reasonCode)
        if (outcome == "failed" || outcome == "ineligible") {
            logger.warn("event=vision_analysis operation=analyze outcome=$outcome reasonCode=$reasonCode provider=$provider confidenceBucket=$confidenceBucket")
        }
        return finalAnalysis
        } catch (exception: RuntimeException) {
            recordAnalysis("analyze", outcome, provider, confidenceBucket, reasonCode)
            logger.warn("event=vision_analysis operation=analyze outcome=failure reasonCode=$reasonCode provider=$provider confidenceBucket=$confidenceBucket")
            throw exception
        } finally {
            observability.stopTimer(
                timer,
                "hotelopai.vision.analysis.duration",
                "operation" to "analyze",
                "outcome" to outcome,
                "provider" to provider,
                "confidence_bucket" to confidenceBucket,
                "reason_code" to reasonCode
            )
        }
    }

    @Transactional
    fun retryFailed(command: RequestVisionAnalysisCommand): VisionAnalysis {
        val timer = observability.startTimer()
        var outcome = "failure"
        var reasonCode = "operation_failed"
        var confidenceBucket = "none"
        val provider = observability.provider(providerIdFor(command.providerMode))
        try {
        require(properties.enabled) { "Vision analysis is disabled" }
        val existing = analysisRepository.findByIdempotencyScope(
            attachmentId = command.attachmentId,
            conversationId = command.conversationId,
            hotelId = command.hotelId,
            userId = command.userId,
            idempotencyKey = command.idempotencyKey
        ) ?: throw VisionAnalysisNotFoundException("Vision analysis not found for retry")

        val retry = existing.retry(Instant.now().toPersistencePrecision())
        analysisRepository.save(retry)
        val finalAnalysis = process(retry, command)
        outcome = analysisOutcome(finalAnalysis)
        reasonCode = finalAnalysis.failureCode?.lowercase() ?: "none"
        confidenceBucket = observability.confidenceBucket(finalAnalysis.confidence)
        recordAnalysis("retry", outcome, provider, confidenceBucket, reasonCode)
        if (outcome == "failed" || outcome == "ineligible") {
            logger.warn("event=vision_analysis operation=retry outcome=$outcome reasonCode=$reasonCode provider=$provider confidenceBucket=$confidenceBucket")
        }
        return finalAnalysis
        } catch (exception: RuntimeException) {
            recordAnalysis("retry", outcome, provider, confidenceBucket, reasonCode)
            logger.warn("event=vision_analysis operation=retry outcome=failure reasonCode=$reasonCode provider=$provider confidenceBucket=$confidenceBucket")
            throw exception
        } finally {
            observability.stopTimer(
                timer,
                "hotelopai.vision.analysis.duration",
                "operation" to "retry",
                "outcome" to outcome,
                "provider" to provider,
                "confidence_bucket" to confidenceBucket,
                "reason_code" to reasonCode
            )
        }
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

        val now = Instant.now().toPersistencePrecision()
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
                message = "Deterministic vision fixtures are not enabled",
                now = Instant.now().toPersistencePrecision()
            )
        }

        return runCatching {
            visionAnalysisPort.analyze(request)
        }.fold(
            onSuccess = { result -> analysis.complete(result, Instant.now().toPersistencePrecision()) },
            onFailure = { failure ->
                analysis.fail(
                    code = "PROVIDER_FAILURE",
                    message = failure.message?.take(500) ?: "Vision provider failed",
                    now = Instant.now().toPersistencePrecision()
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
                message = "Attachment is registered metadata only and has no provider-accessible media",
                now = Instant.now().toPersistencePrecision()
            )
        }

        return runCatching {
            visionAnalysisPort.analyze(request)
        }.fold(
            onSuccess = { result -> analysis.complete(result, Instant.now().toPersistencePrecision()) },
            onFailure = { failure ->
                val code = if (failure is VisionAnalysisProviderUnavailableException) {
                    "PROVIDER_UNAVAILABLE"
                } else {
                    "PROVIDER_FAILURE"
                }
                analysis.fail(
                    code = code,
                    message = failure.message?.take(500) ?: "Vision provider failed",
                    now = Instant.now().toPersistencePrecision()
                )
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

    private fun analysisOutcome(analysis: VisionAnalysis): String =
        when (analysis.status) {
            VisionAnalysisStatus.COMPLETED -> "completed"
            VisionAnalysisStatus.FAILED -> "failed"
            VisionAnalysisStatus.INELIGIBLE -> "ineligible"
            VisionAnalysisStatus.PENDING -> "pending"
        }

    private fun recordAnalysis(
        operation: String,
        outcome: String,
        provider: String,
        confidenceBucket: String,
        reasonCode: String
    ) {
        observability.incrementCounter(
            "hotelopai.vision.analysis.total",
            "operation" to operation,
            "outcome" to outcome,
            "provider" to provider,
            "confidence_bucket" to confidenceBucket,
            "reason_code" to reasonCode
        )
    }

    companion object {
        private val logger = LoggerFactory.getLogger(VisionAnalysisService::class.java)
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
