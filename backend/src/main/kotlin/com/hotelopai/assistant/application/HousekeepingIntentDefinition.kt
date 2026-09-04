package com.hotelopai.assistant.application

import com.hotelopai.assistant.domain.Conversation
import com.hotelopai.assistant.domain.FollowUpQuestion
import com.hotelopai.assistant.domain.IntentType
import com.hotelopai.assistant.domain.TaskPreview

/** Canonical assistant flow for room-cleaning reports. */
class HousekeepingIntentDefinition : ConversationFlowDefinition {
    override val intent: IntentType = IntentType.HOUSEKEEPING
    override val displayName: String = "Housekeeping"
    override val requiredFields = listOf(
        ConversationFieldDefinition(FieldKeys.ROOM_NUMBER, "Room or location"),
        ConversationFieldDefinition(FieldKeys.DESCRIPTION, "Cleaning details")
    )
    override val optionalFields = emptyList<ConversationFieldDefinition>()
    override val validationRules = listOf(ConversationValidationRule { fields ->
        if (fields[FieldKeys.DESCRIPTION].orEmpty().trim().length < 3) {
            listOf(ConversationValidationIssue(FieldKeys.DESCRIPTION, "Cleaning details are too short."))
        } else emptyList()
    })

    override fun matchScore(conversation: Conversation, userText: String): Double {
        val normalized = userText.lowercase()
        return when {
            housekeepingKeywords.any { it in normalized } -> 0.94
            else -> 0.0
        }
    }

    override fun extractFields(conversation: Conversation, userText: String): Map<String, String> {
        val fields = mutableMapOf<String, String>()
        conversation.followUpQuestion?.fieldKey?.let { fields[it] = userText.trim() }
        RoomNumberExtractor.extract(userText)?.let { fields[FieldKeys.ROOM_NUMBER] = it }
        if (conversation.followUpQuestion == null || conversation.followUpQuestion.fieldKey == FieldKeys.DESCRIPTION) {
            fields[FieldKeys.DESCRIPTION] = userText.trim()
        }
        return fields.filterValues(String::isNotBlank)
    }

    override fun buildFollowUpQuestion(missingField: ConversationFieldDefinition, fields: Map<String, String>) =
        when (missingField.key) {
            FieldKeys.ROOM_NUMBER -> FollowUpQuestion("housekeeping-room", missingField.key, "Which room needs cleaning?")
            FieldKeys.DESCRIPTION -> FollowUpQuestion("housekeeping-description", missingField.key, "What cleaning is needed?")
            else -> FollowUpQuestion("housekeeping-${missingField.key}", missingField.key, "Please provide ${missingField.label.lowercase()}.")
        }

    override fun buildPreview(fields: Map<String, String>) = TaskPreview(
        type = intent,
        title = "Room cleaning",
        description = fields.getValue(FieldKeys.DESCRIPTION),
        roomNumber = fields.getValue(FieldKeys.ROOM_NUMBER),
        assignedTeam = "Housekeeping",
        priority = "Medium",
        slaMinutes = 60,
        requiresPmsUpdate = false
    )

    private companion object {
        val housekeepingKeywords = setOf("clean", "cleaning", "housekeeping", "dirty", "messy", "temizlik", "temizle", "pis", "kirli")
    }
}
