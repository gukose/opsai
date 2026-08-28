package com.hotelopai.task.application

import com.hotelopai.observability.OperationalObservability
import com.hotelopai.shared.kernel.UuidV7Generator
import com.hotelopai.task.domain.TaskAssigneeType
import com.hotelopai.task.domain.Task
import com.hotelopai.task.domain.TaskPriority
import com.hotelopai.task.domain.TaskStatus
import com.hotelopai.task.domain.TaskTransition
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.sql.Timestamp
import java.util.UUID

data class InterruptTaskCommand(
    val hotelId: UUID,
    val employeeId: UUID,
    val employeeDisplayName: String,
    val activeTaskId: UUID,
    val interruptingTaskId: UUID,
    val reason: String,
    val idempotencyKey: String,
    val autoStart: Boolean = true,
    val employeeAssigneeId: String = employeeId.toString()
)

enum class InterruptionSource { MANUAL, FLASH_INTERRUPTION }

data class InterruptionResult(
    val interruptionId: UUID,
    val pausedTaskId: UUID,
    val interruptingTaskId: UUID,
    val status: String,
    val source: InterruptionSource
)

data class TaskInterruptionRecord(
    val interruptionId: UUID,
    val hotelId: UUID,
    val employeeId: UUID,
    val pausedTaskId: UUID,
    val interruptingTaskId: UUID,
    val reason: String,
    val status: String,
    val source: InterruptionSource,
    val pausedAt: Instant
) {
    fun result() = InterruptionResult(interruptionId, pausedTaskId, interruptingTaskId, status, source)
}

data class ActiveTaskInterruption(
    val record: TaskInterruptionRecord,
    val pausedTaskStatus: TaskStatus?,
    val pausedAssigneeType: TaskAssigneeType?,
    val pausedAssigneeId: String?,
    val interruptingTaskStatus: TaskStatus?,
    val employeeUserId: UUID? = null
)

interface TaskInterruptionStore {
    fun find(id: UUID, hotelId: UUID): TaskInterruptionRecord?
    fun findByIdempotencyKey(hotelId: UUID, key: String): TaskInterruptionRecord?
    fun insert(record: TaskInterruptionRecord, idempotencyKey: String)
    fun activeForEmployee(hotelId: UUID, employeeId: UUID): List<ActiveTaskInterruption>
    fun findActiveByInterruptingTaskId(hotelId: UUID, interruptingTaskId: UUID): TaskInterruptionRecord? = null
    fun transition(id: UUID, hotelId: UUID, expectedStatus: String, status: String, resumedAt: Instant?): Boolean
}

@Repository
class JdbcTaskInterruptionStore(private val jdbc: NamedParameterJdbcTemplate) : TaskInterruptionStore {
    override fun find(id: UUID, hotelId: UUID): TaskInterruptionRecord? =
        jdbc.query(
            "select * from task_interruption where id=:id and hotel_id=:hotel for update",
            mapOf("id" to id, "hotel" to hotelId)
        ) { rs, _ -> taskInterruptionRecord(rs) }.firstOrNull()

    override fun findByIdempotencyKey(hotelId: UUID, key: String): TaskInterruptionRecord? =
        jdbc.query(
            "select * from task_interruption where hotel_id=:hotel and idempotency_key=:key",
            mapOf("hotel" to hotelId, "key" to key)
        ) { rs, _ -> taskInterruptionRecord(rs) }.firstOrNull()

    override fun insert(record: TaskInterruptionRecord, idempotencyKey: String) {
        jdbc.update(
            """insert into task_interruption(
               id,hotel_id,employee_id,paused_task_id,interrupting_task_id,reason,source,status,idempotency_key,paused_at,created_at
               ) values(
               :id,:hotel,:employee,:paused,:interrupting,:reason,:source,'ACTIVE',:key,:pausedAt,:pausedAt
               )""",
            mapOf(
                "id" to record.interruptionId,
                "hotel" to record.hotelId,
                "employee" to record.employeeId,
                "paused" to record.pausedTaskId,
                "interrupting" to record.interruptingTaskId,
                "reason" to record.reason,
                "source" to record.source.name,
                "key" to idempotencyKey,
                "pausedAt" to Timestamp.from(record.pausedAt)
            )
        )
    }

