package com.hotelopai.auth.api

import com.hotelopai.shared.security.CurrentUserContextResolver
import org.springframework.context.annotation.Profile
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@Profile("test")
@RequestMapping("/api/v1/auth")
class AuthSecurityProbeController(
    private val currentUserContextResolver: CurrentUserContextResolver
) {
    @GetMapping("/context")
    @PreAuthorize("isAuthenticated()")
    fun context(): Map<String, String> {
        val current = currentUserContextResolver.current()
        return mapOf(
            "userId" to current.userId.toString(),
            "hotelId" to current.hotelId.toString(),
            "sessionId" to current.sessionId.toString()
        )
    }
}
