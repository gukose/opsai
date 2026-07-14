package com.hotelopai.assistant.domain

import java.time.Instant

data class ConversationMessage(
    val id: String,
    val role: MessageRole,
    val inputType: InputType,
    val text: String?,
    val voiceTranscript: String? = null,
    val voiceTranscriptMetadata: VoiceTranscriptMetadata? = null,
    val audioMetadata: AudioMetadata? = null,
    val attachments: List<ConversationAttachment> = emptyList(),
    val imageObservations: List<ImageObservation> = emptyList(),
    val createdAt: Instant = Instant.now()
) {
    val attachmentIds: List<String>
        get() = attachments.map { it.id }

    init {
        require(
            text?.isNotBlank() == true ||
                voiceTranscript?.isNotBlank() == true ||
                voiceTranscriptMetadata?.transcript?.isNotBlank() == true ||
                attachments.isNotEmpty() ||
                imageObservations.any { it.semanticText.isNotBlank() }
        ) {
            "ConversationMessage requires text, transcript, observation or at least one attachment"
        }
    }
}

data class VoiceTranscriptMetadata(
    val transcript: String,
    val languageCode: String? = null,
    val durationMs: Long? = null,
    val source: VoiceTranscriptSource = VoiceTranscriptSource.CLIENT_TRANSCRIPT
)

enum class VoiceTranscriptSource {
    CLIENT_TRANSCRIPT
}

data class AudioMetadata(
    val originalFileName: String? = null,
    val mimeType: String? = null,
    val durationMs: Long? = null,
    val sizeBytes: Long? = null
)
