package com.hotelopai.assistant.infrastructure

import com.hotelopai.assistant.application.ConversationRepository
import com.hotelopai.assistant.application.ConversationConcurrencyException
import com.hotelopai.assistant.domain.Conversation
import java.util.concurrent.ConcurrentHashMap

class InMemoryConversationRepository : ConversationRepository {
    private val conversations = ConcurrentHashMap<String, Conversation>()

    override fun save(conversation: Conversation): Conversation {
        val existing = conversations[conversation.id]
        if (existing != null && existing.rowVersion != conversation.rowVersion) {
            throw ConversationConcurrencyException("Conversation was modified by another request")
        }
        val saved = conversation.copy(rowVersion = if (existing == null) 0 else conversation.rowVersion + 1)
        conversations[conversation.id] = saved
        return saved
    }

    override fun findById(id: String): Conversation? =
        conversations[id]
}
