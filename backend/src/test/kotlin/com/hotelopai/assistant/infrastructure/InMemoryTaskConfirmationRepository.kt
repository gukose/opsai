package com.hotelopai.assistant.infrastructure

import com.hotelopai.assistant.application.TaskConfirmationRecord
import com.hotelopai.assistant.application.TaskConfirmationRepository
import java.util.concurrent.ConcurrentHashMap

class InMemoryTaskConfirmationRepository : TaskConfirmationRepository {
    private val records = ConcurrentHashMap<String, TaskConfirmationRecord>()

    override fun findByConversationIdAndIdempotencyKey(
        conversationId: String,
        idempotencyKey: String
    ): TaskConfirmationRecord? =
        records[recordKey(conversationId, idempotencyKey)]

    override fun findByConversationIdAndDraftIdentity(
        conversationId: String,
        draftId: String,
        draftVersion: Int
    ): TaskConfirmationRecord? =
        records.values.firstOrNull {
            it.conversationId == conversationId &&
                it.draftId == draftId &&
                it.draftVersion == draftVersion
        }

    override fun save(record: TaskConfirmationRecord): TaskConfirmationRecord {
        require(findByConversationIdAndDraftIdentity(record.conversationId, record.draftId, record.draftVersion) == null) {
            "confirmation draft already exists"
        }
        records[recordKey(record.conversationId, record.idempotencyKey)] = record
        return record
    }

    private fun recordKey(conversationId: String, idempotencyKey: String): String =
        "$conversationId::$idempotencyKey"
}
