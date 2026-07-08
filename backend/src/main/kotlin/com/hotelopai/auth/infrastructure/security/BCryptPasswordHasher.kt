package com.hotelopai.auth.infrastructure.security

import com.hotelopai.auth.application.PasswordHasher
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Component

@Component
class BCryptPasswordHasher(
    private val passwordEncoder: BCryptPasswordEncoder
) : PasswordHasher {
    override fun hash(rawPassword: String): String =
        requireNotNull(passwordEncoder.encode(rawPassword)) { "BCrypt password encoding failed" }

    override fun matches(rawPassword: String, passwordHash: String): Boolean =
        passwordEncoder.matches(rawPassword, passwordHash)
}
