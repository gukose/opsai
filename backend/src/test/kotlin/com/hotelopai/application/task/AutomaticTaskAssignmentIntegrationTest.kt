package com.hotelopai.application.task

import com.hotelopai.employee.application.DepartmentRepository
import com.hotelopai.employee.application.EmployeeRepository
import com.hotelopai.employee.application.SkillRepository
import com.hotelopai.employee.domain.Department
import com.hotelopai.employee.domain.Employee
import com.hotelopai.employee.domain.EmployeeOperationalStatus
import com.hotelopai.employee.domain.Skill
import com.hotelopai.hotel.application.HotelRepository
import com.hotelopai.hotel.domain.Hotel
import com.hotelopai.outbox.application.OperationalOutboxProcessor
import com.hotelopai.notification.application.NotificationRepository
import com.hotelopai.shared.kernel.UuidV7Generator
import com.hotelopai.support.PostgresIntegrationTestSupport
import com.hotelopai.task.application.AssignmentCommand
import com.hotelopai.task.application.CreateTaskCommand
import com.hotelopai.task.application.TaskLifecycleService
import com.hotelopai.task.application.TaskCreationAssignmentOrchestrator
import com.hotelopai.task.domain.TaskAssigneeType
import com.hotelopai.task.domain.TaskIntentType
import com.hotelopai.task.domain.TaskPriority
import com.hotelopai.task.domain.TaskSource
import com.hotelopai.task.domain.TaskStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.sql.Timestamp
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AutomaticTaskAssignmentIntegrationTest : PostgresIntegrationTestSupport() {
    @Autowired lateinit var hotels: HotelRepository
    @Autowired lateinit var departments: DepartmentRepository
    @Autowired lateinit var skills: SkillRepository
    @Autowired lateinit var employees: EmployeeRepository
    @Autowired lateinit var tasks: TaskLifecycleService
    @Autowired lateinit var jdbc: NamedParameterJdbcTemplate
    @Autowired lateinit var outbox: OperationalOutboxProcessor
    @Autowired lateinit var notifications: NotificationRepository
    @Autowired lateinit var assignmentOrchestrator: TaskCreationAssignmentOrchestrator

    @Test
    fun `voice HVAC task selects persisted on-shift technician and notifies assignee`() {
        val f = fixture("voice", "MAINTENANCE", "HVAC_REPAIR")
        val technician = employee(f, "TECH-1", setOf(f.skill.id))
        activeShift(f.hotel.id, technician.id)

        val task = create(f.hotel.id, TaskIntentType.MAINTENANCE, "Room 302 air conditioning is not working", TaskSource.ASSISTANT)

        assertThat(task.status).isEqualTo(TaskStatus.ASSIGNED)
        assertThat(task.assignment?.assigneeId).isEqualTo(technician.id.toString())
        assertThat(task.unassignedReasonCode).isNull()
        outbox.processBatch()
        assertThat(notifications.findAccessible(f.hotel.id, technician.id, emptySet())).anyMatch { it.sourceTaskId == task.id }
    }

    @Test
    fun `voice HVAC without matching skill remains unassigned with safe reason`() {
        val f = fixture("no-skill", "MAINTENANCE", "HVAC_REPAIR")
        val employee = employee(f, "TECH-2", emptySet())
        activeShift(f.hotel.id, employee.id)

        val task = create(f.hotel.id, TaskIntentType.MAINTENANCE, "Room 302 HVAC is broken", TaskSource.ASSISTANT)

        assertThat(task.status).isEqualTo(TaskStatus.CREATED)
        assertThat(task.unassignedReasonCode).isEqualTo("NO_REQUIRED_SKILL")
    }

    @Test
    fun `checkout housekeeping task selects on-shift room attendant`() {
        val f = fixture("checkout", "HOUSEKEEPING", "ROOM_CLEANING")
        val housekeeper = employee(f, "HK-1", setOf(f.skill.id))
        activeShift(f.hotel.id, housekeeper.id)

        val task = create(f.hotel.id, TaskIntentType.HOUSEKEEPING, "Departure cleaning", TaskSource.IMPORT)

        assertThat(task.status).isEqualTo(TaskStatus.ASSIGNED)
        assertThat(task.assignment?.assigneeId).isEqualTo(housekeeper.id.toString())
    }

    @Test
    fun `guest towel request selects eligible housekeeping employee`() {
        val f = fixture("guest", "HOUSEKEEPING", "ROOM_CLEANING")
        val housekeeper = employee(f, "HK-2", setOf(f.skill.id))
        activeShift(f.hotel.id, housekeeper.id)

        val task = create(f.hotel.id, TaskIntentType.GUEST_REQUEST, "Guest requests towels", TaskSource.API)

        assertThat(task.status).isEqualTo(TaskStatus.ASSIGNED)
        assertThat(task.assignment?.assigneeId).isEqualTo(housekeeper.id.toString())
    }

    @Test
    fun `off-shift unavailable and wrong-skill employees are never selected`() {
        val f = fixture("eligibility", "MAINTENANCE", "HVAC_REPAIR")
        employee(f, "OFF-SHIFT", setOf(f.skill.id))
        val unavailable = employee(f, "UNAVAILABLE", setOf(f.skill.id), EmployeeOperationalStatus.BREAK)
        activeShift(f.hotel.id, unavailable.id)
        val wrongSkill = employee(f, "WRONG-SKILL", emptySet())
        activeShift(f.hotel.id, wrongSkill.id)

        val task = create(f.hotel.id, TaskIntentType.MAINTENANCE, "HVAC failure", TaskSource.ASSISTANT)

        assertThat(task.status).isEqualTo(TaskStatus.CREATED)
        assertThat(task.unassignedReasonCode).isEqualTo("NO_REQUIRED_SKILL")
    }

    @Test
    fun `workload ranking chooses lower-load candidate deterministically`() {
        val f = fixture("workload", "MAINTENANCE", "HVAC_REPAIR")
        val busy = employee(f, "TECH-01", setOf(f.skill.id))
        val available = employee(f, "TECH-02", setOf(f.skill.id))
        activeShift(f.hotel.id, busy.id)
        activeShift(f.hotel.id, available.id)
        create(f.hotel.id, TaskIntentType.MAINTENANCE, "Existing generic repair", TaskSource.MANUAL,
            AssignmentCommand(TaskAssigneeType.USER, busy.id.toString(), busy.displayName))

        val task = create(f.hotel.id, TaskIntentType.MAINTENANCE, "Room 302 HVAC failure", TaskSource.ASSISTANT)

        assertThat(task.assignment?.assigneeId).isEqualTo(available.id.toString())
    }

    @Test
    fun `foreign hotel employee is isolated and cannot be assigned`() {
        val local = hotels.save(Hotel(code = "local-${UuidV7Generator.generate()}", name = "Local"))
        val foreign = fixture("foreign", "MAINTENANCE", "HVAC_REPAIR")
        val technician = employee(foreign, "FOREIGN-1", setOf(foreign.skill.id))
        activeShift(foreign.hotel.id, technician.id)

        val task = create(local.id, TaskIntentType.MAINTENANCE, "HVAC failure", TaskSource.ASSISTANT)

        assertThat(task.status).isEqualTo(TaskStatus.CREATED)
        assertThat(task.assignment).isNull()
    }

    @Test
    fun `duplicate orchestration keeps persisted assignee and a single audit outcome`() {
        val f = fixture("duplicate", "MAINTENANCE", "HVAC_REPAIR")
        val technician = employee(f, "TECH-DEDUP", setOf(f.skill.id))
        activeShift(f.hotel.id, technician.id)
        val task = create(f.hotel.id, TaskIntentType.MAINTENANCE, "Room 302 HVAC failure", TaskSource.ASSISTANT)

        val repeated = assignmentOrchestrator.evaluate(task, task.updatedAt)

        assertThat(repeated.assignment).isEqualTo(task.assignment)
        assertThat(jdbc.queryForObject("select count(*) from task_assignment_orchestration where task_id=:task",
            mapOf("task" to task.id), Long::class.java)).isEqualTo(1L)
    }

    private fun fixture(suffix: String, departmentCode: String, skillCode: String): Fixture {
        val hotel = hotels.save(Hotel(code = "$suffix-${UuidV7Generator.generate()}", name = suffix))
        val department = departments.save(Department(hotelId = hotel.id, code = departmentCode, name = departmentCode))
        val skill = skills.save(Skill(hotelId = hotel.id, code = skillCode, name = skillCode))
        return Fixture(hotel, department, skill)
    }

    private fun employee(f: Fixture, number: String, skillIds: Set<UUID>, status: EmployeeOperationalStatus = EmployeeOperationalStatus.AVAILABLE) =
        employees.save(Employee(hotelId = f.hotel.id, employeeNumber = number, displayName = number,
            departmentId = f.department.id, skillIds = skillIds, operationalStatus = status))

    private fun activeShift(hotelId: UUID, employeeId: UUID) {
        val now = Instant.now()
        jdbc.update(
            """insert into workforce_shift(id,hotel_id,employee_id,planned_start,planned_end,actual_start,status,created_at,updated_at)
               values(:id,:hotel,:employee,:start,:end,:start,'WORKING',:start,:start)""",
            mapOf("id" to UuidV7Generator.generate(), "hotel" to hotelId, "employee" to employeeId,
                "start" to Timestamp.from(now.minus(1, ChronoUnit.HOURS)), "end" to Timestamp.from(now.plus(8, ChronoUnit.HOURS)))
        )
    }

    private fun create(hotelId: UUID, intent: TaskIntentType, title: String, source: TaskSource, assignment: AssignmentCommand? = null) =
        tasks.createTask(CreateTaskCommand(hotelId, intent, source, title, title, "302", TaskPriority.HIGH,
            Instant.now().plus(1, ChronoUnit.HOURS), assignment))

    data class Fixture(val hotel: Hotel, val department: Department, val skill: Skill)
}
