package com.hotelopai.employee.infrastructure.persistence

import com.hotelopai.infrastructure.persistence.AuditedJpaEntity
import com.hotelopai.employee.domain.EmployeeStatus
import com.hotelopai.employee.domain.EmployeeOperationalStatus
import jakarta.persistence.Column
import jakarta.persistence.CollectionTable
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.ForeignKey
import jakarta.persistence.JoinColumn
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "employee")
class EmployeeJpaEntity : AuditedJpaEntity() {
    @Column(name = "hotel_id", nullable = false, columnDefinition = "uuid")
    var hotelId: UUID? = null

    @Column(name = "user_id", columnDefinition = "uuid")
    var userId: UUID? = null

    @Column(name = "employee_number", nullable = false)
    var employeeNumber: String = ""

    @Column(name = "display_name", nullable = false)
    var displayName: String = ""

    @Column(name = "department_id", columnDefinition = "uuid")
    var departmentId: UUID? = null

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: EmployeeStatus = EmployeeStatus.ACTIVE

    @Column(name = "primary_role_code")
    var primaryRoleCode: String? = null

    @Column(name = "supervisor_employee_id", columnDefinition = "uuid")
    var supervisorEmployeeId: UUID? = null

    @Column(name = "home_area")
    var homeArea: String? = null

    @Column(name = "languages", columnDefinition = "text[]")
    var languages: Array<String> = emptyArray()

    @Enumerated(EnumType.STRING)
    @Column(name = "operational_status", nullable = false)
    var operationalStatus: EmployeeOperationalStatus = EmployeeOperationalStatus.OFFLINE

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
        name = "employee_role",
        joinColumns = [
            JoinColumn(
                name = "employee_id",
                referencedColumnName = "id",
                foreignKey = ForeignKey(name = "fk_employee_role_employee")
            )
        ]
    )
    @Column(name = "role_id", nullable = false, columnDefinition = "uuid")
    var roleIds: MutableSet<UUID> = mutableSetOf()

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
        name = "employee_skill",
        joinColumns = [
            JoinColumn(
                name = "employee_id",
                referencedColumnName = "id",
                foreignKey = ForeignKey(name = "fk_employee_skill_employee")
            )
        ]
    )
    @Column(name = "skill_id", nullable = false, columnDefinition = "uuid")
    var skillIds: MutableSet<UUID> = mutableSetOf()
}
