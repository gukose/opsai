package com.hotelopai.assistant.application

import com.hotelopai.assistant.domain.Conversation
import com.hotelopai.assistant.domain.ConversationAttachment
import com.hotelopai.assistant.domain.AudioMetadata
import com.hotelopai.assistant.domain.ImageObservation
import com.hotelopai.assistant.domain.InputType
import com.hotelopai.assistant.domain.VoiceTranscriptMetadata
import com.hotelopai.task.application.TaskApplicationPort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID
import java.time.Instant

@Service
class AssistantConversationService(
    private val conversationRepository: ConversationRepository,
    private val stateMachine: ConversationStateMachine,
    private val taskApplicationPort: TaskApplicationPort,
    private val taskConfirmationRepository: TaskConfirmationRepository,
    private val taskAttachmentLinker: ConfirmedTaskAttachmentLinker = NoOpConfirmedTaskAttachmentLinker
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
        voiceTranscriptMetadata: VoiceTranscriptMetadata? = null,
        audioMetadata: AudioMetadata? = null,
        attachments: List<ConversationAttachment>,
        imageObservations: List<ImageObservation> = emptyList()
    ): ConversationTurnResult {
        validateAttachments(attachments)
        validateImageObservations(imageObservations)
        return handleMessage(getConversation(conversationId), text, inputType, voiceTranscript, voiceTranscriptMetadata, audioMetadata, attachments, imageObservations)
    }

    fun sendMessage(
        conversationId: String,
        hotelId: String,
        userId: String,
        text: String,
        inputType: InputType,
        voiceTranscript: String? = null,
        voiceTranscriptMetadata: VoiceTranscriptMetadata? = null,
        audioMetadata: AudioMetadata? = null,
        attachments: List<ConversationAttachment>,
        imageObservations: List<ImageObservation> = emptyList()
    ): ConversationTurnResult {
        validateAttachments(attachments)
        validateImageObservations(imageObservations)
        val conversation = getConversation(conversationId, hotelId, userId)
        return handleMessage(conversation, text, inputType, voiceTranscript, voiceTranscriptMetadata, audioMetadata, attachments, imageObservations)
    }

    private fun handleMessage(
        conversation: Conversation,
        text: String,
        inputType: InputType,
        voiceTranscript: String?,
        voiceTranscriptMetadata: VoiceTranscriptMetadata?,
        audioMetadata: AudioMetadata?,
        attachments: List<ConversationAttachment>,
        imageObservations: List<ImageObservation>
    ): ConversationTurnResult {
        val result = stateMachine.handleUserMessage(
            conversation = conversation,
            command = ConversationCommand(
                messageId = newId("message"),
                text = text,
                inputType = inputType,
                voiceTranscript = voiceTranscript,
                voiceTranscriptMetadata = voiceTranscriptMetadata,
                audioMetadata = audioMetadata,
                attachments = attachments,
                imageObservations = imageObservations
            )
        )

        return result.copy(
            conversation = conversationRepository.save(result.conversation)
        )
    }

    @Transactional
    fun confirmTask(
        conversationId: String,
        idempotencyKey: String
    ): ConversationTurnResult =
        confirmTask(getConversation(conversationId), idempotencyKey)

    @Transactional
    fun confirmTask(
        conversationId: String,
        hotelId: String,
        userId: String,
        idempotencyKey: String
    ): ConversationTurnResult {
        val conversation = getConversation(conversationId, hotelId, userId)
        return confirmTask(conversation, idempotencyKey)
    }

    private fun confirmTask(
        conversation: Conversation,
        idempotencyKey: String
    ): ConversationTurnResult {
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
        val now = Instant.now()
        taskAttachmentLinker.linkConfirmedTask(
            conversation = conversation,
            taskId = createdTask.id,
            now = now
        )

        val confirmedConversation = conversation.taskCreated(
            taskId = createdTask.id.toString(),
            idempotencyKey = idempotencyKey,
            now = now
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

    fun resetConversation(conversationId: String, hotelId: String, userId: String): ConversationTurnResult {
        val conversation = getConversation(conversationId, hotelId, userId)
        val result = stateMachine.reset(conversation)

        return result.copy(
            conversation = conversationRepository.save(result.conversation)
        )
    }

    private fun getConversation(conversationId: String): Conversation =
        conversationRepository.findById(conversationId)
            ?: throw ConversationNotFoundException(conversationId)

    private fun getConversation(conversationId: String, hotelId: String, userId: String): Conversation =
        conversationRepository.findByIdAndHotelIdAndUserId(conversationId, hotelId, userId)
            ?: throw ConversationNotFoundException(conversationId)

    private fun validateAttachments(attachments: List<ConversationAttachment>) {
        require(attachments.size <= 3) { "at most 3 attachments are allowed" }
        val ids = attachments.map { it.id }
        require(ids.size == ids.toSet().size) { "duplicate attachment ids are not allowed" }
    }

    private fun validateImageObservations(imageObservations: List<ImageObservation>) {
        val ids = imageObservations.map { it.id }
        require(ids.size == ids.toSet().size) { "duplicate image observation ids are not allowed" }
    }

    private fun newId(prefix: String): String =
        "$prefix-${UUID.randomUUID()}"
}
