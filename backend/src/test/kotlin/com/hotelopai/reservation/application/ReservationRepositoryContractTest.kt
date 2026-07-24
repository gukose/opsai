package com.hotelopai.reservation.application

import com.hotelopai.reservation.domain.DateRange
import com.hotelopai.reservation.domain.ExternalReservationReference
import com.hotelopai.reservation.domain.Guest
import com.hotelopai.reservation.domain.GuestId
import com.hotelopai.reservation.domain.Occupancy
import com.hotelopai.reservation.domain.PropertyId
import com.hotelopai.reservation.domain.Reservation
import com.hotelopai.reservation.domain.ReservationSource
import com.hotelopai.reservation.domain.ReservationStatus
import com.hotelopai.reservation.domain.StayStatus
import com.hotelopai.reservation.infrastructure.InMemoryReservationRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

class ReservationRepositoryContractTest {
    @Test
    fun `in-memory repository supports future persistence contract`() {
        val repository: ReservationRepository = InMemoryReservationRepository()
        val reservation = reservation("RES-1", "MUC")

        repository.save(reservation)

        assertEquals(reservation, repository.findById(reservation.id))
        assertEquals(reservation, repository.findByExternalReference(ExternalReservationReference("RES-1")))
        assertEquals(
            listOf(reservation),
            repository.findByPropertyAndDateRange(
                PropertyId("MUC"),
                DateRange(LocalDate.parse("2026-07-25"), LocalDate.parse("2026-07-27"))
            )
        )
        assertEquals(
            emptyList<Reservation>(),
            repository.findByPropertyAndDateRange(
                PropertyId("BER"),
                DateRange(LocalDate.parse("2026-07-25"), LocalDate.parse("2026-07-27"))
            )
        )
    }

    private fun reservation(reference: String, propertyId: String): Reservation =
        Reservation.create(
            externalReference = ExternalReservationReference(reference),
            propertyId = PropertyId(propertyId),
            primaryGuest = Guest(GuestId("guest-$reference"), "Sensitive Guest"),
            stayPeriod = DateRange(LocalDate.parse("2026-07-24"), LocalDate.parse("2026-07-26")),
            reservationStatus = ReservationStatus.CONFIRMED,
            stayStatus = StayStatus.NOT_ARRIVED,
            occupancy = Occupancy(adults = 1),
            source = ReservationSource.PMS
        )
}
