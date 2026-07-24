package com.hotelopai.scheduler.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("ops.ai.scheduler")
data class SchedulerProperties(
    val enabled: Boolean = true,
    val instanceId: String = "backend",
    val defaultLockTimeout: Duration = Duration.ofMinutes(10),
    val shutdownTimeout: Duration = Duration.ofSeconds(30),
    val leaseRenewalEnabled: Boolean = true,
    val leaseRenewalInterval: Duration = Duration.ofMinutes(1),
    val leaseRenewalSafetyMargin: Duration = Duration.ofSeconds(30)
) {
    fun normalizedDefaultLockTimeout(): Duration =
        defaultLockTimeout.takeIf { !it.isNegative && !it.isZero } ?: Duration.ofMinutes(10)

    fun normalizedShutdownTimeout(): Duration =
        shutdownTimeout.takeIf { !it.isNegative } ?: Duration.ofSeconds(30)

    fun validatedRenewalInterval(lockTimeout: Duration): Duration {
        val normalizedLockTimeout = lockTimeout.takeIf { !it.isNegative && !it.isZero }
            ?: normalizedDefaultLockTimeout()
        require(!leaseRenewalInterval.isNegative && !leaseRenewalInterval.isZero) {
            "ops.ai.scheduler.lease-renewal-interval must be positive"
        }
        require(!leaseRenewalSafetyMargin.isNegative) {
            "ops.ai.scheduler.lease-renewal-safety-margin must not be negative"
        }
        require(leaseRenewalInterval < normalizedLockTimeout) {
            "ops.ai.scheduler.lease-renewal-interval must be shorter than the scheduler lock timeout"
        }
        require(leaseRenewalInterval + leaseRenewalSafetyMargin < normalizedLockTimeout) {
            "ops.ai.scheduler.lease-renewal-interval plus safety margin must be shorter than the scheduler lock timeout"
        }
        return leaseRenewalInterval
    }
}
