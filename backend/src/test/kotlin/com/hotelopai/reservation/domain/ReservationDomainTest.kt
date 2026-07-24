package com.hotelopai.reservation.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

class ReservationDomainTest {
    @Test
    fun `aggregate construction enforces date occupancy guest and assignment invariants`() {
        assertThrows(IllegalArgumentException::class.java) {
            DateRange(LocalDate.parse("2026-07-25"), LocalDate.parse("2026-07-25"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            Occupancy(adults = 0, children = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            reservation(accompanyingGuests = listOf(Guest(GuestId("guest-1"), "Duplicate")))
        }
        assertThrows(IllegalArgumentException::class.java) {
            reservation(
                roomAssignment = RoomAssignment(
                    roomId = RoomId("102"),
                    period = DateRange(LocalDate.parse("2026-07-23"), LocalDate.parse("2026-07-26"))
                )
            )
        }
    }

    @Test
    fun `valid lifecycle transitions distinguish reservation and stay state`() {
        val now = Instant.parse("2026-07-24T10:00:00Z")
        val checkedOut = reservation(reservationStatus = ReservationStatus.PENDING)
            .confirm(now)
            .checkIn(now.plusSeconds(1))
            .checkOut(now.plusSeconds(2))

        assertEquals(ReservationStatus.CONFIRMED, checkedOut.reservationStatus)
        assertEquals(StayStatus.CHECKED_OUT, checkedOut.stayStatus)
        assertEquals(now.plusSeconds(2), checkedOut.modifiedAt)
    }

    @Test
    fun `invalid lifecycle transitions are rejected without guest data`() {
        val exception = assertThrows(InvalidReservationTransitionException::class.java) {
            reservation(reservationStatus = ReservationStatus.CANCELLED).checkIn()
        }

        assertThat(exception.message).doesNotContain("Ada")
        assertThat(reservation().toString()).doesNotContain("Ada", "late arrival", "VIP")
    }

    @Test
    fun `cancellation and no-show behavior is explicit`() {
        val cancelled = reservation(reservationStatus = ReservationStatus.CONFIRMED).cancel()
        val noShow = reservation(reservationStatus = ReservationStatus.CONFIRMED).markNoShow()

        assertEquals(ReservationStatus.CANCELLED, cancelled.reservationStatus)
        assertEquals(ReservationStatus.NO_SHOW, noShow.reservationStatus)
        assertThrows(InvalidReservationTransitionException::class.java) {
            cancelled.assignRoom(RoomAssignment(RoomId("104"), cancelled.stayPeriod))
        }
    }

    private fun reservation(
        reservationStatus: ReservationStatus = ReservationStatus.CONFIRMED,
        stayStatus: StayStatus = StayStatus.NOT_ARRIVED,
        accompanyingGuests: List<Guest> = emptyList(),
        roomAssignment: RoomAssignment? = RoomAssignment(
            roomId = RoomId("101"),
            period = DateRange(LocalDate.parse("2026-07-24"), LocalDate.parse("2026-07-26"))
        )
    ): Reservation =
        Reservation.create(
            externalReference = ExternalReservationReference("RES-1"),
            propertyId = PropertyId("MUC"),
            primaryGuest = Guest(GuestId("guest-1"), "Ada Lovelace"),
            accompanyingGuests = accompanyingGuests,
            stayPeriod = DateRange(LocalDate.parse("2026-07-24"), LocalDate.parse("2026-07-26")),
            reservationStatus = reservationStatus,
            stayStatus = stayStatus,
            roomAssignment = roomAssignment,
            occupancy = Occupancy(adults = 1 + accompanyingGuests.size),
            source = ReservationSource.PMS,
            specialRequests = "late arrival",
            operationalNotes = "VIP",
            createdAt = Instant.parse("2026-07-24T09:00:00Z")
        )
}
