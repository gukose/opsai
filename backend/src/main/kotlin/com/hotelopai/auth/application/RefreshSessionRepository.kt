package com.hotelopai.auth.application

import com.hotelopai.auth.domain.RefreshSession
import java.util.UUID

interface RefreshSessionRepository {
    fun save(refreshSession: RefreshSession): RefreshSession

    fun findById(id: UUID): RefreshSession?

    fun findByUserId(userId: UUID): List<RefreshSession>

    fun findByRefreshTokenHash(refreshTokenHash: String): RefreshSession?
}
