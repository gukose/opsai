package com.hotelopai.integration.apaleo

import com.hotelopai.pms.domain.PmsGuest
import com.hotelopai.pms.domain.PmsHotel
import com.hotelopai.pms.domain.PmsReservation
import com.hotelopai.pms.domain.PmsRoom
import com.hotelopai.pms.domain.PmsStay

object ApaleoPmsMapper {
    fun ApaleoPropertyDto.toDomain(): PmsHotel =
        PmsHotel(
            hotelId = id.orEmpty(),
            code = code,
            name = name
        )

    fun ApaleoUnitDto.toDomain(): PmsRoom =
        PmsRoom(
            id = id.orEmpty(),
            number = name?.takeIf { it.isNotBlank() } ?: id.orEmpty(),
            roomTypeId = unitGroup?.id,
            roomTypeName = unitGroup?.name,
            floor = null,
            occupied = false,
            status = condition ?: status
        )

    fun ApaleoReservationDto.toDomain(booking: ApaleoBookingDto): PmsReservation =
        PmsReservation(
            id = id.orEmpty(),
            guestId = primaryGuest?.stableId(id) ?: booking.booker?.stableId(booking.id),
            roomNumber = unit?.name ?: unit?.id,
            arrivalDate = arrival,
            departureDate = departure,
            status = status
        )

    fun ApaleoReservationDto.toStay(): PmsStay? {
        val roomNumber = unit?.name ?: unit?.id ?: return null
        return PmsStay(
            reservationId = id,
            guestId = primaryGuest?.stableId(id),
            roomNumber = roomNumber,
            occupied = status.equals("InHouse", ignoreCase = true) ||
                status.equals("Confirmed", ignoreCase = true)
        )
    }

    fun ApaleoBookingDto.toGuests(): List<PmsGuest> =
        buildList {
            booker?.toDomain(bookingScopedId("booker"))?.let(::add)
            reservations.forEach { reservation ->
                reservation.primaryGuest?.toDomain(reservation.guestScopedId("primary"))?.let(::add)
                reservation.guests.forEachIndexed { index, guest ->
                    guest.toDomain(reservation.guestScopedId("guest-$index"))?.let(::add)
                }
            }
        }.distinctBy { it.id }

    private fun ApaleoPersonDto.toDomain(fallbackId: String): PmsGuest? {
        val id = stableId(fallbackId)
        val fullName = listOfNotNull(firstName, middleInitial, lastName)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .ifBlank { null }

        if (id.isBlank() && fullName == null) {
            return null
        }
        return PmsGuest(
            id = id.ifBlank { fallbackId },
            fullName = fullName
        )
    }

    private fun ApaleoPersonDto.stableId(fallback: String?): String =
        id?.takeIf { it.isNotBlank() }
            ?: email?.takeIf { it.isNotBlank() }
            ?: fallback.orEmpty()

    private fun ApaleoBookingDto.bookingScopedId(suffix: String): String =
        listOfNotNull(id, suffix).joinToString(":")

    private fun ApaleoReservationDto.guestScopedId(suffix: String): String =
        listOfNotNull(id, suffix).joinToString(":")
}
