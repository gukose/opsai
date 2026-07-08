package com.hotelopai.employee.infrastructure.persistence

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface DepartmentJpaRepository : JpaRepository<DepartmentJpaEntity, UUID> {
    fun findByHotelId(hotelId: UUID): List<DepartmentJpaEntity>

    fun findByHotelIdAndCode(hotelId: UUID, code: String): DepartmentJpaEntity?

    fun findAllByHotelIdOrderByCodeAsc(hotelId: UUID): List<DepartmentJpaEntity>
}

interface SkillJpaRepository : JpaRepository<SkillJpaEntity, UUID> {
    fun findByHotelId(hotelId: UUID): List<SkillJpaEntity>

    fun findByHotelIdAndCode(hotelId: UUID, code: String): SkillJpaEntity?

    fun findAllByHotelIdOrderByCodeAsc(hotelId: UUID): List<SkillJpaEntity>
}

interface EmployeeJpaRepository : JpaRepository<EmployeeJpaEntity, UUID> {
    fun findByHotelId(hotelId: UUID): List<EmployeeJpaEntity>

    fun findByHotelIdAndEmployeeNumber(hotelId: UUID, employeeNumber: String): EmployeeJpaEntity?

    fun findByUserId(userId: UUID): EmployeeJpaEntity?

    fun findAllByHotelIdOrderByEmployeeNumberAsc(hotelId: UUID): List<EmployeeJpaEntity>
}
