package com.hotelopai.assistant.application

class MockAiInterpreter(
    private val deterministicInterpreter: DeterministicConversationInterpreter = DeterministicConversationInterpreter()
) : AiInterpreter {
    override fun interpret(request: AssistantInterpretationRequest): InterpretationResult =
        deterministicInterpreter.interpret(request)
}
