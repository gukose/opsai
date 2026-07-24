package com.hotelopai.pms.application

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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

class PmsOperationsServiceTest {
    private val clock = Clock.fixed(Instant.parse("2026-07-24T10:15:30Z"), ZoneId.of("UTC"))

    @Test
    fun `lists sanitized provider summaries and active provider`() {
        val service = service(provider = StubProvider("internal-demo"))

        val summaries = service.listProviders()

        assertEquals(listOf("internal-demo"), summaries.map { it.providerId })
        assertEquals(true, summaries.single().active)
        assertEquals(PmsHealthState.READY, summaries.single().healthState)
    }

    @Test
    fun `rollout readiness blocks external provider missing property id`() {
        val audit = RecordingAuditSink()
        val service = service(
            provider = StubProvider("apaleo"),
            properties = PmsProviderProperties(
                activeProvider = "apaleo",
                providers = mapOf(
                    "apaleo" to PmsConfiguredProviderProperties(enabled = true)
                )
            ),
            auditSink = audit
        )

        val readiness = service.activeRolloutReadiness("actor-1")

        assertEquals(PmsRolloutReadinessState.BLOCKED, readiness.state)
        assertTrue(readiness.blockingReasons.any { it.contains("property identifier") })
        assertEquals(PmsOperationsAuditAction.ROLLOUT_READINESS_INSPECTED, audit.events.single().action)
        assertEquals("actor-1", audit.events.single().actorUserId)
    }

    @Test
    fun `credential refresh records safe audit result`() {
        val audit = RecordingAuditSink()
        val provider = StubProvider("internal-demo")
        val result = service(provider = provider, auditSink = audit).refreshCredentials("internal-demo", "actor-2")

        assertEquals(true, result.success)
        assertEquals(PmsFailureCategory.NONE, result.failureCategory)
        assertEquals(PmsOperationsAuditAction.CREDENTIAL_REFRESH_REQUESTED, audit.events.single().action)
        assertEquals(false, audit.events.single().toString().contains("secret"))
    }

    private fun service(
        provider: StubProvider,
        properties: PmsProviderProperties = PmsProviderProperties(
            activeProvider = provider.id.value,
            providers = mapOf(provider.id.value to PmsConfiguredProviderProperties(enabled = true))
        ),
        auditSink: RecordingAuditSink = RecordingAuditSink()
    ): PmsOperationsService =
        PmsOperationsService(
            registry = PmsProviderRegistry(listOf(provider), properties),
            auditSink = auditSink,
            clock = clock
        )

    private class RecordingAuditSink : PmsOperationsAuditSink {
        val events = mutableListOf<PmsOperationsAuditEvent>()
        override fun record(event: PmsOperationsAuditEvent) {
            events += event
        }
    }

    private class StubProvider(
        id: String,
        private val healthState: PmsHealthState = PmsHealthState.READY
    ) : PmsProvider {
        override val id = PmsProviderId(id)
        override val displayName = "Stub PMS"
        override val capabilities = PmsCapabilities(roomListing = true, hotelLookup = true)

        override fun health(config: PmsConfiguredProviderProperties?): PmsProviderHealth =
            super.health(config).copy(state = healthState)

        override fun listRooms(): List<PmsRoom> = emptyList()
        override fun findRoom(roomNumber: String): PmsRoom? = null
        override fun findRoomStatus(roomNumber: String): PmsRoomStatus? = null
        override fun findStay(roomNumber: String): PmsStay? = null
        override fun getRoomAssets(roomNumber: String): List<PmsAsset> = emptyList()
        override fun findAsset(assetId: String): PmsAsset? = null
        override fun listIssueTypes(): List<PmsIssueType> = emptyList()
        override fun findHousekeepingTask(taskId: String): PmsHousekeepingTask? = null
        override fun updateHousekeepingTaskStatus(taskId: String, request: HousekeepingTaskStatusUpdate): PmsHousekeepingTask =
            error("not used")

        override fun updateRoomStatus(roomNumber: String, request: RoomStatusUpdate): PmsUpdateResult =
            PmsUpdateResult(UUID.randomUUID(), roomNumber, "ROOM_STATUS_UPDATE", request.status)

        override fun updateMaintenance(request: MaintenanceUpdate): PmsUpdateResult =
            PmsUpdateResult(UUID.randomUUID(), request.roomNumber, "MAINTENANCE_UPDATE", request.status)

        override fun createEvent(command: PmsEventCreateCommand): PmsEvent =
            PmsEvent(command.eventId ?: "event-1", command.type, command.subject)
    }
}
