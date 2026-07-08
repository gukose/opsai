package com.hotelopai.assistant.application

import com.hotelopai.assistant.domain.Conversation
import com.hotelopai.assistant.domain.FollowUpQuestion
import com.hotelopai.assistant.domain.IntentType
import com.hotelopai.assistant.domain.TaskPreview

class MaintenanceIntentDefinition : ConversationFlowDefinition {
    override val intent: IntentType = IntentType.MAINTENANCE
    override val displayName: String = "Maintenance"

    override val requiredFields: List<ConversationFieldDefinition> = listOf(
        ConversationFieldDefinition(FieldKeys.ROOM_NUMBER, "Room or location"),
        ConversationFieldDefinition(FieldKeys.DESCRIPTION, "Issue details")
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
        },
        ConversationValidationRule { fields ->
            val description = fields[FieldKeys.DESCRIPTION].orEmpty()
            if (description.isNotBlank() && description.length < 3) {
                listOf(
                    ConversationValidationIssue(
                        fieldKey = FieldKeys.DESCRIPTION,
                        message = "Issue details are too short."
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
            maintenanceKeywords.any { it in normalized } -> 0.93
            normalized.any(Char::isDigit) -> 0.75
            else -> 0.55
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
                id = "maintenance-room",
                fieldKey = missingField.key,
                prompt = "Where is the maintenance issue?"
            )
            FieldKeys.DESCRIPTION -> FollowUpQuestion(
                id = "maintenance-description",
                fieldKey = missingField.key,
                prompt = "What is the maintenance issue?"
            )
            else -> FollowUpQuestion(
                id = "maintenance-${missingField.key}",
                fieldKey = missingField.key,
                prompt = "Please provide ${missingField.label.lowercase()}."
            )
        }

    override fun buildPreview(fields: Map<String, String>): TaskPreview =
        TaskPreview(
            type = intent,
            title = "Maintenance",
            description = fields.getValue(FieldKeys.DESCRIPTION),
            roomNumber = fields.getValue(FieldKeys.ROOM_NUMBER),
            assignedTeam = "Maintenance",
            priority = "Medium",
            slaMinutes = 60,
            requiresPmsUpdate = true
        )

    private companion object {
        val roomPattern = Regex("""\b(?:room\s*)?(\d{2,5})\b""", RegexOption.IGNORE_CASE)

        val maintenanceKeywords = setOf(
            "ac",
            "air condition",
            "broken",
            "leak",
            "maintenance",
            "not working",
            "repair",
            "sink",
            "toilet"
        )
    }
}
