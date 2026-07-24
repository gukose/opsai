package com.hotelopai.reservation.application

import com.hotelopai.reservation.domain.DateRange
import org.springframework.stereotype.Component
import java.time.Clock

@Component
class ReservationSyncWindowPolicy(
    private val clock: Clock
) {
    fun window(properties: ReservationSyncScheduleProperties): DateRange {
        val today = java.time.LocalDate.now(clock.withZone(properties.timezone))
        return DateRange(
            arrival = today.minusDays(properties.lookbackDays.toLong()),
            departure = today.plusDays(properties.lookaheadDays.toLong()).plusDays(1)
        )
    }
}
