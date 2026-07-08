package com.hotelopai.auth.domain

import com.hotelopai.shared.kernel.AuditedRecord
import com.hotelopai.shared.kernel.UuidV7Generator
import java.time.Instant
import java.util.UUID

data class User(
    override val id: UUID = UuidV7Generator.generate(),
    val hotelId: UUID,
    val employeeId: UUID? = null,
    val email: EmailAddress,
    val displayName: String,
    val passwordHash: String,
    val roleIds: Set<UUID> = emptySet(),
    val status: UserStatus = UserStatus.INVITED,
    override val version: Long = 0,
    override val createdAt: Instant = Instant.now(),
    override val createdBy: String? = null,
    override val updatedAt: Instant = createdAt,
    override val updatedBy: String? = null
) : AuditedRecord {
    init {
        require(hotelId != UUID(0L, 0L)) { "hotelId must not be empty" }
        require(displayName.isNotBlank()) { "displayName must not be blank" }
        require(passwordHash.isNotBlank()) { "passwordHash must not be blank" }
    }

    fun canAuthenticate(): Boolean =
        status == UserStatus.ACTIVE

    fun activate(
        updatedBy: String? = null,
        now: Instant = Instant.now()
    ): User =
        copy(
            status = UserStatus.ACTIVE,
            updatedAt = now,
            updatedBy = updatedBy,
            version = version + 1
        )

    fun lock(
        updatedBy: String? = null,
        now: Instant = Instant.now()
    ): User =
        copy(
            status = UserStatus.LOCKED,
            updatedAt = now,
            updatedBy = updatedBy,
            version = version + 1
        )

    fun disable(
        updatedBy: String? = null,
        now: Instant = Instant.now()
    ): User =
        copy(
            status = UserStatus.DISABLED,
            updatedAt = now,
            updatedBy = updatedBy,
            version = version + 1
        )
}
