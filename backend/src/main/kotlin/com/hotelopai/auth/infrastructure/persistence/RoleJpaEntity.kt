package com.hotelopai.auth.infrastructure.persistence

import com.hotelopai.infrastructure.persistence.AuditedJpaEntity
import jakarta.persistence.Column
import jakarta.persistence.CollectionTable
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.ForeignKey
import jakarta.persistence.JoinColumn
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "role")
class RoleJpaEntity : AuditedJpaEntity() {
    @Column(name = "hotel_id", nullable = false, columnDefinition = "uuid")
    var hotelId: UUID? = null

    @Column(name = "code", nullable = false)
    var code: String = ""

    @Column(name = "name", nullable = false)
    var name: String = ""

    @Column(name = "description")
    var description: String? = null

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
        name = "role_permission",
        joinColumns = [
            JoinColumn(
                name = "role_id",
                referencedColumnName = "id",
                foreignKey = ForeignKey(name = "fk_role_permission_role")
            )
        ]
    )
    @Column(name = "permission_id", nullable = false, columnDefinition = "uuid")
    var permissionIds: MutableSet<UUID> = mutableSetOf()
}
