package com.hotelopai.task.application

import com.hotelopai.task.domain.Task
import java.util.UUID

interface TaskRepository {
    fun save(task: Task): Task

    fun findById(id: UUID): Task?

    fun findAll(): List<Task>

    fun findPage(request: TaskPageRequest): TaskPage<Task>
}

data class TaskPageRequest(
    val page: Int,
    val size: Int
) {
    init {
        require(page >= 0) { "page must be greater than or equal to 0" }
        require(size in 1..100) { "size must be between 1 and 100" }
    }

    companion object {
        const val DEFAULT_PAGE = 0
        const val DEFAULT_SIZE = 20
    }
}

data class TaskPage<T>(
    val items: List<T>,
    val page: Int,
    val size: Int,
    val totalItems: Long
) {
    val totalPages: Int =
        if (totalItems == 0L) {
            0
        } else {
            ((totalItems + size - 1) / size).toInt()
        }

    val hasNext: Boolean =
        page + 1 < totalPages

    val hasPrevious: Boolean =
        page > 0 && totalPages > 0
}
