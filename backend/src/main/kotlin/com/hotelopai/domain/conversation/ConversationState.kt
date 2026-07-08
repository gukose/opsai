package com.hotelopai.assistant.domain

enum class ConversationState {
    IDLE,
    ANALYZING,
    NEEDS_FOLLOW_UP,
    WAITING_FOR_USER_ANSWER,
    READY_FOR_PREVIEW,
    WAITING_FOR_CONFIRMATION,
    CREATING_TASK,
    TASK_CREATED,
    FAILED,
    RESET
}
