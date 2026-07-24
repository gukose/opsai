package com.hotelopai.integration.apaleo

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.hotelopai.pms.application.PmsConfiguredProviderProperties
import com.hotelopai.pms.application.PmsCredentialResolver
import com.hotelopai.pms.application.PmsCapabilities
import com.hotelopai.pms.application.PmsProvider
import com.hotelopai.pms.application.PmsProviderProperties
import com.hotelopai.pms.application.PmsProviderRegistry
import com.hotelopai.pms.domain.HousekeepingTaskStatusUpdate
import com.hotelopai.pms.domain.MaintenanceUpdate
import com.hotelopai.pms.domain.PmsAsset
import com.hotelopai.pms.domain.PmsEvent
import com.hotelopai.pms.domain.PmsEventCreateCommand
import com.hotelopai.pms.domain.PmsHousekeepingTask
import com.hotelopai.pms.domain.PmsIssueType
import com.hotelopai.pms.domain.PmsProviderId
import com.hotelopai.pms.domain.PmsRoom
import com.hotelopai.pms.domain.PmsRoomStatus
import com.hotelopai.pms.domain.PmsStay
import com.hotelopai.pms.domain.PmsUpdateResult
import com.hotelopai.pms.domain.RoomStatusUpdate
import com.hotelopai.reservation.application.ReservationWebhookEventCategory
import com.hotelopai.reservation.application.ReservationWebhookProperties
import com.hotelopai.reservation.application.ReservationWebhookRequest
import com.hotelopai.reservation.application.ReservationWebhookVerificationResult
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class ApaleoReservationWebhookAdapterTest {
    private val receivedAt = Instant.parse("2026-07-24T10:00:00Z")

    @Test
    fun `valid Apaleo webhook extracts safe provider-neutral event`() {
        val adapter = adapter()

        val result = adapter.verifyAndExtract(request())

        assertThat(result).isInstanceOf(ReservationWebhookVerificationResult.Verified::class.java)
        val event = (result as ReservationWebhookVerificationResult.Verified).event
        assertThat(event.providerEventId).isEqualTo("event-1")
        assertThat(event.category).isEqualTo(ReservationWebhookEventCategory.RESERVATION_CHANGED)
        assertThat(event.propertyScopeLabel).startsWith("configured:")
        assertThat(event.propertyScopeLabel).doesNotContain("MUC")
        assertThat(event.externalEntityHash).isNotBlank()
        assertThat(event.safeMetadata).containsEntry("topic", "reservation")
        assertThat(event.safeMetadata).containsEntry("type", "changed")
    }

    @Test
    fun `invalid token and stale timestamp are rejected without exposing secret material`() {
        val adapter = adapter()

        val invalidToken = adapter.verifyAndExtract(request(token = "wrong"))
        val staleTimestamp = adapter.verifyAndExtract(
            request(body = payload(timestamp = Instant.parse("2026-07-24T09:00:00Z").toEpochMilli()))
        )

        assertThat(invalidToken).isInstanceOf(ReservationWebhookVerificationResult.Rejected::class.java)
        assertThat((invalidToken as ReservationWebhookVerificationResult.Rejected).reasonCode).isEqualTo("invalid_webhook_token")
        assertThat(staleTimestamp).isInstanceOf(ReservationWebhookVerificationResult.Rejected::class.java)
        assertThat((staleTimestamp as ReservationWebhookVerificationResult.Rejected).reasonCode).isEqualTo("timestamp_outside_tolerance")
    }

    @Test
    fun `unsupported Apaleo topic is verified but categorized for safe ignore`() {
        val adapter = adapter()

        val result = adapter.verifyAndExtract(
            request(body = payload(topic = "folio", type = "created"))
        )

        assertThat(result).isInstanceOf(ReservationWebhookVerificationResult.Verified::class.java)
        assertThat((result as ReservationWebhookVerificationResult.Verified).event.category)
            .isEqualTo(ReservationWebhookEventCategory.UNSUPPORTED)
    }

    private fun adapter(): ApaleoReservationWebhookAdapter {
        val provider = object : PmsProvider {
            override val id = PmsProviderId("apaleo")
            override val displayName = "Apaleo"
            override val capabilities = PmsCapabilities()
            override fun listRooms(): List<PmsRoom> = emptyList()
            override fun findRoom(roomNumber: String): PmsRoom? = null
            override fun findRoomStatus(roomNumber: String): PmsRoomStatus? = null
            override fun findStay(roomNumber: String): PmsStay? = null
            override fun getRoomAssets(roomNumber: String): List<PmsAsset> = emptyList()
            override fun findAsset(assetId: String): PmsAsset? = null
            override fun listIssueTypes(): List<PmsIssueType> = emptyList()
            override fun findHousekeepingTask(taskId: String): PmsHousekeepingTask? = null
            override fun updateHousekeepingTaskStatus(taskId: String, request: HousekeepingTaskStatusUpdate): PmsHousekeepingTask = error("not used")
            override fun updateRoomStatus(roomNumber: String, request: RoomStatusUpdate): PmsUpdateResult = error("not used")
            override fun updateMaintenance(request: MaintenanceUpdate): PmsUpdateResult = error("not used")
            override fun createEvent(command: PmsEventCreateCommand): PmsEvent = error("not used")
        }
        val registry = PmsProviderRegistry(
            providers = listOf(provider),
            properties = PmsProviderProperties(
                activeProvider = "apaleo",
                providers = mapOf(
                    "apaleo" to PmsConfiguredProviderProperties(
                        enabled = true,
                        hotelPropertyIdentifier = "MUC",
                        settings = mapOf("webhook-token-reference" to "env:APALEO_WEBHOOK_TOKEN")
                    )
                )
            )
        )
        return ApaleoReservationWebhookAdapter(
            objectMapper = jacksonObjectMapper(),
            providerRegistry = registry,
            credentialResolver = object : PmsCredentialResolver {
                override fun resolve(reference: String): String {
                    require(reference == "env:APALEO_WEBHOOK_TOKEN")
                    return "webhook-secret"
                }
            },
            properties = ReservationWebhookProperties(enabled = true)
        )
    }

    private fun request(
        token: String = "webhook-secret",
        body: String = payload()
    ): ReservationWebhookRequest =
        ReservationWebhookRequest(
            providerId = "apaleo",
            contentType = "application/json",
            queryToken = token,
            headers = mapOf("apaleo-tracking-id" to "tracking-1"),
            body = body.toByteArray(),
            receivedAt = receivedAt
        )

    private fun payload(
        topic: String = "reservation",
        type: String = "changed",
        timestamp: Long = receivedAt.minusSeconds(30).toEpochMilli()
    ): String =
        """
        {
          "id": "event-1",
          "topic": "$topic",
          "type": "$type",
          "accountId": "HFTF",
          "propertyId": "MUC",
          "timestamp": $timestamp,
          "data": {
            "entityId": "XPGMSXGF-1"
          }
        }
        """.trimIndent()
}
