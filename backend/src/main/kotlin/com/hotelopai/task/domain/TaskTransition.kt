package com.hotelopai.task.domain

enum class TaskTransition {
    CREATE,
    ASSIGN,
    START,
    PAUSE,
    RESUME,
    PROGRESS,
    WAIT,
    COMPLETE,
    CANCEL,
    OVERDUE
}
