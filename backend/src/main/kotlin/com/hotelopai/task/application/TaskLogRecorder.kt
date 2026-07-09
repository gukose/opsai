package com.hotelopai.task.application

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Service
class TaskLogRecorder(
    private val taskLogRepository: TaskLogRepository
) {
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun recordFailure(entry: TaskLogEntry) {
        taskLogRepository.append(entry)
    }
}
