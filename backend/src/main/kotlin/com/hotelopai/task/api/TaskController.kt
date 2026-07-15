package com.hotelopai.task.api

import com.hotelopai.shared.security.CurrentUserContextResolver
import com.hotelopai.task.application.TaskAssignmentFilter
import com.hotelopai.task.application.TaskPageRequest
import com.hotelopai.task.application.TaskSearchQuery
import com.hotelopai.task.application.TaskLifecycleService
import com.hotelopai.task.application.TaskAttachmentLinkService
import com.hotelopai.task.domain.TaskPriority
import com.hotelopai.task.domain.TaskStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

@RestController
@RequestMapping("/api/v1/tasks")
class TaskController(
    private val taskLifecycleService: TaskLifecycleService,
    private val taskAttachmentLinkService: TaskAttachmentLinkService,
    private val currentUserContextResolver: CurrentUserContextResolver
) {
    @PostMapping
    fun createTask(@RequestBody request: CreateTaskRequest): TaskResponse =
        TaskResponse.from(taskLifecycleService.createTask(request.toCommand()))

    @GetMapping("/{taskId}")
    fun getTask(@PathVariable taskId: String): TaskResponse =
        TaskResponse.from(
            taskLifecycleService.getTaskForHotel(
                taskId = taskId,
                hotelId = currentUserContextResolver.current().hotelId
            )
        )

    @GetMapping("/{taskId}/attachments")
    fun getTaskAttachments(@PathVariable taskId: String): List<TaskAttachmentResponse> {
        val currentUser = currentUserContextResolver.current()
        return taskAttachmentLinkService
            .listTaskAttachments(taskId, currentUser.hotelId)
            .map(TaskAttachmentResponse::from)
    }

    @GetMapping
    fun listTasks(
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) priority: String?,
        @RequestParam(required = false) assignment: String?,
        @RequestParam(required = false) createdFrom: String?,
        @RequestParam(required = false) createdTo: String?,
        @RequestParam(required = false) page: Int?,
        @RequestParam(required = false) size: Int?
    ): Any {
        val currentUser = currentUserContextResolver.current()
        if (page == null &&
            size == null &&
            q == null &&
            status == null &&
            priority == null &&
            assignment == null &&
            createdFrom == null &&
            createdTo == null
        ) {
            return taskLifecycleService.listTasksForHotel(currentUser.hotelId).map(TaskResponse::from)
        }

        val pageRequest = TaskPageRequest(
            page = page ?: TaskPageRequest.DEFAULT_PAGE,
            size = size ?: TaskPageRequest.DEFAULT_SIZE
        )

        return TaskPageResponse.from(
            taskLifecycleService.searchTasks(
                TaskSearchQuery(
                    hotelId = currentUser.hotelId,
                    userId = currentUser.userId,
                    roleCodes = currentUser.roles,
                    pageRequest = pageRequest,
                    text = parseSearchText(q),
                    statuses = parseEnumSet<TaskStatus>(status, "status"),
                    priorities = parseEnumSet<TaskPriority>(priority, "priority"),
                    assignment = parseAssignment(assignment),
                    createdFrom = parseInstant(createdFrom, "createdFrom"),
                    createdTo = parseInstant(createdTo, "createdTo")
                ).also { validateCreatedRange(it.createdFrom, it.createdTo) }
            )
        )
    }

    @PostMapping("/{taskId}/assign")
    fun assignTask(
        @PathVariable taskId: String,
        @RequestBody request: AssignTaskRequest
    ): TaskResponse =
        TaskResponse.from(taskLifecycleService.assignTask(taskId, request.toCommand()))

    @PostMapping("/{taskId}/start")
    fun startTask(@PathVariable taskId: String): TaskResponse =
        TaskResponse.from(taskLifecycleService.startTask(taskId))

    @PostMapping("/{taskId}/pause")
    fun pauseTask(@PathVariable taskId: String): TaskResponse =
        TaskResponse.from(taskLifecycleService.pauseTask(taskId))

    @PostMapping("/{taskId}/resume")
    fun resumeTask(@PathVariable taskId: String): TaskResponse =
        TaskResponse.from(taskLifecycleService.resumeTask(taskId))

    @PostMapping("/{taskId}/complete")
    fun completeTask(@PathVariable taskId: String): TaskResponse =
        TaskResponse.from(taskLifecycleService.completeTask(taskId))

    @PostMapping("/{taskId}/cancel")
    fun cancelTask(@PathVariable taskId: String): TaskResponse =
        TaskResponse.from(taskLifecycleService.cancelTask(taskId))

    @PostMapping("/{taskId}/overdue")
    fun overdueTask(@PathVariable taskId: String): TaskResponse =
        TaskResponse.from(taskLifecycleService.markOverdue(taskId))

    private fun parseSearchText(value: String?): String? {
        val trimmed = value?.trim()?.takeIf(String::isNotBlank) ?: return null
        require(trimmed.length <= 100) { "q must be 100 characters or fewer" }
        return trimmed
    }

    private inline fun <reified T : Enum<T>> parseEnumSet(value: String?, field: String): Set<T> {
        val tokens = splitCsv(value)
        if (tokens.isEmpty()) {
            return emptySet()
        }

        return tokens.map { token ->
            try {
                enumValueOf<T>(token.uppercase())
            } catch (_: IllegalArgumentException) {
                throw IllegalArgumentException("Invalid $field value: $token")
            }
        }.toSet()
    }

    private fun parseAssignment(value: String?): TaskAssignmentFilter? {
        val trimmed = value?.trim()?.takeIf(String::isNotBlank) ?: return null
        return when (trimmed.lowercase()) {
            "assigned" -> TaskAssignmentFilter.ASSIGNED
            "unassigned" -> TaskAssignmentFilter.UNASSIGNED
            "mine" -> TaskAssignmentFilter.MINE
            "role" -> TaskAssignmentFilter.ROLE
            "user" -> TaskAssignmentFilter.USER
            "team" -> TaskAssignmentFilter.TEAM
            else -> throw IllegalArgumentException("Invalid assignment value: $trimmed")
        }
    }

    private fun parseInstant(value: String?, field: String): Instant? {
        val trimmed = value?.trim()?.takeIf(String::isNotBlank) ?: return null
        return try {
            Instant.parse(trimmed)
        } catch (_: RuntimeException) {
            throw IllegalArgumentException("$field must be an ISO-8601 instant")
        }
    }

    private fun validateCreatedRange(createdFrom: Instant?, createdTo: Instant?) {
        if (createdFrom != null && createdTo != null) {
            require(createdFrom.isBefore(createdTo)) { "createdFrom must be before createdTo" }
        }
    }

    private fun splitCsv(value: String?): List<String> =
        value
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?: emptyList()
}
