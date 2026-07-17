package com.hotelopai.task.application

import com.hotelopai.task.domain.Task
import com.hotelopai.task.domain.TaskAssignment
import com.hotelopai.task.domain.TaskAssigneeType
import com.hotelopai.task.domain.TaskIntentType
import com.hotelopai.task.domain.TaskPriority
import com.hotelopai.task.domain.TaskSource
import com.hotelopai.task.domain.TaskStatus
import com.hotelopai.task.domain.TaskTransition
import com.hotelopai.observability.OperationalObservability
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import java.util.UUID

@Service
@Transactional
class TaskLifecycleService @Autowired constructor(
    private val taskRepository: TaskRepository,
    private val taskStateHistoryRepository: TaskStateHistoryRepository = NoOpTaskStateHistoryRepository,
    private val taskLogRepository: TaskLogRepository = NoOpTaskLogRepository,
    private val taskLogRecorder: TaskLogRecorder = TaskLogRecorder(NoOpTaskLogRepository),
    private val completionPolicy: CompletionPolicy,
    private val taskNotificationPublisher: TaskNotificationPublisher = NoOpTaskNotificationPublisher,
    private val observability: OperationalObservability = OperationalObservability.noop()
) : TaskApplicationPort {
    constructor(taskRepository: TaskRepository) : this(
        taskRepository = taskRepository,
        taskStateHistoryRepository = NoOpTaskStateHistoryRepository,
        taskLogRepository = NoOpTaskLogRepository,
        taskLogRecorder = TaskLogRecorder(NoOpTaskLogRepository),
        completionPolicy = NoOpCompletionPolicy(),
        taskNotificationPublisher = NoOpTaskNotificationPublisher,
        observability = OperationalObservability.noop()
    )

    fun createTask(request: CreateTaskCommand): Task =
        createTask(request, Instant.now())

    override fun createTask(request: CreateTaskCommand, now: Instant): Task {
        return try {
            val task = Task.create(
                hotelId = request.hotelId,
                intentType = request.intentType,
                source = request.source,
                title = request.title,
                description = request.description,
                roomNumber = request.roomNumber,
                priority = request.priority,
                slaDeadline = request.slaDeadline,
                createdAt = now
            )

            val saved = taskRepository.save(task)
            recordStateHistory(
                before = null,
                after = saved,
                operation = TaskTransition.CREATE,
                note = "Task created",
                now = now
            )
            recordTaskLog(
                task = saved,
                operation = TaskTransition.CREATE,
                outcome = TaskLogOutcome.SUCCESS,
                message = "Task created",
                now = now
            )
            val created = if (request.assignment != null) {
                mutate(
                    taskId = saved.id.toString(),
                    hotelId = saved.hotelId,
                    operation = TaskTransition.ASSIGN,
                    now = now,
                    mutation = { current ->
                        current.assign(request.assignment.toDomain(now), now)
                    }
                )
            } else {
                saved
            }
            taskNotificationPublisher.taskCreated(created, now)
            recordLifecycle(operation = TaskTransition.CREATE, outcome = "success", reasonCode = "none")
            created
        } catch (exception: RuntimeException) {
            recordLifecycle(operation = TaskTransition.CREATE, outcome = "failure", reasonCode = "operation_failed")
            logger.warn("event=task_lifecycle operation=create outcome=failure reasonCode=operation_failed")
            throw exception
        }
    }

    fun getTask(taskId: String, now: Instant = Instant.now()): Task =
        taskRepository.findById(taskId.toTaskId()) ?: throw TaskNotFoundException(taskId.toTaskId())

    fun getTaskForHotel(taskId: String, hotelId: UUID, now: Instant = Instant.now()): Task =
        taskRepository.findByIdAndHotelId(taskId.toTaskId(), hotelId) ?: throw TaskNotFoundException(taskId.toTaskId())

    fun listTasks(now: Instant = Instant.now()): List<Task> =
        taskRepository.findAll()

    fun listTasksForHotel(hotelId: UUID, now: Instant = Instant.now()): List<Task> =
        taskRepository.findAllByHotelId(hotelId)

    fun listTasksPage(request: TaskPageRequest, now: Instant = Instant.now()): TaskPage<Task> =
        taskRepository.findPage(request)

    fun searchTasks(query: TaskSearchQuery, now: Instant = Instant.now()): TaskPage<Task> =
        recordSearchDuration {
            taskRepository.findPage(query)
        }

    fun assignTask(taskId: String, hotelId: UUID, request: AssignTaskCommand, now: Instant = Instant.now()): Task =
        mutate(
            taskId = taskId,
            hotelId = hotelId,
            operation = TaskTransition.ASSIGN,
            now = now,
            mutation = { task ->
                task.assign(request.assignment.toDomain(now), now)
            }
        )

    fun startTask(taskId: String, hotelId: UUID, now: Instant = Instant.now()): Task =
        mutate(taskId = taskId, hotelId = hotelId, operation = TaskTransition.START, now = now, mutation = { it.start(now) })

    fun progressTask(taskId: String, hotelId: UUID, now: Instant = Instant.now()): Task =
        resumeTask(taskId, hotelId, now)

    fun waitTask(taskId: String, hotelId: UUID, now: Instant = Instant.now()): Task =
        pauseTask(taskId, hotelId, now)

    fun pauseTask(taskId: String, hotelId: UUID, now: Instant = Instant.now()): Task =
        mutate(taskId = taskId, hotelId = hotelId, operation = TaskTransition.PAUSE, now = now, mutation = { it.wait(now) })

    fun resumeTask(taskId: String, hotelId: UUID, now: Instant = Instant.now()): Task =
        mutate(taskId = taskId, hotelId = hotelId, operation = TaskTransition.RESUME, now = now, mutation = { it.progress(now) })

    fun completeTask(taskId: String, hotelId: UUID, now: Instant = Instant.now()): Task =
        run {
            var verificationLogId: UUID? = null
            mutate(
                taskId = taskId,
                hotelId = hotelId,
                operation = TaskTransition.COMPLETE,
                now = now,
                mutation = { task ->
                    val decision = completionPolicy.evaluate(task, now)
                    verificationLogId = decision.verificationLogId
                    task.complete(now)
                },
                successMessage = { _, _ ->
                    verificationLogId?.let { "Task completed after PMS verification $it" } ?: "Task completed"
                }
            )
        }

    fun cancelTask(taskId: String, hotelId: UUID, now: Instant = Instant.now()): Task =
        mutate(taskId = taskId, hotelId = hotelId, operation = TaskTransition.CANCEL, now = now, mutation = { it.cancel(now) })

    fun markOverdue(taskId: String, hotelId: UUID, now: Instant = Instant.now()): Task =
        mutate(taskId = taskId, hotelId = hotelId, operation = TaskTransition.OVERDUE, now = now, mutation = { it.markOverdue(now) })

    private fun mutate(
        taskId: String,
        hotelId: UUID,
        operation: TaskTransition,
        now: Instant,
        mutation: (Task) -> Task,
        successMessage: (Task, Task) -> String = { before, after ->
            "Task ${operation.name.lowercase()} succeeded from ${before.status} to ${after.status}"
        }
    ): Task {
        val parsedTaskId = taskId.toTaskId()
        val task = taskRepository.findByIdAndHotelId(parsedTaskId, hotelId)
            ?: run {
                recordLifecycle(operation = operation, outcome = "not_found", reasonCode = "task_not_found")
                logger.info("event=task_lifecycle operation=${operation.name.lowercase()} outcome=not_found reasonCode=task_not_found")
                throw TaskNotFoundException(parsedTaskId)
            }
        return try {
            val updated = mutation(task)
            val saved = taskRepository.save(updated)
            recordStateHistory(
                before = task,
                after = saved,
                operation = operation,
                note = "Task transitioned from ${task.status} to ${saved.status}",
                now = now
            )
            recordTaskLog(
                task = saved,
                operation = operation,
                outcome = TaskLogOutcome.SUCCESS,
                message = successMessage(task, saved),
                fromStatus = task.status,
                toStatus = saved.status,
                now = now
            )
            recordLifecycle(operation = operation, outcome = "success", reasonCode = "none")
            saved
        } catch (exception: RuntimeException) {
            taskLogRecorder.recordFailure(
                TaskLogEntry(
                    taskId = task.id,
                    hotelId = task.hotelId,
                    operation = operation,
                    outcome = TaskLogOutcome.FAILED,
                    message = exception.message ?: "Task ${operation.name.lowercase()} failed",
                    fromStatus = task.status,
                    toStatus = null,
                    occurredAt = now
                )
            )
            recordLifecycle(operation = operation, outcome = "failure", reasonCode = "transition_failed")
            logger.warn("event=task_lifecycle operation=${operation.name.lowercase()} outcome=failure reasonCode=transition_failed")
            throw exception
        }
    }

    private fun recordStateHistory(
        before: Task?,
        after: Task,
        operation: TaskTransition,
        note: String? = null,
        now: Instant
    ) {
        taskStateHistoryRepository.append(
            TaskStateHistoryEntry(
                taskId = after.id,
                hotelId = after.hotelId,
                fromStatus = before?.status,
                toStatus = after.status,
                operation = operation,
                note = note,
                occurredAt = now
            )
        )
    }

    private fun recordTaskLog(
        task: Task,
        operation: TaskTransition,
        outcome: TaskLogOutcome,
        message: String,
        fromStatus: TaskStatus? = task.status,
        toStatus: TaskStatus? = task.status,
        now: Instant
    ) {
        taskLogRepository.append(
            TaskLogEntry(
                taskId = task.id,
                hotelId = task.hotelId,
                operation = operation,
                outcome = outcome,
                message = message,
                fromStatus = fromStatus,
                toStatus = toStatus,
                occurredAt = now
            )
        )
    }

    private fun recordLifecycle(operation: TaskTransition, outcome: String, reasonCode: String) {
        observability.incrementCounter(
            "hotelopai.task.lifecycle.total",
            "operation" to operation.name.lowercase(),
            "outcome" to outcome,
            "transition" to operation.name.lowercase(),
            "reason_code" to reasonCode
        )
    }

    private fun <T> recordSearchDuration(block: () -> T): T {
        val timer = observability.startTimer()
        var outcome = "failure"
        return try {
            block().also {
                outcome = "success"
            }
        } finally {
            observability.stopTimer(
                timer,
                "hotelopai.task.search.duration",
                "operation" to "search",
                "outcome" to outcome
            )
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(TaskLifecycleService::class.java)
    }

}

data class CreateTaskCommand(
    val hotelId: UUID,
    val intentType: TaskIntentType,
    val source: TaskSource,
    val title: String,
    val description: String,
    val roomNumber: String? = null,
    val priority: TaskPriority,
    val slaDeadline: Instant,
    val assignment: AssignmentCommand? = null
)

data class AssignTaskCommand(
    val assignment: AssignmentCommand
)

data class AssignmentCommand(
    val assigneeType: TaskAssigneeType,
    val assigneeId: String,
    val displayName: String
) {
    fun toDomain(now: Instant): TaskAssignment =
        TaskAssignment(
            assigneeType = assigneeType,
            assigneeId = assigneeId,
            displayName = displayName,
            assignedAt = now
        )
}

private object NoOpTaskStateHistoryRepository : TaskStateHistoryRepository {
    override fun append(entry: TaskStateHistoryEntry) = Unit
}

private object NoOpTaskLogRepository : TaskLogRepository {
    override fun append(entry: TaskLogEntry) = Unit
}

private object NoOpTaskNotificationPublisher : TaskNotificationPublisher {
    override fun taskCreated(task: Task, now: Instant) = Unit
}

private fun String.toTaskId(): UUID = UUID.fromString(this)
