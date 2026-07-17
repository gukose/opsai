package com.hotelopai.assistant.application

import com.hotelopai.assistant.domain.AttachmentStorageStatus
import com.hotelopai.assistant.domain.Conversation
import com.hotelopai.assistant.domain.ImageObservationSource
import com.hotelopai.observability.OperationalObservability
import com.hotelopai.task.application.TaskAttachmentLinkRepository
import com.hotelopai.task.domain.TaskAttachmentLink
import com.hotelopai.task.domain.TaskAttachmentSourceType
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

interface ConfirmedTaskAttachmentLinker {
    fun linkConfirmedTask(
        conversation: Conversation,
        taskId: UUID,
        now: Instant
    ): List<TaskAttachmentLink>
}

object NoOpConfirmedTaskAttachmentLinker : ConfirmedTaskAttachmentLinker {
    override fun linkConfirmedTask(
        conversation: Conversation,
        taskId: UUID,
        now: Instant
    ): List<TaskAttachmentLink> = emptyList()
}

@Service
class ConfirmedTaskAttachmentLinkService(
    private val assistantAttachmentRepository: AssistantAttachmentRepository,
    private val importedVisionAnalysisLookupPort: ImportedVisionAnalysisLookupPort,
    private val taskAttachmentLinkRepository: TaskAttachmentLinkRepository,
    private val observability: OperationalObservability = OperationalObservability.noop()
) : ConfirmedTaskAttachmentLinker {
    override fun linkConfirmedTask(
        conversation: Conversation,
        taskId: UUID,
        now: Instant
    ): List<TaskAttachmentLink> {
        var sourceType = "none"
        try {
        val sourceMessageIds = conversation.activeDraftSourceMessageIds.toSet()
        if (sourceMessageIds.isEmpty()) {
            recordLink(outcome = "success", sourceType = sourceType, reasonCode = "none")
            return emptyList()
        }

        val sourceMessages = conversation.messages.filter { it.id in sourceMessageIds }
        sourceType = observability.sourceType(
            hasAssistantMessage = sourceMessages.any { message -> message.attachments.isNotEmpty() },
            hasVisionAnalysis = sourceMessages.any { message ->
                message.imageObservations.any { it.source == ImageObservationSource.VISION_ANALYSIS }
            }
        )
        val assistantMessageLinks = sourceMessages
            .flatMap { message ->
                message.attachments
                    .filter { it.storageStatus == AttachmentStorageStatus.REGISTERED }
                    .map { attachment ->
                        val attachmentId = parseUuid(attachment.id, "attachmentId")
                        val registered = assistantAttachmentRepository.findByIdAndConversationIdAndHotelIdAndUserId(
                            id = attachmentId,
                            conversationId = conversation.id,
                            hotelId = conversation.hotelId,
                            userId = conversation.userId
                        ) ?: throw IllegalStateException("Registered attachment is not owned by this conversation")

                        require(registered.storageStatus == AttachmentStorageStatus.REGISTERED) {
                            "Only REGISTERED attachments can be linked to tasks"
                        }

                        TaskAttachmentLink(
                            id = UUID.randomUUID(),
                            taskId = taskId,
                            attachmentId = registered.id,
                            conversationId = conversation.id,
                            hotelId = conversation.hotelId,
                            userId = conversation.userId,
                            sourceType = TaskAttachmentSourceType.ASSISTANT_MESSAGE,
                            createdAt = now
                        )
                    }
            }

        val visionLinks = sourceMessages
            .flatMap { it.imageObservations }
            .filter { it.source == ImageObservationSource.VISION_ANALYSIS }
            .map { observation ->
                val attachmentId = parseUuid(
                    requireNotNull(observation.attachmentId) { "Vision analysis observation requires attachmentId" },
                    "attachmentId"
                )
                val analysisId = parseUuid(
                    requireNotNull(observation.analysisId) { "Vision analysis observation requires analysisId" },
                    "analysisId"
                )

                val registered = assistantAttachmentRepository.findByIdAndConversationIdAndHotelIdAndUserId(
                    id = attachmentId,
                    conversationId = conversation.id,
                    hotelId = conversation.hotelId,
                    userId = conversation.userId
                ) ?: throw IllegalStateException("Vision analysis attachment is not owned by this conversation")

                require(registered.storageStatus == AttachmentStorageStatus.REGISTERED) {
                    "Only REGISTERED attachments can be linked to tasks"
                }

                val importRecord = importedVisionAnalysisLookupPort.findCompletedImport(
                    conversationId = conversation.id,
                    analysisId = analysisId
                ) ?: throw IllegalStateException("Vision analysis import provenance is missing")

                require(importRecord.attachmentId == registered.id) {
                    "Vision analysis import attachment does not match observation attachment"
                }
                require(importRecord.hotelId == conversation.hotelId && importRecord.userId == conversation.userId) {
                    "Vision analysis import is not owned by this conversation scope"
                }

                TaskAttachmentLink(
                    id = UUID.randomUUID(),
                    taskId = taskId,
                    attachmentId = registered.id,
                    conversationId = conversation.id,
                    hotelId = conversation.hotelId,
                    userId = conversation.userId,
                    sourceType = TaskAttachmentSourceType.VISION_ANALYSIS,
                    analysisId = analysisId,
                    analysisImportId = importRecord.id,
                    createdAt = now
                )
            }

        val links = (visionLinks + assistantMessageLinks)
            .distinctBy { it.attachmentId }
            .sortedWith(compareBy<TaskAttachmentLink> { it.createdAt }.thenBy { it.attachmentId })

        return taskAttachmentLinkRepository.saveAll(links).also {
            recordLink(outcome = "success", sourceType = sourceType, reasonCode = "none")
        }
        } catch (exception: RuntimeException) {
            recordLink(outcome = "failure", sourceType = sourceType, reasonCode = "operation_failed")
            logger.warn("event=task_attachment_link operation=link outcome=failure reasonCode=operation_failed sourceType=$sourceType")
            throw exception
        }
    }

    private fun parseUuid(value: String, field: String): UUID =
        try {
            UUID.fromString(value)
        } catch (_: IllegalArgumentException) {
            throw IllegalStateException("$field must be a server-generated UUID")
        }

    private fun recordLink(outcome: String, sourceType: String, reasonCode: String) {
        observability.incrementCounter(
            "hotelopai.task.attachment.link.total",
            "operation" to "link",
            "outcome" to outcome,
            "source_type" to sourceType,
            "reason_code" to reasonCode
        )
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ConfirmedTaskAttachmentLinkService::class.java)
    }
}
