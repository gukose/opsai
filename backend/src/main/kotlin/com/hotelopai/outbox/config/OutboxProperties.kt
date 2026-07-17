package com.hotelopai.outbox.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("ops.ai.outbox")
data class OutboxProperties(
    val enabled: Boolean = true,
    val pollInterval: Duration = Duration.ofSeconds(5),
    val batchSize: Int = 25,
    val maxAttempts: Int = 5,
    val initialRetryDelay: Duration = Duration.ofSeconds(10),
    val maxRetryDelay: Duration = Duration.ofMinutes(5),
    val lockTimeout: Duration = Duration.ofMinutes(5),
    val processorId: String = "backend"
) {
    fun normalizedBatchSize(): Int = batchSize.coerceIn(1, 100)

    fun normalizedMaxAttempts(): Int = maxAttempts.coerceAtLeast(1)
}
