package com.hotelopai.assistant.application

import com.hotelopai.assistant.domain.Conversation
import com.hotelopai.assistant.domain.FollowUpQuestion
import com.hotelopai.assistant.domain.IntentType
import com.hotelopai.assistant.domain.TaskPreview

class GuestRequestIntentDefinition : ConversationFlowDefinition {
    override val intent: IntentType = IntentType.GUEST_REQUEST
    override val displayName: String = "Guest Request"

    override val requiredFields: List<ConversationFieldDefinition> = listOf(
        ConversationFieldDefinition(FieldKeys.ROOM_NUMBER, "Room"),
        ConversationFieldDefinition(FieldKeys.DESCRIPTION, "Request details")
    )

    override val optionalFields: List<ConversationFieldDefinition> = emptyList()

    override val validationRules: List<ConversationValidationRule> = listOf(
        ConversationValidationRule { fields ->
            val roomNumber = fields[FieldKeys.ROOM_NUMBER].orEmpty()
            if (roomNumber.isNotBlank() && roomNumber.any { !it.isDigit() }) {
                listOf(
                    ConversationValidationIssue(
                        fieldKey = FieldKeys.ROOM_NUMBER,
                        message = "Room number must contain only digits."
                    )
                )
            } else {
                emptyList()
            }
        }
    )

    override fun matchScore(conversation: Conversation, userText: String): Double {
        val normalized = userText.lowercase()
        return when {
            guestRequestKeywords.any { it in normalized } -> 0.92
            normalized.any(Char::isDigit) -> 0.72
            else -> 0.56
        }
    }

    override fun extractFields(conversation: Conversation, userText: String): Map<String, String> {
        val fields = mutableMapOf<String, String>()
        val answerField = conversation.followUpQuestion?.fieldKey

        if (answerField != null) {
            fields[answerField] = userText.trim()
        }

        roomPattern.find(userText)?.groupValues?.getOrNull(1)?.let { roomNumber ->
            fields[FieldKeys.ROOM_NUMBER] = roomNumber
        }

        if (answerField == null || answerField == FieldKeys.DESCRIPTION) {
            fields[FieldKeys.DESCRIPTION] = userText.trim()
        }

        return fields.filterValues { it.isNotBlank() }
    }

    override fun buildFollowUpQuestion(
        missingField: ConversationFieldDefinition,
        fields: Map<String, String>
    ): FollowUpQuestion =
        when (missingField.key) {
            FieldKeys.ROOM_NUMBER -> FollowUpQuestion(
                id = "guest-request-room",
                fieldKey = missingField.key,
                prompt = "Which room is this request for?"
            )
            FieldKeys.DESCRIPTION -> FollowUpQuestion(
                id = "guest-request-description",
                fieldKey = missingField.key,
                prompt = "What does the guest need?"
            )
            else -> FollowUpQuestion(
                id = "guest-request-${missingField.key}",
                fieldKey = missingField.key,
                prompt = "Please provide ${missingField.label.lowercase()}."
            )
        }

    override fun buildPreview(fields: Map<String, String>): TaskPreview =
        TaskPreview(
            type = intent,
            title = "Guest Request",
            description = fields.getValue(FieldKeys.DESCRIPTION),
            roomNumber = fields.getValue(FieldKeys.ROOM_NUMBER),
            assignedTeam = "Guest Services",
            priority = "Medium",
            slaMinutes = 30,
            requiresPmsUpdate = false
        )

    private companion object {
        val roomPattern = Regex("""\b(?:room\s*)?(\d{2,5})\b""", RegexOption.IGNORE_CASE)

        val guestRequestKeywords = setOf(
            "guest",
            "request",
            "towel",
            "blanket",
            "pillow",
            "water",
            "deliver",
            "need",
            "needs"
        )
    }
}
