package com.hotelopai.task.api

import com.hotelopai.shared.security.CurrentUserContextResolver
import com.hotelopai.shared.security.PermissionExpressions
import com.hotelopai.task.application.TaskAssignmentFilter
import com.hotelopai.task.application.TaskPageRequest
import com.hotelopai.task.application.TaskSearchQuery
import com.hotelopai.task.application.TaskLifecycleService
import com.hotelopai.task.application.SupervisorTaskAssignmentService
import com.hotelopai.task.application.TaskAssignmentCandidateQuery
import com.hotelopai.task.application.AssignmentCandidateView
import com.hotelopai.task.application.TaskVisibilityScope
import com.hotelopai.task.application.TaskAttachmentLinkService
import com.hotelopai.task.domain.TaskPriority
import com.hotelopai.task.domain.TaskStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import com.hotelopai.housekeeping.application.HousekeepingRepository
import com.hotelopai.housekeeping.domain.HousekeepingStatus

@RestController
@RequestMapping("/api/v1/tasks")
class TaskController(
    private val taskLifecycleService: TaskLifecycleService,
    private val taskAttachmentLinkService: TaskAttachmentLinkService,
    private val currentUserContextResolver: CurrentUserContextResolver,
    private val supervisorTaskAssignmentService: SupervisorTaskAssignmentService,
    private val taskAssignmentCandidateQuery: TaskAssignmentCandidateQuery,
    private val taskResponseMapper: TaskResponseMapper,
    private val housekeepingRepository: HousekeepingRepository? = null
) {
    @PostMapping
    @PreAuthorize(PermissionExpressions.TASK_CREATE)
    fun createTask(@RequestBody request: CreateTaskRequest): TaskResponse {
        val currentUser = currentUserContextResolver.current()
        return taskResponseMapper.toResponse(taskLifecycleService.createTask(request.toCommand(currentUser.hotelId)))
    }

    @GetMapping("/{taskId}")
    @PreAuthorize(PermissionExpressions.TASK_READ)
    fun getTask(@PathVariable taskId: String): TaskResponse {
        val started = System.nanoTime()
        val authStarted = System.nanoTime()
        val current = currentUserContextResolver.current()
        val authStateMs = elapsedMs(authStarted)
        val taskStarted = System.nanoTime()
        val response = taskResponseMapper.toResponse(taskLifecycleService.getTaskForScope(taskId, TaskVisibilityScope.from(current)))
        logger.info("event=task_detail_load correlationId={} taskId={} taskFetchMs={} authStateMs={} permissionResolutionMs={} totalMs={}", MDC.get("correlationId") ?: "unknown", taskId, elapsedMs(taskStarted), authStateMs, 0, elapsedMs(started))
        return response
    }

    @GetMapping("/{taskId}/attachments")
    @PreAuthorize(PermissionExpressions.TASK_ATTACHMENT_READ)
    fun getTaskAttachments(@PathVariable taskId: String): List<TaskAttachmentResponse> {
        val currentUser = currentUserContextResolver.current()
        taskLifecycleService.getTaskForScope(taskId, TaskVisibilityScope.from(currentUser))
        return taskAttachmentLinkService
            .listTaskAttachments(taskId, currentUser.hotelId)
            .map(TaskAttachmentResponse::from)
    }

    @GetMapping
    @PreAuthorize(PermissionExpressions.TASK_READ)
    fun listTasks(
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) priority: String?,
        @RequestParam(required = false) assignment: String?,
        @RequestParam(required = false) createdFrom: String?,
        @RequestParam(required = false) createdTo: String?,
        @RequestParam(required = false, defaultValue = "false") inspectionRequired: Boolean,
        @RequestParam(required = false) page: Int?,
        @RequestParam(required = false) size: Int?
        ): Any {
        val currentUser = currentUserContextResolver.current()
        val scope = TaskVisibilityScope.from(currentUser)
        if (page == null &&
            size == null &&
            q == null &&
            status == null &&
            priority == null &&
            assignment == null &&
            createdFrom == null &&
            createdTo == null && !inspectionRequired
        ) {
            return taskResponseMapper.toResponses(taskLifecycleService.listTasksForScope(scope))
        }

        val pageRequest = TaskPageRequest(
            page = page ?: TaskPageRequest.DEFAULT_PAGE,
            size = size ?: TaskPageRequest.DEFAULT_SIZE
        )

        return taskResponseMapper.toPageResponse(
            taskLifecycleService.searchTasks(
                TaskSearchQuery(
                    hotelId = currentUser.hotelId,
                    userId = currentUser.userId,
                    employeeId = currentUser.employeeId,
                    canonicalEmployeeUserId = currentUser.canonicalEmployeeUserId,
                    roleCodes = currentUser.roles,
                    pageRequest = pageRequest,
                    visibility = scope,
                    text = parseSearchText(q),
                    statuses = parseEnumSet<TaskStatus>(status, "status"),
                    priorities = parseEnumSet<TaskPriority>(priority, "priority"),
                    assignment = parseAssignment(assignment),
                    createdFrom = parseInstant(createdFrom, "createdFrom"),
                    createdTo = parseInstant(createdTo, "createdTo"),
                    inspectionTaskIds = if (inspectionRequired) housekeepingRepository?.list(currentUser.hotelId).orEmpty()
                        .filter { it.status == HousekeepingStatus.INSPECTION }
                        .map { it.taskId }.toSet() else null
                ).also { validateCreatedRange(it.createdFrom, it.createdTo) }
            )
        )
    }

    @PostMapping("/{taskId}/assign")
    @PreAuthorize(PermissionExpressions.TASK_ASSIGN)
    fun assignTask(
        @PathVariable taskId: String,
        @RequestBody request: AssignTaskRequest
    ): TaskResponse {
        val started = System.nanoTime()
        val current = currentUserContextResolver.current()
        val response = taskResponseMapper.toResponse(
            supervisorTaskAssignmentService.assignScoped(
                taskId = taskId,
                hotelId = current.hotelId,
                actorUserId = current.userId,
                request = request.toCommand(),
                actorScope = TaskVisibilityScope.from(current)
            )
        )
        logger.info("event=task_assignment_request correlationId={} taskId={} totalMs={}", MDC.get("correlationId") ?: "unknown", taskId, elapsedMs(started))
        return response
    }

    @GetMapping("/{taskId}/assignment-candidates")
    @PreAuthorize(PermissionExpressions.TASK_ASSIGN)
    fun assignmentCandidates(@PathVariable taskId: String): List<AssignmentCandidateView> {
        val started = System.nanoTime()
        val current = currentUserContextResolver.current()
        val task = taskLifecycleService.getTaskForScope(taskId, TaskVisibilityScope.from(current))
        val candidates = taskAssignmentCandidateQuery.candidates(task, Instant.now())
        logger.info("event=assignment_candidates correlationId={} taskId={} totalMs={} candidateCount={}", MDC.get("correlationId") ?: "unknown", taskId, elapsedMs(started), candidates.size)
        return candidates
    }

    @PostMapping("/{taskId}/start")
    @PreAuthorize(PermissionExpressions.TASK_START)
    fun startTask(@PathVariable taskId: String): TaskResponse =
        taskResponseMapper.toResponse(taskLifecycleService.startTask(taskId, visibleHotel(taskId)))

    @PostMapping("/{taskId}/pause")
    @PreAuthorize(PermissionExpressions.TASK_PAUSE)
    fun pauseTask(@PathVariable taskId: String): TaskResponse =
        taskResponseMapper.toResponse(taskLifecycleService.pauseTask(taskId, visibleHotel(taskId)))

    @PostMapping("/{taskId}/resume")
    @PreAuthorize(PermissionExpressions.TASK_RESUME)
    fun resumeTask(@PathVariable taskId: String): TaskResponse =
        taskResponseMapper.toResponse(taskLifecycleService.resumeTask(taskId, visibleHotel(taskId)))

    @PostMapping("/{taskId}/complete")
    @PreAuthorize(PermissionExpressions.TASK_COMPLETE)
    fun completeTask(@PathVariable taskId: String): TaskResponse =
        taskResponseMapper.toResponse(taskLifecycleService.completeTask(taskId, visibleHotel(taskId)))

    @PostMapping("/{taskId}/cancel")
    @PreAuthorize(PermissionExpressions.TASK_CANCEL)
    fun cancelTask(@PathVariable taskId: String): TaskResponse =
        taskResponseMapper.toResponse(taskLifecycleService.cancelTask(taskId, visibleHotel(taskId)))

    @PostMapping("/{taskId}/overdue")
    @PreAuthorize(PermissionExpressions.TASK_MARK_OVERDUE)
    fun overdueTask(@PathVariable taskId: String): TaskResponse =
        taskResponseMapper.toResponse(taskLifecycleService.markOverdue(taskId, visibleHotel(taskId)))

    private fun visibleHotel(taskId: String) = currentUserContextResolver.current().let { current ->
        taskLifecycleService.getTaskForScope(taskId, TaskVisibilityScope.from(current))
        current.hotelId
    }

    private fun elapsedMs(started: Long): Long = (System.nanoTime() - started) / 1_000_000

    companion object {
        private val logger = LoggerFactory.getLogger(TaskController::class.java)
    }

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
