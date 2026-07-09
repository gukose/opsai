package com.hotelopai.assistant.infrastructure

import com.hotelopai.assistant.application.ConversationRepository
import com.hotelopai.assistant.domain.Conversation
import java.util.concurrent.ConcurrentHashMap

class InMemoryConversationRepository : ConversationRepository {
    private val conversations = ConcurrentHashMap<String, Conversation>()

    override fun save(conversation: Conversation): Conversation {
        conversations[conversation.id] = conversation
        return conversation
    }

    override fun findById(id: String): Conversation? =
        conversations[id]
}
