package com.hotelopai.assistant.domain

data class FollowUpQuestion(
    val id: String,
    val fieldKey: String,
    val prompt: String,
    val options: List<FollowUpOption> = emptyList()
) {
    val expectsSelection: Boolean
        get() = options.isNotEmpty()
}

data class FollowUpOption(
    val id: String,
    val label: String,
    val value: String
)
