package com.hotelopai.assistant.application

import com.hotelopai.assistant.domain.Conversation
import com.hotelopai.assistant.domain.FollowUpQuestion
import com.hotelopai.assistant.domain.IntentType
import com.hotelopai.assistant.domain.TaskPreview

/**
 * Safe fallback for operational requests whose specific type cannot be
 * determined confidently. Assignment remains deliberately unresolved so a
 * supervisor can route the task instead of the system guessing.
 */
class GeneralOperationalIntentDefinition : ConversationFlowDefinition {
    override val intent: IntentType = IntentType.GENERAL_OPERATIONAL_NOTE
    override val displayName: String = "General operations"

    override val requiredFields: List<ConversationFieldDefinition> = listOf(
        ConversationFieldDefinition(FieldKeys.ROOM_NUMBER, "Room or location"),
        ConversationFieldDefinition(FieldKeys.DESCRIPTION, "Task details")
    )

    override val optionalFields: List<ConversationFieldDefinition> = emptyList()
    override val validationRules: List<ConversationValidationRule> = listOf(
        ConversationValidationRule { fields ->
            val description = fields[FieldKeys.DESCRIPTION].orEmpty()
            if (description.isNotBlank() && description.length < 3) {
                listOf(ConversationValidationIssue(FieldKeys.DESCRIPTION, "Task details are too short."))
            } else emptyList()
        }
    )

    override fun matchScore(conversation: Conversation, userText: String): Double = 0.0

    override fun extractFields(conversation: Conversation, userText: String): Map<String, String> {
        val fields = mutableMapOf<String, String>()
        val answerField = conversation.followUpQuestion?.fieldKey
        if (answerField != null) fields[answerField] = userText.trim()
        RoomNumberExtractor.extract(userText)?.let { fields[FieldKeys.ROOM_NUMBER] = it }
        if (answerField == null || answerField == FieldKeys.DESCRIPTION) {
            fields[FieldKeys.DESCRIPTION] = userText.trim()
        }
        return fields.filterValues { it.isNotBlank() }
    }

    override fun buildFollowUpQuestion(
        missingField: ConversationFieldDefinition,
        fields: Map<String, String>
    ): FollowUpQuestion = when (missingField.key) {
        FieldKeys.ROOM_NUMBER -> FollowUpQuestion(
            id = "general-operation-room",
            fieldKey = missingField.key,
            prompt = "Which room or location is this for? You can enter a room number or text such as Lobby."
        )
        FieldKeys.DESCRIPTION -> FollowUpQuestion(
            id = "general-operation-description",
            fieldKey = missingField.key,
            prompt = "What needs to be done?"
        )
        else -> FollowUpQuestion(
            id = "general-operation-${missingField.key}",
            fieldKey = missingField.key,
            prompt = "Please provide ${missingField.label.lowercase()}."
        )
    }

    override fun buildPreview(fields: Map<String, String>): TaskPreview = TaskPreview(
        type = intent,
        title = "General operations task",
        description = fields.getValue(FieldKeys.DESCRIPTION),
        roomNumber = fields.getValue(FieldKeys.ROOM_NUMBER),
        assignedTeam = "Operations",
        priority = "Medium",
        slaMinutes = 60,
        requiresPmsUpdate = false
    )
}
