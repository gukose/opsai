package com.hotelopai.auth.application

import com.hotelopai.auth.domain.RefreshSession
import java.util.UUID

interface RefreshSessionRepository {
    fun save(refreshSession: RefreshSession): RefreshSession
    fun create(refreshSession: RefreshSession): RefreshSession

    /** Atomically consumes the current session version and stores its replacement. */
    fun rotate(current: RefreshSession, replacement: RefreshSession): RefreshSession

    fun findById(id: UUID): RefreshSession?

    fun findByUserId(userId: UUID): List<RefreshSession>

    fun findByRefreshTokenHash(refreshTokenHash: String): RefreshSession?
}
