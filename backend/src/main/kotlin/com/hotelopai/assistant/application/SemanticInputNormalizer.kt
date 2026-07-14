package com.hotelopai.assistant.application

import com.hotelopai.assistant.domain.ConversationMessage

object SemanticInputNormalizer {
    fun normalize(message: ConversationMessage): String {
        val text = message.text?.trim()?.takeIf(String::isNotBlank)
        val transcript = message.voiceTranscriptMetadata?.transcript
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?: message.voiceTranscript
                ?.trim()
                ?.takeIf(String::isNotBlank)
        val observations = message.imageObservations
            .map { it.semanticText.trim() }
            .filter(String::isNotBlank)

        if (text != null && transcript == null && observations.isEmpty() && message.attachments.isEmpty()) {
            return text
        }

        return buildString {
            text?.let {
                appendSection("User text", it)
            }

            transcript?.let { appendSection("Client-provided transcript", it) }

            if (observations.isNotEmpty()) {
                appendSection(
                    "User-provided image observations",
                    observations.joinToString(separator = "\n") { "- $it" }
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
