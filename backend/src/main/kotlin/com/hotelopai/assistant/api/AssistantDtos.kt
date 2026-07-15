package com.hotelopai.assistant.api

import com.fasterxml.jackson.annotation.JsonAnySetter
import com.fasterxml.jackson.annotation.JsonIgnore
import com.hotelopai.assistant.application.ConversationTurnResult
import com.hotelopai.assistant.application.RegisterAssistantAttachmentCommand
import com.hotelopai.assistant.domain.ConversationAttachment
import com.hotelopai.assistant.domain.Conversation
import com.hotelopai.assistant.domain.ConversationMessage
import com.hotelopai.assistant.domain.AttachmentStorageStatus
import com.hotelopai.assistant.domain.AttachmentType
import com.hotelopai.assistant.domain.AudioMetadata
import com.hotelopai.assistant.domain.ImageObservation
import com.hotelopai.assistant.domain.ImageObservationSource
import com.hotelopai.assistant.domain.FollowUpOption
import com.hotelopai.assistant.domain.FollowUpQuestion
import com.hotelopai.assistant.domain.InputType
import com.hotelopai.assistant.domain.MissingField
import com.hotelopai.assistant.domain.TaskCreationCandidate
import com.hotelopai.assistant.domain.TaskPreview
import com.hotelopai.assistant.domain.VoiceTranscriptMetadata
import com.hotelopai.assistant.domain.VoiceTranscriptSource
import com.hotelopai.assistant.domain.RegisteredConversationAttachment

data class StartConversationRequest(
    val hotelId: String,
    val userId: String
)

data class SendAssistantMessageRequest(
    val text: String = "",
    val inputType: InputType = InputType.TEXT,
    val transcript: String? = null,
    val voiceTranscript: VoiceTranscriptDto? = null,
    val audioMetadata: AudioMetadataDto? = null,
    val attachments: List<MessageAttachmentDto>? = null,
    val attachmentIds: List<String>? = null,
    val imageObservations: List<ImageObservationDto>? = null,
    val hotelId: String? = null,
    val userId: String? = null,
    val audioBase64: String? = null,
    val audioBytes: String? = null,
    val audioUrl: String? = null,
    val localAudioUri: String? = null,
    val fileUri: String? = null,
    val imageBase64: String? = null,
    val imageBytes: String? = null,
    val imageUrl: String? = null
) {
    fun rejectUnsafeOwnershipAndMediaFields() {
        require(hotelId == null && userId == null) { "message ownership fields are not accepted" }
        require(audioBase64 == null && audioBytes == null && audioUrl == null && localAudioUri == null && fileUri == null) {
            "raw audio fields and audio URIs are not accepted"
        }
        require(imageBase64 == null && imageBytes == null && imageUrl == null) {
            "raw image fields and image URLs are not accepted"
        }
    }
}

