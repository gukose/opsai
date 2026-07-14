package com.hotelopai.task.infrastructure

import com.hotelopai.task.application.TaskRepository
import com.hotelopai.task.application.TaskPage
import com.hotelopai.task.application.TaskPageRequest
import com.hotelopai.task.application.TaskAssignmentFilter
import com.hotelopai.task.application.TaskSearchQuery
import com.hotelopai.task.domain.TaskAssigneeType
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

    override fun findByIdAndHotelId(id: UUID, hotelId: UUID): Task? =
        tasks[id]?.takeIf { it.hotelId == hotelId }

    override fun findAll(): List<Task> =
        tasks.values.sortedByDescending { it.updatedAt }

    override fun findAllByHotelId(hotelId: UUID): List<Task> =
        findAll().filter { it.hotelId == hotelId }

    override fun findPage(request: TaskPageRequest): TaskPage<Task> {
        val sorted = findAll()
        return page(sorted, request)
    }

    override fun findPage(query: TaskSearchQuery): TaskPage<Task> {
        val filtered = findAll()
            .filter { it.hotelId == query.hotelId }
            .filter { task ->
                query.text?.let { text ->
                    val haystack = listOfNotNull(
                        task.title,
                        task.description,
                        task.roomNumber,
                        task.assignment?.displayName
                    ).joinToString(" ").lowercase()
                    haystack.contains(text.lowercase())
                } ?: true
            }
            .filter { query.statuses.isEmpty() || it.status in query.statuses }
            .filter { query.priorities.isEmpty() || it.priority in query.priorities }
            .filter { task ->
                when (query.assignment) {
                    null -> true
                    TaskAssignmentFilter.ASSIGNED -> task.assignment != null
                    TaskAssignmentFilter.UNASSIGNED -> task.assignment == null
                    TaskAssignmentFilter.MINE -> task.assignment?.assigneeType == TaskAssigneeType.USER &&
                        task.assignment.assigneeId == query.userId.toString()
                    TaskAssignmentFilter.ROLE -> task.assignment?.assigneeType == TaskAssigneeType.TEAM &&
                        task.assignment.assigneeId in query.roleCodes
                    TaskAssignmentFilter.USER -> task.assignment?.assigneeType == TaskAssigneeType.USER
                    TaskAssignmentFilter.TEAM -> task.assignment?.assigneeType == TaskAssigneeType.TEAM
                }
            }
            .filter { task -> query.createdFrom?.let { !task.createdAt.isBefore(it) } ?: true }
            .filter { task -> query.createdTo?.let { task.createdAt.isBefore(it) } ?: true }

        return page(filtered, query.pageRequest)
    }

    private fun page(sorted: List<Task>, request: TaskPageRequest): TaskPage<Task> {
        val fromIndex = (request.page * request.size).coerceAtMost(sorted.size)
        val toIndex = (fromIndex + request.size).coerceAtMost(sorted.size)

        return TaskPage(
            items = sorted.subList(fromIndex, toIndex),
            page = request.page,
            size = request.size,
            totalItems = sorted.size.toLong()
        )
    }
}
