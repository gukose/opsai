package com.hotelopai.assistant.application

import com.hotelopai.assistant.domain.FollowUpOption
import com.hotelopai.assistant.domain.FollowUpQuestion
import com.hotelopai.assistant.domain.IntentType

class ConversationFlowRegistry(
    definitions: List<ConversationFlowDefinition> = listOf(
        GuestRequestIntentDefinition(),
        MaintenanceIntentDefinition()
    )
) {
    private val definitionsByIntent = definitions.associateBy { it.intent }

    fun resolve(intent: IntentType): ConversationFlowDefinition? =
        definitionsByIntent[intent]

    fun supports(intent: IntentType): Boolean =
        resolve(intent) != null

    fun allDefinitions(): List<ConversationFlowDefinition> =
        definitionsByIntent.values.toList()

    fun buildClarificationQuestion(aiPrompt: String? = null): FollowUpQuestion {
        val prompt = aiPrompt?.takeIf { it.isNotBlank() }
            ?: "Which type of task is this?"

        return FollowUpQuestion(
            id = "clarify-flow",
            fieldKey = FieldKeys.REQUEST_TYPE,
            prompt = prompt,
            options = allDefinitions().map { definition ->
                FollowUpOption(
                    id = "flow-${definition.intent.name.lowercase()}",
                    label = definition.displayName,
                    value = definition.intent.name
                )
            }
        )
    }
}
