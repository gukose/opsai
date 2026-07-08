package com.hotelopai.assistant.infrastructure

import com.hotelopai.assistant.application.TaskConfirmationRecord
import com.hotelopai.assistant.application.TaskConfirmationRepository
import org.springframework.stereotype.Repository
import java.util.concurrent.ConcurrentHashMap

@Repository
class InMemoryTaskConfirmationRepository : TaskConfirmationRepository {
    private val records = ConcurrentHashMap<String, TaskConfirmationRecord>()

    override fun findByConversationIdAndIdempotencyKey(
        conversationId: String,
        idempotencyKey: String
    ): TaskConfirmationRecord? =
        records[recordKey(conversationId, idempotencyKey)]

    override fun save(record: TaskConfirmationRecord): TaskConfirmationRecord {
        records[recordKey(record.conversationId, record.idempotencyKey)] = record
        return record
    }

    private fun recordKey(conversationId: String, idempotencyKey: String): String =
        "$conversationId::$idempotencyKey"
}
