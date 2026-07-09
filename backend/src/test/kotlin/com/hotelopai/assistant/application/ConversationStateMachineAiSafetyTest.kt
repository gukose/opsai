package com.hotelopai.assistant.application

import com.hotelopai.assistant.domain.Conversation
import com.hotelopai.assistant.domain.ConversationState
import com.hotelopai.assistant.domain.IntentType
import com.hotelopai.assistant.domain.InputType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ConversationStateMachineAiSafetyTest {
    @Test
    fun `low confidence output produces clarification and cannot create task`() {
        val stateMachine = ConversationStateMachine(
            interpreter = object : AiInterpreter {
                override fun interpret(request: AssistantInterpretationRequest): InterpretationResult =
                    InterpretationResult(
                        intent = IntentType.MAINTENANCE,
                        fields = mapOf(
                            "roomNumber" to "101",
                            "description" to "AC not working"
                        ),
                        confidence = 0.2,
                        followUpQuestion = null,
                        missingFields = emptyList(),
                        promptVersion = AssistantAiVersions.PROMPT_VERSION,
                        schemaVersion = AssistantAiVersions.SCHEMA_VERSION
                    )
            }
        )

        val result = stateMachine.handleUserMessage(
            conversation = conversation(),
            command = messageCommand()
        )

        assertEquals(ConversationState.WAITING_FOR_USER_ANSWER, result.conversation.state)
        assertEquals(1, result.conversation.messages.size)
        assertNull(result.taskCreationCandidate)
        assertNull(result.createdTaskId)
        assertNull(result.conversation.taskPreview)
    }

    @Test
    fun `malformed structured output is rejected safely and cannot create task`() {
        val stateMachine = ConversationStateMachine(
            interpreter = object : AiInterpreter {
                override fun interpret(request: AssistantInterpretationRequest): InterpretationResult {
                    throw InvalidStructuredInterpretationException("bad payload")
                }
            }
        )

        val result = stateMachine.handleUserMessage(
            conversation = conversation(),
            command = messageCommand()
        )

        assertEquals(ConversationState.WAITING_FOR_USER_ANSWER, result.conversation.state)
        assertEquals(1, result.conversation.messages.size)
        assertNull(result.taskCreationCandidate)
        assertNull(result.createdTaskId)
    }

    @Test
    fun `deterministic assistant flow continues to work`() {
        val stateMachine = ConversationStateMachine()

        val result = stateMachine.handleUserMessage(
            conversation = conversation(),
            command = messageCommand()
        )

        assertEquals(ConversationState.WAITING_FOR_CONFIRMATION, result.conversation.state)
        assertEquals(IntentType.MAINTENANCE, result.conversation.intent)
        assertNull(result.createdTaskId)
    }

    private fun conversation(): Conversation =
        Conversation(
            id = "conversation-1",
            hotelId = "hotel-123",
            userId = "user-456",
            state = ConversationState.IDLE
        )

    private fun messageCommand(): ConversationCommand =
        ConversationCommand(
            messageId = "message-1",
            text = "Room 101 AC not working",
            inputType = InputType.TEXT
        )
}