data class RegisterAssistantAttachmentRequest(
    val type: AttachmentType?,
    val originalFileName: String?,
    val mimeType: String?,
    val sizeBytes: Long?,
    val widthPx: Int? = null,
    val heightPx: Int? = null,
    val id: String? = null,
    val hotelId: String? = null,
    val userId: String? = null,
    val ownerId: String? = null,
    val storageReference: String? = null,
    val storageStatus: String? = null,
    val providerUrl: String? = null,
    val mediaUrl: String? = null,
    val imageUrl: String? = null,
    val fileUrl: String? = null,
    val imageBase64: String? = null,
    val imageBytes: String? = null,
    val base64: String? = null,
    val binary: String? = null,
    val rawBytes: String? = null,
    val localReference: String? = null,
    val localUri: String? = null,
    val fileUri: String? = null,
    val deviceUri: String? = null
) {
    @JsonIgnore
    private val unsupportedFields: MutableSet<String> = linkedSetOf()

    @JsonAnySetter
    fun captureUnsupportedField(name: String, value: Any?) {
        unsupportedFields += name
    }

    fun toCommand(): RegisterAssistantAttachmentCommand {
        rejectForbiddenFields()
        val normalizedType = requireNotNull(type) { "attachment type is required" }
        val normalizedFileName = normalizedFileName()
        val normalizedMimeType = normalizedMimeType()
        val normalizedSizeBytes = validSizeBytes()
        val normalizedWidthPx = validWidthPx()
        val normalizedHeightPx = validHeightPx()
        validateConsistency(normalizedType, normalizedMimeType, normalizedWidthPx, normalizedHeightPx)

        return RegisterAssistantAttachmentCommand(
            type = normalizedType,
            originalFileName = normalizedFileName,
            mimeType = normalizedMimeType,
            sizeBytes = normalizedSizeBytes,
            widthPx = normalizedWidthPx,
            heightPx = normalizedHeightPx
        )
    }

    private fun rejectForbiddenFields() {
        require(unsupportedFields.isEmpty()) {
            "unsupported attachment registration fields are not accepted: ${unsupportedFields.joinToString(", ")}"
        }
        require(id == null) { "client attachment id is not accepted" }
        require(hotelId == null && userId == null && ownerId == null) {
            "attachment ownership fields are not accepted"
        }
        require(storageReference == null && storageStatus == null) {
            "attachment storage fields are not accepted"
        }
        require(providerUrl == null && mediaUrl == null && imageUrl == null && fileUrl == null) {
            "attachment media URLs are not accepted"
        }
        require(imageBase64 == null && imageBytes == null && base64 == null && binary == null && rawBytes == null) {
            "raw attachment bytes are not accepted"
        }
        require(localReference == null && localUri == null && fileUri == null && deviceUri == null) {
            "device-local attachment references are not accepted"
        }
    }

    private fun normalizedFileName(): String =
        originalFileName?.trim()?.also {
            require(it.isNotBlank()) { "attachment originalFileName is required" }
            require(it.length <= 180) { "attachment originalFileName must be 180 characters or fewer" }
        } ?: throw IllegalArgumentException("attachment originalFileName is required")

    private fun normalizedMimeType(): String =
        mimeType?.trim()?.lowercase()?.also {
            require(it in SUPPORTED_MIME_TYPES) { "unsupported attachment mimeType: $it" }
        } ?: throw IllegalArgumentException("attachment mimeType is required")

    private fun validSizeBytes(): Long =
        sizeBytes?.also {
            require(it in 1..10_000_000) { "attachment sizeBytes must be between 1 and 10000000" }
        } ?: throw IllegalArgumentException("attachment sizeBytes is required")

    private fun validWidthPx(): Int? =
        widthPx?.also {
            require(it in 1..10_000) { "attachment widthPx must be between 1 and 10000" }
        }

    private fun validHeightPx(): Int? =
        heightPx?.also {
            require(it in 1..10_000) { "attachment heightPx must be between 1 and 10000" }
        }

    private fun validateConsistency(type: AttachmentType, mimeType: String, widthPx: Int?, heightPx: Int?) {
        when (type) {
            AttachmentType.IMAGE -> require(mimeType in IMAGE_MIME_TYPES) {
                "IMAGE attachments require an image MIME type"
            }
            AttachmentType.PDF -> {
                require(mimeType == "application/pdf") { "PDF attachments require application/pdf" }
                require(widthPx == null && heightPx == null) {
                    "image dimensions are only accepted for image attachments"
                }
            }
            AttachmentType.DOCUMENT -> {
                require(mimeType == "text/plain") { "DOCUMENT attachments require text/plain" }
                require(widthPx == null && heightPx == null) {
                    "image dimensions are only accepted for image attachments"
                }
            }
        }
    }
}

data class RegisteredAssistantAttachmentResponse(
    val attachmentId: String,
    val conversationId: String,
    val type: AttachmentType,
    val originalFileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val widthPx: Int?,
    val heightPx: Int?,
    val storageStatus: AttachmentStorageStatus,
    val storageReference: String?,
    val createdAt: String
) {
    companion object {
        fun from(attachment: RegisteredConversationAttachment): RegisteredAssistantAttachmentResponse =
            RegisteredAssistantAttachmentResponse(
                attachmentId = attachment.id.toString(),
                conversationId = attachment.conversationId,
                type = attachment.type,
                originalFileName = attachment.originalFileName,
                mimeType = attachment.declaredMimeType,
                sizeBytes = attachment.declaredSizeBytes,
                widthPx = attachment.widthPx,
                heightPx = attachment.heightPx,
                storageStatus = attachment.storageStatus,
                storageReference = attachment.storageReference,
                createdAt = attachment.createdAt.toString()
            )
    }
}

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
    val voiceTranscriptMetadata: VoiceTranscriptDto?,
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
                voiceTranscriptMetadata = message.voiceTranscriptMetadata?.let { VoiceTranscriptDto.from(it) },
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

