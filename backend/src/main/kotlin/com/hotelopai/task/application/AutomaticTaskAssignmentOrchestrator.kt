package com.hotelopai.task.application

import com.hotelopai.employee.application.DepartmentRepository
import com.hotelopai.employee.application.EmployeeRepository
import com.hotelopai.employee.application.SkillRepository
import com.hotelopai.employee.domain.Employee
import com.hotelopai.employee.domain.EmployeeOperationalStatus
import com.hotelopai.employee.domain.EmployeeStatus
import com.hotelopai.observability.OperationalObservability
import com.hotelopai.task.domain.Task
import com.hotelopai.task.domain.TaskAssigneeType
import com.hotelopai.task.domain.TaskAssignment
import com.hotelopai.task.domain.TaskIntentType
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import java.time.Instant
import java.sql.Timestamp
import java.util.UUID

data class AutomaticAssignmentResult(
    val assignment: TaskAssignment?,
    val reasonCode: String,
    val selectedEmployeeId: UUID? = null,
    val candidateCount: Int = 0
)

fun interface TaskCreationAssignmentOrchestrator {
    fun evaluate(task: Task, now: Instant): AutomaticAssignmentResult
}

object NoOpTaskCreationAssignmentOrchestrator : TaskCreationAssignmentOrchestrator {
    override fun evaluate(task: Task, now: Instant) =
        AutomaticAssignmentResult(null, "ORCHESTRATION_DISABLED")
}

