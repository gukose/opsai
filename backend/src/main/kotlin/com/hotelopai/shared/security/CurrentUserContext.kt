package com.hotelopai.shared.security

import com.hotelopai.auth.application.InvalidAccessSessionException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Component
import java.util.UUID

data class CurrentUserContext(
    val userId: UUID,
    val hotelId: UUID,
    val sessionId: UUID,
    val permissions: Set<String>,
    val roles: Set<String>
)

@Component
class CurrentUserContextResolver {
    fun current(): CurrentUserContext {
        val authentication = SecurityContextHolder.getContext().authentication
            ?: throw InvalidAccessSessionException()
        val jwtAuthentication = authentication as? JwtAuthenticationToken
            ?: throw InvalidAccessSessionException()
        val jwt = jwtAuthentication.token

        return CurrentUserContext(
            userId = claimUuid(jwt.subject) ?: throw InvalidAccessSessionException(),
            hotelId = claimUuid(jwt.getClaimAsString("hotelId")) ?: throw InvalidAccessSessionException(),
            sessionId = claimUuid(jwt.getClaimAsString("sid")) ?: throw InvalidAccessSessionException(),
            permissions = claimStrings(jwt.claims["permissions"]),
            roles = claimStrings(jwt.claims["roleCodes"])
        )
    }

    private fun claimUuid(value: String?): UUID? =
        value?.let {
            try {
                UUID.fromString(it)
            } catch (_: IllegalArgumentException) {
                null
            }
        }

    private fun claimStrings(value: Any?): Set<String> =
        when (value) {
            is Collection<*> -> value.mapNotNull { it?.toString()?.takeIf(String::isNotBlank) }.toSet()
            is Array<*> -> value.mapNotNull { it?.toString()?.takeIf(String::isNotBlank) }.toSet()
            is String -> if (value.isBlank()) emptySet() else setOf(value)
            else -> emptySet()
        }
}
