package com.hotelopai.assistant.domain

import java.time.Instant

data class Conversation(
    val id: String,
    val hotelId: String,
    val userId: String,
    val state: ConversationState = ConversationState.IDLE,
    val messages: List<ConversationMessage> = emptyList(),
    val intent: IntentType = IntentType.UNKNOWN,
    val collectedFields: Map<String, String> = emptyMap(),
    val missingFields: List<MissingField> = emptyList(),
    val followUpQuestion: FollowUpQuestion? = null,
    val taskPreview: TaskPreview? = null,
    val activeDraftId: String? = null,
    val activeDraftSourceMessageIds: List<String> = emptyList(),
    val draftVersion: Int = 0,
    val createdTaskId: String? = null,
    val confirmationIdempotencyKey: String? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = createdAt
) {
    fun addMessage(message: ConversationMessage, now: Instant = Instant.now()): Conversation =
        copy(
            messages = messages + message,
            updatedAt = now
        )

    fun analyzing(now: Instant = Instant.now()): Conversation =
        transitionTo(ConversationState.ANALYZING, now)

    fun withInterpretation(
        intent: IntentType,
        collectedFields: Map<String, String>,
        draftId: String,
        sourceMessageIds: List<String>,
        draftVersion: Int,
        now: Instant = Instant.now()
    ): Conversation {
        require(draftId.isNotBlank()) { "draftId must not be blank" }
        require(sourceMessageIds.all(String::isNotBlank)) { "source message ids must not be blank" }
        require(draftVersion > 0) { "draftVersion must be positive" }

        return copy(
            intent = intent,
            collectedFields = collectedFields,
            activeDraftId = draftId,
            activeDraftSourceMessageIds = sourceMessageIds.distinct(),
            draftVersion = draftVersion,
            updatedAt = now
        )
    }

    fun requireFollowUp(
        missingFields: List<MissingField>,
        question: FollowUpQuestion,
        now: Instant = Instant.now()
    ): Conversation {
        require(missingFields.isNotEmpty()) { "Follow-up requires at least one missing field" }

        return copy(
            state = ConversationState.NEEDS_FOLLOW_UP,
            missingFields = missingFields,
            followUpQuestion = question,
            taskPreview = null,
            updatedAt = now
        )
    }

    fun waitForUserAnswer(now: Instant = Instant.now()): Conversation =
        transitionTo(ConversationState.WAITING_FOR_USER_ANSWER, now)

    fun readyForPreview(
        intent: IntentType,
        draftId: String,
        draftVersion: Int,
        preview: TaskPreview,
        now: Instant = Instant.now()
    ): Conversation {
        require(intent != IntentType.UNKNOWN) { "Task preview requires a known intent" }
        require(draftId.isNotBlank()) { "draftId must not be blank" }
        require(draftVersion > 0) { "draftVersion must be positive" }

        return copy(
            state = ConversationState.READY_FOR_PREVIEW,
            intent = intent,
            missingFields = emptyList(),
            followUpQuestion = null,
            taskPreview = preview,
            activeDraftId = draftId,
            draftVersion = draftVersion,
            updatedAt = now
        )
    }

    fun waitForConfirmation(now: Instant = Instant.now()): Conversation {
        require(taskPreview != null) { "Confirmation requires a task preview" }
        return transitionTo(ConversationState.WAITING_FOR_CONFIRMATION, now)
    }

    fun createTaskCandidate(idempotencyKey: String): TaskCreationCandidate {
        val draftId = requireNotNull(activeDraftId) {
            "Task creation requires an active draft"
        }
        val preview = requireNotNull(taskPreview) {
            "Task creation requires a task preview"
        }

        return TaskCreationCandidate(
            conversationId = id,
            draftId = draftId,
            draftVersion = draftVersion,
            preview = preview,
            idempotencyKey = idempotencyKey
        )
    }

    fun creatingTask(now: Instant = Instant.now()): Conversation =
        transitionTo(ConversationState.CREATING_TASK, now)

    fun taskCreated(now: Instant = Instant.now()): Conversation =
        transitionTo(ConversationState.TASK_CREATED, now)

    fun taskCreated(
        taskId: String,
        idempotencyKey: String,
        now: Instant = Instant.now()
    ): Conversation {
        require(taskId.isNotBlank()) { "taskId must not be blank" }
        require(idempotencyKey.isNotBlank()) { "idempotencyKey must not be blank" }

        return copy(
            state = ConversationState.TASK_CREATED,
            createdTaskId = taskId,
            confirmationIdempotencyKey = idempotencyKey,
            updatedAt = now
        )
    }

    fun fail(now: Instant = Instant.now()): Conversation =
        transitionTo(ConversationState.FAILED, now)

    fun reset(now: Instant = Instant.now()): Conversation =
        copy(
            state = ConversationState.RESET,
            intent = IntentType.UNKNOWN,
            collectedFields = emptyMap(),
            missingFields = emptyList(),
            followUpQuestion = null,
            taskPreview = null,
            activeDraftId = null,
            activeDraftSourceMessageIds = emptyList(),
            draftVersion = 0,
            createdTaskId = null,
            confirmationIdempotencyKey = null,
            updatedAt = now
        )

    fun idle(now: Instant = Instant.now()): Conversation =
        transitionTo(ConversationState.IDLE, now)

    private fun transitionTo(
        nextState: ConversationState,
        now: Instant
    ): Conversation =
        copy(
            state = nextState,
            updatedAt = now
        )
}
