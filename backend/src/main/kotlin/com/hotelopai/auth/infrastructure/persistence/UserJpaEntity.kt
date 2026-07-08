package com.hotelopai.auth.infrastructure.persistence

import com.hotelopai.auth.domain.UserStatus
import com.hotelopai.infrastructure.persistence.AuditedJpaEntity
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
@Table(name = "app_user")
class UserJpaEntity : AuditedJpaEntity() {
    @Column(name = "hotel_id", nullable = false, columnDefinition = "uuid")
    var hotelId: UUID? = null

    @Column(name = "employee_id", columnDefinition = "uuid")
    var employeeId: UUID? = null

    @Column(name = "email", nullable = false)
    var email: String = ""

    @Column(name = "display_name", nullable = false)
    var displayName: String = ""

    @Column(name = "password_hash", nullable = false)
    var passwordHash: String = ""

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: UserStatus = UserStatus.INVITED

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
        name = "user_role",
        joinColumns = [
            JoinColumn(
                name = "user_id",
                referencedColumnName = "id",
                foreignKey = ForeignKey(name = "fk_user_role_user")
            )
        ]
    )
    @Column(name = "role_id", nullable = false, columnDefinition = "uuid")
    var roleIds: MutableSet<UUID> = mutableSetOf()
}
