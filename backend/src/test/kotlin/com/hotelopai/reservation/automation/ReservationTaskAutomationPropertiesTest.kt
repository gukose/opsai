package com.hotelopai.reservation.automation

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.UUID

class ReservationTaskAutomationPropertiesTest {
    @Test
    fun `automation is disabled by default`() {
        val properties = ReservationTaskAutomationProperties()

        assertThat(properties.enabled).isFalse()
        assertThat(properties.hotelId).isNull()
        assertThat(properties.batchSize).isEqualTo(10)
        assertThat(properties.schedule.enabled).isFalse()
        assertThat(properties.rules).isEmpty()
    }

    @Test
    fun `enabled automation requires valid bounded configuration`() {
        assertThrows(IllegalArgumentException::class.java) {
            ReservationTaskAutomationProperties(enabled = true)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ReservationTaskAutomationProperties(enabled = true, hotelId = UUID(0L, 0L))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ReservationTaskAutomationProperties(batchSize = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ReservationTaskAutomationProperties(retryDelay = Duration.ZERO)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ReservationTaskAutomationScheduleProperties(batchSize = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ReservationTaskAutomationRulePolicyProperties(minimumLeadTime = Duration.ofSeconds(-1))
        }
    }
}
