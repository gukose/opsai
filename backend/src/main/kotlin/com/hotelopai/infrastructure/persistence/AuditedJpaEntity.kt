package com.hotelopai.infrastructure.persistence

import jakarta.persistence.Column
import jakarta.persistence.Id
import jakarta.persistence.MappedSuperclass
import jakarta.persistence.Version
import java.time.Instant
import java.util.UUID

@MappedSuperclass
abstract class AuditedJpaEntity {
    @field:Id
    @field:Column(name = "id", nullable = false, updatable = false, columnDefinition = "uuid")
    open var id: UUID? = null

    @field:Version
    @field:Column(name = "version", nullable = false)
    open var version: Long? = null

    @field:Column(name = "created_at", nullable = false)
    open var createdAt: Instant? = null

    @field:Column(name = "created_by")
    open var createdBy: String? = null

    @field:Column(name = "updated_at", nullable = false)
    open var updatedAt: Instant? = null

    @field:Column(name = "updated_by")
    open var updatedBy: String? = null
}
