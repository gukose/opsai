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
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.sql.Timestamp
import java.time.Clock
import java.util.UUID

@Service
class SupervisorTaskAssignmentService(
    private val lifecycle: TaskLifecycleService,
    private val employees: EmployeeRepository,
    private val jdbc: NamedParameterJdbcTemplate,
    private val notifications: NotificationRepository,
    private val clock: Clock
) {
    @Transactional
    fun assign(
        taskId: String,
        hotelId: UUID,
        actorUserId: UUID,
        request: AssignTaskCommand
    ): Task {
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
        val canonicalAssignment = if (employee != null) {
            AssignmentCommand(
                TaskAssigneeType.USER,
                (employee.userId ?: employee.id).toString(),
                employee.displayName
            )
        } else {
            request.assignment
        }
        val before = lifecycle.getTaskForHotel(taskId, hotelId)
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
        return updated
    }
}
