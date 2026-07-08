package com.hotelopai.auth.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class RefreshSessionTest {
    @Test
    fun `refresh session can be revoked and rotated`() {
        val now = Instant.parse("2026-07-08T10:00:00Z")
        val session = RefreshSession(
            id = UUID.fromString("018fca00-0000-7000-8000-000000000001"),
            userId = UUID.fromString("018fca00-0000-7000-8000-000000000002"),
            hotelId = UUID.fromString("018fca00-0000-7000-8000-000000000003"),
            refreshTokenHash = "hash-1",
            deviceId = "device-1",
            deviceName = "iPhone",
            ipAddress = "127.0.0.1",
            userAgent = "MobileSafari",
            expiresAt = now.plusSeconds(3600),
            createdAt = now,
            updatedAt = now
        )

        val revoked = session.revoke(now.plusSeconds(10))
        assertNotNull(revoked.revokedAt)
        assertEquals(RefreshSessionStatus.REVOKED, revoked.status(now.plusSeconds(10)))

        val rotated = session.rotate(
            nextRefreshTokenHash = "hash-2",
            nextExpiresAt = now.plusSeconds(7200),
            now = now.plusSeconds(5)
        )

        assertEquals("hash-2", rotated.refreshTokenHash)
        assertEquals(RefreshSessionStatus.ACTIVE, rotated.status(now.plusSeconds(5)))
        assertSame(session.userId, rotated.userId)
    }
}
