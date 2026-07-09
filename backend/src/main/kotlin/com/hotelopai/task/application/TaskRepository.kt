package com.hotelopai.task.application

import com.hotelopai.task.domain.Task
import java.util.UUID

interface TaskRepository {
    fun save(task: Task): Task

    fun findById(id: UUID): Task?

    fun findAll(): List<Task>
}
