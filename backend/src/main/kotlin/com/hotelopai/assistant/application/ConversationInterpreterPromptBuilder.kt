package com.hotelopai.assistant.application

import com.hotelopai.assistant.domain.Conversation
import com.hotelopai.assistant.domain.ConversationMessage

object ConversationInterpreterPromptBuilder {
    fun build(conversation: Conversation, userText: String): String =
        buildString {
            appendLine("Conversation context:")
            appendLine("State: ${conversation.state}")
            appendLine("Current intent: ${conversation.intent}")
            appendLine("Collected fields: ${formatMap(conversation.collectedFields)}")
            appendLine("Missing fields: ${formatMissingFields(conversation.missingFields)}")
            appendLine(
                "Pending question: ${conversation.followUpQuestion?.prompt ?: "none"}"
            )
            appendLine("Recent messages:")
            appendLine(formatMessages(conversation.messages.takeLast(6)))
            appendLine("Latest user message:")
            appendLine(userText.trim())
        }

    private fun formatMessages(messages: List<ConversationMessage>): String =
        if (messages.isEmpty()) {
            "none"
        } else {
            messages.joinToString(separator = "\n") { message ->
                "- ${message.role.name.lowercase()}: ${message.text ?: attachmentSummary(message)}${observationSummary(message)}"
            }
        }

    private fun attachmentSummary(message: ConversationMessage): String =
        if (message.attachments.isEmpty()) {
            "[empty]"
        } else {
            val names = message.attachments
                .mapNotNull { it.originalFileName }
                .take(3)
                .joinToString()
            if (names.isBlank()) {
                "[attachments:${message.attachments.size}]"
            } else {
                "[attachments:${message.attachments.size}: $names]"
            }
        }

    private fun observationSummary(message: ConversationMessage): String =
        if (message.imageObservations.isEmpty()) {
            ""
        } else {
            " [observations:${message.imageObservations.size}]"
        }

    private fun formatMap(values: Map<String, String>): String =
        if (values.isEmpty()) {
            "none"
        } else {
            values.entries.joinToString(prefix = "{", postfix = "}") { (key, value) ->
                "$key=$value"
            }
        }

    private fun formatMissingFields(missingFields: List<com.hotelopai.assistant.domain.MissingField>): String =
        if (missingFields.isEmpty()) {
            "none"
        } else {
            missingFields.joinToString { "${it.key}:${it.label}" }
        }
}
