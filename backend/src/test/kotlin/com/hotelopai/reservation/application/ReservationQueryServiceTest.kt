package com.hotelopai.reservation.application

import com.hotelopai.pms.application.PmsCapabilities
import com.hotelopai.pms.application.PmsConfiguredProviderProperties
import com.hotelopai.pms.application.PmsProvider
import com.hotelopai.pms.application.PmsProviderProperties
import com.hotelopai.pms.application.PmsProviderRegistry
import com.hotelopai.pms.domain.HousekeepingTaskStatusUpdate
import com.hotelopai.pms.domain.MaintenanceUpdate
import com.hotelopai.pms.domain.PmsAsset
import com.hotelopai.pms.domain.PmsEvent
import com.hotelopai.pms.domain.PmsEventCreateCommand
import com.hotelopai.pms.domain.PmsGuest
import com.hotelopai.pms.domain.PmsHousekeepingTask
import com.hotelopai.pms.domain.PmsIssueType
import com.hotelopai.pms.domain.PmsProviderId
import com.hotelopai.pms.domain.PmsReservation
import com.hotelopai.pms.domain.PmsRoom
import com.hotelopai.pms.domain.PmsRoomStatus
import com.hotelopai.pms.domain.PmsStay
import com.hotelopai.pms.domain.PmsUpdateResult
import com.hotelopai.pms.domain.RoomStatusUpdate
import com.hotelopai.reservation.domain.DateRange
import com.hotelopai.reservation.domain.ExternalReservationReference
import com.hotelopai.reservation.domain.PropertyId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

class ReservationQueryServiceTest {
    private val clock = Clock.fixed(Instant.parse("2026-07-24T10:00:00Z"), ZoneId.of("UTC"))

    @Test
    fun `query service loads reservations from active PMS provider through canonical mapping`() {
        val service = service(
            reservations = listOf(
                pmsReservation("RES-ARR", "2026-07-24", "2026-07-26", "InHouse"),
                pmsReservation("RES-DEP", "2026-07-22", "2026-07-24", "CheckedOut")
            )
        )

        assertEquals("RES-ARR", service.findByExternalReference(ExternalReservationReference("RES-ARR"))!!.externalReference.value)
        assertEquals(1, service.activeStays(PropertyId("MUC")).size)
        assertEquals(1, service.arrivals(PropertyId("MUC"), LocalDate.parse("2026-07-24")).size)
        assertEquals(1, service.departures(PropertyId("MUC"), LocalDate.parse("2026-07-24")).size)
        assertEquals(listOf("guest-1", "guest-2"), service.inHouseGuests(PropertyId("MUC")).map { it.id.value })
    }

    @Test
    fun `query service filters by property and date range`() {
        val service = service(reservations = listOf(pmsReservation("RES-1", "2026-07-24", "2026-07-26")))

        val reservations = service.findByPropertyAndDateRange(
            PropertyId("MUC"),
            DateRange(LocalDate.parse("2026-07-25"), LocalDate.parse("2026-07-27"))
        )

        assertEquals(listOf("RES-1"), reservations.map { it.externalReference.value })
    }

    private fun service(reservations: List<PmsReservation>): ReservationQueryService {
        val provider = StubPmsProvider(reservations)
        val registry = PmsProviderRegistry(
            providers = listOf(provider),
            properties = PmsProviderProperties(
                activeProvider = "stub-pms",
                providers = mapOf(
                    "stub-pms" to PmsConfiguredProviderProperties(
                        enabled = true,
                        hotelPropertyIdentifier = "MUC"
                    )
                )
            )
        )
        return ReservationQueryService(registry, PmsReservationMapper(), clock)
    }

    private fun pmsReservation(
        id: String,
        arrivalDate: String,
        departureDate: String,
        status: String = "Confirmed"
    ): PmsReservation =
        PmsReservation(
            id = id,
            guestId = "guest-1",
            roomNumber = "101",
            arrivalDate = arrivalDate,
            departureDate = departureDate,
            status = status
        )

    private class StubPmsProvider(
        private val reservations: List<PmsReservation>
    ) : PmsProvider {
        override val id = PmsProviderId("stub-pms")
        override val displayName = "Stub PMS"
        override val capabilities = PmsCapabilities(reservationLookup = true, guestLookup = true, stayLookup = true)
        override fun listReservations(): List<PmsReservation> = reservations
        override fun listGuests(): List<PmsGuest> = listOf(PmsGuest("guest-1", "Ada Lovelace"), PmsGuest("guest-2", "Grace Hopper"))
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
