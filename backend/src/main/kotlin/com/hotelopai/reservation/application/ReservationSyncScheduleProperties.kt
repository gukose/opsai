package com.hotelopai.reservation.application

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration
import java.time.Period
import java.time.ZoneId

@ConfigurationProperties("ops.ai.reservation.sync.schedule")
data class ReservationSyncScheduleProperties(
    val enabled: Boolean = false,
    val providerId: String? = null,
    val propertyScope: String = "configured",
    val windowStrategy: ReservationSyncWindowStrategy = ReservationSyncWindowStrategy.LOOKBACK_LOOKAHEAD,
    val lookbackDays: Int = 1,
    val lookaheadDays: Int = 14,
    val timezone: ZoneId = ZoneId.of("UTC"),
    val executionInterval: Duration = Duration.ofMinutes(30),
    val startupDelay: Duration = Duration.ofMinutes(2),
    val maxRunsPerExecution: Int = 1,
    val staleRunRecoveryThreshold: Duration = Duration.ofMinutes(30),
    val lockTimeout: Duration = Duration.ofMinutes(10),
    val allowedProfiles: List<String> = emptyList(),
    val retentionCleanupEnabled: Boolean = false,
    val retentionCleanupMaxRuns: Int = 100
) {
    init {
        if (enabled) {
            require(lookbackDays >= 0) { "reservation sync schedule lookback days must not be negative" }
            require(lookaheadDays >= 0) { "reservation sync schedule lookahead days must not be negative" }
            require(Period.ofDays(lookbackDays + lookaheadDays + 1).days <= 366) {
                "reservation sync schedule window must not exceed 366 days"
            }
            require(!executionInterval.isNegative && !executionInterval.isZero) {
                "reservation sync schedule execution interval must be positive"
            }
            require(!startupDelay.isNegative) {
                "reservation sync schedule startup delay must not be negative"
            }
            require(maxRunsPerExecution in 1..10) {
                "reservation sync schedule maximum runs per execution must be between 1 and 10"
            }
            require(!staleRunRecoveryThreshold.isNegative && !staleRunRecoveryThreshold.isZero) {
                "reservation sync schedule stale-run recovery threshold must be positive"
            }
            require(!lockTimeout.isNegative && !lockTimeout.isZero) {
                "reservation sync schedule lock timeout must be positive"
            }
            require(retentionCleanupMaxRuns in 1..1_000) {
                "reservation sync schedule retention cleanup maximum runs must be between 1 and 1000"
            }
        }
    }
}