data class VoiceTranscriptDto(
    val transcript: String?,
    val languageCode: String? = null,
    val durationMs: Long? = null,
    val source: VoiceTranscriptSource? = null,
    val audioBase64: String? = null,
    val audioBytes: String? = null,
    val audioUrl: String? = null,
    val localAudioUri: String? = null,
    val fileUri: String? = null
) {
    companion object {
        private val LANGUAGE_CODE_PATTERN = Regex("^[a-zA-Z]{2,3}(-[a-zA-Z]{2})?$")

        fun from(metadata: VoiceTranscriptMetadata): VoiceTranscriptDto =
            VoiceTranscriptDto(
                transcript = metadata.transcript,
                languageCode = metadata.languageCode,
                durationMs = metadata.durationMs,
                source = metadata.source
            )
    }

    fun toDomain(): VoiceTranscriptMetadata {
        require(audioBase64 == null && audioBytes == null && audioUrl == null && localAudioUri == null && fileUri == null) {
            "raw audio fields and audio URIs are not accepted"
        }
        val normalizedTranscript = transcript?.trim()
            ?: throw IllegalArgumentException("voiceTranscript.transcript is required")
        require(normalizedTranscript.isNotBlank()) { "voiceTranscript.transcript is required" }
        require(normalizedTranscript.length <= 4_000) {
            "voiceTranscript.transcript must be 4000 characters or fewer"
        }

        val normalizedLanguageCode = languageCode?.trim()?.takeIf(String::isNotBlank)
        require(normalizedLanguageCode == null || LANGUAGE_CODE_PATTERN.matches(normalizedLanguageCode)) {
            "voiceTranscript.languageCode must be BCP-47 compatible"
        }

        require(durationMs == null || durationMs in 1..600_000) {
            "voiceTranscript.durationMs must be between 1 and 600000"
        }
        require((source ?: VoiceTranscriptSource.CLIENT_TRANSCRIPT) == VoiceTranscriptSource.CLIENT_TRANSCRIPT) {
            "voiceTranscript.source must be CLIENT_TRANSCRIPT"
        }

        return VoiceTranscriptMetadata(
            transcript = normalizedTranscript,
            languageCode = normalizedLanguageCode,
            durationMs = durationMs,
            source = VoiceTranscriptSource.CLIENT_TRANSCRIPT
        )
    }
}

data class MessageAttachmentDto(
    val id: String,
    val type: AttachmentType? = null,
    val originalFileName: String?,
    val mimeType: String?,
    val sizeBytes: Long?,
    val widthPx: Int?,
    val heightPx: Int?,
    val localReference: String? = null,
    val storageStatus: AttachmentStorageStatus? = null,
    val storageReference: String? = null,
    val hotelId: String? = null,
    val userId: String? = null,
    val ownerId: String? = null
) {
    companion object {
        fun from(attachment: ConversationAttachment): MessageAttachmentDto =
            MessageAttachmentDto(
                id = attachment.id,
                type = attachment.type,
                originalFileName = attachment.originalFileName,
                mimeType = attachment.mimeType,
                sizeBytes = attachment.sizeBytes,
                widthPx = attachment.widthPx,
                heightPx = attachment.heightPx,
                localReference = attachment.localReference,
                storageStatus = attachment.storageStatus,
                storageReference = attachment.storageReference
            )
    }

    fun toDomain(): ConversationAttachment =
        ConversationAttachment(
            id = normalizedId(),
            type = requireNotNull(type) { "attachment type is required" },
            originalFileName = normalizedFileName(),
            mimeType = normalizedMimeType(),
            sizeBytes = validSizeBytes(),
            widthPx = validWidthPx(),
            heightPx = validHeightPx(),
            localReference = localReference?.trim()?.takeIf(String::isNotBlank),
            storageStatus = storageStatus ?: AttachmentStorageStatus.LOCAL_METADATA_ONLY,
            storageReference = storageReference?.trim()?.takeIf(String::isNotBlank)
        ).also { validateForbiddenFields(); validateConsistency(it) }

    private fun normalizedId(): String =
        id.trim().also { require(it.isNotBlank()) { "attachment id is required" } }

    private fun normalizedFileName(): String =
        originalFileName?.trim()?.also {
            require(it.isNotBlank()) { "attachment originalFileName is required" }
            require(it.length <= 180) { "attachment originalFileName must be 180 characters or fewer" }
        } ?: throw IllegalArgumentException("attachment originalFileName is required")

    private fun normalizedMimeType(): String =
        mimeType?.trim()?.lowercase()?.also {
            require(it in SUPPORTED_MIME_TYPES) { "unsupported attachment mimeType: $it" }
        } ?: throw IllegalArgumentException("attachment mimeType is required")

    private fun validSizeBytes(): Long =
        sizeBytes?.also {
            require(it in 1..10_000_000) { "attachment sizeBytes must be between 1 and 10000000" }
        } ?: throw IllegalArgumentException("attachment sizeBytes is required")

    private fun validWidthPx(): Int? =
        widthPx?.also {
            require(it in 1..10_000) { "attachment widthPx must be between 1 and 10000" }
        }

    private fun validHeightPx(): Int? =
        heightPx?.also {
            require(it in 1..10_000) { "attachment heightPx must be between 1 and 10000" }
        }

    private fun validateForbiddenFields() {
        require(hotelId == null && userId == null && ownerId == null) {
            "attachment ownership fields are not accepted"
        }
        require(storageReference == null) {
            "attachment storageReference is not accepted"
        }
    }

    private fun validateConsistency(attachment: ConversationAttachment) {
        require(attachment.storageStatus == AttachmentStorageStatus.LOCAL_METADATA_ONLY) {
            "attachment storageStatus must be LOCAL_METADATA_ONLY"
        }
        when (attachment.type) {
            AttachmentType.IMAGE -> require(attachment.mimeType in IMAGE_MIME_TYPES) {
                "IMAGE attachments require an image MIME type"
            }
            AttachmentType.PDF -> {
                require(attachment.mimeType == "application/pdf") { "PDF attachments require application/pdf" }
                require(attachment.widthPx == null && attachment.heightPx == null) {
                    "image dimensions are only accepted for image attachments"
                }
            }
            AttachmentType.DOCUMENT -> {
                require(attachment.mimeType == "text/plain") { "DOCUMENT attachments require text/plain" }
                require(attachment.widthPx == null && attachment.heightPx == null) {
                    "image dimensions are only accepted for image attachments"
                }
            }
        }
    }
}

