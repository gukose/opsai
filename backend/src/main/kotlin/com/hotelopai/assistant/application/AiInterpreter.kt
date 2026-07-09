package com.hotelopai.assistant.application

import com.hotelopai.assistant.domain.Conversation

interface AiInterpreter {
    fun interpret(request: AssistantInterpretationRequest): InterpretationResult

    @Deprecated("Use interpret(request) to preserve prompt/schema metadata.")
    fun interpret(conversation: Conversation, userText: String): InterpretationResult =
        interpret(AssistantInterpretationRequest.of(conversation, userText))
}
