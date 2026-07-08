package com.hotelopai.task.infrastructure

import com.hotelopai.task.application.TaskRepository
import com.hotelopai.task.domain.Task
import org.springframework.stereotype.Repository
import org.springframework.context.annotation.Profile
import java.util.concurrent.ConcurrentHashMap
import java.util.UUID

@Repository
@Profile("memory")
class InMemoryTaskRepository : TaskRepository {
    private val tasks = ConcurrentHashMap<UUID, Task>()

    override fun save(task: Task): Task {
        tasks[task.id] = task
        return task
    }

    override fun findById(id: UUID): Task? =
        tasks[id]

    override fun findAll(): List<Task> =
        tasks.values.sortedByDescending { it.updatedAt }
}
