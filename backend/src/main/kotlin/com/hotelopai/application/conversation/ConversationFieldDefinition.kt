package com.hotelopai.assistant.application

data class ConversationFieldDefinition(
    val key: String,
    val label: String,
    val required: Boolean = true
)

data class ConversationValidationIssue(
    val fieldKey: String,
    val message: String
)

fun interface ConversationValidationRule {
    fun validate(fields: Map<String, String>): List<ConversationValidationIssue>
}
