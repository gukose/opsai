package com.hotelopai.assistant.application

import com.hotelopai.assistant.domain.Conversation
import com.hotelopai.assistant.domain.IntentType

class DeterministicConversationInterpreter(
    private val flowRegistry: ConversationFlowRegistry = ConversationFlowRegistry(),
    private val validator: StructuredInterpretationValidator = StructuredInterpretationValidator()
) : AiInterpreter {

    override fun interpret(request: AssistantInterpretationRequest): InterpretationResult {
        val conversation = request.conversation
        val userText = request.userText
        val flow = resolveFlow(conversation, userText)
        val extractedFields = flow?.extractFields(conversation, userText).orEmpty()
        val intent = flow?.intent ?: IntentType.UNKNOWN
        val confidence = flow?.matchScore(conversation, userText) ?: 0.0
        val mergedFields = conversation.collectedFields + extractedFields
        val missingDefinitions = flow?.missingRequiredFields(mergedFields).orEmpty()
        val preview = if (flow != null && missingDefinitions.isEmpty() && flow.validationIssues(mergedFields).isEmpty()) {
            flow.buildPreview(mergedFields)
        } else {
            null
        }

        val payload = StructuredInterpretationPayload(
            promptVersion = request.promptVersion,
            schemaVersion = request.schemaVersion,
            intentCode = intent.name,
            confidence = confidence,
            detectedLanguage = detectLanguage(userText, conversation),
            extractedFields = extractedFields,
            missingRequiredFields = missingDefinitions.map { it.key },
            followUpQuestion = missingDefinitions.firstOrNull()?.let { missingField ->
                flow?.buildFollowUpQuestion(missingField, mergedFields)?.prompt
            },
            assistantMessage = if (confidence < 0.65 || flow == null) {
                flowRegistry.buildClarificationQuestion(null).prompt
            } else {
                null
            },
            taskPreviewCandidate = preview?.toStructuredPayload(intent.name),
            prioritySuggestion = preview?.priority,
            slaPolicyKey = if (preview != null && flow != null) flow.intent.name.lowercase() else null,
            requiredSkillCode = missingDefinitions.firstOrNull()?.key,
            departmentCode = preview?.assignedTeam?.uppercase()?.replace(' ', '_'),
            pmsUpdateType = if (preview?.requiresPmsUpdate == true && flow != null) flow.intent.name else null,
            requiresPmsUpdate = preview?.requiresPmsUpdate ?: false,
            providerName = "deterministic",
            providerModel = "rule-based"
        )

        return validator.validate(payload)
    }

    private fun resolveFlow(conversation: Conversation, userText: String): ConversationFlowDefinition? {
        if (conversation.intent != IntentType.UNKNOWN) {
            flowRegistry.resolve(conversation.intent)?.let { return it }
        }

        return flowRegistry.allDefinitions()
            .maxByOrNull { it.matchScore(conversation, userText) }
    }

    private fun detectLanguage(userText: String, conversation: Conversation): String? {
        val source = buildString {
            append(userText)
            append(' ')
            append(conversation.messages.takeLast(3).joinToString(" ") { it.text.orEmpty() })
        }.lowercase()

        return when {
            source.contains("çalışm") || source.contains("klima") -> "tr"
            source.contains("funktioniert nicht") || source.contains("zimmer") -> "de"
            else -> null
        }
    }

    private fun Conversation.firstMissingFieldDefinition(flow: ConversationFlowDefinition?): ConversationFieldDefinition =
        flow?.missingRequiredFields(collectedFields)?.firstOrNull()
            ?: ConversationFieldDefinition(FieldKeys.REQUEST_TYPE, "Request type")
}
