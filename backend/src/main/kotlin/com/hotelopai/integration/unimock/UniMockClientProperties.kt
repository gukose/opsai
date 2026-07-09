package com.hotelopai.integration.unimock

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "ops.ai.unimock")
data class UniMockClientProperties(
    val baseUrl: String = "http://localhost:8090",
    val apiPrefix: String = "/api/pms",
    val connectTimeout: Duration = Duration.ofSeconds(2),
    val requestTimeout: Duration = Duration.ofSeconds(4),
    val retries: RetryProperties = RetryProperties()
) {
    data class RetryProperties(
        val maxAttempts: Int = 3,
        val initialBackoff: Duration = Duration.ofMillis(200),
        val maxBackoff: Duration = Duration.ofSeconds(1)
    )
}
