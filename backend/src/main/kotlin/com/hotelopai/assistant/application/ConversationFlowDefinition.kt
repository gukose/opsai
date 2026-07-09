package com.hotelopai.assistant.application

import com.hotelopai.assistant.domain.Conversation
import com.hotelopai.assistant.domain.FollowUpQuestion
import com.hotelopai.assistant.domain.IntentType
import com.hotelopai.assistant.domain.TaskPreview

interface ConversationFlowDefinition {
    val intent: IntentType
    val displayName: String
    val requiredFields: List<ConversationFieldDefinition>
    val optionalFields: List<ConversationFieldDefinition>
    val validationRules: List<ConversationValidationRule>

    fun matchScore(conversation: Conversation, userText: String): Double

    fun extractFields(conversation: Conversation, userText: String): Map<String, String> = emptyMap()

    fun buildFollowUpQuestion(
        missingField: ConversationFieldDefinition,
        fields: Map<String, String>
    ): FollowUpQuestion

    fun buildPreview(fields: Map<String, String>): TaskPreview

    fun allFields(): List<ConversationFieldDefinition> = requiredFields + optionalFields

    fun missingRequiredFields(fields: Map<String, String>): List<ConversationFieldDefinition> =
        requiredFields.filter { field -> fields[field.key].isNullOrBlank() }

    fun validationIssues(fields: Map<String, String>): List<ConversationValidationIssue> =
        validationRules.flatMap { rule -> rule.validate(fields) }

    fun fieldForKey(key: String): ConversationFieldDefinition? =
        allFields().firstOrNull { it.key == key }
}
