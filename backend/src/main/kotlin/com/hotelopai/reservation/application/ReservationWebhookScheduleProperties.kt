package com.hotelopai.reservation.application

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration
import java.time.Period

@ConfigurationProperties("ops.ai.reservation.webhooks.schedule")
data class ReservationWebhookScheduleProperties(
    val enabled: Boolean = false,
    val executionInterval: Duration = Duration.ofMinutes(1),
    val startupDelay: Duration = Duration.ofMinutes(2),
    val batchSize: Int = 10,
    val maxRecordsPerExecution: Int = 10,
    val lockTimeout: Duration = Duration.ofMinutes(5),
    val allowedProfiles: List<String> = emptyList(),
    val retentionCleanupEnabled: Boolean = false,
    val cleanupMaxRecords: Int = 100,
    val deadLetterRetention: Period = Period.ofDays(90),
    val cleanupLockTimeout: Duration = Duration.ofMinutes(5)
) {
    init {
        require(!executionInterval.isNegative && !executionInterval.isZero) {
            "reservation webhook processing schedule execution interval must be positive"
        }
        require(!startupDelay.isNegative) {
            "reservation webhook processing schedule startup delay must not be negative"
        }
        require(batchSize in 1..100) {
            "reservation webhook processing schedule batch size must be between 1 and 100"
        }
        require(maxRecordsPerExecution in 1..1_000) {
            "reservation webhook processing schedule max records per execution must be between 1 and 1000"
        }
        require(!lockTimeout.isNegative && !lockTimeout.isZero) {
            "reservation webhook processing schedule lock timeout must be positive"
        }
        require(cleanupMaxRecords in 1..1_000) {
            "reservation webhook cleanup schedule max records must be between 1 and 1000"
        }
        require(!cleanupLockTimeout.isNegative && !cleanupLockTimeout.isZero) {
            "reservation webhook cleanup schedule lock timeout must be positive"
        }
    }
}
