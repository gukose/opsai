package com.hotelopai.reservation.application

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("ops.ai.reservation.sync")
data class ReservationSyncProperties(
    val maxReservationsPerRun: Int = 500
) {
    fun normalizedMaxReservationsPerRun(): Int =
        maxReservationsPerRun.coerceIn(1, 5_000)
}
