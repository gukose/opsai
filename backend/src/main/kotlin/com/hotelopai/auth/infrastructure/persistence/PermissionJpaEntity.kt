package com.hotelopai.auth.infrastructure.persistence

import com.hotelopai.infrastructure.persistence.AuditedJpaEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "permission")
class PermissionJpaEntity : AuditedJpaEntity() {
    @Column(name = "code", nullable = false, unique = true)
    var code: String = ""

    @Column(name = "name", nullable = false)
    var name: String = ""

    @Column(name = "description")
    var description: String? = null
}
