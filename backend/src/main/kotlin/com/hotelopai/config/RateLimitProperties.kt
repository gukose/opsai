package com.hotelopai.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "ops.ai.rate-limit")
data class RateLimitProperties(
    val enabled: Boolean = true,
    val window: Duration = Duration.ofMinutes(1),
    val defaultLimit: Int = 1000,
    val authLimit: Int = 100,
    val writeLimit: Int = 500
) {
    init {
        require(!window.isNegative && !window.isZero) { "rate-limit window must be positive" }
        require(defaultLimit > 0) { "defaultLimit must be positive" }
        require(authLimit > 0) { "authLimit must be positive" }
        require(writeLimit > 0) { "writeLimit must be positive" }
    }
}
