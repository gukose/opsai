package com.hotelopai.assistant.application

class ConversationNotFoundException(
    conversationId: String
) : RuntimeException("Conversation not found: $conversationId")
