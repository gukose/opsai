package com.hotelopai.assistant.application

import com.hotelopai.assistant.domain.ConversationMessage
import com.hotelopai.assistant.domain.ImageObservationSource

object SemanticInputNormalizer {
    fun normalize(message: ConversationMessage): String {
        val text = message.text?.trim()?.takeIf(String::isNotBlank)
        val transcript = message.voiceTranscriptMetadata?.transcript
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?: message.voiceTranscript
                ?.trim()
                ?.takeIf(String::isNotBlank)
        val userObservations = message.imageObservations
            .filter { it.source == ImageObservationSource.USER_PROVIDED }
            .map { it.semanticText.trim() }
            .filter(String::isNotBlank)
        val visionObservations = message.imageObservations
            .filter { it.source == ImageObservationSource.VISION_ANALYSIS }
            .sortedWith(compareBy(nullsLast()) { it.order })
            .map { it.semanticText.trim() }
            .filter(String::isNotBlank)

        if (text != null && transcript == null && userObservations.isEmpty() && visionObservations.isEmpty() && message.attachments.isEmpty()) {
            return text
        }

        return buildString {
            text?.let {
                appendSection("User text", it)
            }

            transcript?.let { appendSection("Client-provided transcript", it) }

            if (userObservations.isNotEmpty()) {
                appendSection(
                    "User-provided image observations",
                    userObservations.joinToString(separator = "\n") { "- $it" }
                )
            }

            if (visionObservations.isNotEmpty()) {
                appendSection(
                    "Vision analysis - advisory provider observations",
                    visionObservations.joinToString(separator = "\n") { "- $it" }
                )
            }

            if (message.attachments.isNotEmpty()) {
                val summary = message.attachments.joinToString(separator = "\n") { attachment ->
                    val name = attachment.originalFileName?.trim()?.takeIf(String::isNotBlank) ?: attachment.id
                    "- ${attachment.type} attachment metadata: filename=$name, mimeType=${attachment.mimeType ?: "unknown"}"
                }
                appendSection("Attachment metadata only", summary)
            }
        }.trim()
    }

    private fun StringBuilder.appendSection(title: String, body: String) {
        if (isNotEmpty()) {
            appendLine()
        }
        appendLine("$title:")
        append(body)
        appendLine()
    }
}
