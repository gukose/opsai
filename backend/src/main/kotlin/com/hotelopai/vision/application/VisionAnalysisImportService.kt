package com.hotelopai.vision.application

import com.hotelopai.assistant.application.AssistantAttachmentRepository
import com.hotelopai.assistant.application.AssistantConversationService
import com.hotelopai.assistant.application.ConversationNotFoundException
import com.hotelopai.assistant.application.ConversationRepository
import com.hotelopai.assistant.application.ConversationTurnResult
import com.hotelopai.assistant.domain.AttachmentType
import com.hotelopai.assistant.domain.ImageObservation
import com.hotelopai.assistant.domain.ImageObservationSource
import com.hotelopai.assistant.domain.InputType
import com.hotelopai.observability.OperationalObservability
import com.hotelopai.shared.kernel.PersistenceInstant
import com.hotelopai.shared.kernel.UuidV7Generator
import com.hotelopai.vision.domain.VisionAnalysis
import com.hotelopai.vision.domain.VisionAnalysisImport
import com.hotelopai.vision.domain.VisionAnalysisImportStatus
import com.hotelopai.vision.domain.VisionAnalysisStatus
import com.hotelopai.vision.domain.VisionDetectedObservation
import org.springframework.dao.DuplicateKeyException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Service
class VisionAnalysisImportService(
    private val conversationRepository: ConversationRepository,
    private val attachmentRepository: AssistantAttachmentRepository,
    private val visionAnalysisRepository: VisionAnalysisRepository,
    private val visionAnalysisImportRepository: VisionAnalysisImportRepository,
    private val assistantConversationService: AssistantConversationService,
    private val observability: OperationalObservability = OperationalObservability.noop()
) {
    @Transactional
    fun importCompletedAnalysis(command: ImportVisionAnalysisCommand): ConversationTurnResult {
        val timer = observability.startTimer()
        var outcome = "failure"
        var reasonCode = "operation_failed"
        try {
        val conversation = conversationRepository.findByIdAndHotelIdAndUserId(
            id = command.conversationId,
            hotelId = command.hotelId,
            userId = command.userId
        ) ?: throw ConversationNotFoundException(command.conversationId)

        val existingImport = visionAnalysisImportRepository.findByConversationIdAndAnalysisId(
            conversationId = command.conversationId,
            analysisId = command.analysisId
        )
        if (existingImport != null) {
            return when (existingImport.status) {
                VisionAnalysisImportStatus.COMPLETED -> {
                    outcome = "duplicate"
                    reasonCode = "already_completed"
                    recordImport(outcome, reasonCode)
                    logger.info("event=vision_import operation=import outcome=duplicate reasonCode=already_completed")
                    ConversationTurnResult(conversation)
                }
                VisionAnalysisImportStatus.PENDING -> throw VisionAnalysisImportConflictException("Vision analysis import is already in progress")
                VisionAnalysisImportStatus.FAILED -> throw VisionAnalysisImportConflictException("Vision analysis import previously failed and requires explicit retry")
            }
        }

        val analysis = visionAnalysisRepository.findById(command.analysisId)
            ?: throw VisionAnalysisImportNotFoundException("Vision analysis not found")
        validateAnalysisScope(analysis, command)
        requireCompleted(analysis)

        val attachment = attachmentRepository.findByIdAndConversationIdAndHotelIdAndUserId(
            id = analysis.attachmentId,
            conversationId = command.conversationId,
            hotelId = command.hotelId,
            userId = command.userId
        ) ?: throw VisionAnalysisImportNotFoundException("Vision analysis attachment not found")
        require(attachment.id == analysis.attachmentId) { "Vision analysis attachment mismatch" }
        require(attachment.type == AttachmentType.IMAGE) { "Only IMAGE attachment analysis can be imported" }

        val observations = toImageObservations(analysis)
        if (observations.isEmpty()) {
            throw VisionAnalysisImportConflictException("Completed vision analysis has no usable observations")
        }

        val now = PersistenceInstant.toPersistencePrecision(Instant.now())
        val importRecord = VisionAnalysisImport(
            id = UuidV7Generator.generate(now),
            analysisId = analysis.id,
            conversationId = command.conversationId,
            attachmentId = analysis.attachmentId,
            hotelId = command.hotelId,
            userId = command.userId,
            status = VisionAnalysisImportStatus.PENDING,
            createdAt = now,
            updatedAt = now
        )

        try {
            visionAnalysisImportRepository.save(importRecord)
        } catch (_: DuplicateKeyException) {
            val duplicate = visionAnalysisImportRepository.findByConversationIdAndAnalysisId(
                conversationId = command.conversationId,
                analysisId = command.analysisId
            )
            if (duplicate?.status == VisionAnalysisImportStatus.COMPLETED) {
                outcome = "duplicate"
                reasonCode = "duplicate_key_completed"
                recordImport(outcome, reasonCode)
                logger.info("event=vision_import operation=import outcome=duplicate reasonCode=duplicate_key_completed")
                return ConversationTurnResult(conversation)
            }
            throw VisionAnalysisImportConflictException("Vision analysis import is already in progress")
        }

        val result = assistantConversationService.sendMessage(
            conversationId = command.conversationId,
            hotelId = command.hotelId,
            userId = command.userId,
            text = "",
            inputType = InputType.IMAGE,
            attachments = emptyList(),
            imageObservations = observations
        )

        val messageId = result.conversation.messages.lastOrNull { message ->
            message.imageObservations.any { it.analysisId == analysis.id.toString() }
        }?.id ?: throw VisionAnalysisImportConflictException("Vision analysis import did not persist a conversation message")

        visionAnalysisImportRepository.save(importRecord.complete(messageId = messageId, now = now))
        outcome = "success"
        reasonCode = "none"
        recordImport(outcome, reasonCode)
        return result
        } catch (exception: ConversationNotFoundException) {
            outcome = "not_found"
            reasonCode = "conversation_not_found"
            recordImport(outcome, reasonCode)
            throw exception
        } catch (exception: VisionAnalysisImportNotFoundException) {
            outcome = "not_found"
            reasonCode = "analysis_not_found"
            recordImport(outcome, reasonCode)
            throw exception
        } catch (exception: VisionAnalysisImportConflictException) {
            outcome = "conflict"
            reasonCode = "import_conflict"
            recordImport(outcome, reasonCode)
            logger.warn("event=vision_import operation=import outcome=conflict reasonCode=import_conflict")
            throw exception
        } catch (exception: RuntimeException) {
            recordImport(outcome, reasonCode)
            logger.warn("event=vision_import operation=import outcome=failure reasonCode=operation_failed")
            throw exception
        } finally {
            observability.stopTimer(
                timer,
                "hotelopai.vision.import.duration",
                "operation" to "import",
                "outcome" to outcome,
                "reason_code" to reasonCode
            )
        }
    }

    private fun validateAnalysisScope(analysis: VisionAnalysis, command: ImportVisionAnalysisCommand) {
        if (
            analysis.conversationId != command.conversationId ||
            analysis.hotelId != command.hotelId ||
            analysis.userId != command.userId
        ) {
            throw VisionAnalysisImportNotFoundException("Vision analysis not found")
        }
    }

    private fun requireCompleted(analysis: VisionAnalysis) {
        if (analysis.status != VisionAnalysisStatus.COMPLETED) {
            throw VisionAnalysisImportConflictException("Only COMPLETED vision analysis can be imported")
        }
    }

    private fun toImageObservations(analysis: VisionAnalysis): List<ImageObservation> {
        val confidence = analysis.confidence ?: BigDecimal.ZERO
        val confidenceBand = VisionAnalysisConfidenceBand.from(confidence)
        return analysis.observations
            .sortedWith(compareBy<VisionDetectedObservation> { it.order }.thenBy { it.description })
            .mapNotNull { observation ->
                val text = normalizedObservationText(confidenceBand, observation)
                    ?: return@mapNotNull null
                ImageObservation(
                    id = "vision-${analysis.id}-${observation.order}",
                    attachmentId = analysis.attachmentId.toString(),
                    analysisId = analysis.id.toString(),
                    text = text,
                    source = ImageObservationSource.VISION_ANALYSIS,
                    confidence = observation.confidence.toDouble(),
                    providerId = sanitizeProviderId(analysis.providerId),
                    advisory = true,
                    order = observation.order
                )
            }
    }

    private fun normalizedObservationText(
        confidenceBand: VisionAnalysisConfidenceBand,
        observation: VisionDetectedObservation
    ): String? {
        val description = observation.description.trim()
        if (description.isBlank()) {
            return null
        }
        return when (confidenceBand) {
            VisionAnalysisConfidenceBand.LOW ->
                "Uncertain provider analysis. Ask the user to clarify what is shown."
            VisionAnalysisConfidenceBand.MEDIUM,
            VisionAnalysisConfidenceBand.HIGH ->
                "Advisory provider analysis: $description"
        }
    }

    private fun sanitizeProviderId(providerId: String): String =
        providerId.trim()
            .filter { it.isLetterOrDigit() || it == '-' || it == '_' || it == '.' }
            .take(120)
            .ifBlank { "unknown" }

    private fun recordImport(outcome: String, reasonCode: String) {
        observability.incrementCounter(
            "hotelopai.vision.import.total",
            "operation" to "import",
            "outcome" to outcome,
            "reason_code" to reasonCode
        )
    }

    companion object {
        private val logger = LoggerFactory.getLogger(VisionAnalysisImportService::class.java)
    }
}

data class ImportVisionAnalysisCommand(
    val conversationId: String,
    val analysisId: UUID,
    val hotelId: String,
    val userId: String
) {
    init {
        require(conversationId.isNotBlank()) { "conversationId must not be blank" }
        require(hotelId.isNotBlank()) { "hotelId must not be blank" }
        require(userId.isNotBlank()) { "userId must not be blank" }
    }
}

enum class VisionAnalysisConfidenceBand {
    LOW,
    MEDIUM,
    HIGH;

    companion object {
        private val MEDIUM_THRESHOLD = BigDecimal("0.50")
        private val HIGH_THRESHOLD = BigDecimal("0.80")

        fun from(confidence: BigDecimal): VisionAnalysisConfidenceBand =
            when {
                confidence < MEDIUM_THRESHOLD -> LOW
                confidence < HIGH_THRESHOLD -> MEDIUM
                else -> HIGH
            }
    }
}

class VisionAnalysisImportNotFoundException(message: String) : RuntimeException(message)

class VisionAnalysisImportConflictException(message: String) : RuntimeException(message)
