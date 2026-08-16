package com.hotelopai.task.application

import com.hotelopai.task.domain.TaskAssigneeType
import com.hotelopai.task.domain.TaskAssignment
import com.hotelopai.employee.application.EmployeeRepository
import com.hotelopai.employee.domain.Employee
import com.hotelopai.employee.domain.EmployeeStatus
import com.hotelopai.employee.domain.EmployeeOperationalStatus
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

interface DeterministicAssignmentService {
    fun assign(criteria: AssignmentCriteria, now: Instant = Instant.now()): TaskAssignment?
    fun evaluate(criteria: AssignmentCriteria, now: Instant = Instant.now()): AssignmentDecision
}

data class AssignmentCriteria(
    val hotelId: UUID,
    val employees: List<Employee>? = null,
    val requiredSkillId: UUID? = null,
    val departmentId: UUID? = null,
    val requiredRoleId: UUID? = null,
    val minimumSkillLevel: Int = 1,
    val strictRequiredSkill: Boolean = false,
    val employeeSkillLevels: Map<UUID, Map<UUID, Int>> = emptyMap(),
    val activeShiftEmployeeIds: Set<UUID> = emptySet(),
    val requireActiveShift: Boolean = false,
    val workloadByEmployeeId: Map<UUID, Int> = emptyMap(),
    val maximumWorkload: Int = Int.MAX_VALUE,
    val activeTaskEmployeeIds: Set<UUID> = emptySet(),
    val preferredArea: String? = null,
    val requiredLanguage: String? = null,
    val unavailableEmployeeIds: Set<UUID> = emptySet(),
    val emergency: Boolean = false
)

data class AssignmentCandidate(val employeeId: UUID, val displayName: String, val score: Int, val reasons: List<String>)
data class AssignmentDecision(val assignment: TaskAssignment?, val candidates: List<AssignmentCandidate>, val outcome: String, val explanation: String)

@Service
class DefaultDeterministicAssignmentService(
    private val employeeRepository: EmployeeRepository
) : DeterministicAssignmentService {
    override fun assign(criteria: AssignmentCriteria, now: Instant): TaskAssignment? {
        return evaluate(criteria, now).assignment
    }

    override fun evaluate(criteria: AssignmentCriteria, now: Instant): AssignmentDecision {
        val baseCandidates = (criteria.employees ?: employeeRepository.findByHotelId(criteria.hotelId))
            .asSequence()
            .filter { it.status == EmployeeStatus.ACTIVE }
            .filter { it.operationalStatus.acceptsNormalWork() }
            .filterNot { it.id in criteria.unavailableEmployeeIds }
            .filter { !criteria.requireActiveShift || it.id in criteria.activeShiftEmployeeIds }
            .filter { criteria.departmentId == null || it.departmentId == criteria.departmentId }
            .filter { (criteria.workloadByEmployeeId[it.id] ?: 0) < criteria.maximumWorkload }
            .filter { criteria.requiredRoleId == null || criteria.requiredRoleId in it.roleIds }
            .filter { criteria.requiredLanguage == null || it.languages.any { language -> language.equals(criteria.requiredLanguage, true) } }
            .toList()
        val skillMatchedCandidates = baseCandidates.filter { employee -> skillLevel(employee, criteria) >= criteria.minimumSkillLevel }
        val eligibleCandidates = when {
            criteria.requiredSkillId == null -> baseCandidates
            skillMatchedCandidates.isNotEmpty() -> skillMatchedCandidates
            criteria.strictRequiredSkill -> emptyList()
            else -> baseCandidates
        }
        val ranked = eligibleCandidates
            .asSequence()
            .map { candidate -> candidate to score(candidate, criteria) }
            .sortedWith(
                compareByDescending<Pair<Employee, Pair<Int,List<String>>>> { it.second.first }
                    .thenBy { it.first.employeeNumber }
                    .thenBy { it.first.displayName }
            )
            .toList()
        val candidates = ranked.map { (employee, scored) -> AssignmentCandidate(employee.id,employee.displayName,scored.first,scored.second) }
        val selected = ranked.firstOrNull()?.first
        if(selected==null) return AssignmentDecision(null,candidates,"NO_CANDIDATE",if(criteria.emergency) "No eligible candidate; escalate to supervisor" else "No eligible candidate matched availability, shift, role, skill, and language rules")
        if (ranked.size > 1 && ranked[0].second.first == ranked[1].second.first) {
            return AssignmentDecision(
                assignment = null,
                candidates = candidates,
                outcome = "AMBIGUOUS",
                explanation = "Multiple equally suitable candidates require supervisor assignment"
            )
        }
        val assignment = TaskAssignment(
            assigneeType = TaskAssigneeType.USER,
            assigneeId = selected.id.toString(),
            displayName = selected.displayName,
            assignedAt = now
        )
        return AssignmentDecision(assignment,candidates,"ASSIGNED","Selected ${selected.displayName} by deterministic operational ranking")
    }

    private fun skillLevel(employee: Employee, criteria: AssignmentCriteria): Int {
        val skillId = criteria.requiredSkillId ?: return criteria.minimumSkillLevel
        return criteria.employeeSkillLevels[employee.id]?.get(skillId)
            ?: if (skillId in employee.skillIds) 1 else 0
    }

    private fun score(employee: Employee, criteria: AssignmentCriteria): Pair<Int,List<String>> {
        var score = 0
        val reasons=mutableListOf<String>()

        if (criteria.requiredSkillId != null && criteria.requiredSkillId in employee.skillIds) {
            score += 200
            reasons += "required_skill"
        }

        if (criteria.departmentId != null && employee.departmentId == criteria.departmentId) {
            score += 100
            reasons += "department"
        }
        if(criteria.preferredArea!=null && employee.homeArea?.equals(criteria.preferredArea,true)==true){ score+=40; reasons += "home_area" }
        if(employee.operationalStatus==EmployeeOperationalStatus.AVAILABLE){ score+=50; reasons += "available" }
        if(employee.id !in criteria.activeTaskEmployeeIds){ score+=30; reasons += "no_active_task" }
        val workload=criteria.workloadByEmployeeId[employee.id] ?: 0
        score -= workload.coerceAtLeast(0)*10
        reasons += "workload_$workload"
        return score to reasons
    }
}
