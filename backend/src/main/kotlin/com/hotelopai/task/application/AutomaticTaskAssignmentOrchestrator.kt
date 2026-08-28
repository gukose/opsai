package com.hotelopai.task.application

import com.hotelopai.employee.application.DepartmentRepository
import com.hotelopai.employee.application.SkillRepository
import com.hotelopai.employee.domain.Employee
import com.hotelopai.employee.domain.EmployeeOperationalStatus
import com.hotelopai.observability.OperationalObservability
import com.hotelopai.task.domain.Task
import com.hotelopai.task.domain.TaskAssigneeType
import com.hotelopai.task.domain.TaskAssignment
import com.hotelopai.task.domain.TaskIntentType
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import com.zaxxer.hikari.HikariDataSource
import javax.sql.DataSource
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.sql.Timestamp
import java.util.UUID
import java.sql.ResultSet

data class AutomaticAssignmentResult(
    val assignment: TaskAssignment?,
    val reasonCode: String,
    val selectedEmployeeId: UUID? = null,
    val candidateCount: Int = 0
)

fun interface TaskCreationAssignmentOrchestrator {
    fun evaluate(task: Task, now: Instant): AutomaticAssignmentResult
}

data class AssignmentCandidateView(
    val assigneeId: String,
    val displayName: String,
    val skillCodes: Set<String>,
    val onShift: Boolean,
    val available: Boolean,
    val workload: Int,
    val score: Int
)

fun interface TaskAssignmentCandidateQuery {
    fun candidates(task: Task, now: Instant): List<AssignmentCandidateView>
}

object NoOpTaskCreationAssignmentOrchestrator : TaskCreationAssignmentOrchestrator {
    override fun evaluate(task: Task, now: Instant) =
        AutomaticAssignmentResult(null, "ORCHESTRATION_DISABLED")
}

