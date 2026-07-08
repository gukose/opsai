package com.hotelopai.employee.infrastructure.persistence

import com.hotelopai.employee.domain.Department
import com.hotelopai.employee.domain.Employee
import com.hotelopai.employee.domain.Skill

internal object DepartmentPersistenceMapper {
    fun toDomain(entity: DepartmentJpaEntity): Department =
        Department(
            id = requireNotNull(entity.id) { "department.id must not be null" },
            hotelId = requireNotNull(entity.hotelId) { "department.hotelId must not be null" },
            code = entity.code,
            name = entity.name,
            isActive = entity.isActive,
            version = requireNotNull(entity.version) { "department.version must not be null" },
            createdAt = requireNotNull(entity.createdAt) { "department.createdAt must not be null" },
            createdBy = entity.createdBy,
            updatedAt = requireNotNull(entity.updatedAt) { "department.updatedAt must not be null" },
            updatedBy = entity.updatedBy
        )

    fun toEntity(domain: Department): DepartmentJpaEntity =
        DepartmentJpaEntity().apply {
            id = domain.id
            hotelId = domain.hotelId
            code = domain.code
            name = domain.name
            isActive = domain.isActive
            version = domain.version.takeIf { it > 0 }
            createdAt = domain.createdAt
            createdBy = domain.createdBy
            updatedAt = domain.updatedAt
            updatedBy = domain.updatedBy
        }
}

internal object SkillPersistenceMapper {
    fun toDomain(entity: SkillJpaEntity): Skill =
        Skill(
            id = requireNotNull(entity.id) { "skill.id must not be null" },
            hotelId = requireNotNull(entity.hotelId) { "skill.hotelId must not be null" },
            code = entity.code,
            name = entity.name,
            description = entity.description,
            isActive = entity.isActive,
            version = requireNotNull(entity.version) { "skill.version must not be null" },
            createdAt = requireNotNull(entity.createdAt) { "skill.createdAt must not be null" },
            createdBy = entity.createdBy,
            updatedAt = requireNotNull(entity.updatedAt) { "skill.updatedAt must not be null" },
            updatedBy = entity.updatedBy
        )

    fun toEntity(domain: Skill): SkillJpaEntity =
        SkillJpaEntity().apply {
            id = domain.id
            hotelId = domain.hotelId
            code = domain.code
            name = domain.name
            description = domain.description
            isActive = domain.isActive
            version = domain.version.takeIf { it > 0 }
            createdAt = domain.createdAt
            createdBy = domain.createdBy
            updatedAt = domain.updatedAt
            updatedBy = domain.updatedBy
        }
}

internal object EmployeePersistenceMapper {
    fun toDomain(entity: EmployeeJpaEntity): Employee =
        Employee(
            id = requireNotNull(entity.id) { "employee.id must not be null" },
            hotelId = requireNotNull(entity.hotelId) { "employee.hotelId must not be null" },
            userId = entity.userId,
            employeeNumber = entity.employeeNumber,
            displayName = entity.displayName,
            departmentId = entity.departmentId,
            roleIds = entity.roleIds.toSet(),
            skillIds = entity.skillIds.toSet(),
            status = entity.status,
            version = requireNotNull(entity.version) { "employee.version must not be null" },
            createdAt = requireNotNull(entity.createdAt) { "employee.createdAt must not be null" },
            createdBy = entity.createdBy,
            updatedAt = requireNotNull(entity.updatedAt) { "employee.updatedAt must not be null" },
            updatedBy = entity.updatedBy
        )

    fun toEntity(domain: Employee): EmployeeJpaEntity =
        EmployeeJpaEntity().apply {
            id = domain.id
            hotelId = domain.hotelId
            userId = domain.userId
            employeeNumber = domain.employeeNumber
            displayName = domain.displayName
            departmentId = domain.departmentId
            status = domain.status
            roleIds = domain.roleIds.toMutableSet()
            skillIds = domain.skillIds.toMutableSet()
            version = domain.version.takeIf { it > 0 }
            createdAt = domain.createdAt
            createdBy = domain.createdBy
            updatedAt = domain.updatedAt
            updatedBy = domain.updatedBy
        }
}
