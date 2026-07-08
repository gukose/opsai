package com.hotelopai.employee.infrastructure.persistence

import com.hotelopai.infrastructure.persistence.AuditedJpaEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "skill")
class SkillJpaEntity : AuditedJpaEntity() {
    @Column(name = "hotel_id", nullable = false, columnDefinition = "uuid")
    var hotelId: UUID? = null

    @Column(name = "code", nullable = false)
    var code: String = ""

    @Column(name = "name", nullable = false)
    var name: String = ""

    @Column(name = "description")
    var description: String? = null

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true
}
