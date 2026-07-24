package com.hotelopai.reservation.application

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Duration

class ReservationSyncSchedulePropertiesTest {
    @Test
    fun `scheduled reservation sync is disabled by default`() {
        val properties = ReservationSyncScheduleProperties()

        assertThat(properties.enabled).isFalse()
        assertThat(properties.retentionCleanupEnabled).isFalse()
        assertThat(properties.maxRunsPerExecution).isEqualTo(1)
    }

    @Test
    fun `enabled schedule rejects invalid bounded execution settings`() {
        assertThrows(IllegalArgumentException::class.java) {
            ReservationSyncScheduleProperties(enabled = true, executionInterval = Duration.ZERO)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ReservationSyncScheduleProperties(enabled = true, maxRunsPerExecution = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ReservationSyncScheduleProperties(enabled = true, retentionCleanupMaxRuns = 0)
        }
    }
}
