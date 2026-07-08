package com.hotelopai.task.application

import com.hotelopai.task.domain.TaskAssigneeType
import com.hotelopai.task.domain.TaskAssignment
import com.hotelopai.employee.application.EmployeeRepository
import com.hotelopai.employee.domain.Employee
import com.hotelopai.employee.domain.EmployeeStatus
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

interface DeterministicAssignmentService {
    fun assign(criteria: AssignmentCriteria, now: Instant = Instant.now()): TaskAssignment?
}

data class AssignmentCriteria(
    val hotelId: UUID,
    val requiredSkillId: UUID? = null,
    val departmentId: UUID? = null,
    val unavailableEmployeeIds: Set<UUID> = emptySet()
)

@Service
class DefaultDeterministicAssignmentService(
    private val employeeRepository: EmployeeRepository
) : DeterministicAssignmentService {
    override fun assign(criteria: AssignmentCriteria, now: Instant): TaskAssignment? {
        val candidates = employeeRepository.findByHotelId(criteria.hotelId)
            .asSequence()
            .filter { it.status == EmployeeStatus.ACTIVE }
            .filterNot { it.id in criteria.unavailableEmployeeIds }
            .map { candidate -> candidate to score(candidate, criteria) }
            .filter { (_, score) -> score > Int.MIN_VALUE }
            .sortedWith(
                compareByDescending<Pair<Employee, Int>> { it.second }
                    .thenBy { it.first.employeeNumber }
                    .thenBy { it.first.displayName }
            )
            .toList()

        val selected = candidates.firstOrNull()?.first ?: return null

        return TaskAssignment(
            assigneeType = TaskAssigneeType.USER,
            assigneeId = selected.id.toString(),
            displayName = selected.displayName,
            assignedAt = now
        )
    }

    private fun score(employee: Employee, criteria: AssignmentCriteria): Int {
        var score = 0

        if (criteria.requiredSkillId != null && criteria.requiredSkillId in employee.skillIds) {
            score += 200
        } else if (criteria.requiredSkillId != null) {
            score -= 50
        }

        if (criteria.departmentId != null && employee.departmentId == criteria.departmentId) {
            score += 100
        }

        return score
    }
}
