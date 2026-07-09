package com.hotelopai.assistant.api

import com.hotelopai.assistant.application.ConversationTurnResult
import com.hotelopai.assistant.domain.ConversationAttachment
import com.hotelopai.assistant.domain.Conversation
import com.hotelopai.assistant.domain.ConversationMessage
import com.hotelopai.assistant.domain.AudioMetadata
import com.hotelopai.assistant.domain.FollowUpOption
import com.hotelopai.assistant.domain.FollowUpQuestion
import com.hotelopai.assistant.domain.InputType
import com.hotelopai.assistant.domain.MissingField
import com.hotelopai.assistant.domain.TaskCreationCandidate
import com.hotelopai.assistant.domain.TaskPreview

data class StartConversationRequest(
    val hotelId: String,
    val userId: String
)

data class SendAssistantMessageRequest(
    val text: String = "",
    val inputType: InputType = InputType.TEXT,
    val transcript: String? = null,
    val audioMetadata: AudioMetadataDto? = null,
    val attachments: List<MessageAttachmentDto>? = null,
    val attachmentIds: List<String>? = null
)

data class ConfirmTaskRequest(
    val idempotencyKey: String
)

data class AssistantConversationResponse(
    val conversationId: String,
    val state: String,
    val assistantMessage: String,
    val intent: String,
    val missingFields: List<MissingFieldDto>,
    val followUpQuestion: FollowUpQuestionDto?,
    val taskPreview: TaskPreviewDto?,
    val taskCreationRequest: TaskCreationRequestDto?,
    val createdTaskId: String?,
    val messages: List<ConversationMessageDto>
) {
    companion object {
        fun from(result: ConversationTurnResult): AssistantConversationResponse {
            val conversation = result.conversation

            return AssistantConversationResponse(
                conversationId = conversation.id,
                state = conversation.state.name,
                assistantMessage = assistantMessageFor(result),
                intent = conversation.intent.name,
                missingFields = conversation.missingFields.map { MissingFieldDto.from(it) },
                followUpQuestion = conversation.followUpQuestion?.let { FollowUpQuestionDto.from(it) },
                taskPreview = conversation.taskPreview?.let { TaskPreviewDto.from(it) },
                taskCreationRequest = result.taskCreationCandidate?.let { TaskCreationRequestDto.from(it) },
                createdTaskId = result.createdTaskId ?: conversation.createdTaskId,
                messages = conversation.messages.map { ConversationMessageDto.from(it) }
            )
        }

        private fun assistantMessageFor(result: ConversationTurnResult): String {
            val conversation = result.conversation

            return when {
                result.createdTaskId != null -> "Task created successfully. Task ID: ${result.createdTaskId}"
                result.taskCreationCandidate != null -> "I have enough information. Please review the task."
                conversation.followUpQuestion != null -> conversation.followUpQuestion.prompt
                conversation.taskPreview != null -> "I have enough information. Please review the task."
                conversation.messages.isEmpty() -> "Hi. How can I help you today?"
                else -> "I could not complete that request. Please try again."
            }
        }
    }
}

data class MissingFieldDto(
    val key: String,
    val label: String,
    val required: Boolean
) {
    companion object {
        fun from(field: MissingField): MissingFieldDto =
            MissingFieldDto(
                key = field.key,
                label = field.label,
                required = field.required
            )
    }
}

data class FollowUpQuestionDto(
    val id: String,
    val fieldKey: String,
    val prompt: String,
    val options: List<FollowUpOptionDto>
) {
    companion object {
        fun from(question: FollowUpQuestion): FollowUpQuestionDto =
            FollowUpQuestionDto(
                id = question.id,
                fieldKey = question.fieldKey,
                prompt = question.prompt,
                options = question.options.map { FollowUpOptionDto.from(it) }
            )
    }
}

data class FollowUpOptionDto(
    val id: String,
    val label: String,
    val value: String
) {
    companion object {
        fun from(option: FollowUpOption): FollowUpOptionDto =
            FollowUpOptionDto(
                id = option.id,
                label = option.label,
                value = option.value
            )
    }
}

