package com.hotelopai.task.application

import com.hotelopai.task.domain.Task
import com.hotelopai.task.domain.TaskPriority
import com.hotelopai.task.domain.TaskStatus
import java.time.Instant
import java.util.UUID

interface TaskRepository {
    fun save(task: Task): Task

    fun findById(id: UUID): Task?

    fun findByIdAndHotelId(id: UUID, hotelId: UUID): Task? =
        findById(id)?.takeIf { it.hotelId == hotelId }

    fun findAll(): List<Task>

    fun findAllByHotelId(hotelId: UUID): List<Task> =
        findAll().filter { it.hotelId == hotelId }

    fun findPage(request: TaskPageRequest): TaskPage<Task>

    fun findPage(query: TaskSearchQuery): TaskPage<Task> {
        val filtered = findAllByHotelId(query.hotelId)
            .filter { task ->
                query.text?.let { text ->
                    listOfNotNull(task.title, task.description, task.roomNumber, task.assignment?.displayName)
                        .joinToString(" ")
                        .contains(text, ignoreCase = true)
                } ?: true
            }
            .filter { query.statuses.isEmpty() || it.status in query.statuses }
            .filter { query.priorities.isEmpty() || it.priority in query.priorities }
            .filter { task ->
                when (query.assignment) {
                    null -> true
                    TaskAssignmentFilter.ASSIGNED -> task.assignment != null
                    TaskAssignmentFilter.UNASSIGNED -> task.assignment == null
                    TaskAssignmentFilter.MINE -> task.assignment?.assigneeType?.name == "USER" &&
                        task.assignment.assigneeId == query.userId.toString()
                    TaskAssignmentFilter.ROLE -> task.assignment?.assigneeType?.name == "TEAM" &&
                        task.assignment.assigneeId in query.roleCodes
                    TaskAssignmentFilter.USER -> task.assignment?.assigneeType?.name == "USER"
                    TaskAssignmentFilter.TEAM -> task.assignment?.assigneeType?.name == "TEAM"
                }
            }
            .filter { task -> query.createdFrom?.let { !task.createdAt.isBefore(it) } ?: true }
            .filter { task -> query.createdTo?.let { task.createdAt.isBefore(it) } ?: true }

        val fromIndex = (query.pageRequest.page * query.pageRequest.size).coerceAtMost(filtered.size)
        val toIndex = (fromIndex + query.pageRequest.size).coerceAtMost(filtered.size)
        return TaskPage(
            items = filtered.subList(fromIndex, toIndex),
            page = query.pageRequest.page,
            size = query.pageRequest.size,
            totalItems = filtered.size.toLong()
        )
    }
}

data class TaskSearchQuery(
    val hotelId: UUID,
    val userId: UUID,
    val roleCodes: Set<String>,
    val pageRequest: TaskPageRequest,
    val text: String? = null,
    val statuses: Set<TaskStatus> = emptySet(),
    val priorities: Set<TaskPriority> = emptySet(),
    val assignment: TaskAssignmentFilter? = null,
    val createdFrom: Instant? = null,
    val createdTo: Instant? = null
)

enum class TaskAssignmentFilter {
    ASSIGNED,
    UNASSIGNED,
    MINE,
    ROLE,
    USER,
    TEAM
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
