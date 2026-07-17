package com.hotelopai.assistant.application

import com.hotelopai.assistant.domain.Conversation

interface ConversationRepository {
    fun save(conversation: Conversation): Conversation

    fun findById(id: String): Conversation?

    fun findByIdForUpdate(id: String): Conversation? =
        findById(id)

    fun findByIdAndHotelIdAndUserId(id: String, hotelId: String, userId: String): Conversation? =
        findById(id)?.takeIf { it.hotelId == hotelId && it.userId == userId }

    fun findByIdAndHotelIdAndUserIdForUpdate(id: String, hotelId: String, userId: String): Conversation? =
        findByIdAndHotelIdAndUserId(id, hotelId, userId)
}

class ConversationConcurrencyException(message: String) : RuntimeException(message)
