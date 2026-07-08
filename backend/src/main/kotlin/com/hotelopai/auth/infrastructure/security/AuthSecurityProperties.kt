package com.hotelopai.auth.infrastructure.security

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "ops.ai.auth")
data class AuthSecurityProperties(
    val jwt: Jwt = Jwt(),
    val seed: Seed = Seed()
) {
    data class Jwt(
        val issuer: String = "hotel-opai",
        val secret: String = "",
        val accessTokenTtl: Duration = Duration.ofMinutes(15),
        val refreshTokenTtl: Duration = Duration.ofDays(30)
    ) {
        init {
            require(issuer.isNotBlank()) { "issuer must not be blank" }
            require(secret.isNotBlank()) { "secret must not be blank" }
        }
    }

    data class Seed(
        val enabled: Boolean = false
    )
}
