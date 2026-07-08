package com.hotelopai.assistant.application

import com.hotelopai.assistant.domain.Conversation
import com.hotelopai.assistant.domain.ConversationAttachment
import com.hotelopai.assistant.domain.AudioMetadata
import com.hotelopai.assistant.domain.InputType
import com.hotelopai.task.application.TaskApplicationPort
import com.hotelopai.task.application.toCreateTaskCommand
import org.springframework.stereotype.Service
import java.util.UUID
import java.time.Instant

@Service
class AssistantConversationService(
    private val conversationRepository: ConversationRepository,
    private val stateMachine: ConversationStateMachine,
    private val taskApplicationPort: TaskApplicationPort,
    private val taskConfirmationRepository: TaskConfirmationRepository
) {
    fun startConversation(
        hotelId: String,
        userId: String
    ): ConversationTurnResult {
        val conversation = Conversation(
            id = newId("conversation"),
            hotelId = hotelId,
            userId = userId
        )

        return ConversationTurnResult(conversationRepository.save(conversation))
    }

    fun sendMessage(
        conversationId: String,
        text: String,
        inputType: InputType,
        voiceTranscript: String? = null,
        audioMetadata: AudioMetadata? = null,
        attachments: List<ConversationAttachment>
    ): ConversationTurnResult {
        val conversation = getConversation(conversationId)
        val result = stateMachine.handleUserMessage(
            conversation = conversation,
            command = ConversationCommand(
                messageId = newId("message"),
                text = text,
                inputType = inputType,
                voiceTranscript = voiceTranscript,
                audioMetadata = audioMetadata,
                attachments = attachments
            )
        )

        return result.copy(
            conversation = conversationRepository.save(result.conversation)
        )
    }

    fun confirmTask(
        conversationId: String,
        idempotencyKey: String
    ): ConversationTurnResult {
        val conversation = getConversation(conversationId)
        require(idempotencyKey.isNotBlank()) { "idempotencyKey must not be blank" }

        val existingConfirmation =
            taskConfirmationRepository.findByConversationIdAndIdempotencyKey(conversation.id, idempotencyKey)

        if (existingConfirmation != null) {
            val confirmedConversation = if (
                conversation.state == com.hotelopai.assistant.domain.ConversationState.TASK_CREATED &&
                conversation.createdTaskId == existingConfirmation.createdTaskId &&
                conversation.confirmationIdempotencyKey == idempotencyKey
            ) {
                conversation
            } else {
                conversation.taskCreated(
                    taskId = existingConfirmation.createdTaskId,
                    idempotencyKey = idempotencyKey
                )
            }

            val savedConversation = if (confirmedConversation === conversation) {
                conversation
            } else {
                conversationRepository.save(confirmedConversation)
            }

            return ConversationTurnResult(
                conversation = savedConversation,
                taskCreationCandidate = savedConversation.createTaskCandidate(idempotencyKey),
                createdTaskId = existingConfirmation.createdTaskId
            )
        }

        require(conversation.state == com.hotelopai.assistant.domain.ConversationState.WAITING_FOR_CONFIRMATION) {
            "Conversation must be waiting for confirmation before task creation"
        }

        val candidate = conversation.createTaskCandidate(idempotencyKey)
        val createCommand = candidate.toCreateTaskCommand(
            hotelId = conversation.hotelId,
            now = Instant.now()
        )
        val createdTask = taskApplicationPort.createTask(createCommand)

        val confirmedConversation = conversation.taskCreated(
            taskId = createdTask.id.toString(),
            idempotencyKey = idempotencyKey
        )

        taskConfirmationRepository.save(
            TaskConfirmationRecord(
                conversationId = conversation.id,
                idempotencyKey = idempotencyKey,
                createdTaskId = createdTask.id.toString(),
                draftId = candidate.draftId,
                draftVersion = candidate.draftVersion,
                preview = candidate.preview
            )
        )

        return ConversationTurnResult(
            conversation = conversationRepository.save(confirmedConversation),
            taskCreationCandidate = candidate,
            createdTaskId = createdTask.id.toString()
        )
    }

    fun resetConversation(conversationId: String): ConversationTurnResult {
        val conversation = getConversation(conversationId)
        val result = stateMachine.reset(conversation)

        return result.copy(
            conversation = conversationRepository.save(result.conversation)
        )
    }

    private fun getConversation(conversationId: String): Conversation =
        conversationRepository.findById(conversationId)
            ?: throw ConversationNotFoundException(conversationId)

    private fun newId(prefix: String): String =
        "$prefix-${UUID.randomUUID()}"
}
