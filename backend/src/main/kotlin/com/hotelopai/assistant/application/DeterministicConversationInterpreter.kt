package com.hotelopai.assistant.application

import com.hotelopai.assistant.domain.Conversation
import com.hotelopai.assistant.domain.IntentType

class DeterministicConversationInterpreter(
    private val flowRegistry: ConversationFlowRegistry = ConversationFlowRegistry()
) {

    fun interpret(conversation: Conversation, userText: String): InterpretationResult {
        val flow = resolveFlow(conversation, userText)
        val extractedFields = flow?.extractFields(conversation, userText).orEmpty()

        return InterpretationResult(
            intent = flow?.intent ?: IntentType.UNKNOWN,
            fields = conversation.collectedFields + extractedFields,
            confidence = flow?.matchScore(conversation, userText) ?: 0.0
        )
    }

    private fun resolveFlow(conversation: Conversation, userText: String): ConversationFlowDefinition? {
        if (conversation.intent != IntentType.UNKNOWN) {
            flowRegistry.resolve(conversation.intent)?.let { return it }
        }

        return flowRegistry.allDefinitions()
            .maxByOrNull { it.matchScore(conversation, userText) }
    }
}
