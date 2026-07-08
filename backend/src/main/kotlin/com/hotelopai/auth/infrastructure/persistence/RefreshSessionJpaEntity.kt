package com.hotelopai.auth.infrastructure.persistence

import com.hotelopai.infrastructure.persistence.AuditedJpaEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "refresh_session")
class RefreshSessionJpaEntity : AuditedJpaEntity() {
    @Column(name = "user_id", nullable = false, columnDefinition = "uuid")
    var userId: UUID? = null

    @Column(name = "hotel_id", nullable = false, columnDefinition = "uuid")
    var hotelId: UUID? = null

    @Column(name = "refresh_token_hash", nullable = false)
    var refreshTokenHash: String = ""

    @Column(name = "device_id", nullable = false)
    var deviceId: String = ""

    @Column(name = "device_name")
    var deviceName: String? = null

    @Column(name = "ip_address")
    var ipAddress: String? = null

    @Column(name = "user_agent")
    var userAgent: String? = null

    @Column(name = "expires_at", nullable = false)
    var expiresAt: Instant? = null

    @Column(name = "revoked_at")
    var revokedAt: Instant? = null

    @Column(name = "last_used_at")
    var lastUsedAt: Instant? = null
}
