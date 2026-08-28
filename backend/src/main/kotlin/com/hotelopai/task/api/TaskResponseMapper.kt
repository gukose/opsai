package com.hotelopai.task.api

import com.hotelopai.task.application.*
import com.hotelopai.task.domain.Task
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class TaskResponseMapper(private val history: TaskStateHistoryRepository) {
    fun toResponse(task: Task): TaskResponse {
        val timing = TaskTimingCalculator.calculate(history.findByTaskId(task.id), Instant.now())
        return TaskResponse.from(task, timing)
    }

    fun toResponses(tasks: List<Task>): List<TaskResponse> {
        if (tasks.isEmpty()) return emptyList()
        val grouped = history.findByTaskIds(tasks.map { it.id })
        val now = Instant.now()
        return tasks.map { TaskResponse.from(it, TaskTimingCalculator.calculate(grouped[it.id].orEmpty(), now)) }
    }

    fun toPageResponse(page: TaskPage<Task>): TaskPageResponse =
        TaskPageResponse.from(page, toResponses(page.items))
}
