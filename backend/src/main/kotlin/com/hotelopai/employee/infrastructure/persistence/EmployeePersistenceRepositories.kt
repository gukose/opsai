package com.hotelopai.employee.infrastructure.persistence

import com.hotelopai.employee.application.DepartmentRepository
import com.hotelopai.employee.application.EmployeeRepository
import com.hotelopai.employee.application.SkillRepository
import com.hotelopai.employee.domain.Department
import com.hotelopai.employee.domain.Employee
import com.hotelopai.employee.domain.Skill
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Repository
@Transactional
class JpaDepartmentRepositoryAdapter(
    private val departmentJpaRepository: DepartmentJpaRepository
) : DepartmentRepository {
    override fun save(department: Department): Department =
        DepartmentPersistenceMapper.toDomain(
            departmentJpaRepository.saveAndFlush(DepartmentPersistenceMapper.toEntity(department))
        )

    override fun findById(id: UUID): Department? =
        departmentJpaRepository.findById(id).orElse(null)?.let(DepartmentPersistenceMapper::toDomain)

    override fun findByHotelId(hotelId: UUID): List<Department> =
        departmentJpaRepository.findAllByHotelIdOrderByCodeAsc(hotelId).map(DepartmentPersistenceMapper::toDomain)

    override fun findByHotelIdAndCode(hotelId: UUID, code: String): Department? =
        departmentJpaRepository.findByHotelIdAndCode(hotelId, code)?.let(DepartmentPersistenceMapper::toDomain)
}

@Repository
@Transactional
class JpaSkillRepositoryAdapter(
    private val skillJpaRepository: SkillJpaRepository
) : SkillRepository {
    override fun save(skill: Skill): Skill =
        SkillPersistenceMapper.toDomain(
            skillJpaRepository.saveAndFlush(SkillPersistenceMapper.toEntity(skill))
        )

    override fun findById(id: UUID): Skill? =
        skillJpaRepository.findById(id).orElse(null)?.let(SkillPersistenceMapper::toDomain)

    override fun findByHotelId(hotelId: UUID): List<Skill> =
        skillJpaRepository.findAllByHotelIdOrderByCodeAsc(hotelId).map(SkillPersistenceMapper::toDomain)

    override fun findByHotelIdAndCode(hotelId: UUID, code: String): Skill? =
        skillJpaRepository.findByHotelIdAndCode(hotelId, code)?.let(SkillPersistenceMapper::toDomain)
}

@Repository
@Transactional
class JpaEmployeeRepositoryAdapter(
    private val employeeJpaRepository: EmployeeJpaRepository
) : EmployeeRepository {
    override fun save(employee: Employee): Employee =
        EmployeePersistenceMapper.toDomain(
            employeeJpaRepository.saveAndFlush(EmployeePersistenceMapper.toEntity(employee))
        )

    override fun findById(id: UUID): Employee? =
        employeeJpaRepository.findById(id).orElse(null)?.let(EmployeePersistenceMapper::toDomain)

    override fun findByHotelId(hotelId: UUID): List<Employee> =
        employeeJpaRepository.findAllByHotelIdOrderByEmployeeNumberAsc(hotelId).map(EmployeePersistenceMapper::toDomain)

    override fun findByHotelIdAndEmployeeNumber(hotelId: UUID, employeeNumber: String): Employee? =
        employeeJpaRepository.findByHotelIdAndEmployeeNumber(hotelId, employeeNumber)?.let(EmployeePersistenceMapper::toDomain)

    override fun findByUserId(userId: UUID): Employee? =
        employeeJpaRepository.findByUserId(userId)?.let(EmployeePersistenceMapper::toDomain)
}
