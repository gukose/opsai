package com.hotelopai.reservation.application

import com.hotelopai.pms.domain.PmsGuest
import com.hotelopai.pms.domain.PmsReservation
import com.hotelopai.reservation.domain.DateRange
import com.hotelopai.reservation.domain.ExternalReservationReference
import com.hotelopai.reservation.domain.Guest
import com.hotelopai.reservation.domain.GuestId
import com.hotelopai.reservation.domain.Occupancy
import com.hotelopai.reservation.domain.PropertyId
import com.hotelopai.reservation.domain.Reservation
import com.hotelopai.reservation.domain.ReservationSource
import com.hotelopai.reservation.domain.ReservationStatus
import com.hotelopai.reservation.domain.RoomAssignment
import com.hotelopai.reservation.domain.RoomId
import com.hotelopai.reservation.domain.StayStatus
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime

@Component
class PmsReservationMapper {
    fun toReservation(
        pmsReservation: PmsReservation,
        guestsById: Map<String, PmsGuest>,
        propertyId: PropertyId,
        now: Instant = Instant.now()
    ): Reservation {
        val externalReference = required(pmsReservation.id, "reservation external reference")
        val primaryGuestId = required(pmsReservation.guestId, "primary guest id")
        val stayPeriod = DateRange(
            arrival = parseProviderDate(required(pmsReservation.arrivalDate, "arrival date")),
            departure = parseProviderDate(required(pmsReservation.departureDate, "departure date"))
        )
        val primaryGuest = guestsById[primaryGuestId]?.toGuest()
            ?: Guest(id = GuestId(primaryGuestId))
        val accompanyingGuests = guestsById.values
            .filter { it.id.isNotBlank() && it.id != primaryGuestId }
            .map { it.toGuest() }
            .distinctBy { it.id }
        val reservationStatus = pmsReservation.status.toReservationStatus()
        val stayStatus = pmsReservation.status.toStayStatus()
        val roomAssignment = pmsReservation.roomNumber
            ?.takeIf { it.isNotBlank() }
            ?.let { RoomAssignment(RoomId(it), stayPeriod) }

        return Reservation.create(
            externalReference = ExternalReservationReference(externalReference),
            propertyId = propertyId,
            primaryGuest = primaryGuest,
            accompanyingGuests = accompanyingGuests,
            stayPeriod = stayPeriod,
            reservationStatus = reservationStatus,
            stayStatus = stayStatus,
            roomAssignment = roomAssignment,
            occupancy = Occupancy(adults = 1 + accompanyingGuests.size),
            source = ReservationSource.PMS,
            createdAt = now,
            modifiedAt = now
        )
    }

    private fun PmsGuest.toGuest(): Guest =
        Guest(
            id = GuestId(required(id, "guest id")),
            displayName = fullName?.takeIf { it.isNotBlank() }
        )

    private fun String?.toReservationStatus(): ReservationStatus =
        when (this?.trim()?.lowercase()) {
            "pending", "tentative", "optional" -> ReservationStatus.PENDING
            "confirmed", "inhouse", "in_house", "checkedin", "checked_in", "checkedout", "checked_out" ->
                ReservationStatus.CONFIRMED
            "cancelled", "canceled" -> ReservationStatus.CANCELLED
            "noshow", "no_show", "no-show" -> ReservationStatus.NO_SHOW
            null, "" -> ReservationStatus.CONFIRMED
            else -> ReservationStatus.CONFIRMED
        }

    private fun String?.toStayStatus(): StayStatus =
        when (this?.trim()?.lowercase()) {
            "inhouse", "in_house", "checkedin", "checked_in" -> StayStatus.IN_HOUSE
            "checkedout", "checked_out" -> StayStatus.CHECKED_OUT
            else -> StayStatus.NOT_ARRIVED
        }

    private fun parseProviderDate(value: String): LocalDate =
        runCatching { OffsetDateTime.parse(value).toLocalDate() }
            .recoverCatching { LocalDate.parse(value) }
            .getOrElse {
                throw ReservationMappingException("Provider reservation date could not be parsed.")
            }

    private fun required(value: String?, field: String): String =
        value?.takeIf { it.isNotBlank() }
            ?: throw ReservationMappingException("Provider reservation is missing required $field.")
}

class ReservationMappingException(
    message: String
) : RuntimeException(message)
