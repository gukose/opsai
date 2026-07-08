package com.hotelopai.employee.domain

import com.hotelopai.shared.kernel.AuditedRecord
import com.hotelopai.shared.kernel.UuidV7Generator
import java.time.Instant
import java.util.UUID

data class Employee(
    override val id: UUID = UuidV7Generator.generate(),
    val hotelId: UUID,
    val userId: UUID? = null,
    val employeeNumber: String,
    val displayName: String,
    val departmentId: UUID? = null,
    val roleIds: Set<UUID> = emptySet(),
    val skillIds: Set<UUID> = emptySet(),
    val status: EmployeeStatus = EmployeeStatus.ACTIVE,
    override val version: Long = 0,
    override val createdAt: Instant = Instant.now(),
    override val createdBy: String? = null,
    override val updatedAt: Instant = createdAt,
    override val updatedBy: String? = null
) : AuditedRecord {
    init {
        require(hotelId != UUID(0L, 0L)) { "hotelId must not be empty" }
        require(employeeNumber.isNotBlank()) { "employeeNumber must not be blank" }
        require(displayName.isNotBlank()) { "displayName must not be blank" }
    }

    fun assignDepartment(
        departmentId: UUID?,
        updatedBy: String? = null,
        now: Instant = Instant.now()
    ): Employee =
        copy(
            departmentId = departmentId,
            updatedAt = now,
            updatedBy = updatedBy,
            version = version + 1
        )

    fun assignUser(
        userId: UUID?,
        updatedBy: String? = null,
        now: Instant = Instant.now()
    ): Employee =
        copy(
            userId = userId,
            updatedAt = now,
            updatedBy = updatedBy,
            version = version + 1
        )
}
