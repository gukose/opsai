package com.hotelopai.assistant.domain

data class TaskCreationCandidate(
    val conversationId: String,
    val draftId: String,
    val draftVersion: Int,
    val preview: TaskPreview,
    val idempotencyKey: String
) {
    init {
        require(draftVersion > 0) { "draftVersion must be positive" }
        require(idempotencyKey.isNotBlank()) { "idempotencyKey must not be blank" }
    }
}
