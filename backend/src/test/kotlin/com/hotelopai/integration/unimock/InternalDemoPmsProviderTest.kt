package com.hotelopai.integration.unimock

import com.hotelopai.pms.application.PmsAuthenticationMode
import com.hotelopai.pms.application.PmsAuthenticationProperties
import com.hotelopai.pms.application.BearerTokenPmsAuthenticationProperties
import com.hotelopai.pms.application.PmsConfiguredProviderProperties
import com.hotelopai.pms.domain.MaintenanceUpdate
import com.hotelopai.pms.domain.PmsProviderResourceNotFoundException
import com.hotelopai.pms.domain.PmsProviderTimeoutException
import com.hotelopai.pms.domain.RoomStatusUpdate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class InternalDemoPmsProviderTest {
    @Test
    fun `internal demo declares capabilities matching implemented operations`() {
        val provider = InternalDemoPmsProvider(RecordingUniMockClient())

        assertEquals("internal-demo", provider.id.value)
        assertEquals("Internal Demo PMS", provider.displayName)
        assertEquals(true, provider.capabilities.roomListing)
        assertEquals(true, provider.capabilities.roomStatusLookup)
        assertEquals(true, provider.capabilities.roomStatusUpdate)
        assertEquals(true, provider.capabilities.stayLookup)
        assertEquals(true, provider.capabilities.assetLookup)
        assertEquals(true, provider.capabilities.issueTypeLookup)
        assertEquals(true, provider.capabilities.housekeepingStatusUpdate)
        assertEquals(true, provider.capabilities.maintenanceUpdate)
        assertEquals(true, provider.capabilities.eventCreation)
        assertEquals(false, provider.capabilities.webhooks)
        assertEquals(false, provider.capabilities.incrementalSync)
    }

    @Test
    fun `internal demo default configuration is ready without credentials`() {
        val provider = InternalDemoPmsProvider(RecordingUniMockClient())
        val readiness = provider.readiness(PmsConfiguredProviderProperties(enabled = true))

        assertEquals(true, readiness.enabled)
        assertEquals(true, readiness.configured)
        assertEquals(null, readiness.message)
    }

    @Test
    fun `internal demo rejects credentialed authentication configuration`() {
        val provider = InternalDemoPmsProvider(RecordingUniMockClient())
        val readiness = provider.readiness(
            PmsConfiguredProviderProperties(
                enabled = true,
                authentication = PmsAuthenticationProperties(
                    mode = PmsAuthenticationMode.BEARER_TOKEN,
                    bearerToken = BearerTokenPmsAuthenticationProperties("secret://internal-demo/token")
                )
            )
        )

        assertEquals(true, readiness.enabled)
        assertEquals(false, readiness.configured)
        assertEquals(true, readiness.message!!.contains("authentication mode NONE"))
    }

    @Test
    fun `provider exposes internal demo rooms through provider-independent model`() {
        val provider = InternalDemoPmsProvider(RecordingUniMockClient())

        val room = provider.findRoom("101")

        assertEquals("room-101", room?.id)
        assertEquals("101", room?.number)
        assertEquals("VACANT", room?.status)
    }

    @Test
    fun `maintenance update validates room and issue type then delegates to internal demo client`() {
        val client = RecordingUniMockClient()
        val provider = InternalDemoPmsProvider(client)

        val result = provider.updateMaintenance(
            MaintenanceUpdate(
                roomNumber = "101",
                issueTypeCode = "MAINTENANCE_AC",
                description = "AC resolved",
                status = "RESOLVED"
            )
        )

        assertEquals("101", client.maintenanceRequest?.roomNumber)
        assertEquals("MAINTENANCE_AC", client.maintenanceRequest?.issueTypeCode)
        assertEquals(client.updateResult.verificationLogId, result.verificationLogId)
    }

    @Test
    fun `room status update delegates through neutral request model`() {
        val client = RecordingUniMockClient()
        val provider = InternalDemoPmsProvider(client)

        provider.updateRoomStatus("101", RoomStatusUpdate(status = "OUT_OF_ORDER"))

        assertEquals("OUT_OF_ORDER", client.roomStatusRequest?.status)
    }

    @Test
    fun `provider maps missing room to provider not found exception`() {
        val provider = InternalDemoPmsProvider(RecordingUniMockClient(room = null))

        assertThrows(PmsProviderResourceNotFoundException::class.java) {
            provider.updateMaintenance(
                MaintenanceUpdate(
                    roomNumber = "404",
                    issueTypeCode = "MAINTENANCE_AC",
                    description = "AC resolved",
                    status = "RESOLVED"
                )
            )
        }
    }

    @Test
    fun `provider maps internal demo timeout to provider timeout`() {
        val provider = InternalDemoPmsProvider(RecordingUniMockClient(timeout = true))

        assertThrows(PmsProviderTimeoutException::class.java) {
            provider.listRooms()
        }
    }

    private class RecordingUniMockClient(
        private val room: PmsRoom? = defaultRoom,
        private val timeout: Boolean = false
    ) : UniMockClient {
        val updateResult = PmsUpdateResult(
            verificationLogId = UUID.fromString("11111111-1111-1111-1111-111111111111"),
            entityId = "101",
            operation = "PMS_UPDATE",
            status = "SUCCESS"
        )
        var maintenanceRequest: PmsMaintenanceUpdateRequest? = null
        var roomStatusRequest: PmsRoomStatusUpdateRequest? = null

        override fun getRoom(roomId: String): PmsRoom? = room

        override fun getRoomStatus(roomNumber: String): PmsRoomStatus? =
            PmsRoomStatus(roomNumber, "VACANT", "2026-07-24T08:00:00Z")

        override fun getRoomOccupancy(roomNumber: String): PmsRoomOccupancy? =
            PmsRoomOccupancy(roomNumber, reservationId = "res-1", guestId = "guest-1", occupied = true)

        override fun getRoomAssets(roomNumber: String): List<PmsAsset> =
            listOf(PmsAsset("asset-1", assetName = "Air conditioner", roomId = "room-101"))

        override fun listRooms(): List<PmsRoom> {
            if (timeout) throw UniMockClientTimeoutException("timeout")
            return listOfNotNull(room)
        }

        override fun getAsset(assetId: String): PmsAsset? =
            PmsAsset(assetId, assetName = "Air conditioner", roomId = "room-101")

        override fun listIssueTypes(): List<PmsIssueType> =
            listOf(PmsIssueType("issue-1", "MAINTENANCE_AC", "AC issue", requiresPmsUpdate = true))

        override fun getGuestRequest(guestRequestId: String): PmsGuestRequest? =
            PmsGuestRequest(
                guestRequestId = guestRequestId,
                status = "OPEN",
                roomId = "room-101",
                roomNumber = "101",
                guestName = "Guest",
                description = "Extra towels",
                source = "guest",
                createdAt = Instant.parse("2026-07-24T08:00:00Z")
            )

        override fun updateGuestRequestStatus(
            guestRequestId: String,
            request: PmsGuestRequestStatusUpdateRequest
        ): PmsGuestRequest =
            PmsGuestRequest(guestRequestId = guestRequestId, status = request.status)

        override fun updateRoomStatus(
            roomNumber: String,
            request: PmsRoomStatusUpdateRequest
        ): PmsUpdateResult {
            roomStatusRequest = request
            return updateResult
        }

        override fun updateMaintenance(request: PmsMaintenanceUpdateRequest): PmsUpdateResult {
            maintenanceRequest = request
            return updateResult
        }

        override fun createEvent(request: PmsEventCreateRequest): PmsEvent =
            PmsEvent(request.eventId ?: "event-1", request.type, request.subject)

        companion object {
            private val defaultRoom = PmsRoom(
                roomId = "room-101",
                roomNumber = "101",
                roomTypeId = "type-deluxe",
                roomTypeName = "Deluxe",
                floor = "1",
                occupied = false,
                status = "VACANT"
            )
        }
    }
}