data class TaskPreviewDto(
    val type: String,
    val title: String,
    val description: String,
    val roomNumber: String?,
    val publicAreaId: String?,
    val assetId: String?,
    val assignedTeam: String?,
    val priority: String?,
    val slaMinutes: Int?,
    val requiresPmsUpdate: Boolean
) {
    companion object {
        fun from(preview: TaskPreview): TaskPreviewDto =
            TaskPreviewDto(
                type = preview.type.name,
                title = preview.title,
                description = preview.description,
                roomNumber = preview.roomNumber,
                publicAreaId = preview.publicAreaId,
                assetId = preview.assetId,
                assignedTeam = preview.assignedTeam,
                priority = preview.priority,
                slaMinutes = preview.slaMinutes,
                requiresPmsUpdate = preview.requiresPmsUpdate
            )
    }
}

data class TaskCreationRequestDto(
    val conversationId: String,
    val draftId: String,
    val draftVersion: Int,
    val idempotencyKey: String,
    val preview: TaskPreviewDto
) {
    companion object {
        fun from(candidate: TaskCreationCandidate): TaskCreationRequestDto =
            TaskCreationRequestDto(
                conversationId = candidate.conversationId,
                draftId = candidate.draftId,
                draftVersion = candidate.draftVersion,
                idempotencyKey = candidate.idempotencyKey,
                preview = TaskPreviewDto.from(candidate.preview)
            )
    }
}

data class ConversationMessageDto(
    val id: String,
    val role: String,
    val inputType: String,
    val text: String?,
    val voiceTranscript: String?,
    val audioMetadata: AudioMetadataDto?,
    val attachments: List<MessageAttachmentDto>,
    val imageObservations: List<ImageObservationDto>,
    val attachmentIds: List<String>,
    val createdAt: String
) {
    companion object {
        fun from(message: ConversationMessage): ConversationMessageDto =
            ConversationMessageDto(
                id = message.id,
                role = message.role.name,
                inputType = message.inputType.name,
                text = message.text,
                voiceTranscript = message.voiceTranscript,
                audioMetadata = message.audioMetadata?.let { AudioMetadataDto.from(it) },
                attachments = message.attachments.map { MessageAttachmentDto.from(it) },
                imageObservations = message.imageObservations.map { ImageObservationDto.from(it) },
                attachmentIds = message.attachmentIds,
                createdAt = message.createdAt.toString()
            )
    }
}

data class AudioMetadataDto(
    val originalFileName: String?,
    val mimeType: String?,
    val durationMs: Long?,
    val sizeBytes: Long?
) {
    companion object {
        fun from(metadata: AudioMetadata): AudioMetadataDto =
            AudioMetadataDto(
                originalFileName = metadata.originalFileName,
                mimeType = metadata.mimeType,
                durationMs = metadata.durationMs,
                sizeBytes = metadata.sizeBytes
            )
    }

    fun toDomain(): AudioMetadata =
        AudioMetadata(
            originalFileName = originalFileName,
            mimeType = mimeType,
            durationMs = durationMs,
            sizeBytes = sizeBytes
        )
}

data class MessageAttachmentDto(
    val id: String,
    val originalFileName: String?,
    val mimeType: String?,
    val sizeBytes: Long?,
    val widthPx: Int?,
    val heightPx: Int?
) {
    companion object {
        fun from(attachment: ConversationAttachment): MessageAttachmentDto =
            MessageAttachmentDto(
                id = attachment.id,
                originalFileName = attachment.originalFileName,
                mimeType = attachment.mimeType,
                sizeBytes = attachment.sizeBytes,
                widthPx = attachment.widthPx,
                heightPx = attachment.heightPx
            )
    }

    fun toDomain(): ConversationAttachment =
        ConversationAttachment(
            id = id,
            originalFileName = originalFileName,
            mimeType = mimeType,
            sizeBytes = sizeBytes,
            widthPx = widthPx,
            heightPx = heightPx
        )
}

data class ImageObservationDto(
    val attachmentId: String?,
    val description: String,
    val confidence: Double?
) {
    companion object {
        fun from(observation: com.hotelopai.assistant.domain.ImageObservation): ImageObservationDto =
            ImageObservationDto(
                attachmentId = observation.attachmentId,
                description = observation.description,
                confidence = observation.confidence
            )
    }
}
