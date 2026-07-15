package com.hotelopai.assistant.application

import com.hotelopai.assistant.domain.AttachmentStorageStatus
import com.hotelopai.assistant.domain.Conversation
import com.hotelopai.assistant.domain.ImageObservationSource
import com.hotelopai.task.application.TaskAttachmentLinkRepository
import com.hotelopai.task.domain.TaskAttachmentLink
import com.hotelopai.task.domain.TaskAttachmentSourceType
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
    private val taskAttachmentLinkRepository: TaskAttachmentLinkRepository
) : ConfirmedTaskAttachmentLinker {
    override fun linkConfirmedTask(
        conversation: Conversation,
        taskId: UUID,
        now: Instant
    ): List<TaskAttachmentLink> {
        val sourceMessageIds = conversation.activeDraftSourceMessageIds.toSet()
        if (sourceMessageIds.isEmpty()) {
            return emptyList()
        }

        val sourceMessages = conversation.messages.filter { it.id in sourceMessageIds }
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

        return taskAttachmentLinkRepository.saveAll(links)
    }

    private fun parseUuid(value: String, field: String): UUID =
        try {
            UUID.fromString(value)
        } catch (_: IllegalArgumentException) {
            throw IllegalStateException("$field must be a server-generated UUID")
        }
}