@Service
class PersistedWorkforceTaskAssignmentOrchestrator(
    private val assignmentService: DeterministicAssignmentService,
    private val employeeRepository: EmployeeRepository,
    private val departmentRepository: DepartmentRepository,
    private val skillRepository: SkillRepository,
    private val jdbc: NamedParameterJdbcTemplate,
    private val observability: OperationalObservability = OperationalObservability.noop()
) : TaskCreationAssignmentOrchestrator {

    override fun evaluate(task: Task, now: Instant): AutomaticAssignmentResult {
        existing(task.id, task.hotelId)?.let { existing ->
            if (task.assignment != null) return existing.copy(assignment = task.assignment)
        }

        val requirement = resolveRequirement(task)
        val employees = employeeRepository.findByHotelId(task.hotelId)
            .filter { it.status == EmployeeStatus.ACTIVE }
        val activeShiftIds = activeShiftEmployeeIds(task.hotelId, now)
        val workload = workload(task.hotelId)
        val activeTaskIds = activeTaskEmployeeIds(task.hotelId)
        val skillLevels = skillLevels(task.hotelId)

        val reason = noCandidateReason(
            employees = employees,
            departmentId = requirement.departmentId,
            requiredSkillId = requirement.requiredSkillId,
            requiredSkillKnown = requirement.requiredSkillKnown,
            activeShiftIds = activeShiftIds,
            workload = workload,
            maximumWorkload = MAXIMUM_ACTIVE_WORKLOAD
        )

        val decision = if (reason == null) assignmentService.evaluate(
            AssignmentCriteria(
                hotelId = task.hotelId,
                requiredSkillId = requirement.requiredSkillId,
                departmentId = requirement.departmentId,
                minimumSkillLevel = 1,
                strictRequiredSkill = requirement.requiredSkillCode != null,
                employeeSkillLevels = skillLevels,
                activeShiftEmployeeIds = activeShiftIds,
                requireActiveShift = true,
                workloadByEmployeeId = workload,
                maximumWorkload = MAXIMUM_ACTIVE_WORKLOAD,
                activeTaskEmployeeIds = activeTaskIds,
                preferredArea = task.roomNumber
            ),
            now
        ) else null

        val selectedEmployee = decision?.assignment?.assigneeId
            ?.let(UUID::fromString)
            ?.let(employeeRepository::findById)
            ?.takeIf { it.hotelId == task.hotelId }
        val assignment = selectedEmployee?.let { employee ->
            TaskAssignment(
                assigneeType = TaskAssigneeType.USER,
                assigneeId = (employee.userId ?: employee.id).toString(),
                displayName = employee.displayName,
                assignedAt = now
            )
        }
        val result = AutomaticAssignmentResult(
            assignment = assignment,
            reasonCode = if (assignment != null) "AUTO_ASSIGNED" else reason ?: "MANUAL_ASSIGNMENT_REQUIRED",
            selectedEmployeeId = selectedEmployee?.id,
            candidateCount = decision?.candidates?.size ?: 0
        )
        persist(task, result, selectedEmployee?.userId, now)
        observability.incrementCounter(
            "hotelopai.task.auto_assignment.total",
            "outcome" to if (assignment == null) "unassigned" else "assigned",
            "reason_code" to result.reasonCode.lowercase()
        )
        return result
    }

    private fun resolveRequirement(task: Task): Requirement {
        val text = "${task.title} ${task.description}".lowercase()
        val departmentCode = when (task.intentType) {
            TaskIntentType.MAINTENANCE, TaskIntentType.DAMAGE_REPORT -> "MAINTENANCE"
            TaskIntentType.HOUSEKEEPING, TaskIntentType.MINIBAR, TaskIntentType.FLASH_TASK,
            TaskIntentType.LAUNDRY, TaskIntentType.TRAY_REMOVAL -> "HOUSEKEEPING"
            TaskIntentType.GUEST_REQUEST -> if (listOf("towel", "havlu", "pillow", "yastık", "water", "su").any(text::contains)) "HOUSEKEEPING" else "FRONT_OFFICE"
            else -> null
        }
        val skillCode = when {
            listOf("hvac", "air condition", "air-conditioning", "klima").any(text::contains) -> "HVAC_REPAIR"
            task.intentType == TaskIntentType.HOUSEKEEPING -> "ROOM_CLEANING"
            task.intentType == TaskIntentType.MINIBAR || "minibar" in text -> "MINIBAR"
            else -> null
        }
        val departments = departmentRepository.findByHotelId(task.hotelId)
        val skills = skillRepository.findByHotelId(task.hotelId)
        val department = departmentCode?.let { code -> departments.firstOrNull { it.code.equals(code, true) } }
        val skill = skillCode?.let { code ->
            skills.firstOrNull { it.code.equals(code, true) } ?:
                if (code == "HVAC_REPAIR") skills.firstOrNull { it.code.equals("HVAC", true) } else null
        }
        return Requirement(department?.id, skill?.id, skillCode, skillCode == null || skill != null)
    }

    private fun noCandidateReason(
        employees: List<Employee>, departmentId: UUID?, requiredSkillId: UUID?, requiredSkillKnown: Boolean,
        activeShiftIds: Set<UUID>, workload: Map<UUID, Int>, maximumWorkload: Int
    ): String? {
        val departmental = employees.filter { departmentId == null || it.departmentId == departmentId }
        if (departmental.none { it.id in activeShiftIds }) return "NO_ACTIVE_SHIFT"
        val onShift = departmental.filter { it.id in activeShiftIds }
        val available = onShift.filter { it.operationalStatus.acceptsNormalWork() }
        if (available.isEmpty()) return "NO_AVAILABLE_EMPLOYEE"
        if (!requiredSkillKnown || (requiredSkillId != null && available.none { requiredSkillId in it.skillIds })) return "NO_REQUIRED_SKILL"
        val skilled = if (requiredSkillId == null) available else available.filter { requiredSkillId in it.skillIds }
        if (skilled.none { (workload[it.id] ?: 0) < maximumWorkload }) return "CAPACITY_EXCEEDED"
        return null
    }

    private fun activeShiftEmployeeIds(hotelId: UUID, now: Instant): Set<UUID> = jdbc.query(
        """select distinct employee_id from workforce_shift
           where hotel_id=:hotel and status in ('STARTED','WORKING')
             and coalesce(actual_start, planned_start) <= :now
             and coalesce(actual_end, planned_end) > :now""",
        mapOf("hotel" to hotelId, "now" to Timestamp.from(now))
    ) { rs, _ -> rs.getObject(1, UUID::class.java) }.toSet()

    private fun workload(hotelId: UUID): Map<UUID, Int> = jdbc.query(
        """select e.id, count(t.id)::int
           from employee e left join task t on t.hotel_id=e.hotel_id
             and (t.assignee_id=e.id::text or t.assignee_id=e.user_id::text)
             and t.status in ('ASSIGNED','STARTED','IN_PROGRESS','WAITING','OVERDUE')
           where e.hotel_id=:hotel group by e.id""",
        mapOf("hotel" to hotelId)
    ) { rs, _ -> rs.getObject(1, UUID::class.java) to rs.getInt(2) }.toMap()

    private fun activeTaskEmployeeIds(hotelId: UUID): Set<UUID> = jdbc.query(
        """select distinct e.id from employee e join task t on t.hotel_id=e.hotel_id
             and (t.assignee_id=e.id::text or t.assignee_id=e.user_id::text)
           where e.hotel_id=:hotel and t.status in ('STARTED','IN_PROGRESS')""",
        mapOf("hotel" to hotelId)
    ) { rs, _ -> rs.getObject(1, UUID::class.java) }.toSet()

    private fun skillLevels(hotelId: UUID): Map<UUID, Map<UUID, Int>> = jdbc.query(
        """select es.employee_id, es.skill_id, es.skill_level from employee_skill es
           join employee e on e.id=es.employee_id where e.hotel_id=:hotel""",
        mapOf("hotel" to hotelId)
    ) { rs, _ -> Triple(rs.getObject(1, UUID::class.java), rs.getObject(2, UUID::class.java), rs.getInt(3)) }
        .groupBy({ it.first }, { it.second to it.third })
        .mapValues { (_, values) -> values.toMap() }

    private fun persist(task: Task, result: AutomaticAssignmentResult, selectedUserId: UUID?, now: Instant) {
        jdbc.update(
            """insert into task_assignment_orchestration(task_id,hotel_id,outcome,reason_code,selected_employee_id,
               selected_user_id,candidate_count,assignment_source,rule_version,created_at,updated_at)
               values(:task,:hotel,:outcome,:reason,:employee,:userId,:count,:source,:version,:now,:now)
               on conflict(task_id) do nothing""",
            mapOf(
                "task" to task.id, "hotel" to task.hotelId,
                "outcome" to if (result.assignment == null) "UNASSIGNED" else "ASSIGNED",
                "reason" to result.reasonCode, "employee" to result.selectedEmployeeId,
                "userId" to selectedUserId, "count" to result.candidateCount,
                "source" to task.source.name, "version" to RULE_VERSION, "now" to Timestamp.from(now)
            )
        )
    }

    private fun existing(taskId: UUID, hotelId: UUID): AutomaticAssignmentResult? = jdbc.query(
        """select outcome,reason_code,selected_employee_id,candidate_count from task_assignment_orchestration
           where task_id=:task and hotel_id=:hotel""",
        mapOf("task" to taskId, "hotel" to hotelId)
    ) { rs, _ -> AutomaticAssignmentResult(null, rs.getString("reason_code"), rs.getObject("selected_employee_id", UUID::class.java), rs.getInt("candidate_count")) }
        .firstOrNull()

    private data class Requirement(
        val departmentId: UUID?, val requiredSkillId: UUID?, val requiredSkillCode: String?, val requiredSkillKnown: Boolean
    )

    companion object {
        private const val MAXIMUM_ACTIVE_WORKLOAD = 5
        private const val RULE_VERSION = "automatic-assignment-v1"
    }
}
