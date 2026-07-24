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
    val retryMultiplier: Double = 2.0,
    val maxRetryDelay: Duration = Duration.ofMinutes(5),
    val lockTimeout: Duration = Duration.ofMinutes(5),
    val completedRetention: Duration = Duration.ofDays(14),
    val failedRetention: Duration = Duration.ofDays(30),
    val cleanupBatchSize: Int = 100,
    val processorId: String = "backend"
) {
    fun normalizedBatchSize(): Int = batchSize.coerceIn(1, 100)

    fun normalizedMaxAttempts(): Int = maxAttempts.coerceAtLeast(1)

    fun normalizedRetryMultiplier(): Double = retryMultiplier.takeIf { it.isFinite() && it >= 1.0 } ?: 2.0

    fun normalizedCleanupBatchSize(): Int = cleanupBatchSize.coerceIn(1, 1_000)

    fun normalizedCompletedRetention(): Duration =
        completedRetention.takeIf { !it.isNegative && !it.isZero } ?: Duration.ofDays(14)

    fun normalizedFailedRetention(): Duration =
        failedRetention.takeIf { !it.isNegative && !it.isZero } ?: Duration.ofDays(30)
}
