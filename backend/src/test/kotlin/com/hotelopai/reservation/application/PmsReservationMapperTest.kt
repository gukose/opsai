package com.hotelopai.reservation.application

import com.hotelopai.integration.apaleo.ApaleoBookingDto
import com.hotelopai.integration.apaleo.ApaleoPersonDto
import com.hotelopai.integration.apaleo.ApaleoPmsMapper.toDomain
import com.hotelopai.integration.apaleo.ApaleoReservationDto
import com.hotelopai.integration.apaleo.ApaleoUnitDto
import com.hotelopai.pms.domain.PmsGuest
import com.hotelopai.pms.domain.PmsReservation
import com.hotelopai.reservation.domain.PropertyId
import com.hotelopai.reservation.domain.ReservationStatus
import com.hotelopai.reservation.domain.StayStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

class PmsReservationMapperTest {
    private val mapper = PmsReservationMapper()

    @Test
    fun `maps PMS reservation and guests into canonical reservation domain`() {
        val reservation = mapper.toReservation(
            pmsReservation = pmsReservation(status = "InHouse"),
            guestsById = mapOf(
                "guest-1" to PmsGuest("guest-1", "Ada Lovelace"),
                "guest-2" to PmsGuest("guest-2", "Grace Hopper")
            ),
            propertyId = PropertyId("MUC"),
            now = Instant.parse("2026-07-24T12:00:00Z")
        )

        assertEquals("RES-1", reservation.externalReference.value)
        assertEquals(LocalDate.parse("2026-07-24"), reservation.stayPeriod.arrival)
        assertEquals(LocalDate.parse("2026-07-26"), reservation.stayPeriod.departure)
        assertEquals(ReservationStatus.CONFIRMED, reservation.reservationStatus)
        assertEquals(StayStatus.IN_HOUSE, reservation.stayStatus)
        assertEquals("101", reservation.roomAssignment!!.roomId.value)
        assertEquals(2, reservation.occupancy.total)
        assertThat(reservation.toString()).doesNotContain("Ada", "Grace")
    }

    @Test
    fun `incomplete PMS reservation data fails explicitly`() {
        assertThrows(ReservationMappingException::class.java) {
            mapper.toReservation(
                pmsReservation = pmsReservation(id = ""),
                guestsById = emptyMap(),
                propertyId = PropertyId("MUC")
            )
        }
        assertThrows(ReservationMappingException::class.java) {
            mapper.toReservation(
                pmsReservation = pmsReservation(arrivalDate = null),
                guestsById = emptyMap(),
                propertyId = PropertyId("MUC")
            )
        }
    }

    @Test
    fun `Apaleo DTOs map through PMS models before canonical reservation domain`() {
        val apaleoReservation = ApaleoReservationDto(
            id = "RES-APALEO",
            status = "Confirmed",
            arrival = "2026-07-24T15:00:00+02:00",
            departure = "2026-07-26T11:00:00+02:00",
            unit = ApaleoUnitDto(id = "unit-101", name = "101"),
            primaryGuest = ApaleoPersonDto(id = "guest-1", firstName = "Ada", lastName = "Lovelace")
        )
        val pmsReservation = apaleoReservation.toDomain(ApaleoBookingDto(id = "BOOK-1"))

        val canonical = mapper.toReservation(
            pmsReservation = pmsReservation,
            guestsById = mapOf("guest-1" to PmsGuest("guest-1", "Ada Lovelace")),
            propertyId = PropertyId("MUC")
        )

        assertEquals("RES-APALEO", canonical.externalReference.value)
        assertEquals("MUC", canonical.propertyId.value)
        assertThat(canonical.toString()).doesNotContain("Lovelace")
    }

    private fun pmsReservation(
        id: String = "RES-1",
        arrivalDate: String? = "2026-07-24T15:00:00+02:00",
        departureDate: String? = "2026-07-26T11:00:00+02:00",
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
}