@Service
class PersistedWorkforceTaskAssignmentOrchestrator(
    private val assignmentService: DeterministicAssignmentService,
    private val departmentRepository: DepartmentRepository,
    private val skillRepository: SkillRepository,
    private val jdbc: NamedParameterJdbcTemplate,
    private val observability: OperationalObservability = OperationalObservability.noop(),
    private val dataSource: DataSource
) : TaskCreationAssignmentOrchestrator, TaskAssignmentCandidateQuery {
    init { logger.info("HOUSEKEEPING_AUTO_ASSIGN_VERSION=floor-affinity-v2") }

    @Transactional(readOnly = true)
    override fun candidates(task: Task, now: Instant): List<AssignmentCandidateView> {
        val started = System.nanoTime()
        val requirementStarted = System.nanoTime()
        val requirement = resolveRequirement(task)
        val departmentMs = elapsedMs(requirementStarted)
        val loadedEmployees = loadCandidateEmployees(task.hotelId, now, requirement.requiredSkillId)
        val employees = loadedEmployees.employees
        val employeeQueryMs = loadedEmployees.queryMs
        val employeeMappingMs = loadedEmployees.mappingMs
        val excluded = employees.filter { it.primaryRoleCode?.let(::isSupervisoryRole) == true }.map(Employee::id).toSet()
        val shifts = loadedEmployees.activeShiftEmployeeIds
        val workloads = loadedEmployees.workloadByEmployeeId
        val skillLevels = loadedEmployees.employeeSkillLevels
        val activeTaskIds = loadedEmployees.activeTaskEmployeeIds
        val decision = assignmentService.evaluate(
            AssignmentCriteria(
                hotelId = task.hotelId,
                employees = employees,
                requiredSkillId = requirement.requiredSkillId,
                departmentId = requirement.departmentId,
                strictRequiredSkill = requirement.requiredSkillId != null,
                employeeSkillLevels = skillLevels,
                activeShiftEmployeeIds = shifts,
                requireActiveShift = true,
                workloadByEmployeeId = workloads,
                maximumWorkload = MAXIMUM_ACTIVE_WORKLOAD,
                activeTaskEmployeeIds = activeTaskIds,
                preferredArea = targetFloorNumber(task),
                unavailableEmployeeIds = excluded
            ),
            now
        )
        val skillCodeById = requirement.skillCodeById
        val rankedScore = decision.candidates.associate { it.employeeId to it.score }
        val rankedIds = decision.candidates.map { it.employeeId }
        val manualChoices = employees
            .filterNot { it.id in excluded }
            .filter { !requirement.departmentKnown || requirement.departmentId == null || it.departmentId == requirement.departmentId }
            .sortedWith(
                compareByDescending<Employee> { it.id in rankedIds }
                    .thenByDescending { requirement.requiredSkillId != null && requirement.requiredSkillId in it.skillIds }
                    .thenByDescending { it.id in shifts }
                    .thenByDescending { it.operationalStatus.acceptsNormalWork() }
                    .thenBy { workloads[it.id] ?: 0 }
                    .thenBy { it.employeeNumber }
            )
        val candidates = manualChoices.map { employee ->
                AssignmentCandidateView(
                    assigneeId = (employee.userId ?: employee.id).toString(),
                    displayName = employee.displayName,
                    skillCodes = employee.skillIds.mapNotNull(skillCodeById::get).toSet(),
                    onShift = employee.id in shifts,
                    available = employee.operationalStatus.acceptsNormalWork(),
                    workload = workloads[employee.id] ?: 0,
                    score = rankedScore[employee.id] ?: Int.MIN_VALUE
                )
        }
        logger.info(
            "event=assignment_candidates correlationId={} taskId={} departmentMs={} employeeQueryMs={} employeeMappingMs={} shiftMs={} workloadMs={} skillLevelMs={} activeTaskMs={} mappingMs={} dbMs={} externalMs={} totalMs={} candidateCount={} poolActive={} poolIdle={} poolPending={} poolMax={}",
            MDC.get("correlationId") ?: "unknown", task.id, departmentMs, employeeQueryMs, employeeMappingMs, 0,
            0, 0, 0, elapsedMs(started) - departmentMs - employeeQueryMs - employeeMappingMs,
            elapsedMs(started), 0, elapsedMs(started), candidates.size, pool().active, pool().idle, pool().pending, pool().max
        )
        return candidates
    }

    override fun evaluate(task: Task, now: Instant): AutomaticAssignmentResult {
        existing(task.id, task.hotelId)?.let { existing ->
            if (task.assignment != null) return existing.copy(assignment = task.assignment)
        }

        val requirement = resolveRequirement(task)
        val loadedEmployees = loadCandidateEmployees(task.hotelId, now, requirement.requiredSkillId)
        val employees = loadedEmployees.employees
        val supervisorEmployeeIds = employees
            .filter { employee -> employee.primaryRoleCode?.let(::isSupervisoryRole) == true }
            .map(Employee::id)
            .toSet()
        val assignableEmployees = employees.filterNot { it.id in supervisorEmployeeIds }
        val activeShiftIds = loadedEmployees.activeShiftEmployeeIds
        val workload = loadedEmployees.workloadByEmployeeId
        val activeTaskIds = loadedEmployees.activeTaskEmployeeIds
        val skillLevels = loadedEmployees.employeeSkillLevels

        val reason = noCandidateReason(
            employees = assignableEmployees,
            departmentId = requirement.departmentId,
            requiredSkillId = requirement.requiredSkillId,
            requiredSkillKnown = requirement.requiredSkillKnown,
            departmentKnown = requirement.departmentKnown,
            skillResolutionRequired = requirement.skillResolutionRequired,
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
                preferredArea = targetFloorNumber(task),
                unavailableEmployeeIds = supervisorEmployeeIds
            ),
            now
        ) else null

        val floorAffinity = housekeepingFloorAffinity(task, employees)
        val targetFloorNumber = targetFloorNumber(task)
        val rankedCandidates = decision?.candidates.orEmpty()
            .sortedWith(compareByDescending<AssignmentCandidate> { employees.firstOrNull { e -> e.id == it.employeeId }?.homeArea?.equals(targetFloorNumber, true) == true }
                .thenByDescending { floorAffinity[it.employeeId] ?: 0 }
                .thenBy { workload[it.employeeId] ?: 0 }
                .thenBy { it.employeeId.toString() })
        val targetFloorId = targetFloor(task)
        rankedCandidates.forEachIndexed { index, candidate ->
            val employee = employees.firstOrNull { it.id == candidate.employeeId }
            logger.info("event=housekeeping_auto_assignment_candidate roomNumber={} targetFloorId={} employeeId={} employeeNumber={} sameFloorActiveTaskCount={} activeWorkload={} activeShift={} rank={} selected={}", task.roomNumber, targetFloorId, candidate.employeeId, employee?.employeeNumber, floorAffinity[candidate.employeeId] ?: 0, workload[candidate.employeeId] ?: 0, candidate.employeeId in activeShiftIds, index + 1, index == 0)
        }
        val selectedEmployee = (rankedCandidates.firstOrNull()?.employeeId
            ?: decision?.assignment?.assigneeId?.let(UUID::fromString))
            ?.let { selectedId -> employees.firstOrNull { it.id == selectedId } }
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
            reasonCode = if (assignment != null) "AUTO_ASSIGNED" else reason ?: when (decision?.outcome) {
                "AMBIGUOUS" -> "AMBIGUOUS_CANDIDATES"
                else -> "MANUAL_ASSIGNMENT_REQUIRED"
            },
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

    private fun housekeepingFloorAffinity(task: Task, employees: List<Employee>): Map<UUID, Int> {
        if (task.intentType != TaskIntentType.HOUSEKEEPING || task.roomNumber.isNullOrBlank() || employees.isEmpty()) return emptyMap()
        return jdbc.query(
            """select t.assignee_id, count(*)::int
               from task t join room_master r on r.hotel_id=t.hotel_id and r.room_number=t.room_number
               where t.hotel_id=:hotel and r.floor_id=(select floor_id from room_master where hotel_id=:hotel and room_number=:room)
                 and t.status in ('ASSIGNED','STARTED','IN_PROGRESS','WAITING','OVERDUE')
               group by t.assignee_id""",
            mapOf("hotel" to task.hotelId, "room" to task.roomNumber)
        ) { rs, _ -> rs.getString(1) to rs.getInt(2) }
            .flatMap { (id, count) -> employees.filter { it.id.toString() == id || it.userId?.toString() == id }.map { it.id to count } }
            .toMap()
    }

    private fun targetFloor(task: Task): UUID? = task.roomNumber?.let {
        jdbc.query("select floor_id from room_master where hotel_id=:hotel and room_number=:room", mapOf("hotel" to task.hotelId, "room" to it)) { rs, _ -> rs.getObject(1, UUID::class.java) }.firstOrNull()
    }

    private fun targetFloorNumber(task: Task): String? = task.roomNumber?.let {
        jdbc.query("select floor_number from room_master r join hotel_floor f on f.id=r.floor_id where r.hotel_id=:hotel and r.room_number=:room", mapOf("hotel" to task.hotelId, "room" to it)) { rs, _ -> rs.getInt(1).toString() }.firstOrNull()
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
            listOf("electrical", "electric", "light", "lighting", "socket", "power").any(text::contains) -> "ELECTRICAL"
            listOf("plumbing", "pipe", "faucet", "toilet", "drain", "leak").any(text::contains) -> "PLUMBING"
            task.intentType == TaskIntentType.HOUSEKEEPING -> "ROOM_CLEANING"
            task.intentType == TaskIntentType.MINIBAR || "minibar" in text -> "MINIBAR"
            else -> null
        }
        val departments = departmentRepository.findByHotelId(task.hotelId)
        val skills = skillRepository.findByHotelId(task.hotelId)
        val skillCodeById = skills.associate { it.id to it.code }
        val department = departmentCode?.let { code -> departments.firstOrNull { it.code.equals(code, true) } }
        val skill = skillCode?.let { code ->
            skills.firstOrNull { it.code.equals(code, true) } ?:
                if (code == "HVAC_REPAIR") skills.firstOrNull { it.code.equals("HVAC", true) } else null
        }
        return Requirement(
            department?.id,
            skill?.id,
            skillCode,
            requiredSkillKnown = skillCode == null || skill != null,
            departmentKnown = departmentCode != null && department != null,
            skillResolutionRequired = task.intentType in setOf(TaskIntentType.MAINTENANCE, TaskIntentType.DAMAGE_REPORT),
            skillCodeById = skillCodeById
        )
    }

    private fun noCandidateReason(
        employees: List<Employee>, departmentId: UUID?, requiredSkillId: UUID?, requiredSkillKnown: Boolean,
        departmentKnown: Boolean, skillResolutionRequired: Boolean,
        activeShiftIds: Set<UUID>, workload: Map<UUID, Int>, maximumWorkload: Int
    ): String? {
        if (!departmentKnown) return "UNKNOWN_DEPARTMENT"
        if (skillResolutionRequired && requiredSkillId == null) return "UNKNOWN_REQUIRED_SKILL"
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

    /**
     * Candidate reads deliberately avoid EmployeeJpaEntity. Its lazy roleIds
     * and skillIds are materialized by the domain mapper, turning 35 employees
     * into dozens of SQL statements while holding the repository transaction.
     */
    private fun loadCandidateEmployees(hotelId: UUID, now: Instant, requiredSkillId: UUID?): LoadedEmployees {
        val queryStarted = System.nanoTime()
        var mappingMs = 0L
        val rows = jdbc.query(
            """select e.id,e.hotel_id,e.user_id,e.employee_number,e.display_name,e.department_id,
                      e.status,e.primary_role_code,e.supervisor_employee_id,e.home_area,e.languages,
                      e.operational_status,e.version,e.created_at,e.created_by,e.updated_at,e.updated_by,
                      coalesce(array_agg(distinct er.role_id) filter (where er.role_id is not null), '{}'::uuid[]) as role_ids,
                      coalesce(array_agg(distinct es.skill_id) filter (where es.skill_id is not null), '{}'::uuid[]) as skill_ids,
                      (select es2.skill_level from employee_skill es2 where es2.employee_id=e.id and es2.skill_id=:requiredSkill limit 1) as required_skill_level,
                      exists (select 1 from workforce_shift ws where ws.employee_id=e.id and ws.hotel_id=e.hotel_id
                                and ws.status in ('STARTED','WORKING')
                                and coalesce(ws.actual_start,ws.planned_start) <= :now
                                and coalesce(ws.actual_end,ws.planned_end) > :now) as on_shift,
                      (select count(*)::int from task t where t.hotel_id=e.hotel_id
                         and (t.assignee_id=e.id::text or t.assignee_id=e.user_id::text)
                         and t.status in ('ASSIGNED','STARTED','IN_PROGRESS','WAITING','OVERDUE')) as workload,
                      exists (select 1 from task at where at.hotel_id=e.hotel_id
                         and (at.assignee_id=e.id::text or at.assignee_id=e.user_id::text)
                         and at.status in ('STARTED','IN_PROGRESS')) as active_task
                 from employee e
                 left join employee_role er on er.employee_id=e.id
                 left join employee_skill es on es.employee_id=e.id
                where e.hotel_id=:hotel and e.status='ACTIVE'
                group by e.id,e.hotel_id,e.user_id,e.employee_number,e.display_name,e.department_id,
                         e.status,e.primary_role_code,e.supervisor_employee_id,e.home_area,e.languages,
                         e.operational_status,e.version,e.created_at,e.created_by,e.updated_at,e.updated_by
                order by e.employee_number""",
            mapOf("hotel" to hotelId, "now" to Timestamp.from(now), "requiredSkill" to requiredSkillId)
        ) { rs, _ ->
            val started = System.nanoTime()
            val employee = rs.toCandidateEmployee()
            mappingMs += elapsedMs(started)
            LoadedEmployeeRow(
                employee = employee,
                onShift = rs.getBoolean("on_shift"),
                workload = rs.getInt("workload"),
                activeTask = rs.getBoolean("active_task"),
                requiredSkillLevel = (rs.getObject("required_skill_level") as? Number)?.toInt()
            )
        }
        val employees = rows.map { it.employee }
        val activeShiftIds = rows.filter { it.onShift }.map { it.employee.id }.toSet()
        val workloads = rows.associate { it.employee.id to it.workload }
        val activeTasks = rows.filter { it.activeTask }.map { it.employee.id }.toSet()
        val skillLevels = rows.mapNotNull { row ->
            val level = row.requiredSkillLevel ?: return@mapNotNull null
            requiredSkillId?.let { row.employee.id to (it to level) }
        }.groupBy({ it.first }, { it.second }).mapValues { (_, value) -> value.toMap() }
        return LoadedEmployees(employees, activeShiftIds, workloads, activeTasks, skillLevels, elapsedMs(queryStarted) - mappingMs, mappingMs)
    }

    private fun ResultSet.toCandidateEmployee(): Employee = Employee(
        id = getObject("id", UUID::class.java),
        hotelId = getObject("hotel_id", UUID::class.java),
        userId = getObject("user_id", UUID::class.java),
        employeeNumber = getString("employee_number"),
        displayName = getString("display_name"),
        departmentId = getObject("department_id", UUID::class.java),
        roleIds = uuidArray("role_ids"),
        skillIds = uuidArray("skill_ids"),
        status = com.hotelopai.employee.domain.EmployeeStatus.valueOf(getString("status")),
        primaryRoleCode = getString("primary_role_code"),
        supervisorEmployeeId = getObject("supervisor_employee_id", UUID::class.java),
        homeArea = getString("home_area"),
        languages = stringArray("languages"),
        operationalStatus = EmployeeOperationalStatus.valueOf(getString("operational_status")),
        version = getLong("version"),
        createdAt = getTimestamp("created_at").toInstant(),
        createdBy = getString("created_by"),
        updatedAt = getTimestamp("updated_at").toInstant(),
        updatedBy = getString("updated_by")
    )

    private fun ResultSet.uuidArray(column: String): Set<UUID> =
        (getArray(column)?.array as? Array<*>)?.mapNotNull { it as? UUID }?.toSet() ?: emptySet()

    private fun ResultSet.stringArray(column: String): Set<String> =
        (getArray(column)?.array as? Array<*>)?.mapNotNull { it?.toString() }?.toSet() ?: emptySet()

    private data class LoadedEmployees(
        val employees: List<Employee>,
        val activeShiftEmployeeIds: Set<UUID>,
        val workloadByEmployeeId: Map<UUID, Int>,
        val activeTaskEmployeeIds: Set<UUID>,
        val employeeSkillLevels: Map<UUID, Map<UUID, Int>>,
        val queryMs: Long,
        val mappingMs: Long
    )

    private data class LoadedEmployeeRow(
        val employee: Employee,
        val onShift: Boolean,
        val workload: Int,
        val activeTask: Boolean,
        val requiredSkillLevel: Int?
    )

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
        val departmentId: UUID?, val requiredSkillId: UUID?, val requiredSkillCode: String?, val requiredSkillKnown: Boolean,
        val departmentKnown: Boolean, val skillResolutionRequired: Boolean, val skillCodeById: Map<UUID, String>
    )

    private fun isSupervisoryRole(roleCode: String): Boolean =
        roleCode == "GM" || roleCode == "ADMIN" || "SUPERVISOR" in roleCode || "MANAGER" in roleCode

    companion object {
        private const val MAXIMUM_ACTIVE_WORKLOAD = 5
        private const val RULE_VERSION = "automatic-assignment-v1"
        private val logger = LoggerFactory.getLogger(PersistedWorkforceTaskAssignmentOrchestrator::class.java)
    }

    private fun elapsedMs(started: Long): Long = (System.nanoTime() - started) / 1_000_000

    private fun pool(): PoolSnapshot {
        val hikari = dataSource as? HikariDataSource
        val bean = hikari?.hikariPoolMXBean
        return PoolSnapshot(bean?.activeConnections ?: -1, bean?.idleConnections ?: -1, bean?.threadsAwaitingConnection ?: -1, hikari?.maximumPoolSize ?: -1)
    }

    private data class PoolSnapshot(val active: Int, val idle: Int, val pending: Int, val max: Int)
}
