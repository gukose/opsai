package com.hotelopai.employee.infrastructure.persistence

import com.hotelopai.auth.application.RoleRepository
import com.hotelopai.auth.domain.Role
import com.hotelopai.employee.application.DepartmentRepository
import com.hotelopai.employee.application.EmployeeRepository
import com.hotelopai.employee.application.SkillRepository
import com.hotelopai.employee.domain.Department
import com.hotelopai.employee.domain.Employee
import com.hotelopai.employee.domain.EmployeeStatus
import com.hotelopai.employee.domain.Skill
import com.hotelopai.hotel.application.HotelRepository
import com.hotelopai.hotel.domain.Hotel
import com.hotelopai.support.PostgresIntegrationTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EmployeePersistenceRepositoryIntegrationTest : PostgresIntegrationTestSupport() {
    @Autowired
    private lateinit var hotelRepository: HotelRepository

    @Autowired
    private lateinit var departmentRepository: DepartmentRepository

    @Autowired
    private lateinit var skillRepository: SkillRepository

    @Autowired
    private lateinit var roleRepository: RoleRepository

    @Autowired
    private lateinit var employeeRepository: EmployeeRepository

    @Test
    fun `persists department skill and employee references`() {
        val hotel = hotelRepository.save(Hotel(code = "delta-hotel", name = "Delta Hotel"))
        val department = departmentRepository.save(
            Department(
                hotelId = hotel.id,
                code = "housekeeping",
                name = "Housekeeping"
            )
        )
        val skill = skillRepository.save(
            Skill(
                hotelId = hotel.id,
                code = "linen-folding",
                name = "Linen Folding"
            )
        )
        val role = roleRepository.save(
            Role(
                hotelId = hotel.id,
                code = "room-attendant",
                name = "Room Attendant"
            )
        )

        val employee = Employee(
            hotelId = hotel.id,
            employeeNumber = "EMP-001",
            displayName = "Alex Smith",
            departmentId = department.id,
            roleIds = setOf(role.id),
            skillIds = setOf(skill.id),
            status = EmployeeStatus.ACTIVE
        )

        val saved = employeeRepository.save(employee)

        assertThat(saved).isEqualTo(employee)
        assertThat(employeeRepository.findByHotelId(hotel.id)).containsExactly(saved)
        assertThat(employeeRepository.findByHotelIdAndEmployeeNumber(hotel.id, "EMP-001")).isEqualTo(saved)
    }

    @Test
    fun `allows same employee number across hotels but scopes queries by hotel`() {
        val firstHotel = hotelRepository.save(Hotel(code = "epsilon-hotel", name = "Epsilon Hotel"))
        val secondHotel = hotelRepository.save(Hotel(code = "zeta-hotel", name = "Zeta Hotel"))

        val firstEmployee = employeeRepository.save(
            Employee(
                hotelId = firstHotel.id,
                employeeNumber = "EMP-100",
                displayName = "First Employee"
            )
        )
        val secondEmployee = employeeRepository.save(
            Employee(
                hotelId = secondHotel.id,
                employeeNumber = "EMP-100",
                displayName = "Second Employee"
            )
        )

        assertThat(employeeRepository.findByHotelId(firstHotel.id)).containsExactly(firstEmployee)
        assertThat(employeeRepository.findByHotelId(secondHotel.id)).containsExactly(secondEmployee)
    }

    @Test
    fun `rejects duplicate employee number inside a hotel`() {
        val hotel = hotelRepository.save(Hotel(code = "theta-hotel", name = "Theta Hotel"))

        employeeRepository.save(
            Employee(
                hotelId = hotel.id,
                employeeNumber = "EMP-200",
                displayName = "First Employee"
            )
        )

        assertThrows<DataIntegrityViolationException> {
            employeeRepository.save(
                Employee(
                    hotelId = hotel.id,
                    employeeNumber = "EMP-200",
                    displayName = "Second Employee"
                )
            )
        }
    }
}
