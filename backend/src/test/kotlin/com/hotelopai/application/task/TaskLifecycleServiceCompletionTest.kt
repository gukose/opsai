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

class TaskLifecycleServiceCompletionTest {
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
        service.startTask(task.id.toString(), Instant.parse("2026-07-08T10:10:00Z"))
        val completed = service.completeTask(task.id.toString(), Instant.parse("2026-07-08T10:30:00Z"))

        assertEquals(TaskStatus.COMPLETED, completed.status)
        assertEquals(TaskTransition.COMPLETE, historyRepository.entries.last().operation)
        assertEquals(TaskLogOutcome.SUCCESS, logRepository.entries.last().outcome)
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
        service.startTask(task.id.toString(), Instant.parse("2026-07-08T10:10:00Z"))

        assertThrows(TaskCompletionPolicyException::class.java) {
            service.completeTask(task.id.toString(), Instant.parse("2026-07-08T10:30:00Z"))
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