    override fun activeForEmployee(hotelId: UUID, employeeId: UUID): List<ActiveTaskInterruption> =
        jdbc.query(
            """select i.*,paused.status paused_status,paused.assignee_type paused_assignee_type,
                      paused.assignee_id paused_assignee_id,interrupting.status interrupting_status,
                      employee.user_id employee_user_id
               from task_interruption i
               left join employee on employee.id=i.employee_id and employee.hotel_id=i.hotel_id
               left join task paused on paused.id=i.paused_task_id and paused.hotel_id=i.hotel_id
               left join task interrupting on interrupting.id=i.interrupting_task_id and interrupting.hotel_id=i.hotel_id
               where i.hotel_id=:hotel and i.employee_id=:employee and i.status='ACTIVE'
               order by i.paused_at desc,i.created_at desc
               for update of i""",
            mapOf("hotel" to hotelId, "employee" to employeeId)
        ) { rs, _ ->
            ActiveTaskInterruption(
                record = taskInterruptionRecord(rs),
                pausedTaskStatus = rs.getString("paused_status")?.let(TaskStatus::valueOf),
                pausedAssigneeType = rs.getString("paused_assignee_type")?.let(TaskAssigneeType::valueOf),
                pausedAssigneeId = rs.getString("paused_assignee_id"),
                interruptingTaskStatus = rs.getString("interrupting_status")?.let(TaskStatus::valueOf),
                employeeUserId = rs.getObject("employee_user_id", UUID::class.java)
            )
        }

    override fun findActiveByInterruptingTaskId(hotelId: UUID, interruptingTaskId: UUID): TaskInterruptionRecord? =
        jdbc.query(
            "select * from task_interruption where hotel_id=:hotel and interrupting_task_id=:task and status='ACTIVE' and source='FLASH_INTERRUPTION' for update",
            mapOf("hotel" to hotelId, "task" to interruptingTaskId)
        ) { rs, _ -> taskInterruptionRecord(rs) }.firstOrNull()

    override fun transition(
        id: UUID,
        hotelId: UUID,
        expectedStatus: String,
        status: String,
        resumedAt: Instant?
    ): Boolean = jdbc.update(
        """update task_interruption set status=:status,resumed_at=:resumedAt
           where id=:id and hotel_id=:hotel and status=:expected""",
        mapOf(
            "status" to status,
            "resumedAt" to resumedAt?.let(Timestamp::from),
            "id" to id,
            "hotel" to hotelId,
            "expected" to expectedStatus
        )
    ) == 1

}

internal fun taskInterruptionRecord(rs: java.sql.ResultSet) = TaskInterruptionRecord(
    interruptionId = rs.getObject("id", UUID::class.java),
    hotelId = rs.getObject("hotel_id", UUID::class.java),
    employeeId = rs.getObject("employee_id", UUID::class.java),
    pausedTaskId = rs.getObject("paused_task_id", UUID::class.java),
    interruptingTaskId = rs.getObject("interrupting_task_id", UUID::class.java),
    reason = rs.getString("reason"),
    status = rs.getString("status"),
    source = InterruptionSource.valueOf(rs.getString("source")),
    pausedAt = rs.getTimestamp("paused_at").toInstant()
)

