package com.hotelopai.task.application

import com.hotelopai.task.domain.TaskIntentType
import com.hotelopai.task.domain.TaskPriority
import com.hotelopai.task.domain.TaskSource
import com.hotelopai.task.domain.TaskStatus
import com.hotelopai.task.domain.TaskTransition
import com.hotelopai.shared.kernel.UuidV7Generator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import com.hotelopai.housekeeping.application.HousekeepingRepository
import com.hotelopai.housekeeping.domain.*

class TaskLifecycleServiceCompletionTest {
    @Test
    fun `housekeeping completion enters inspection through canonical task endpoint`() {
        val tasks = InMemoryTaskStore()
        val hotel = UUID.fromString("018f6b7a-4f22-7caa-8f60-9e4b0f7f4001")
        val now = Instant.parse("2026-07-08T10:00:00Z")
        val workflowId = UUID.randomUUID()
        var workflow = HousekeepingWorkflow(id = workflowId, hotelId = hotel, taskId = UUID.randomUUID(), type = HousekeepingWorkflowType.DEPARTURE_CLEANING, roomNumber = "101", status = HousekeepingStatus.STARTED, inspectionRequired = true, idempotencyKey = "completion-test", createdAt = now, updatedAt = now)
        val housekeeping = object : HousekeepingRepository {
            override fun insert(w: HousekeepingWorkflow) = w
            override fun findByIdAndHotelId(id: UUID, hotelId: UUID) = workflow.takeIf { it.id == id && it.hotelId == hotelId }
            override fun findByTaskIdAndHotelId(taskId: UUID, hotelId: UUID) = workflow.takeIf { it.taskId == taskId && it.hotelId == hotelId }
            override fun findByIdempotencyKey(hotelId: UUID, key: String) = workflow
            override fun save(w: HousekeepingWorkflow): HousekeepingWorkflow { workflow = w; return w }
            override fun list(hotelId: UUID) = listOf(workflow)
            override fun appendInspection(hotelId: UUID, inspection: HousekeepingInspection) = Unit
            override fun inspections(workflowId: UUID, hotelId: UUID) = emptyList<HousekeepingInspection>()
        }
        val service = TaskLifecycleService(tasks, completionPolicy = NoOpCompletionPolicy(), housekeepingRepository = housekeeping)
        val created = service.createTask(newCreateCommand(now).copy(hotelId = hotel, intentType = TaskIntentType.HOUSEKEEPING), now)
        workflow = workflow.copy(taskId = created.id)
        service.startTask(created.id.toString(), hotel, now.plusSeconds(60))
        val waiting = service.completeTask(created.id.toString(), hotel, now.plusSeconds(120))
        assertEquals(TaskStatus.WAITING, waiting.status)
        assertEquals(HousekeepingStatus.INSPECTION, workflow.status)
    }
    @Test
    fun `complete task succeeds after PMS verification`() {
        val taskRepository = InMemoryTaskStore()
        val historyRepository = RecordingTaskStateHistoryRepository()
        val logRepository = RecordingTaskLogRepository()
        val service = TaskLifecycleService(
            taskRepository = taskRepository,
            taskStateHistoryRepository = historyRepository,
            taskLogRepository = logRepository,
            taskLogRecorder = TaskLogRecorder(logRepository),
            completionPolicy = object : CompletionPolicy {
                override fun evaluate(task: com.hotelopai.task.domain.Task, now: Instant): CompletionDecision =
                    CompletionDecision(
                        requiresPmsUpdate = true,
                        verificationLogId = UUID.fromString("018f6b7a-4f22-7caa-8f60-9e4b0f7f3001")
                    )
            }
        )

        val now = Instant.parse("2026-07-08T10:00:00Z")
        val task = service.createTask(newCreateCommand(now), now = now)
        service.startTask(task.id.toString(), task.hotelId, Instant.parse("2026-07-08T10:10:00Z"))
        val completed = service.completeTask(task.id.toString(), task.hotelId, Instant.parse("2026-07-08T10:30:00Z"))

        assertEquals(TaskStatus.COMPLETED, completed.status)
        assertEquals(TaskTransition.COMPLETE, historyRepository.entries.last().operation)
        assertEquals(TaskLogOutcome.SUCCESS, logRepository.entries.last().outcome)
    }

