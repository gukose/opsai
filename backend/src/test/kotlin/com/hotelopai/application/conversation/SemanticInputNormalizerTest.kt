package com.hotelopai.assistant.application

import com.hotelopai.assistant.domain.AttachmentType
import com.hotelopai.assistant.domain.ConversationAttachment
import com.hotelopai.assistant.domain.ConversationMessage
import com.hotelopai.assistant.domain.ImageObservation
import com.hotelopai.assistant.domain.InputType
import com.hotelopai.assistant.domain.MessageRole
import com.hotelopai.assistant.domain.VoiceTranscriptMetadata
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SemanticInputNormalizerTest {
    @Test
    fun `text only input remains plain text`() {
        val message = message(text = "Room 502 sink is leaking")

        assertEquals("Room 502 sink is leaking", SemanticInputNormalizer.normalize(message))
    }

    @Test
    fun `semantic input includes transcript and user observation but excludes local media references`() {
        val message = message(
            text = "Please inspect this",
            voiceTranscriptMetadata = VoiceTranscriptMetadata(
                transcript = "Room 502 sink is leaking",
                languageCode = "en",
                durationMs = 4200
            ),
            attachments = listOf(
                ConversationAttachment(
                    id = "att-1",
                    type = AttachmentType.IMAGE,
                    originalFileName = "sink.png",
                    mimeType = "image/png",
                    sizeBytes = 1234,
                    localReference = "file:///private/device/sink.png"
                )
            ),
            imageObservations = listOf(
                ImageObservation(
                    id = "obs-1",
                    attachmentId = "att-1",
                    text = "Water visible under the sink"
                )
            )
        )

        val normalized = SemanticInputNormalizer.normalize(message)

        assertTrue(normalized.contains("Client-provided transcript:"))
        assertTrue(normalized.contains("Room 502 sink is leaking"))
        assertTrue(normalized.contains("User-provided image observations:"))
        assertTrue(normalized.contains("Water visible under the sink"))
        assertTrue(normalized.contains("Attachment metadata only:"))
        assertFalse(normalized.contains("file:///private/device"))
        assertFalse(normalized.contains("localReference"))
        assertFalse(normalized.contains("base64"))
    }

    private fun message(
        text: String? = null,
        voiceTranscriptMetadata: VoiceTranscriptMetadata? = null,
        attachments: List<ConversationAttachment> = emptyList(),
        imageObservations: List<ImageObservation> = emptyList()
    ): ConversationMessage =
        ConversationMessage(
            id = "msg-1",
            role = MessageRole.USER,
            inputType = InputType.MIXED,
            text = text,
            voiceTranscriptMetadata = voiceTranscriptMetadata,
            attachments = attachments,
            imageObservations = imageObservations
        )
}