@Service
class SmartInterruptionService(
    private val store: TaskInterruptionStore,
    private val repository: TaskRepository,
    private val lifecycle: TaskLifecycleService,
    private val history: TaskStateHistoryRepository,
    private val metrics: OperationalObservability,
    private val clock: Clock = Clock.systemUTC()
) {
    @Transactional
    fun interrupt(command: InterruptTaskCommand): InterruptionResult {
        require(command.reason.isNotBlank() && command.idempotencyKey.isNotBlank())
        store.findByIdempotencyKey(command.hotelId, command.idempotencyKey)?.let { return it.result() }
        val active = repository.findByIdAndHotelId(command.activeTaskId, command.hotelId)
            ?: throw TaskNotFoundException(command.activeTaskId)
        val urgent = repository.findByIdAndHotelId(command.interruptingTaskId, command.hotelId)
            ?: throw TaskNotFoundException(command.interruptingTaskId)
        require(active.status in setOf(TaskStatus.STARTED, TaskStatus.IN_PROGRESS)) { "Previous task is not active" }
        require(active.assignment?.assigneeType == TaskAssigneeType.USER && active.assignment.assigneeId == command.employeeAssigneeId) {
            "Previous task is not assigned to the expected employee"
        }
        require(urgent.priority in setOf(TaskPriority.HIGH, TaskPriority.URGENT)) { "Interrupting task is not high priority" }
        require(urgent.status in setOf(TaskStatus.CREATED, TaskStatus.ASSIGNED)) { "Interrupting task cannot be activated" }
        val now = clock.instant()
        lifecycle.pauseTask(active.id.toString(), command.hotelId, now)
        if (urgent.status == TaskStatus.CREATED) {
            lifecycle.assignTask(
                urgent.id.toString(),
                command.hotelId,
                AssignTaskCommand(AssignmentCommand(TaskAssigneeType.USER, command.employeeId.toString(), command.employeeDisplayName)),
                now
            )
        }
        if (command.autoStart) lifecycle.startTask(urgent.id.toString(), command.hotelId, now)
        val record = TaskInterruptionRecord(
            interruptionId = UuidV7Generator.generate(now),
            hotelId = command.hotelId,
            employeeId = command.employeeId,
            pausedTaskId = active.id,
            interruptingTaskId = urgent.id,
            reason = command.reason,
            status = "ACTIVE",
            source = InterruptionSource.FLASH_INTERRUPTION,
            pausedAt = now
        )
        store.insert(record, command.idempotencyKey)
        metrics.incrementCounter("hotelopai.task.interruption.total", "operation" to "interrupt", "outcome" to "success")
        return record.result()
    }

    @Transactional
    fun interruptingTaskCompleted(task: Task) {
        val interruption = store.findActiveByInterruptingTaskId(task.hotelId, task.id) ?: return
        if (interruption.source == InterruptionSource.FLASH_INTERRUPTION) reEvaluateEmployee(interruption.hotelId, interruption.employeeId, clock.instant())
    }

    @Transactional
    fun completeAndResume(interruptionId: UUID, hotelId: UUID): InterruptionResult {
        val selected = store.find(interruptionId, hotelId) ?: throw NoSuchElementException("Interruption not found")
        if (selected.status != "ACTIVE") return selected.result()
        val now = clock.instant()
        val interrupting = repository.findByIdAndHotelId(selected.interruptingTaskId, hotelId)
            ?: throw TaskNotFoundException(selected.interruptingTaskId)
        if (!interrupting.isTerminal()) lifecycle.completeTask(interrupting.id.toString(), hotelId, now)
        if (selected.source != InterruptionSource.FLASH_INTERRUPTION) {
            store.transition(selected.interruptionId, hotelId, "ACTIVE", "CLOSED", now)
            return (store.find(interruptionId, hotelId) ?: selected.copy(status = "CLOSED")).result()
        }
        reEvaluateEmployee(selected.hotelId, selected.employeeId, now)
        return (store.find(interruptionId, hotelId) ?: selected).result()
    }

    private fun reEvaluateEmployee(hotelId: UUID, employeeId: UUID, now: Instant) {
        val active = store.activeForEmployee(hotelId, employeeId)
        if (active.isEmpty()) return
        val histories = history.findByTaskIds(active.map { it.record.pausedTaskId }.distinct())

        active.forEachIndexed { index, candidate ->
            if (candidate.record.source != InterruptionSource.FLASH_INTERRUPTION) return@forEachIndexed
            if (!candidate.interruptingTaskStatus.isTerminal()) return@forEachIndexed
            val newerBlocker = active.take(index).any {
                it.record.source == InterruptionSource.FLASH_INTERRUPTION && !it.interruptingTaskStatus.isTerminal()
            }
            if (newerBlocker) return@forEachIndexed

            if (!eligible(candidate, histories[candidate.record.pausedTaskId].orEmpty())) {
                store.transition(candidate.record.interruptionId, hotelId, "ACTIVE", "CLOSED", now)
                return@forEachIndexed
            }

            if (store.transition(candidate.record.interruptionId, hotelId, "ACTIVE", "RESUMING", null)) {
                lifecycle.resumeTask(candidate.record.pausedTaskId.toString(), hotelId, now)
                store.transition(candidate.record.interruptionId, hotelId, "RESUMING", "RESUMED", now)
            }
        }
    }

    private fun eligible(candidate: ActiveTaskInterruption, taskHistory: List<TaskStateHistoryEntry>): Boolean {
        if (candidate.pausedTaskStatus != TaskStatus.WAITING) return false
        if (candidate.pausedAssigneeType != TaskAssigneeType.USER || candidate.pausedAssigneeId !in setOfNotNull(candidate.record.employeeId.toString(), candidate.employeeUserId?.toString())) return false
        return taskHistory.none { entry ->
            entry.occurredAt.isAfter(candidate.record.pausedAt) && entry.operation in MANUAL_INVALIDATING_ACTIONS
        }
    }

    private fun TaskStatus?.isTerminal(): Boolean = this == TaskStatus.COMPLETED || this == TaskStatus.CANCELLED

    companion object {
        private val MANUAL_INVALIDATING_ACTIONS = setOf(
            TaskTransition.ASSIGN,
            TaskTransition.START,
            TaskTransition.PAUSE,
            TaskTransition.RESUME,
            TaskTransition.COMPLETE,
            TaskTransition.CANCEL
        )
    }
}
