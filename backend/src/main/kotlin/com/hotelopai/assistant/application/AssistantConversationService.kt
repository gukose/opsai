package com.hotelopai.assistant.application

import com.hotelopai.assistant.domain.Conversation
import com.hotelopai.assistant.domain.ConversationAttachment
import com.hotelopai.assistant.domain.AudioMetadata
import com.hotelopai.assistant.domain.ImageObservation
import com.hotelopai.assistant.domain.InputType
import com.hotelopai.assistant.domain.TaskCreationCandidate
import com.hotelopai.assistant.domain.VoiceTranscriptMetadata
import com.hotelopai.assistant.domain.ConversationState
import com.hotelopai.observability.OperationalObservability
import com.hotelopai.shared.kernel.PersistenceInstant
import com.hotelopai.task.application.TaskApplicationPort
import org.slf4j.LoggerFactory
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
    private val taskAttachmentLinker: ConfirmedTaskAttachmentLinker = NoOpConfirmedTaskAttachmentLinker,
    private val observability: OperationalObservability = OperationalObservability.noop()
) {
    @Transactional
    fun startConversation(
        hotelId: String,
        userId: String
    ): ConversationTurnResult {
        val conversation = Conversation(
            id = newId("conversation"),
            hotelId = hotelId,
            userId = userId
        )

        return try {
            ConversationTurnResult(conversationRepository.save(conversation)).also {
                observability.incrementCounter(
                    "hotelopai.assistant.conversation.total",
                    "operation" to "start",
                    "outcome" to "success"
                )
            }
        } catch (exception: RuntimeException) {
            observability.incrementCounter(
                "hotelopai.assistant.conversation.total",
                "operation" to "start",
                "outcome" to "failure",
                "reason_code" to "operation_failed"
            )
            logger.warn("event=assistant_conversation operation=start outcome=failure reasonCode=operation_failed")
            throw exception
        }
    }

    @Transactional
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

    @Transactional
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
        val timer = observability.startTimer()
        var outcome = "failure"
        var reasonCode = "operation_failed"
        try {
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

            val saved = result.copy(
                conversation = conversationRepository.save(result.conversation)
            )
            outcome = messageOutcome(saved.conversation)
            reasonCode = "none"
            observability.incrementCounter(
                "hotelopai.assistant.message.total",
                "operation" to "send",
                "outcome" to outcome
            )
            return saved
        } catch (exception: ConversationConcurrencyException) {
            outcome = "concurrency_conflict"
            reasonCode = "concurrency_conflict"
            observability.incrementCounter(
                "hotelopai.assistant.message.total",
                "operation" to "send",
                "outcome" to outcome,
                "reason_code" to reasonCode
            )
            logger.warn("event=assistant_message operation=send outcome=concurrency_conflict reasonCode=concurrency_conflict")
            throw exception
        } catch (exception: IllegalArgumentException) {
            reasonCode = "validation_failure"
            observability.incrementCounter(
                "hotelopai.assistant.message.total",
                "operation" to "send",
                "outcome" to outcome,
                "reason_code" to reasonCode
            )
            throw exception
        } catch (exception: RuntimeException) {
            observability.incrementCounter(
                "hotelopai.assistant.message.total",
                "operation" to "send",
                "outcome" to outcome,
                "reason_code" to reasonCode
            )
            logger.warn("event=assistant_message operation=send outcome=failure reasonCode=operation_failed")
            throw exception
        } finally {
            observability.stopTimer(
                timer,
                "hotelopai.assistant.interpretation.duration",
                "operation" to "send",
                "outcome" to outcome,
                "reason_code" to reasonCode
            )
        }
    }

    @Transactional
    fun confirmTask(
        conversationId: String,
        idempotencyKey: String
    ): ConversationTurnResult =
        confirmTask(getConversationForUpdate(conversationId), idempotencyKey)

    @Transactional
    fun confirmTask(
        conversationId: String,
        hotelId: String,
        userId: String,
        idempotencyKey: String
    ): ConversationTurnResult {
        val conversation = getConversationForUpdate(conversationId, hotelId, userId)
        return confirmTask(conversation, idempotencyKey)
    }

    private fun confirmTask(
        conversation: Conversation,
        idempotencyKey: String
    ): ConversationTurnResult {
        val timer = observability.startTimer()
        var outcome = "failure"
        var reasonCode = "operation_failed"

        try {
            require(idempotencyKey.isNotBlank()) { "idempotencyKey must not be blank" }
            val existingConfirmation =
                taskConfirmationRepository.findByConversationIdAndIdempotencyKey(conversation.id, idempotencyKey)

            if (existingConfirmation != null) {
                outcome = "duplicate"
                reasonCode = "idempotency_reuse"
                observability.incrementCounter(
                    "hotelopai.assistant.confirmation.total",
                    "operation" to "confirm",
                    "outcome" to outcome,
                    "reason_code" to reasonCode
                )
                logger.info("event=assistant_confirmation operation=confirm outcome=duplicate reasonCode=idempotency_reuse")
                return existingConfirmationResult(conversation, existingConfirmation, idempotencyKey)
            }

            val candidate = conversation.createTaskCandidate(idempotencyKey)
            taskConfirmationRepository.findByConversationIdAndDraftIdentity(
                conversationId = conversation.id,
                draftId = candidate.draftId,
                draftVersion = candidate.draftVersion
            )?.let { existingDraftConfirmation ->
                outcome = "duplicate"
                reasonCode = "draft_already_confirmed"
                observability.incrementCounter(
                    "hotelopai.assistant.confirmation.total",
                    "operation" to "confirm",
                    "outcome" to outcome,
                    "reason_code" to reasonCode
                )
                logger.info("event=assistant_confirmation operation=confirm outcome=duplicate reasonCode=draft_already_confirmed")
                return existingConfirmationResult(conversation, existingDraftConfirmation, idempotencyKey)
            }

            require(conversation.state == ConversationState.WAITING_FOR_CONFIRMATION) {
                "Conversation must be waiting for confirmation before task creation"
            }

            val now = PersistenceInstant.toPersistencePrecision(Instant.now())
            val createCommand = candidate.toCreateTaskCommand(
                hotelId = conversation.hotelId,
                now = now
            )

            val createdTask = taskApplicationPort.createTask(createCommand)
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

            val savedConversation = conversationRepository.save(confirmedConversation)
            outcome = "success"
            reasonCode = "none"
            observability.incrementCounter(
                "hotelopai.assistant.confirmation.total",
                "operation" to "confirm",
                "outcome" to outcome
            )
            return ConversationTurnResult(
                conversation = savedConversation,
                taskCreationCandidate = candidate,
                createdTaskId = createdTask.id.toString()
            )
        } catch (exception: ConversationConcurrencyException) {
            outcome = "conflict"
            reasonCode = "concurrency_conflict"
            observability.incrementCounter(
                "hotelopai.assistant.confirmation.total",
                "operation" to "confirm",
                "outcome" to outcome,
                "reason_code" to reasonCode
            )
            logger.warn("event=assistant_confirmation operation=confirm outcome=conflict reasonCode=concurrency_conflict")
            throw exception
        } catch (exception: IllegalArgumentException) {
            outcome = "conflict"
            reasonCode = "invalid_state"
            observability.incrementCounter(
                "hotelopai.assistant.confirmation.total",
                "operation" to "confirm",
                "outcome" to outcome,
                "reason_code" to reasonCode
            )
            logger.warn("event=assistant_confirmation operation=confirm outcome=conflict reasonCode=invalid_state")
            throw exception
        } catch (exception: RuntimeException) {
            observability.incrementCounter(
                "hotelopai.assistant.confirmation.total",
                "operation" to "confirm",
                "outcome" to outcome,
                "reason_code" to reasonCode
            )
            logger.warn("event=assistant_confirmation operation=confirm outcome=failure reasonCode=operation_failed")
            throw exception
        } finally {
            observability.stopTimer(
                timer,
                "hotelopai.assistant.confirmation.duration",
                "operation" to "confirm",
                "outcome" to outcome,
                "reason_code" to reasonCode
            )
        }
    }

    private fun existingConfirmationResult(
        conversation: Conversation,
        existingConfirmation: TaskConfirmationRecord,
        requestedIdempotencyKey: String
    ): ConversationTurnResult {
        val confirmedConversation = if (
            conversation.state == ConversationState.TASK_CREATED &&
            conversation.createdTaskId == existingConfirmation.createdTaskId
        ) {
            conversation
        } else {
            conversation.taskCreated(
                taskId = existingConfirmation.createdTaskId,
                idempotencyKey = existingConfirmation.idempotencyKey
            )
        }

        val savedConversation = if (confirmedConversation === conversation) {
            conversation
        } else {
            conversationRepository.save(confirmedConversation)
        }

        return ConversationTurnResult(
            conversation = savedConversation,
            taskCreationCandidate = TaskCreationCandidate(
                conversationId = existingConfirmation.conversationId,
                draftId = existingConfirmation.draftId,
                draftVersion = existingConfirmation.draftVersion,
                preview = existingConfirmation.preview,
                idempotencyKey = requestedIdempotencyKey
            ),
            createdTaskId = existingConfirmation.createdTaskId
        )
    }

    @Transactional
    fun resetConversation(conversationId: String): ConversationTurnResult {
        val conversation = getConversation(conversationId)
        val result = stateMachine.reset(conversation)

        return result.copy(
            conversation = conversationRepository.save(result.conversation)
        )
    }

    @Transactional
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

    private fun getConversationForUpdate(conversationId: String): Conversation =
        conversationRepository.findByIdForUpdate(conversationId)
            ?: throw ConversationNotFoundException(conversationId)

    private fun getConversationForUpdate(conversationId: String, hotelId: String, userId: String): Conversation =
        conversationRepository.findByIdAndHotelIdAndUserIdForUpdate(conversationId, hotelId, userId)
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

    private fun messageOutcome(conversation: Conversation): String =
        when {
            conversation.state == ConversationState.WAITING_FOR_CONFIRMATION && conversation.taskPreview != null -> "preview"
            conversation.state == ConversationState.WAITING_FOR_USER_ANSWER ||
                conversation.followUpQuestion != null ||
                conversation.missingFields.isNotEmpty() -> "clarification"
            else -> "completed"
        }

    companion object {
        private val logger = LoggerFactory.getLogger(AssistantConversationService::class.java)
    }
}
