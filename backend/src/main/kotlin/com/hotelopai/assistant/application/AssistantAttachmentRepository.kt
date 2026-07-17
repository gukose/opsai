package com.hotelopai.assistant.application

import com.hotelopai.assistant.domain.RegisteredConversationAttachment
import java.util.UUID

interface AssistantAttachmentRepository {
    fun save(attachment: RegisteredConversationAttachment): RegisteredConversationAttachment

    fun findByIdAndConversationIdAndHotelIdAndUserId(
        id: UUID,
        conversationId: String,
        hotelId: String,
        userId: String
    ): RegisteredConversationAttachment?

    fun findByRegistrationIdempotencyKey(
        conversationId: String,
        hotelId: String,
        userId: String,
        registrationIdempotencyKey: String
    ): RegisteredConversationAttachment?
}

class AssistantAttachmentIdempotencyConflictException(message: String) : RuntimeException(message)
