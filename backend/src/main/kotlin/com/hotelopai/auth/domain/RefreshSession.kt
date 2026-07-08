package com.hotelopai.auth.domain

import com.hotelopai.shared.kernel.AuditedRecord
import com.hotelopai.shared.kernel.UuidV7Generator
import java.time.Instant
import java.util.UUID

data class RefreshSession(
    override val id: UUID = UuidV7Generator.generate(),
    val userId: UUID,
    val hotelId: UUID,
    val refreshTokenHash: String,
    val deviceId: String,
    val deviceName: String? = null,
    val ipAddress: String? = null,
    val userAgent: String? = null,
    val expiresAt: Instant,
    val revokedAt: Instant? = null,
    val lastUsedAt: Instant? = null,
    override val version: Long = 0,
    override val createdAt: Instant = Instant.now(),
    override val createdBy: String? = null,
    override val updatedAt: Instant = createdAt,
    override val updatedBy: String? = null
) : AuditedRecord {
    init {
        require(userId != UUID(0L, 0L)) { "userId must not be empty" }
        require(hotelId != UUID(0L, 0L)) { "hotelId must not be empty" }
        require(refreshTokenHash.isNotBlank()) { "refreshTokenHash must not be blank" }
        require(deviceId.isNotBlank()) { "deviceId must not be blank" }
        require(expiresAt.isAfter(createdAt)) { "expiresAt must be after createdAt" }
    }

    fun status(now: Instant = Instant.now()): RefreshSessionStatus =
        when {
            revokedAt != null -> RefreshSessionStatus.REVOKED
            !now.isBefore(expiresAt) -> RefreshSessionStatus.EXPIRED
            else -> RefreshSessionStatus.ACTIVE
        }

    fun markUsed(
        now: Instant = Instant.now(),
        updatedBy: String? = null
    ): RefreshSession =
        requireActive(now).copy(
            lastUsedAt = now,
            updatedAt = now,
            updatedBy = updatedBy,
            version = version + 1
        )

    fun revoke(
        now: Instant = Instant.now(),
        updatedBy: String? = null
    ): RefreshSession =
        copy(
            revokedAt = revokedAt ?: now,
            updatedAt = now,
            updatedBy = updatedBy,
            version = version + 1
        )

    fun rotate(
        nextRefreshTokenHash: String,
        nextExpiresAt: Instant,
        now: Instant = Instant.now(),
        updatedBy: String? = null
    ): RefreshSession {
        require(nextRefreshTokenHash.isNotBlank()) { "nextRefreshTokenHash must not be blank" }
        require(nextExpiresAt.isAfter(now)) { "nextExpiresAt must be after now" }

        return requireActive(now).copy(
            refreshTokenHash = nextRefreshTokenHash,
            expiresAt = nextExpiresAt,
            lastUsedAt = now,
            updatedAt = now,
            updatedBy = updatedBy,
            version = version + 1
        )
    }

    private fun requireActive(now: Instant): RefreshSession {
        require(revokedAt == null) { "Refresh session has been revoked" }
        require(now.isBefore(expiresAt)) { "Refresh session has expired" }
        return this
    }
}
