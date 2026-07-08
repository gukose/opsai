package com.hotelopai.task.infrastructure.persistence

import com.hotelopai.task.application.TaskLifecycleService
import com.hotelopai.task.application.TaskRepository
import com.hotelopai.task.domain.Task
import com.hotelopai.task.domain.TaskIntentType
import com.hotelopai.task.domain.TaskPriority
import com.hotelopai.task.domain.TaskSource
import com.hotelopai.task.domain.TaskStatus
import com.hotelopai.task.domain.TaskTransition
import com.hotelopai.hotel.application.HotelRepository
import com.hotelopai.hotel.domain.Hotel
import com.hotelopai.support.PostgresIntegrationTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import java.util.UUID

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TaskPersistenceRepositoryIntegrationTest : PostgresIntegrationTestSupport() {
    @Autowired
    private lateinit var hotelRepository: HotelRepository

    @Autowired
    private lateinit var taskRepository: TaskRepository

    @Autowired
    private lateinit var taskLifecycleService: TaskLifecycleService

    @Autowired
    private lateinit var taskOverdueService: com.hotelopai.task.application.TaskOverdueService

    @Autowired
    private lateinit var taskStateHistoryJpaRepository: TaskStateHistoryJpaRepository

    @Autowired
    private lateinit var taskLogJpaRepository: TaskLogJpaRepository

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    @Test
    fun `task repository saves and loads tasks`() {
        val hotel = hotelRepository.save(Hotel(code = "task-hotel-1", name = "Task Hotel"))
        val task = Task.create(
            hotelId = hotel.id,
            intentType = TaskIntentType.MAINTENANCE,
            source = TaskSource.MANUAL,
            title = "AC not working",
            description = "Room 101 AC not working",
            priority = TaskPriority.HIGH,
            slaDeadline = Instant.parse("2026-07-08T12:00:00Z"),
            createdAt = Instant.parse("2026-07-08T10:00:00Z")
        )

        val saved = taskRepository.save(task)

        assertThat(saved).isEqualTo(task)
        assertThat(saved.id.version()).isEqualTo(7)
        assertThat(taskRepository.findById(saved.id)).isEqualTo(saved)
        assertThat(taskRepository.findAll()).contains(saved)
    }

    @Test
    fun `lifecycle operations persist history and logs`() {
        val hotel = hotelRepository.save(Hotel(code = "task-hotel-2", name = "Task Hotel 2"))
        val created = taskLifecycleService.createTask(
            com.hotelopai.task.application.CreateTaskCommand(
                hotelId = hotel.id,
                intentType = TaskIntentType.MAINTENANCE,
                source = TaskSource.ASSISTANT,
                title = "AC not working",
                description = "Room 101 AC not working",
                priority = TaskPriority.HIGH,
                slaDeadline = Instant.parse("2026-07-08T13:00:00Z")
            ),
            now = Instant.parse("2026-07-08T10:00:00Z")
        )

        assertEquals(TaskTransition.CREATE, taskStateHistoryJpaRepository.findAllByTaskIdOrderByCreatedAtAsc(created.id).first().operation)
        assertEquals(1, taskLogJpaRepository.countByTaskId(created.id))

        taskLifecycleService.startTask(created.id.toString(), Instant.parse("2026-07-08T10:05:00Z"))
        taskLifecycleService.pauseTask(created.id.toString(), Instant.parse("2026-07-08T10:10:00Z"))
        taskLifecycleService.resumeTask(created.id.toString(), Instant.parse("2026-07-08T10:15:00Z"))
        taskLifecycleService.completeTask(created.id.toString(), Instant.parse("2026-07-08T10:20:00Z"))

        val history = taskStateHistoryJpaRepository.findAllByTaskIdOrderByCreatedAtAsc(created.id)
        assertThat(history.map { it.operation }).containsExactly(
            TaskTransition.CREATE,
            TaskTransition.START,
            TaskTransition.PAUSE,
            TaskTransition.RESUME,
            TaskTransition.COMPLETE
        )
        assertThat(taskLogJpaRepository.findAllByTaskIdOrderByCreatedAtAsc(created.id))
            .allMatch { it.outcome == TaskLogOutcomeJpa.SUCCESS }
    }

    @Test
    fun `invalid transition writes a failure log and rejects the request`() {
        val createdTaskId = inNewTransaction {
            val hotel = hotelRepository.save(Hotel(code = "task-hotel-3", name = "Task Hotel 3"))
            val created = taskLifecycleService.createTask(
                com.hotelopai.task.application.CreateTaskCommand(
                    hotelId = hotel.id,
                    intentType = TaskIntentType.GUEST_REQUEST,
                    source = TaskSource.MANUAL,
                    title = "Need towels",
                    description = "Guest needs towels",
                    priority = TaskPriority.MEDIUM,
                    slaDeadline = Instant.parse("2026-07-08T13:00:00Z")
                ),
                now = Instant.parse("2026-07-08T10:00:00Z")
            )

            taskLifecycleService.startTask(created.id.toString(), Instant.parse("2026-07-08T10:05:00Z"))
            taskLifecycleService.completeTask(created.id.toString(), Instant.parse("2026-07-08T10:10:00Z"))
            created.id
        }

        val failure = assertThrows(IllegalArgumentException::class.java) {
            inNewTransaction {
                taskLifecycleService.startTask(createdTaskId.toString(), Instant.parse("2026-07-08T10:15:00Z"))
            }
        }

        assertThat(failure.message).contains("Invalid workflow transition")
        assertThat(taskLogJpaRepository.findAllByTaskIdOrderByCreatedAtAsc(createdTaskId))
            .anyMatch { it.outcome == TaskLogOutcomeJpa.FAILED && it.operation == TaskTransition.START }
    }

    @Test
    fun `read operations do not mutate overdue tasks`() {
        val hotel = hotelRepository.save(Hotel(code = "task-hotel-4", name = "Task Hotel 4"))
        val overdueTask = taskLifecycleService.createTask(
            com.hotelopai.task.application.CreateTaskCommand(
                hotelId = hotel.id,
                intentType = TaskIntentType.HOUSEKEEPING,
                source = TaskSource.MANUAL,
                title = "Room cleanup",
                description = "Room 301 needs cleanup",
                priority = TaskPriority.MEDIUM,
                slaDeadline = Instant.parse("2026-07-08T09:00:00Z")
            ),
            now = Instant.parse("2026-07-08T08:00:00Z")
        )

        val historyBefore = taskStateHistoryJpaRepository.countByTaskId(overdueTask.id)
        val logBefore = taskLogJpaRepository.countByTaskId(overdueTask.id)

        taskLifecycleService.getTask(overdueTask.id.toString(), Instant.parse("2026-07-08T12:00:00Z"))
        taskLifecycleService.listTasks(Instant.parse("2026-07-08T12:00:00Z"))

        assertEquals(historyBefore, taskStateHistoryJpaRepository.countByTaskId(overdueTask.id))
        assertEquals(logBefore, taskLogJpaRepository.countByTaskId(overdueTask.id))
        assertEquals(TaskTransition.CREATE, taskStateHistoryJpaRepository.findAllByTaskIdOrderByCreatedAtAsc(overdueTask.id).first().operation)
    }

    @Test
    fun `foreign key integrity rejects orphan task and history rows`() {
        val orphanHotelId = UUID.fromString("018f6b7a-4f22-7caa-8f60-9e4b0f7f0001")
        val missingTaskId = UUID.fromString("018f6b7a-4f22-7caa-8f60-9e4b0f7f0002")

        assertThrows(DataIntegrityViolationException::class.java) {
            inNewTransaction {
                taskRepository.save(
                    Task.create(
                        hotelId = orphanHotelId,
                        intentType = TaskIntentType.MAINTENANCE,
                        source = TaskSource.MANUAL,
                        title = "Broken AC",
                        description = "Room 404 AC not working",
                        priority = TaskPriority.HIGH,
                        slaDeadline = Instant.parse("2026-07-08T12:00:00Z"),
                        createdAt = Instant.parse("2026-07-08T10:00:00Z")
                    )
                )
            }
        }

        assertThrows(DataIntegrityViolationException::class.java) {
            inNewTransaction {
                jdbcTemplate.update(
                    """
                    insert into task_state_history (
                        id, task_id, hotel_id, version, created_at, created_by, updated_at, updated_by, from_status, to_status, operation, note, correlation_id
                    ) values (
                        ?, ?, ?, 0, now(), null, now(), null, null, 'CREATED', 'CREATE', 'orphan history', null
                    )
                    """.trimIndent(),
                    UUID.randomUUID(),
                    missingTaskId,
                    UUID.fromString("018f6b7a-4f22-7caa-8f60-9e4b0f7f0003")
                )
            }
        }

        assertThrows(DataIntegrityViolationException::class.java) {
            inNewTransaction {
                jdbcTemplate.update(
                    """
                    insert into task_log (
                        id, task_id, hotel_id, version, created_at, created_by, updated_at, updated_by, operation, outcome, message, from_status, to_status, correlation_id
                    ) values (
                        ?, ?, ?, 0, now(), null, now(), null, 'CREATE', 'SUCCESS', 'orphan log', null, null, null
                    )
                    """.trimIndent(),
                    UUID.randomUUID(),
                    missingTaskId,
                    UUID.fromString("018f6b7a-4f22-7caa-8f60-9e4b0f7f0003")
                )
            }
        }
    }

    @Test
    fun `overdue scheduler marks overdue tasks explicitly`() {
        val hotel = hotelRepository.save(Hotel(code = "task-hotel-6", name = "Task Hotel 6"))
        val overdueTask = taskRepository.save(
            Task.create(
                hotelId = hotel.id,
                intentType = TaskIntentType.HOUSEKEEPING,
                source = TaskSource.MANUAL,
                title = "Clean room",
                description = "Room 202 needs cleaning",
                priority = TaskPriority.MEDIUM,
                slaDeadline = Instant.parse("2026-07-08T09:00:00Z"),
                createdAt = Instant.parse("2026-07-08T08:00:00Z")
            )
        )

        val updated = taskOverdueService.markOverdueTasks(Instant.parse("2026-07-08T12:00:00Z"))

        assertThat(updated).contains(overdueTask.id.toString())
        assertEquals(TaskStatus.OVERDUE, taskRepository.findById(overdueTask.id)?.status)
    }

    private fun <T> inNewTransaction(block: () -> T): T {
        val template = TransactionTemplate(transactionManager)
        template.propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
        return template.execute {
            block()
        } ?: error("Transaction did not return a value")
    }
}
