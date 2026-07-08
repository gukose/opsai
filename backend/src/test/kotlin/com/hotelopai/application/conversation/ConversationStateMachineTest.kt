package com.hotelopai.assistant.application

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import com.hotelopai.assistant.domain.Conversation
import com.hotelopai.assistant.domain.ConversationAttachment
import com.hotelopai.assistant.domain.ConversationState
import com.hotelopai.assistant.domain.InputType
import com.hotelopai.assistant.domain.IntentType

class ConversationStateMachineTest {
    private val stateMachine = ConversationStateMachine()

    @Test
    fun `guest request asks follow-up when room is missing then generates preview`() {
        val conversation = newConversation()

        val firstTurn = stateMachine.handleUserMessage(
            conversation = conversation,
            command = command("msg-1", "Guest needs extra towels")
        ).conversation

        assertEquals(ConversationState.WAITING_FOR_USER_ANSWER, firstTurn.state)
        assertEquals(IntentType.GUEST_REQUEST, firstTurn.intent)
        assertEquals(FieldKeys.ROOM_NUMBER, firstTurn.followUpQuestion?.fieldKey)

        val secondTurn = stateMachine.handleUserMessage(
            conversation = firstTurn,
            command = command("msg-2", "203")
        ).conversation

        assertEquals(ConversationState.WAITING_FOR_CONFIRMATION, secondTurn.state)
        assertEquals(IntentType.GUEST_REQUEST, secondTurn.taskPreview?.type)
        assertEquals("203", secondTurn.taskPreview?.roomNumber)
        assertEquals("Guest needs extra towels", secondTurn.taskPreview?.description)
    }

    @Test
    fun `maintenance message with enough information generates task preview`() {
        val result = stateMachine.handleUserMessage(
            conversation = newConversation(),
            command = command("msg-1", "Room 101 AC not working")
        ).conversation

        assertEquals(ConversationState.WAITING_FOR_CONFIRMATION, result.state)
        assertEquals(IntentType.MAINTENANCE, result.intent)
        assertEquals(IntentType.MAINTENANCE, result.taskPreview?.type)
        assertEquals("101", result.taskPreview?.roomNumber)
        assertEquals("Maintenance", result.taskPreview?.assignedTeam)
    }

    @Test
    fun `confirmation creates task creation candidate`() {
        val readyForConfirmation = stateMachine.handleUserMessage(
            conversation = newConversation(),
            command = command("msg-1", "Room 101 AC not working")
        ).conversation

        val result = stateMachine.confirmTask(
            conversation = readyForConfirmation,
            idempotencyKey = "confirm-1"
        )

        assertEquals(ConversationState.TASK_CREATED, result.conversation.state)
        assertNotNull(result.taskCreationCandidate)
        assertEquals("confirm-1", result.taskCreationCandidate?.idempotencyKey)
        assertEquals(readyForConfirmation.activeDraftId, result.taskCreationCandidate?.draftId)
    }

    @Test
    fun `reset clears active draft and returns to idle`() {
        val readyForConfirmation = stateMachine.handleUserMessage(
            conversation = newConversation(),
            command = command("msg-1", "Room 101 AC not working")
        ).conversation

        val reset = stateMachine.reset(readyForConfirmation).conversation

        assertEquals(ConversationState.IDLE, reset.state)
        assertEquals(IntentType.UNKNOWN, reset.intent)
        assertNull(reset.activeDraftId)
        assertNull(reset.taskPreview)
        assertEquals(emptyMap<String, String>(), reset.collectedFields)
    }

    @Test
    fun `image message stores attachment metadata and asks for clarification`() {
        val result = stateMachine.handleUserMessage(
            conversation = newConversation(),
            command = command(
                id = "msg-1",
                text = "",
                inputType = InputType.IMAGE,
                attachments = listOf(
                    ConversationAttachment(
                        id = "att-1",
                        originalFileName = "broken-door.jpg",
                        mimeType = "image/jpeg",
                        sizeBytes = 123_456,
                        widthPx = 1200,
                        heightPx = 900
                    )
                )
            )
        ).conversation

        assertEquals(ConversationState.WAITING_FOR_USER_ANSWER, result.state)
        assertEquals(1, result.messages.last().attachments.size)
        assertEquals("att-1", result.messages.last().attachmentIds.first())
    }

    private fun newConversation(): Conversation =
        Conversation(
            id = "conversation-1",
            hotelId = "hotel-1",
            userId = "user-1"
        )

    private fun command(
        id: String,
        text: String,
        inputType: InputType = InputType.TEXT,
        attachments: List<ConversationAttachment> = emptyList()
    ): ConversationCommand =
        ConversationCommand(
            messageId = id,
            text = text,
            inputType = inputType,
            attachments = attachments
        )
}
