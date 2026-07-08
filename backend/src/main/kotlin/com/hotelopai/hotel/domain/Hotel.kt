package com.hotelopai.hotel.domain

import com.hotelopai.shared.kernel.AuditedRecord
import com.hotelopai.shared.kernel.UuidV7Generator
import java.time.Instant
import java.util.UUID

data class Hotel(
    override val id: UUID = UuidV7Generator.generate(),
    val code: String,
    val name: String,
    val status: HotelStatus = HotelStatus.ACTIVE,
    override val version: Long = 0,
    override val createdAt: Instant = Instant.now(),
    override val createdBy: String? = null,
    override val updatedAt: Instant = createdAt,
    override val updatedBy: String? = null
) : AuditedRecord {
    init {
        require(code.isNotBlank()) { "code must not be blank" }
        require(name.isNotBlank()) { "name must not be blank" }
    }

    fun rename(
        newName: String,
        updatedBy: String? = null,
        now: Instant = Instant.now()
    ): Hotel {
        require(newName.isNotBlank()) { "name must not be blank" }

        return copy(
            name = newName,
            updatedAt = now,
            updatedBy = updatedBy,
            version = version + 1
        )
    }

    fun deactivate(
        updatedBy: String? = null,
        now: Instant = Instant.now()
    ): Hotel =
        copy(
            status = HotelStatus.INACTIVE,
            updatedAt = now,
            updatedBy = updatedBy,
            version = version + 1
        )

    fun activate(
        updatedBy: String? = null,
        now: Instant = Instant.now()
    ): Hotel =
        copy(
            status = HotelStatus.ACTIVE,
            updatedAt = now,
            updatedBy = updatedBy,
            version = version + 1
        )
}
