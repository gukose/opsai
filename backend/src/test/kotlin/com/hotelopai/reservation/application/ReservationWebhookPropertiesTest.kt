package com.hotelopai.reservation.application

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Duration

class ReservationWebhookPropertiesTest {
    @Test
    fun `webhook ingestion processing and scheduling are disabled by default`() {
        val webhookProperties = ReservationWebhookProperties()
        val scheduleProperties = ReservationWebhookScheduleProperties()

        assertThat(webhookProperties.enabled).isFalse()
        assertThat(webhookProperties.processingEnabled).isFalse()
        assertThat(scheduleProperties.enabled).isFalse()
        assertThat(scheduleProperties.retentionCleanupEnabled).isFalse()
    }

    @Test
    fun `webhook schedule rejects invalid bounded settings`() {
        assertThrows(IllegalArgumentException::class.java) {
            ReservationWebhookScheduleProperties(executionInterval = Duration.ZERO)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ReservationWebhookScheduleProperties(batchSize = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ReservationWebhookScheduleProperties(maxRecordsPerExecution = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ReservationWebhookScheduleProperties(lockTimeout = Duration.ZERO)
        }
    }
}
