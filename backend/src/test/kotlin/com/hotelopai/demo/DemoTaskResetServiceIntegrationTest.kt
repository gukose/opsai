package com.hotelopai.demo

import com.hotelopai.auth.application.PermissionRepository
import com.hotelopai.auth.application.RoleRepository
import com.hotelopai.auth.application.UserRepository
import com.hotelopai.auth.domain.EmailAddress
import com.hotelopai.auth.domain.Permission
import com.hotelopai.auth.domain.Role
import com.hotelopai.auth.domain.User
import com.hotelopai.auth.domain.UserStatus
import com.hotelopai.employee.application.DepartmentRepository
import com.hotelopai.employee.application.EmployeeRepository
import com.hotelopai.employee.application.SkillRepository
import com.hotelopai.employee.domain.Department
import com.hotelopai.employee.domain.Employee
import com.hotelopai.employee.domain.Skill
import com.hotelopai.hotel.application.HotelRepository
import com.hotelopai.hotel.domain.Hotel
import com.hotelopai.shared.kernel.UuidV7Generator
import com.hotelopai.support.PostgresIntegrationTestSupport
import com.hotelopai.task.application.CreateTaskCommand
import com.hotelopai.task.application.TaskLifecycleService
import com.hotelopai.task.domain.TaskIntentType
import com.hotelopai.task.domain.TaskPriority
import com.hotelopai.task.domain.TaskSource
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

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DemoTaskResetServiceIntegrationTest : PostgresIntegrationTestSupport() {
    @Autowired lateinit var resetService: DemoTaskResetService
    @Autowired lateinit var hotels: HotelRepository
    @Autowired lateinit var permissions: PermissionRepository
    @Autowired lateinit var roles: RoleRepository
    @Autowired lateinit var users: UserRepository
    @Autowired lateinit var departments: DepartmentRepository
    @Autowired lateinit var skills: SkillRepository
    @Autowired lateinit var employees: EmployeeRepository
    @Autowired lateinit var tasks: TaskLifecycleService
    @Autowired lateinit var jdbc: NamedParameterJdbcTemplate

    @Test
    fun `reset removes demo task graph preserves master data isolates hotels and remains idempotent`() {
        val now = Instant.now()
        val demo = hotels.save(Hotel(code = DemoTaskResetService.DEMO_HOTEL_CODE, name = "Demo"))
        val other = hotels.save(Hotel(code = "other-${UuidV7Generator.generate()}", name = "Other"))
        val permission = permissions.save(Permission(code = "DEMO_RESET_TEST_${UuidV7Generator.generate()}", name = "Reset test"))
        val role = roles.save(Role(hotelId = demo.id, code = "ADMIN", name = "Admin", permissionIds = setOf(permission.id)))
        val user = users.save(User(hotelId = demo.id, email = EmailAddress.of("reset-${UuidV7Generator.generate()}@example.test"),
            displayName = "Reset Admin", passwordHash = "not-a-real-password-hash", roleIds = setOf(role.id), status = UserStatus.ACTIVE))
        val department = departments.save(Department(hotelId = demo.id, code = "RESET_HK", name = "Housekeeping"))
        val skill = skills.save(Skill(hotelId = demo.id, code = "RESET_CLEAN", name = "Cleaning"))
        val employee = employees.save(Employee(hotelId = demo.id, employeeNumber = "RESET-1", displayName = "Reset Employee",
            departmentId = department.id, skillIds = setOf(skill.id)))
        jdbc.update(
            """insert into workforce_shift(id,hotel_id,employee_id,planned_start,planned_end,actual_start,status,created_at,updated_at)
               values(:id,:hotel,:employee,:start,:end,:start,'WORKING',:start,:start)""",
            mapOf("id" to UuidV7Generator.generate(), "hotel" to demo.id, "employee" to employee.id,
                "start" to Timestamp.from(now.minusSeconds(60)), "end" to Timestamp.from(now.plusSeconds(3600)))
        )

        val first = create(demo.id, TaskIntentType.HOUSEKEEPING, "Reset housekeeping")
        val second = create(demo.id, TaskIntentType.PUBLIC_AREA, "Reset public area")
        val foreign = create(other.id, TaskIntentType.PUBLIC_AREA, "Keep foreign task")
        val workflowId = UuidV7Generator.generate()
        jdbc.update(
            """insert into housekeeping_workflow(id,hotel_id,task_id,workflow_type,room_number,status,inspection_required,
               working_seconds,paused_seconds,idempotency_key,version,created_at,updated_at)
               values(:id,:hotel,:task,'STAYOVER_CLEANING','305','CREATED',false,0,0,:key,0,:now,:now)""",
            mapOf("id" to workflowId, "hotel" to demo.id, "task" to first.id, "key" to "reset-workflow-$workflowId", "now" to Timestamp.from(now))
        )
        jdbc.update(
            """insert into task_interruption(id,hotel_id,employee_id,paused_task_id,interrupting_task_id,reason,status,
               idempotency_key,paused_at,created_at) values(:id,:hotel,:employee,:paused,:interrupting,'test','ACTIVE',:key,:now,:now)""",
            mapOf("id" to UuidV7Generator.generate(), "hotel" to demo.id, "employee" to employee.id, "paused" to first.id,
                "interrupting" to second.id, "key" to "reset-interruption-${UuidV7Generator.generate()}", "now" to Timestamp.from(now))
        )
        jdbc.update(
            """insert into task_assignment_audit(id,hotel_id,task_id,previous_assignee_id,new_assignee_id,reason_code,
               actor_user_id,action,created_at) values(:id,:hotel,:task,null,:employee,'MANUAL_SUPERVISOR',:actor,'ASSIGN',:now)""",
            mapOf("id" to UuidV7Generator.generate(), "hotel" to demo.id, "task" to first.id,
                "employee" to employee.id.toString(), "actor" to user.id, "now" to Timestamp.from(now))
        )
        val reservationId = UuidV7Generator.generate()
        val outboxId = UuidV7Generator.generate()
        val automationId = UuidV7Generator.generate()
        jdbc.update(
            """insert into reservation_snapshot(id,provider_id,external_reservation_reference,property_reference,
               reservation_status,stay_status,arrival_date,departure_date,occupancy_adults,occupancy_children,
               source,created_at,updated_at,version)
               values(:id,'demo','reset-reservation','hotel-opai-demo','CONFIRMED','IN_HOUSE',current_date,
               current_date + 1,1,0,'TEST',:now,:now,0)""",
            mapOf("id" to reservationId, "now" to Timestamp.from(now))
        )
        jdbc.update(
            """insert into operational_outbox(id,event_type,aggregate_type,aggregate_id,hotel_id,payload_json,status,
               attempt_count,next_attempt_at,created_at,updated_at)
               values(:id,'RESERVATION_IMPORTED','RESERVATION',:reservation,:hotel,'{}','PENDING',0,:now,:now,:now)""",
            mapOf("id" to outboxId, "reservation" to reservationId, "hotel" to demo.id, "now" to Timestamp.from(now))
        )
        jdbc.update(
            """insert into reservation_task_automation_execution(id,outbox_event_id,reservation_id,rule_id,rule_version,
               trigger_event_type,deduplication_key,outcome,created_task_id,created_at,updated_at,version)
               values(:id,:outbox,:reservation,'reset-rule',1,'RESERVATION_IMPORTED',:key,'CREATED',:task,:now,:now,0)""",
            mapOf("id" to automationId, "outbox" to outboxId, "reservation" to reservationId,
                "key" to "reset-automation-$automationId", "task" to first.id, "now" to Timestamp.from(now))
        )

        val masterCounts = masterCounts(demo.id)
        assertThat(resetService.status(demo.id).taskCount).isEqualTo(2)

        val result = resetService.resetTasks(demo.id)

        assertThat(result.tasksDeleted).isEqualTo(2)
        assertThat(result.relatedRecordsDeleted).isGreaterThan(0)
        assertThat(result.remainingTasks).isZero()
        assertThat(count("task", "hotel_id", demo.id)).isZero()
        assertThat(count("task_state_history", "hotel_id", demo.id)).isZero()
        assertThat(count("task_log", "hotel_id", demo.id)).isZero()
        assertThat(count("task_assignment_audit", "hotel_id", demo.id)).isZero()
        assertThat(count("housekeeping_workflow", "hotel_id", demo.id)).isZero()
        assertThat(count("task_interruption", "hotel_id", demo.id)).isZero()
        assertThat(count("operational_outbox", "hotel_id", demo.id)).isZero()
        assertThat(jdbc.queryForObject(
            "select count(*) from reservation_task_automation_execution where created_task_id=:task",
            mapOf("task" to first.id), Int::class.java
        )).isZero()
        assertThat(masterCounts(demo.id)).isEqualTo(masterCounts)
        assertThat(permissions.findByCode(permission.code)?.id).isEqualTo(permission.id)
        assertThat(roles.findByHotelIdAndCode(demo.id, "ADMIN")?.permissionIds).contains(permission.id)
        assertThat(users.findByHotelIdAndEmail(demo.id, user.email.value)?.id).isEqualTo(user.id)
        assertThat(count("task", "hotel_id", other.id)).isEqualTo(1)
        assertThat(tasks.getTaskForHotel(foreign.id.toString(), other.id).id).isEqualTo(foreign.id)

        assertThat(resetService.resetTasks(demo.id)).isEqualTo(DemoTaskResetResult(0, 0, 0))
        assertThat(resetService.status(demo.id)).isEqualTo(DemoTaskResetStatus(DemoTaskResetService.DEMO_HOTEL_CODE, 0))

        val createdAfterReset = create(demo.id, TaskIntentType.PUBLIC_AREA, "Created after reset")
        assertThat(tasks.getTaskForHotel(createdAfterReset.id.toString(), demo.id).id).isEqualTo(createdAfterReset.id)
    }

    private fun create(hotelId: java.util.UUID, intent: TaskIntentType, title: String) = tasks.createTask(
        CreateTaskCommand(hotelId, intent, TaskSource.MANUAL, title, title, "305", TaskPriority.MEDIUM,
            Instant.now().plus(1, ChronoUnit.HOURS))
    )

    private fun count(table: String, hotelColumn: String, hotelId: java.util.UUID): Int =
        jdbc.queryForObject("select count(*) from $table where $hotelColumn=:hotel", mapOf("hotel" to hotelId), Int::class.java) ?: 0

    private fun masterCounts(hotelId: java.util.UUID): List<Int> = listOf(
        count("app_user", "hotel_id", hotelId),
        count("role", "hotel_id", hotelId),
        count("employee", "hotel_id", hotelId),
        count("department", "hotel_id", hotelId),
        count("skill", "hotel_id", hotelId),
        count("workforce_shift", "hotel_id", hotelId)
    )
}