    @Test
    fun `duplicate completion is idempotent and does not repeat PMS verification`() {
        val taskRepository = InMemoryTaskStore()
        var verificationCalls = 0
        val service = TaskLifecycleService(
            taskRepository = taskRepository,
            completionPolicy = object : CompletionPolicy {
                override fun evaluate(task: com.hotelopai.task.domain.Task, now: Instant): CompletionDecision {
                    verificationCalls += 1
                    return CompletionDecision(requiresPmsUpdate = true, verificationLogId = UUID.randomUUID())
                }
            }
        )
        val now = Instant.parse("2026-07-08T10:00:00Z")
        val task = service.createTask(newCreateCommand(now), now = now)
        service.startTask(task.id.toString(), task.hotelId, now.plusSeconds(60))

        val first = service.completeTask(task.id.toString(), task.hotelId, now.plusSeconds(120))
        val second = service.completeTask(task.id.toString(), task.hotelId, now.plusSeconds(180))

        assertEquals(TaskStatus.COMPLETED, first.status)
        assertEquals(first, second)
        assertEquals(1, verificationCalls)
    }

    @Test
    fun `complete task failure keeps task recoverable and records failure log`() {
        val taskRepository = InMemoryTaskStore()
        val historyRepository = RecordingTaskStateHistoryRepository()
        val logRepository = RecordingTaskLogRepository()
        val service = TaskLifecycleService(
            taskRepository = taskRepository,
            taskStateHistoryRepository = historyRepository,
            taskLogRepository = logRepository,
            taskLogRecorder = TaskLogRecorder(logRepository),
            completionPolicy = object : CompletionPolicy {
                override fun evaluate(task: com.hotelopai.task.domain.Task, now: Instant): CompletionDecision {
                    throw TaskCompletionPolicyException("UniMock unavailable")
                }
            }
        )

        val now = Instant.parse("2026-07-08T10:00:00Z")
        val task = service.createTask(newCreateCommand(now), now = now)
        service.startTask(task.id.toString(), task.hotelId, Instant.parse("2026-07-08T10:10:00Z"))

        assertThrows(TaskCompletionPolicyException::class.java) {
            service.completeTask(task.id.toString(), task.hotelId, Instant.parse("2026-07-08T10:30:00Z"))
        }

        assertEquals(TaskStatus.STARTED, taskRepository.findById(task.id)?.status)
        assertEquals(TaskLogOutcome.FAILED, logRepository.entries.last().outcome)
    }

    private fun newCreateCommand(now: Instant) =
        CreateTaskCommand(
            hotelId = UUID.fromString("018f6b7a-4f22-7caa-8f60-9e4b0f7f4001"),
            intentType = TaskIntentType.MAINTENANCE,
            source = TaskSource.ASSISTANT,
            title = "Room 101 AC not working",
            description = "Room 101 AC not working",
            roomNumber = "101",
            priority = TaskPriority.HIGH,
            slaDeadline = now.plusSeconds(5400)
        )

    private class InMemoryTaskStore : TaskRepository {
        private val tasks = ConcurrentHashMap<UUID, com.hotelopai.task.domain.Task>()

        override fun save(task: com.hotelopai.task.domain.Task): com.hotelopai.task.domain.Task {
            tasks[task.id] = task
            return task
        }

        override fun findById(id: UUID): com.hotelopai.task.domain.Task? = tasks[id]

        override fun findAll(): List<com.hotelopai.task.domain.Task> = tasks.values.sortedByDescending { it.updatedAt }

        override fun findPage(request: TaskPageRequest): TaskPage<com.hotelopai.task.domain.Task> {
            val sorted = findAll()
            val fromIndex = (request.page * request.size).coerceAtMost(sorted.size)
            val toIndex = (fromIndex + request.size).coerceAtMost(sorted.size)
            return TaskPage(sorted.subList(fromIndex, toIndex), request.page, request.size, sorted.size.toLong())
        }
    }

    private class RecordingTaskStateHistoryRepository : TaskStateHistoryRepository {
        val entries = mutableListOf<TaskStateHistoryEntry>()

        override fun append(entry: TaskStateHistoryEntry) {
            entries += entry
        }
    }

    private class RecordingTaskLogRepository : TaskLogRepository {
        val entries = mutableListOf<TaskLogEntry>()

        override fun append(entry: TaskLogEntry) {
            entries += entry
        }
    }
}
