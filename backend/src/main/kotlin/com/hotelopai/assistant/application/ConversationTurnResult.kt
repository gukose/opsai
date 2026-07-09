package com.hotelopai.assistant.application

import com.hotelopai.assistant.domain.Conversation
import com.hotelopai.assistant.domain.TaskCreationCandidate

data class ConversationTurnResult(
    val conversation: Conversation,
    val taskCreationCandidate: TaskCreationCandidate? = null,
    val createdTaskId: String? = null
)
