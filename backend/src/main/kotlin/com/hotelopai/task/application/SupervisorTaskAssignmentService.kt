package com.hotelopai.task.application

import com.hotelopai.employee.application.EmployeeRepository
import com.hotelopai.employee.domain.EmployeeStatus
import com.hotelopai.notification.application.NotificationRepository
import com.hotelopai.notification.domain.Notification
import com.hotelopai.notification.domain.NotificationRecipient
import com.hotelopai.notification.domain.NotificationType
import com.hotelopai.shared.kernel.PersistenceInstant
import com.hotelopai.shared.kernel.UuidV7Generator
import com.hotelopai.task.domain.Task
import com.hotelopai.task.domain.TaskAssigneeType
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.sql.Timestamp
import java.time.Clock
import java.util.UUID
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import com.zaxxer.hikari.HikariDataSource
import javax.sql.DataSource

@Service
class SupervisorTaskAssignmentService(
    private val lifecycle: TaskLifecycleService,
    private val employees: EmployeeRepository,
    private val jdbc: NamedParameterJdbcTemplate,
    private val notifications: NotificationRepository,
    private val clock: Clock,
    private val dataSource: DataSource? = null
) {
    @Transactional
    fun assign(
        taskId: String,
        hotelId: UUID,
        actorUserId: UUID,
        request: AssignTaskCommand
    ): Task = assignInternal(taskId, hotelId, actorUserId, request, null)

    @Transactional
    fun assignScoped(
        taskId: String,
        hotelId: UUID,
        actorUserId: UUID,
        request: AssignTaskCommand,
        actorScope: TaskVisibilityScope
    ): Task = assignInternal(taskId, hotelId, actorUserId, request, actorScope)

    private fun assignInternal(
        taskId: String,
        hotelId: UUID,
        actorUserId: UUID,
        request: AssignTaskCommand,
        actorScope: TaskVisibilityScope?
    ): Task {
        val started = System.nanoTime()
        val employeeStarted = System.nanoTime()
        val now = clock.instant()
        val employee = if (request.assignment.assigneeType == TaskAssigneeType.USER) {
            val requestedId = UUID.fromString(request.assignment.assigneeId)
            employees.findByHotelId(hotelId).firstOrNull {
                it.id == requestedId || it.userId == requestedId
            }?.takeIf { it.status == EmployeeStatus.ACTIVE }
                ?: throw IllegalArgumentException("Assignee must be an active employee in the authenticated hotel")
        } else {
            null
        }
        val employeeLoadMs = elapsedMs(employeeStarted)
        val canonicalAssignment = if (employee != null) {
            AssignmentCommand(
                TaskAssigneeType.USER,
                (employee.userId ?: employee.id).toString(),
                employee.displayName
            )
        } else {
            request.assignment
        }
        val taskStarted = System.nanoTime()
        val before = lifecycle.getTaskForHotel(taskId, hotelId)
        val taskLoadMs = elapsedMs(taskStarted)
        actorScope?.let { scope ->
            if (!TaskVisibilityRules.canView(before, scope) || scope.level == TaskVisibilityLevel.SELF) {
                throw AccessDeniedException("Task is outside the actor's assignment scope")
            }
            if (employee != null && scope.level == TaskVisibilityLevel.DEPARTMENT &&
                !employee.primaryRoleCode.orEmpty().uppercase().let { role ->
                    when {
                        scope.roleCodes.any { it.contains("HOUSEKEEPING") } -> role.contains("HOUSEKEEP")
                        scope.roleCodes.any { it.contains("TECHNICAL") || it.contains("ENGINEER") } ->
                            role.contains("TECHNIC") || role.contains("ENGINEER") || role.contains("MAINTENANCE") || role.contains("PLUMB")
                        scope.roleCodes.any { it.contains("FRONT_OFFICE") || it.contains("RECEPTION") } ->
                            role.contains("FRONT") || role.contains("RECEPTION") || role.contains("GUEST")
                        scope.roleCodes.any { it.contains("SECURITY") } -> role.contains("SECURITY")
                        else -> false
                    }
                }
            ) {
                throw AccessDeniedException("Assignee is outside the actor's department scope")
            }
        }
        if (before.assignment?.assigneeType == canonicalAssignment.assigneeType &&
            before.assignment.assigneeId == canonicalAssignment.assigneeId
        ) {
            return before
        }
        val persistedNow = PersistenceInstant.toPersistencePrecision(now)
        val updated = lifecycle.assignTask(
            taskId,
            hotelId,
            AssignTaskCommand(canonicalAssignment),
            persistedNow
        )
        val assignmentDbMs = elapsedMs(taskStarted) - taskLoadMs
        jdbc.update(
            """insert into task_assignment_audit(id,hotel_id,task_id,previous_assignee_id,new_assignee_id,
               reason_code,actor_user_id,action,created_at)
               values(:id,:hotel,:task,:previous,:next,'MANUAL_SUPERVISOR',:actor,:action,:now)""",
            mapOf(
                "id" to UuidV7Generator.generate(persistedNow), "hotel" to hotelId, "task" to updated.id,
                "previous" to before.assignment?.assigneeId, "next" to canonicalAssignment.assigneeId, "actor" to actorUserId,
                "action" to if (before.assignment == null) "ASSIGN" else "REASSIGN",
                "now" to Timestamp.from(persistedNow)
            )
        )
        val notificationStarted = System.nanoTime()
        employee?.userId?.let { userId ->
            notifications.save(
                Notification(
                    hotelId = hotelId,
                    recipient = NotificationRecipient.User(userId),
                    type = NotificationType.TASK_ASSIGNED,
                    title = if (before.assignment == null) "Task assigned" else "Task reassigned",
                    body = "${updated.title} was assigned to you.",
                    sourceTaskId = updated.id,
                    createdAt = persistedNow,
                    createdBy = actorUserId.toString(),
                    updatedAt = persistedNow,
                    updatedBy = actorUserId.toString()
                )
            )
        }
        val notificationMs = elapsedMs(notificationStarted)
        logger.info(
            "event={} correlationId={} taskId={} employeeLoadMs={} taskLoadMs={} assignmentDbMs={} notificationMs={} outboxMs={} externalMs={} totalMs={} poolActive={} poolIdle={} poolPending={} poolMax={}",
            if (before.assignment == null) "task_assign" else "task_reassign", MDC.get("correlationId") ?: "unknown", taskId,
            employeeLoadMs, taskLoadMs, assignmentDbMs, notificationMs, 0, 0, elapsedMs(started), pool().active, pool().idle, pool().pending, pool().max
        )
        return updated
    }

    private fun elapsedMs(started: Long): Long = (System.nanoTime() - started) / 1_000_000

    private fun pool(): PoolSnapshot {
        val hikari = dataSource as? HikariDataSource
        val bean = hikari?.hikariPoolMXBean
        return PoolSnapshot(bean?.activeConnections ?: -1, bean?.idleConnections ?: -1, bean?.threadsAwaitingConnection ?: -1, hikari?.maximumPoolSize ?: -1)
    }

    private data class PoolSnapshot(val active: Int, val idle: Int, val pending: Int, val max: Int)

    companion object {
        private val logger = LoggerFactory.getLogger(SupervisorTaskAssignmentService::class.java)
    }
}
