package com.hotelopai.auth.api

import com.hotelopai.auth.application.AuthSessionResult
import com.hotelopai.auth.application.CurrentUserResult
import com.hotelopai.auth.application.LoginCommand
import com.hotelopai.auth.application.RefreshCommand
import jakarta.validation.constraints.NotBlank
import java.time.Instant
import java.util.UUID

data class LoginRequest(
    @field:NotBlank
    val hotelCode: String,
    @field:NotBlank
    val email: String,
    @field:NotBlank
    val password: String,
    val deviceId: String? = null,
    val deviceName: String? = null,
    val ipAddress: String? = null,
    val userAgent: String? = null
) {
    fun toCommand(): LoginCommand =
        LoginCommand(
            hotelCode = hotelCode,
            email = email,
            password = password,
            deviceId = deviceId,
            deviceName = deviceName,
            ipAddress = ipAddress,
            userAgent = userAgent
        )
}

data class RefreshRequest(
    @field:NotBlank
    val refreshToken: String,
    val deviceId: String? = null,
    val deviceName: String? = null,
    val ipAddress: String? = null,
    val userAgent: String? = null
) {
    fun toCommand(): RefreshCommand =
        RefreshCommand(
            refreshToken = refreshToken,
            deviceId = deviceId,
            deviceName = deviceName,
            ipAddress = ipAddress,
            userAgent = userAgent
        )
}

data class AuthSessionResponse(
    val tokenType: String,
    val accessToken: String,
    val accessTokenExpiresAt: Instant,
    val refreshToken: String,
    val refreshTokenExpiresAt: Instant,
    val user: CurrentUserResponse
) {
    companion object {
        fun from(result: AuthSessionResult): AuthSessionResponse =
            AuthSessionResponse(
                tokenType = "Bearer",
                accessToken = result.accessToken,
                accessTokenExpiresAt = result.accessTokenExpiresAt,
                refreshToken = result.refreshToken,
                refreshTokenExpiresAt = result.refreshTokenExpiresAt,
                user = CurrentUserResponse.from(result.currentUser)
            )
    }
}

data class CurrentUserResponse(
    val userId: UUID,
    val hotelId: UUID,
    val employeeId: UUID?,
    val email: String,
    val displayName: String,
    val hotelName: String,
    val roles: List<RoleResponse>,
    val permissions: List<PermissionResponse>
) {
    companion object {
        fun from(result: CurrentUserResult): CurrentUserResponse =
            CurrentUserResponse(
                userId = result.userId,
                hotelId = result.hotelId,
                employeeId = result.employeeId,
                email = result.email,
                displayName = result.displayName,
                hotelName = result.hotelName,
                roles = result.roles.map(RoleResponse::from),
                permissions = result.permissions.map(PermissionResponse::from)
            )
    }
}

data class RoleResponse(
    val roleId: UUID,
    val code: String,
    val name: String
) {
    companion object {
        fun from(result: com.hotelopai.auth.application.RoleSummaryResult): RoleResponse =
            RoleResponse(
                roleId = result.roleId,
                code = result.code,
                name = result.name
            )
    }
}

data class PermissionResponse(
    val permissionId: UUID,
    val code: String,
    val name: String
) {
    companion object {
        fun from(result: com.hotelopai.auth.application.PermissionSummaryResult): PermissionResponse =
            PermissionResponse(
                permissionId = result.permissionId,
                code = result.code,
                name = result.name
            )
    }
}