private val IMAGE_MIME_TYPES = setOf("image/jpeg", "image/png", "image/webp")
private val SUPPORTED_MIME_TYPES = IMAGE_MIME_TYPES + setOf("application/pdf", "text/plain")

data class ImageObservationDto(
    val id: String? = null,
    val attachmentId: String?,
    val analysisId: String? = null,
    val text: String? = null,
    val source: ImageObservationSource? = null,
    val advisory: Boolean? = null,
    val order: Int? = null,
    val providerId: String? = null,
    val description: String? = null,
    val confidence: Double? = null,
    val imageBase64: String? = null,
    val imageBytes: String? = null,
    val imageUrl: String? = null
) {
    companion object {
        fun from(observation: ImageObservation): ImageObservationDto =
            ImageObservationDto(
                id = observation.id,
                attachmentId = observation.attachmentId,
                analysisId = observation.analysisId,
                text = observation.semanticText,
                source = observation.source,
                advisory = observation.advisory,
                order = observation.order,
                providerId = observation.providerId,
                description = observation.description,
                confidence = observation.confidence
            )
    }

    fun toDomain(attachments: List<ConversationAttachment>): ImageObservation {
        require(imageBase64 == null && imageBytes == null && imageUrl == null) {
            "raw image fields and image URLs are not accepted"
        }

        val normalizedId = id?.trim()?.also {
            require(it.isNotBlank()) { "image observation id is required" }
        } ?: throw IllegalArgumentException("image observation id is required")
        val normalizedAttachmentId = attachmentId?.trim()?.also {
            require(it.isNotBlank()) { "image observation attachmentId is required" }
        } ?: throw IllegalArgumentException("image observation attachmentId is required")
        val normalizedText = (text ?: description)?.trim()?.also {
            require(it.isNotBlank()) { "image observation text is required" }
            require(it.length <= 2_000) { "image observation text must be 2000 characters or fewer" }
        } ?: throw IllegalArgumentException("image observation text is required")
        require((source ?: ImageObservationSource.USER_PROVIDED) == ImageObservationSource.USER_PROVIDED) {
            "image observation source must be USER_PROVIDED"
        }
        require(analysisId == null && advisory == null && order == null && providerId == null) {
            "provider analysis observation fields are not accepted from clients"
        }

        val attachment = attachments.firstOrNull { it.id == normalizedAttachmentId }
            ?: throw IllegalArgumentException("image observation attachmentId must reference an attachment in the same message")
        require(attachment.type == AttachmentType.IMAGE) {
            "image observations can only reference IMAGE attachments"
        }

        return ImageObservation(
            id = normalizedId,
            attachmentId = normalizedAttachmentId,
            text = normalizedText,
            source = ImageObservationSource.USER_PROVIDED
        )
    }
}
