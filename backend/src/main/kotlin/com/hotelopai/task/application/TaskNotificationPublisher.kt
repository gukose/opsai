package com.hotelopai.task.application

import com.hotelopai.task.domain.Task
import java.time.Instant

interface TaskNotificationPublisher {
    fun taskCreated(task: Task, now: Instant = Instant.now())
}
