package com.hotelopai.reservation.application

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration
import java.time.Period

@ConfigurationProperties("ops.ai.reservation.webhooks")
data class ReservationWebhookProperties(
    val enabled: Boolean = false,
    val processingEnabled: Boolean = false,
    val maxRequestBytes: Int = 64 * 1024,
    val timestampTolerance: Duration = Duration.ofMinutes(10),
    val batchSize: Int = 10,
    val maxAttempts: Int = 3,
    val initialBackoff: Duration = Duration.ofMinutes(1),
    val maxBackoff: Duration = Duration.ofMinutes(15),
    val abandonedProcessingTimeout: Duration = Duration.ofMinutes(15),
    val completedRetention: Period = Period.ofDays(30),
    val rejectedRetention: Period = Period.ofDays(7),
    val cleanupBatchSize: Int = 100
) {
    init {
        if (enabled) {
            require(maxRequestBytes in 1..1_048_576) { "reservation webhook max request bytes must be between 1 and 1048576" }
            require(!timestampTolerance.isNegative && !timestampTolerance.isZero) { "reservation webhook timestamp tolerance must be positive" }
        }
        require(batchSize in 1..100) { "reservation webhook batch size must be between 1 and 100" }
        require(maxAttempts in 1..10) { "reservation webhook max attempts must be between 1 and 10" }
        require(!initialBackoff.isNegative && !initialBackoff.isZero) { "reservation webhook initial backoff must be positive" }
        require(!maxBackoff.isNegative && !maxBackoff.isZero) { "reservation webhook max backoff must be positive" }
        require(!abandonedProcessingTimeout.isNegative && !abandonedProcessingTimeout.isZero) {
            "reservation webhook abandoned processing timeout must be positive"
        }
        require(cleanupBatchSize in 1..1_000) { "reservation webhook cleanup batch size must be between 1 and 1000" }
    }
}
