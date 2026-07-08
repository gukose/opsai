package com.hotelopai.assistant.application

import com.hotelopai.assistant.domain.Conversation

class MockAiInterpreter(
    private val deterministicInterpreter: DeterministicConversationInterpreter = DeterministicConversationInterpreter()
) : AiInterpreter {
    override fun interpret(conversation: Conversation, userText: String): InterpretationResult =
        deterministicInterpreter.interpret(conversation, userText)
}
