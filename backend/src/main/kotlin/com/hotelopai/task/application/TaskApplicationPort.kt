package com.hotelopai.task.application

import com.hotelopai.task.domain.Task

interface TaskApplicationPort {
    fun createTask(request: CreateTaskCommand, now: java.time.Instant = java.time.Instant.now()): Task
}
