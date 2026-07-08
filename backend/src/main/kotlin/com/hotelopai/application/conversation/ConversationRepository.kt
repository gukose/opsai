package com.hotelopai.assistant.application

import com.hotelopai.assistant.domain.Conversation

interface ConversationRepository {
    fun save(conversation: Conversation): Conversation

    fun findById(id: String): Conversation?
}
