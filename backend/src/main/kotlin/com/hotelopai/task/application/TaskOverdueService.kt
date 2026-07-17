package com.hotelopai.task.application

import com.hotelopai.task.domain.TaskStatus
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class TaskOverdueService(
    private val taskRepository: TaskRepository,
    private val taskLifecycleService: TaskLifecycleService
) {
    fun markOverdueTasks(now: Instant = Instant.now()): List<String> =
        taskRepository.findAll()
            .filter { it.status != TaskStatus.OVERDUE && it.isOverdue(now) }
            .map { task -> taskLifecycleService.markOverdue(task.id.toString(), task.hotelId, now).id.toString() }
}
