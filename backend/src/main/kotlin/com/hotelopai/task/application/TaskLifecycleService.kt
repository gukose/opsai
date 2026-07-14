package com.hotelopai.task.application

import com.hotelopai.task.domain.Task
import com.hotelopai.task.domain.TaskAssignment
import com.hotelopai.task.domain.TaskAssigneeType
import com.hotelopai.task.domain.TaskIntentType
import com.hotelopai.task.domain.TaskPriority
import com.hotelopai.task.domain.TaskSource
import com.hotelopai.task.domain.TaskStatus
import com.hotelopai.task.domain.TaskTransition
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
    private val taskNotificationPublisher: TaskNotificationPublisher = NoOpTaskNotificationPublisher
) : TaskApplicationPort {
    constructor(taskRepository: TaskRepository) : this(
        taskRepository = taskRepository,
        taskStateHistoryRepository = NoOpTaskStateHistoryRepository,
        taskLogRepository = NoOpTaskLogRepository,
        taskLogRecorder = TaskLogRecorder(NoOpTaskLogRepository),
        completionPolicy = NoOpCompletionPolicy(),
        taskNotificationPublisher = NoOpTaskNotificationPublisher
    )

    fun createTask(request: CreateTaskCommand): Task =
        createTask(request, Instant.now())

    override fun createTask(request: CreateTaskCommand, now: Instant): Task {
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
        return created
    }

    fun getTask(taskId: String, now: Instant = Instant.now()): Task =
        taskRepository.findById(taskId.toTaskId()) ?: throw TaskNotFoundException(taskId.toTaskId())

    fun listTasks(now: Instant = Instant.now()): List<Task> =
        taskRepository.findAll()

    fun listTasksPage(request: TaskPageRequest, now: Instant = Instant.now()): TaskPage<Task> =
        taskRepository.findPage(request)

    fun assignTask(taskId: String, request: AssignTaskCommand, now: Instant = Instant.now()): Task =
        mutate(
            taskId = taskId,
            operation = TaskTransition.ASSIGN,
            now = now,
            mutation = { task ->
                task.assign(request.assignment.toDomain(now), now)
            }
        )

    fun startTask(taskId: String, now: Instant = Instant.now()): Task =
        mutate(taskId = taskId, operation = TaskTransition.START, now = now, mutation = { it.start(now) })

    fun progressTask(taskId: String, now: Instant = Instant.now()): Task =
        resumeTask(taskId, now)

    fun waitTask(taskId: String, now: Instant = Instant.now()): Task =
        pauseTask(taskId, now)

    fun pauseTask(taskId: String, now: Instant = Instant.now()): Task =
        mutate(taskId = taskId, operation = TaskTransition.PAUSE, now = now, mutation = { it.wait(now) })

    fun resumeTask(taskId: String, now: Instant = Instant.now()): Task =
        mutate(taskId = taskId, operation = TaskTransition.RESUME, now = now, mutation = { it.progress(now) })

    fun completeTask(taskId: String, now: Instant = Instant.now()): Task =
        run {
            var verificationLogId: UUID? = null
            mutate(
                taskId = taskId,
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

    fun cancelTask(taskId: String, now: Instant = Instant.now()): Task =
        mutate(taskId = taskId, operation = TaskTransition.CANCEL, now = now, mutation = { it.cancel(now) })

    fun markOverdue(taskId: String, now: Instant = Instant.now()): Task =
        mutate(taskId = taskId, operation = TaskTransition.OVERDUE, now = now, mutation = { it.markOverdue(now) })

    private fun mutate(
        taskId: String,
        operation: TaskTransition,
        now: Instant,
        mutation: (Task) -> Task,
        successMessage: (Task, Task) -> String = { before, after ->
            "Task ${operation.name.lowercase()} succeeded from ${before.status} to ${after.status}"
        }
    ): Task {
        val parsedTaskId = taskId.toTaskId()
        val task = taskRepository.findById(parsedTaskId) ?: throw TaskNotFoundException(parsedTaskId)
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
