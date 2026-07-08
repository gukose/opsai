package com.hotelopai.assistant.application

import com.hotelopai.assistant.domain.Conversation

interface AiInterpreter {
    fun interpret(conversation: Conversation, userText: String): InterpretationResult
}
