package com.hotelopai.auth.domain

import com.hotelopai.shared.kernel.AuditedRecord
import com.hotelopai.shared.kernel.UuidV7Generator
import java.time.Instant
import java.util.UUID

data class Role(
    override val id: UUID = UuidV7Generator.generate(),
    val hotelId: UUID,
    val code: String,
    val name: String,
    val description: String? = null,
    val permissionIds: Set<UUID> = emptySet(),
    override val version: Long = 0,
    override val createdAt: Instant = Instant.now(),
    override val createdBy: String? = null,
    override val updatedAt: Instant = createdAt,
    override val updatedBy: String? = null
) : AuditedRecord {
    init {
        require(hotelId != UUID(0L, 0L)) { "hotelId must not be empty" }
        require(code.isNotBlank()) { "code must not be blank" }
        require(name.isNotBlank()) { "name must not be blank" }
    }
}
