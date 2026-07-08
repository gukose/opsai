package com.hotelopai.task.application

import com.hotelopai.employee.application.EmployeeRepository
import com.hotelopai.employee.domain.Employee
import com.hotelopai.employee.domain.EmployeeStatus
import com.hotelopai.shared.kernel.UuidV7Generator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class DeterministicAssignmentServiceTest {
    @Test
    fun `assigns same hotel employee with required skill first`() {
        val hotelId = UuidV7Generator.generate()
        val otherHotelId = UuidV7Generator.generate()
        val skillId = UuidV7Generator.generate()
        val departmentId = UuidV7Generator.generate()
        val preferred = employee(hotelId, "E-002", "Preferred", departmentId, setOf(skillId))
        val fallback = employee(hotelId, "E-003", "Fallback", departmentId, emptySet())
        val foreign = employee(otherHotelId, "E-999", "Foreign", departmentId, setOf(skillId))
        val service = DefaultDeterministicAssignmentService(
            employeeRepository = FakeEmployeeRepository(listOf(fallback, foreign, preferred))
        )

        val assignment = service.assign(
            AssignmentCriteria(
                hotelId = hotelId,
                requiredSkillId = skillId,
                departmentId = departmentId
            ),
            now = Instant.parse("2026-07-08T10:00:00Z")
        )

        assertNotNull(assignment)
        assertEquals(preferred.id.toString(), assignment!!.assigneeId)
    }

    @Test
    fun `falls back to department match when required skill is unavailable`() {
        val hotelId = UuidV7Generator.generate()
        val departmentId = UuidV7Generator.generate()
        val otherDepartment = UuidV7Generator.generate()
        val preferred = employee(hotelId, "E-010", "Preferred", departmentId, emptySet())
        val fallback = employee(hotelId, "E-020", "Fallback", otherDepartment, emptySet())
        val service = DefaultDeterministicAssignmentService(
            employeeRepository = FakeEmployeeRepository(listOf(fallback, preferred))
        )

        val assignment = service.assign(
            AssignmentCriteria(
                hotelId = hotelId,
                requiredSkillId = UuidV7Generator.generate(),
                departmentId = departmentId
            ),
            now = Instant.parse("2026-07-08T10:00:00Z")
        )

        assertNotNull(assignment)
        assertEquals(preferred.id.toString(), assignment!!.assigneeId)
    }

    @Test
    fun `skips inactive or unavailable employees`() {
        val hotelId = UuidV7Generator.generate()
        val departmentId = UuidV7Generator.generate()
        val inactive = employee(hotelId, "E-100", "Inactive", departmentId, emptySet(), EmployeeStatus.INACTIVE)
        val active = employee(hotelId, "E-101", "Active", departmentId, emptySet())
        val service = DefaultDeterministicAssignmentService(
            employeeRepository = FakeEmployeeRepository(listOf(inactive, active))
        )

        val assignment = service.assign(
            AssignmentCriteria(
                hotelId = hotelId,
                departmentId = departmentId,
                unavailableEmployeeIds = setOf(active.id)
            ),
            now = Instant.parse("2026-07-08T10:00:00Z")
        )

        assertEquals(null, assignment)
    }

    private fun employee(
        hotelId: UUID,
        employeeNumber: String,
        displayName: String,
        departmentId: UUID?,
        skillIds: Set<UUID>,
        status: EmployeeStatus = EmployeeStatus.ACTIVE
    ): Employee =
        Employee(
            id = UuidV7Generator.generate(),
            hotelId = hotelId,
            employeeNumber = employeeNumber,
            displayName = displayName,
            departmentId = departmentId,
            skillIds = skillIds,
            status = status,
            createdAt = Instant.parse("2026-07-08T09:00:00Z")
        )

    private class FakeEmployeeRepository(
        private val employees: List<Employee>
    ) : EmployeeRepository {
        override fun save(employee: Employee): Employee = employee
        override fun findById(id: UUID): Employee? = employees.find { it.id == id }
        override fun findByHotelId(hotelId: UUID): List<Employee> = employees.filter { it.hotelId == hotelId }
        override fun findByHotelIdAndEmployeeNumber(hotelId: UUID, employeeNumber: String): Employee? =
            employees.find { it.hotelId == hotelId && it.employeeNumber == employeeNumber }

        override fun findByUserId(userId: UUID): Employee? = employees.find { it.userId == userId }
    }
}
