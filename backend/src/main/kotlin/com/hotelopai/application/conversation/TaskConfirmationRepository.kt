package com.hotelopai.assistant.application

import com.hotelopai.assistant.domain.TaskPreview
import java.time.Instant

interface TaskConfirmationRepository {
    fun findByConversationIdAndIdempotencyKey(
        conversationId: String,
        idempotencyKey: String
    ): TaskConfirmationRecord?

    fun save(record: TaskConfirmationRecord): TaskConfirmationRecord
}

data class TaskConfirmationRecord(
    val conversationId: String,
    val idempotencyKey: String,
    val createdTaskId: String,
    val draftId: String,
    val draftVersion: Int,
    val preview: TaskPreview,
    val createdAt: Instant = Instant.now()
) {
    init {
        require(conversationId.isNotBlank()) { "conversationId must not be blank" }
        require(idempotencyKey.isNotBlank()) { "idempotencyKey must not be blank" }
        require(createdTaskId.isNotBlank()) { "createdTaskId must not be blank" }
        require(draftId.isNotBlank()) { "draftId must not be blank" }
        require(draftVersion > 0) { "draftVersion must be positive" }
    }
}
