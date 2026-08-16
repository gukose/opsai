package com.hotelopai.task.application

import com.hotelopai.employee.application.EmployeeRepository
import com.hotelopai.employee.domain.Employee
import com.hotelopai.notification.application.NotificationRepository
import com.hotelopai.notification.domain.Notification
import com.hotelopai.shared.kernel.UuidV7Generator
import com.hotelopai.task.domain.Task
import com.hotelopai.task.domain.TaskAssigneeType
import com.hotelopai.task.domain.TaskIntentType
import com.hotelopai.task.domain.TaskPriority
import com.hotelopai.task.domain.TaskSource
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class SupervisorTaskAssignmentServiceTest {
    private val now = Instant.parse("2026-08-16T10:00:00Z")

    @Test
    fun `assignment has no clock default argument bridge`() {
        assertThat(SupervisorTaskAssignmentService::class.java.declaredMethods)
            .noneMatch { it.name == "assign\$default" }
    }

    @Test
    fun `manual assignment and reassignment use canonical same-hotel employees and notify`() {
        val hotelId = UuidV7Generator.generate()
        val repository = InMemoryTasks()
        val task = repository.save(task(hotelId))
        val first = employee(hotelId, "TECH-1")
        val second = employee(hotelId, "TECH-2")
        val notifications = RecordingNotifications()
        val service = SupervisorTaskAssignmentService(
            TaskLifecycleService(repository),
            Employees(listOf(first, second)),
            mock(NamedParameterJdbcTemplate::class.java),
            notifications,
            Clock.fixed(now, ZoneOffset.UTC)
        )

        val assigned = service.assign(task.id.toString(), hotelId, UuidV7Generator.generate(), command(first))
        val reassignmentService = SupervisorTaskAssignmentService(
            TaskLifecycleService(repository), Employees(listOf(first, second)),
            mock(NamedParameterJdbcTemplate::class.java), notifications,
            Clock.fixed(now.plusSeconds(60), ZoneOffset.UTC)
        )
        val reassigned = reassignmentService.assign(task.id.toString(), hotelId, UuidV7Generator.generate(), command(second))

        assertThat(assigned.assignment?.assigneeId).isEqualTo(first.userId.toString())
        assertThat(assigned.assignment?.assignedAt).isEqualTo(now)
        assertThat(reassigned.assignment?.assigneeId).isEqualTo(second.userId.toString())
        assertThat(reassigned.assignment?.assignedAt).isEqualTo(now.plusSeconds(60))
        assertThat(notifications.items).hasSize(2)
    }

    @Test
    fun `cross-hotel employee is rejected`() {
        val hotelId = UuidV7Generator.generate()
        val repository = InMemoryTasks()
        val task = repository.save(task(hotelId))
        val foreign = employee(UuidV7Generator.generate(), "FOREIGN")
        val service = SupervisorTaskAssignmentService(
            TaskLifecycleService(repository), Employees(listOf(foreign)),
            mock(NamedParameterJdbcTemplate::class.java), RecordingNotifications(), Clock.fixed(now, ZoneOffset.UTC)
        )

        assertThatThrownBy {
            service.assign(task.id.toString(), hotelId, UuidV7Generator.generate(), command(foreign))
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    private fun command(employee: Employee) = AssignTaskCommand(
        AssignmentCommand(TaskAssigneeType.USER, employee.userId.toString(), "Untrusted display name")
    )

    private fun employee(hotelId: UUID, number: String) = Employee(
        hotelId = hotelId, userId = UuidV7Generator.generate(), employeeNumber = number, displayName = number
    )

    private fun task(hotelId: UUID) = Task.create(
        hotelId, TaskIntentType.MAINTENANCE, TaskSource.ASSISTANT, "HVAC", "HVAC", "302",
        TaskPriority.HIGH, now.plusSeconds(3600), now
    )

    private class Employees(private val values: List<Employee>) : EmployeeRepository {
        override fun save(employee: Employee) = employee
        override fun findById(id: UUID) = values.firstOrNull { it.id == id }
        override fun findByHotelId(hotelId: UUID) = values.filter { it.hotelId == hotelId }
        override fun findByHotelIdAndEmployeeNumber(hotelId: UUID, employeeNumber: String) =
            values.firstOrNull { it.hotelId == hotelId && it.employeeNumber == employeeNumber }
        override fun findByUserId(userId: UUID) = values.firstOrNull { it.userId == userId }
    }

    private class InMemoryTasks : TaskRepository {
        private val values = mutableMapOf<UUID, Task>()
        override fun save(task: Task) = task.also { values[it.id] = it }
        override fun findById(id: UUID) = values[id]
        override fun findAll() = values.values.toList()
        override fun findPage(request: TaskPageRequest) = TaskPage(findAll(), 0, request.size, values.size.toLong())
    }

    private class RecordingNotifications : NotificationRepository {
        val items = mutableListOf<Notification>()
        override fun save(notification: Notification) = notification.also(items::add)
        override fun findById(id: UUID) = items.firstOrNull { it.id == id }
        override fun findBySourceEventId(sourceEventId: UUID) = items.firstOrNull { it.sourceEventId == sourceEventId }
        override fun findTaskCreatedBySourceTaskId(sourceTaskId: UUID) = null
        override fun findAccessible(hotelId: UUID, userId: UUID, roleCodes: Set<String>) = emptyList<Notification>()
        override fun countBySourceTaskId(sourceTaskId: UUID) = items.count { it.sourceTaskId == sourceTaskId }.toLong()
        override fun countBySourceEventId(sourceEventId: UUID) = items.count { it.sourceEventId == sourceEventId }.toLong()
    }
}
