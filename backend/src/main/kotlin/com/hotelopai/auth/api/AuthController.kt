package com.hotelopai.auth.api

import com.hotelopai.auth.application.AuthenticationApplicationService
import com.hotelopai.auth.application.CurrentUserQuery
import com.hotelopai.auth.application.LogoutCommand
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import jakarta.validation.Valid
import java.util.UUID

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(
    private val authenticationApplicationService: AuthenticationApplicationService
) {
    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest): AuthSessionResponse =
        AuthSessionResponse.from(authenticationApplicationService.login(request.toCommand()))

    @PostMapping("/refresh")
    fun refresh(@Valid @RequestBody request: RefreshRequest): AuthSessionResponse =
        AuthSessionResponse.from(authenticationApplicationService.refresh(request.toCommand()))

    @PostMapping("/logout")
    @PreAuthorize("@permissionGuard.hasAnyPermission('AUTH_LOGIN') or isAuthenticated()")
    fun logout(@AuthenticationPrincipal jwt: Jwt): Map<String, String> {
        authenticationApplicationService.logout(
            LogoutCommand(sessionId = claimUuid(jwt, "sid"))
        )
        return mapOf("message" to "Logged out")
    }

    @GetMapping("/me")
    @PreAuthorize("@permissionGuard.hasAnyPermission('AUTH_VIEW')")
    fun me(@AuthenticationPrincipal jwt: Jwt): CurrentUserResponse =
        CurrentUserResponse.from(
            authenticationApplicationService.currentUser(
                CurrentUserQuery(userId = claimSubjectUuid(jwt))
            )
        )

    private fun claimSubjectUuid(jwt: Jwt): UUID =
        try {
            UUID.fromString(jwt.subject)
        } catch (exception: IllegalArgumentException) {
            throw com.hotelopai.auth.application.InvalidAccessSessionException()
        }

    private fun claimUuid(jwt: Jwt, name: String): UUID =
        try {
            UUID.fromString(requireNotNull(jwt.getClaimAsString(name)) { "Missing $name claim" })
        } catch (exception: IllegalArgumentException) {
            throw com.hotelopai.auth.application.InvalidAccessSessionException()
        }
}
