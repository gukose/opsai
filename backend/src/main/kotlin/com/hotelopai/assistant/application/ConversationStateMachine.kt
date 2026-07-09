package com.hotelopai.assistant.application

import com.hotelopai.assistant.domain.Conversation
import com.hotelopai.assistant.domain.ConversationMessage
import com.hotelopai.assistant.domain.ConversationState
import com.hotelopai.assistant.domain.FollowUpQuestion
import com.hotelopai.assistant.domain.IntentType
import com.hotelopai.assistant.domain.MessageRole
import com.hotelopai.assistant.domain.MissingField
import java.time.Instant

class ConversationStateMachine(
    private val interpreter: AiInterpreter = MockAiInterpreter(),
    private val flowRegistry: ConversationFlowRegistry = ConversationFlowRegistry(),
    private val confidenceThreshold: Double = 0.65
) {
    fun handleUserMessage(
        conversation: Conversation,
        command: ConversationCommand,
        now: Instant = Instant.now()
    ): ConversationTurnResult {
        val transcript = when {
            command.voiceTranscript?.isNotBlank() == true -> command.voiceTranscript
            command.inputType == com.hotelopai.assistant.domain.InputType.VOICE -> command.text
            else -> command.text
        }

        require(
            transcript.isNullOrBlank().not() || command.attachments.isNotEmpty()
        ) {
            "User message requires text, transcript or an attachment"
        }

        val voiceTranscript = if (
            command.inputType == com.hotelopai.assistant.domain.InputType.VOICE ||
                command.inputType == com.hotelopai.assistant.domain.InputType.VOICE_TRANSCRIPT
        ) {
            transcript.takeIf { it.isNotBlank() }
        } else {
            null
        }

        val userMessage = ConversationMessage(
            id = command.messageId,
            role = MessageRole.USER,
            inputType = command.inputType,
            text = command.text.ifBlank { null },
            voiceTranscript = voiceTranscript,
            audioMetadata = command.audioMetadata,
            attachments = command.attachments,
            createdAt = now
        )

        val withUserMessage = conversation
            .addMessage(userMessage, now)
            .analyzing(now)

        val interpretation = runCatching {
            interpreter.interpret(
                AssistantInterpretationRequest.of(withUserMessage, transcript.orEmpty())
            )
        }.getOrElse { exception ->
            return clarificationTurn(withUserMessage, exception.message, now)
        }

        val draftId = withUserMessage.activeDraftId ?: newDraftId()
        val draftVersion = if (withUserMessage.activeDraftId == null) {
            1
        } else {
            withUserMessage.draftVersion + 1
        }

        val activeFlow = flowRegistry.resolve(conversation.intent)
        val interpretedFlowIntent = if (flowRegistry.supports(interpretation.intent)) {
            interpretation.intent
        } else {
            IntentType.UNKNOWN
        }
        val selectedFlow = activeFlow ?: flowRegistry.resolve(interpretedFlowIntent)

        val collectedFields = withUserMessage.collectedFields + interpretation.fields

        val interpreted = withUserMessage.withInterpretation(
            intent = selectedFlow?.intent ?: interpretedFlowIntent,
            collectedFields = collectedFields,
            draftId = draftId,
            draftVersion = draftVersion,
            now = now
        )

        val lowConfidence = interpretation.confidence < confidenceThreshold

        if (selectedFlow == null || (activeFlow == null && lowConfidence)) {
            return clarificationTurn(
                conversation = interpreted,
                aiPrompt = interpretation.followUpQuestion,
                now = now
            )
        }

        val missingFields = selectedFlow.missingRequiredFields(interpreted.collectedFields)
            .map { MissingField(it.key, it.label, it.required) }

        if (missingFields.isNotEmpty()) {
            val missingField = missingFields.first()
            val fieldDefinition = ConversationFieldDefinition(
                key = missingField.key,
                label = missingField.label,
                required = missingField.required
            )
            val question = buildFollowUpQuestion(selectedFlow, fieldDefinition, interpretation.followUpQuestion)
            val nextConversation = interpreted
                .requireFollowUp(missingFields, question, now)
                .waitForUserAnswer(now)

            return ConversationTurnResult(nextConversation)
        }

        val validationIssues = selectedFlow.validationIssues(interpreted.collectedFields)

        if (validationIssues.isNotEmpty()) {
            val firstIssue = validationIssues.first()
            val missingField = selectedFlow.fieldForKey(firstIssue.fieldKey)
                ?: ConversationFieldDefinition(firstIssue.fieldKey, firstIssue.fieldKey)
            val question = buildFollowUpQuestion(selectedFlow, missingField, interpretation.followUpQuestion)
            return ConversationTurnResult(
                interpreted
                    .requireFollowUp(
                        listOf(MissingField(missingField.key, missingField.label, missingField.required)),
                        question,
                        now
                    )
                    .waitForUserAnswer(now)
            )
        }

        val preview = selectedFlow.buildPreview(interpreted.collectedFields)
        val nextConversation = interpreted
            .readyForPreview(
                intent = selectedFlow.intent,
                draftId = draftId,
                draftVersion = draftVersion,
                preview = preview,
                now = now
            )
            .waitForConfirmation(now)

        return ConversationTurnResult(nextConversation)
    }

    fun confirmTask(
        conversation: Conversation,
        idempotencyKey: String,
        now: Instant = Instant.now()
    ): ConversationTurnResult {
        require(conversation.state == ConversationState.WAITING_FOR_CONFIRMATION) {
            "Conversation must be waiting for confirmation before task creation"
        }

        val creatingTask = conversation.creatingTask(now)
        val candidate = creatingTask.createTaskCandidate(idempotencyKey)
        val taskCreated = creatingTask.taskCreated(now)

        return ConversationTurnResult(
            conversation = taskCreated,
            taskCreationCandidate = candidate
        )
    }

    fun reset(
        conversation: Conversation,
        now: Instant = Instant.now()
    ): ConversationTurnResult =
        ConversationTurnResult(
            conversation = conversation.reset(now).idle(now)
        )

    private fun buildFollowUpQuestion(
        definition: ConversationFlowDefinition,
        missingField: ConversationFieldDefinition,
        aiPrompt: String?
    ): FollowUpQuestion {
        val question = definition.buildFollowUpQuestion(missingField, emptyMap())

        return if (aiPrompt.isNullOrBlank()) {
            question
        } else {
            question.copy(prompt = aiPrompt)
        }
    }

    private fun newDraftId(): String =
        "draft-${java.util.UUID.randomUUID()}"

    private fun clarificationTurn(
        conversation: Conversation,
        aiPrompt: String?,
        now: Instant
    ): ConversationTurnResult {
        val question = flowRegistry.buildClarificationQuestion(aiPrompt)

        return ConversationTurnResult(
            conversation
                .requireFollowUp(
                    listOf(MissingField(FieldKeys.REQUEST_TYPE, "Request type")),
                    question,
                    now
                )
                .waitForUserAnswer(now)
        )
    }
}
