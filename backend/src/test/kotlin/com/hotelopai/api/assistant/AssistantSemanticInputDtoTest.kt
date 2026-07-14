package com.hotelopai.assistant.api

import com.hotelopai.assistant.domain.AttachmentStorageStatus
import com.hotelopai.assistant.domain.AttachmentType
import com.hotelopai.assistant.domain.ImageObservationSource
import com.hotelopai.assistant.domain.VoiceTranscriptSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class AssistantSemanticInputDtoTest {
    @Test
    fun `voice transcript dto trims and validates client transcript metadata`() {
        val metadata = VoiceTranscriptDto(
            transcript = " Room 502 sink is leaking ",
            languageCode = "en-US",
            durationMs = 4200,
            source = VoiceTranscriptSource.CLIENT_TRANSCRIPT
        ).toDomain()

        assertEquals("Room 502 sink is leaking", metadata.transcript)
        assertEquals("en-US", metadata.languageCode)
        assertEquals(4200, metadata.durationMs)
        assertEquals(VoiceTranscriptSource.CLIENT_TRANSCRIPT, metadata.source)
    }

    @Test
    fun `voice transcript dto rejects raw audio fields`() {
        assertThrows(IllegalArgumentException::class.java) {
            VoiceTranscriptDto(
                transcript = "Room 502 sink is leaking",
                source = VoiceTranscriptSource.CLIENT_TRANSCRIPT,
                audioUrl = "file:///private/audio.m4a"
            ).toDomain()
        }
    }

    @Test
    fun `image observation dto requires a same message image attachment`() {
        val attachment = MessageAttachmentDto(
            id = " att-1 ",
            type = AttachmentType.IMAGE,
            originalFileName = "sink.png",
            mimeType = "image/png",
            sizeBytes = 1234,
            widthPx = 100,
            heightPx = 100,
            storageStatus = AttachmentStorageStatus.LOCAL_METADATA_ONLY
        ).toDomain()

        val observation = ImageObservationDto(
            id = " obs-1 ",
            attachmentId = " att-1 ",
            text = " Water visible under the sink ",
            source = ImageObservationSource.USER_PROVIDED
        ).toDomain(listOf(attachment))

        assertEquals("obs-1", observation.id)
        assertEquals("att-1", observation.attachmentId)
        assertEquals("Water visible under the sink", observation.text)
        assertEquals(ImageObservationSource.USER_PROVIDED, observation.source)
    }

    @Test
    fun `image observation dto rejects non image attachment and raw image fields`() {
        val document = MessageAttachmentDto(
            id = "doc-1",
            type = AttachmentType.DOCUMENT,
            originalFileName = "note.txt",
            mimeType = "text/plain",
            sizeBytes = 12,
            widthPx = null,
            heightPx = null,
            storageStatus = AttachmentStorageStatus.LOCAL_METADATA_ONLY
        ).toDomain()

        assertThrows(IllegalArgumentException::class.java) {
            ImageObservationDto(
                id = "obs-1",
                attachmentId = "doc-1",
                text = "Visible issue",
                source = ImageObservationSource.USER_PROVIDED
            ).toDomain(listOf(document))
        }

        assertThrows(IllegalArgumentException::class.java) {
            ImageObservationDto(
                id = "obs-2",
                attachmentId = "doc-1",
                text = "Visible issue",
                source = ImageObservationSource.USER_PROVIDED,
                imageBase64 = "AAAA"
            ).toDomain(listOf(document))
        }
    }
}
