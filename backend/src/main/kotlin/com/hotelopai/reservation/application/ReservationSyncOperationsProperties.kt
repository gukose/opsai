package com.hotelopai.reservation.application

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration
import java.time.Period

@ConfigurationProperties("ops.ai.reservation.sync.operations")
data class ReservationSyncOperationsProperties(
    val enabledAutomaticTriggers: Boolean = false,
    val maxWindow: Period = Period.ofDays(31),
    val historyRetention: Period = Period.ofDays(180),
    val lockTtl: Duration = Duration.ofMinutes(15),
    val maxPageSize: Int = 100
) {
    fun normalizedMaxPageSize(): Int = maxPageSize.coerceIn(1, 500)
    fun normalizedLockTtl(): Duration = lockTtl.takeIf { !it.isNegative && !it.isZero } ?: Duration.ofMinutes(15)
}
