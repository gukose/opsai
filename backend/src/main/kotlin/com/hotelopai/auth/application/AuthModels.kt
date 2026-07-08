package com.hotelopai.auth.application

import java.time.Instant
import java.util.UUID

data class LoginCommand(
    val hotelCode: String,
    val email: String,
    val password: String,
    val deviceId: String? = null,
    val deviceName: String? = null,
    val ipAddress: String? = null,
    val userAgent: String? = null
)

data class RefreshCommand(
    val refreshToken: String,
    val deviceId: String? = null,
    val deviceName: String? = null,
    val ipAddress: String? = null,
    val userAgent: String? = null
)

data class LogoutCommand(
    val sessionId: UUID
)

data class CurrentUserQuery(
    val userId: UUID
)

data class AuthSessionResult(
    val accessToken: String,
    val accessTokenExpiresAt: Instant,
    val refreshToken: String,
    val refreshTokenExpiresAt: Instant,
    val currentUser: CurrentUserResult
)

data class CurrentUserResult(
    val userId: UUID,
    val hotelId: UUID,
    val employeeId: UUID?,
    val email: String,
    val displayName: String,
    val hotelName: String,
    val roles: List<RoleSummaryResult>,
    val permissions: List<PermissionSummaryResult>
)

data class RoleSummaryResult(
    val roleId: UUID,
    val code: String,
    val name: String
)

data class PermissionSummaryResult(
    val permissionId: UUID,
    val code: String,
    val name: String
)

data class AccessTokenContext(
    val userId: UUID,
    val hotelId: UUID,
    val employeeId: UUID?,
    val email: String,
    val displayName: String,
    val hotelName: String,
    val sessionId: UUID,
    val roleIds: Set<UUID>,
    val roleCodes: Set<String>,
    val permissionIds: Set<UUID>,
    val permissionCodes: Set<String>
)

data class AccessTokenResult(
    val token: String,
    val expiresAt: Instant,
    val jti: String
)
