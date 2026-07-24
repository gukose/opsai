package com.hotelopai.reservation.domain

import com.hotelopai.shared.kernel.UuidV7Generator
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@JvmInline
value class ReservationId(val value: UUID) {
    companion object {
        fun generate(): ReservationId = ReservationId(UuidV7Generator.generate())
    }
}

@JvmInline
value class ExternalReservationReference(val value: String) {
    init {
        require(value.isNotBlank()) { "external reservation reference must not be blank" }
    }

    override fun toString(): String = "ExternalReservationReference(**redacted**)"
}

@JvmInline
value class GuestId(val value: String) {
    init {
        require(value.isNotBlank()) { "guest id must not be blank" }
    }

    override fun toString(): String = "GuestId(**redacted**)"
}

@JvmInline
value class PropertyId(val value: String) {
    init {
        require(value.isNotBlank()) { "property id must not be blank" }
    }
}

@JvmInline
value class RoomId(val value: String) {
    init {
        require(value.isNotBlank()) { "room id must not be blank" }
    }
}

enum class ReservationStatus {
    PENDING,
    CONFIRMED,
    CANCELLED,
    NO_SHOW
}

enum class StayStatus {
    NOT_ARRIVED,
    IN_HOUSE,
    CHECKED_OUT
}

enum class ReservationSource {
    PMS,
    DIRECT,
    OTA,
    UNKNOWN
}

data class DateRange(
    val arrival: LocalDate,
    val departure: LocalDate
) {
    init {
        require(departure.isAfter(arrival)) { "departure must be after arrival" }
    }

    fun overlaps(other: DateRange): Boolean =
        arrival.isBefore(other.departure) && departure.isAfter(other.arrival)
}

data class Occupancy(
    val adults: Int,
    val children: Int = 0
) {
    init {
        require(adults >= 0) { "adult occupancy must not be negative" }
        require(children >= 0) { "child occupancy must not be negative" }
        require(total > 0) { "occupancy must include at least one occupant" }
    }

    val total: Int
        get() = adults + children
}

data class Guest(
    val id: GuestId,
    val displayName: String? = null
) {
    override fun toString(): String =
        "Guest(id=$id, displayName=**redacted**)"
}

data class RoomAssignment(
    val roomId: RoomId,
    val period: DateRange
) {
    fun validateWithin(stayPeriod: DateRange) {
        require(!period.arrival.isBefore(stayPeriod.arrival) && !period.departure.isAfter(stayPeriod.departure)) {
            "room assignment period must be within reservation stay dates"
        }
    }
}

class Reservation private constructor(
    val id: ReservationId,
    val externalReference: ExternalReservationReference,
    val propertyId: PropertyId,
    val primaryGuest: Guest,
    accompanyingGuests: List<Guest>,
    val stayPeriod: DateRange,
    val reservationStatus: ReservationStatus,
    val stayStatus: StayStatus,
    val roomAssignment: RoomAssignment?,
    val occupancy: Occupancy,
    val source: ReservationSource,
    val specialRequests: String?,
    val operationalNotes: String?,
    val createdAt: Instant,
    val modifiedAt: Instant
) {
    val accompanyingGuests: List<Guest> = accompanyingGuests.toList()

    init {
        requireNoDuplicateGuests(primaryGuest, this.accompanyingGuests)
        roomAssignment?.validateWithin(stayPeriod)
        requireStatusConsistency(reservationStatus, stayStatus)
    }

    fun confirm(now: Instant = Instant.now()): Reservation =
        transitionReservation(
            allowed = setOf(ReservationStatus.PENDING),
            nextReservationStatus = ReservationStatus.CONFIRMED,
            nextStayStatus = StayStatus.NOT_ARRIVED,
            now = now
        )

    fun checkIn(now: Instant = Instant.now()): Reservation =
        transitionReservation(
            allowed = setOf(ReservationStatus.CONFIRMED),
            nextReservationStatus = ReservationStatus.CONFIRMED,
            nextStayStatus = StayStatus.IN_HOUSE,
            now = now
        )

    fun checkOut(now: Instant = Instant.now()): Reservation {
        if (stayStatus != StayStatus.IN_HOUSE) {
            throw InvalidReservationTransitionException("Only in-house stays can be checked out.")
        }
        return copy(stayStatus = StayStatus.CHECKED_OUT, modifiedAt = now)
    }

    fun cancel(now: Instant = Instant.now()): Reservation {
        if (reservationStatus !in setOf(ReservationStatus.PENDING, ReservationStatus.CONFIRMED)) {
            throw InvalidReservationTransitionException("Only pending or confirmed reservations can be cancelled.")
        }
        if (stayStatus == StayStatus.IN_HOUSE) {
            throw InvalidReservationTransitionException("In-house stays cannot be cancelled.")
        }
        return copy(reservationStatus = ReservationStatus.CANCELLED, modifiedAt = now)
    }

    fun markNoShow(now: Instant = Instant.now()): Reservation {
        if (reservationStatus != ReservationStatus.CONFIRMED || stayStatus != StayStatus.NOT_ARRIVED) {
            throw InvalidReservationTransitionException("Only confirmed not-arrived reservations can be marked no-show.")
        }
        return copy(reservationStatus = ReservationStatus.NO_SHOW, modifiedAt = now)
    }

    fun assignRoom(assignment: RoomAssignment, now: Instant = Instant.now()): Reservation {
        assignment.validateWithin(stayPeriod)
        if (reservationStatus in setOf(ReservationStatus.CANCELLED, ReservationStatus.NO_SHOW)) {
            throw InvalidReservationTransitionException("Cancelled or no-show reservations cannot receive room assignments.")
        }
        return copy(roomAssignment = assignment, modifiedAt = now)
    }

    private fun transitionReservation(
        allowed: Set<ReservationStatus>,
        nextReservationStatus: ReservationStatus,
        nextStayStatus: StayStatus,
        now: Instant
    ): Reservation {
        if (reservationStatus !in allowed) {
            throw InvalidReservationTransitionException("Reservation transition is not allowed.")
        }
        return copy(
            reservationStatus = nextReservationStatus,
            stayStatus = nextStayStatus,
            modifiedAt = now
        )
    }

    private fun copy(
        reservationStatus: ReservationStatus = this.reservationStatus,
        stayStatus: StayStatus = this.stayStatus,
        roomAssignment: RoomAssignment? = this.roomAssignment,
        modifiedAt: Instant = this.modifiedAt
    ): Reservation =
        Reservation(
            id = id,
            externalReference = externalReference,
            propertyId = propertyId,
            primaryGuest = primaryGuest,
            accompanyingGuests = accompanyingGuests,
            stayPeriod = stayPeriod,
            reservationStatus = reservationStatus,
            stayStatus = stayStatus,
            roomAssignment = roomAssignment,
            occupancy = occupancy,
            source = source,
            specialRequests = specialRequests,
            operationalNotes = operationalNotes,
            createdAt = createdAt,
            modifiedAt = modifiedAt
        )

    override fun toString(): String =
        "Reservation(id=$id, externalReference=$externalReference, propertyId=$propertyId, " +
            "reservationStatus=$reservationStatus, stayStatus=$stayStatus, occupancy=$occupancy, " +
            "source=$source, guestData=**redacted**)"

    companion object {
        fun create(
            id: ReservationId = ReservationId.generate(),
            externalReference: ExternalReservationReference,
            propertyId: PropertyId,
            primaryGuest: Guest,
            accompanyingGuests: List<Guest> = emptyList(),
            stayPeriod: DateRange,
            reservationStatus: ReservationStatus,
            stayStatus: StayStatus,
            roomAssignment: RoomAssignment? = null,
            occupancy: Occupancy,
            source: ReservationSource = ReservationSource.UNKNOWN,
            specialRequests: String? = null,
            operationalNotes: String? = null,
            createdAt: Instant = Instant.now(),
            modifiedAt: Instant = createdAt
        ): Reservation =
            Reservation(
                id = id,
                externalReference = externalReference,
                propertyId = propertyId,
                primaryGuest = primaryGuest,
                accompanyingGuests = accompanyingGuests,
                stayPeriod = stayPeriod,
                reservationStatus = reservationStatus,
                stayStatus = stayStatus,
                roomAssignment = roomAssignment,
                occupancy = occupancy,
                source = source,
                specialRequests = specialRequests?.takeIf { it.isNotBlank() },
                operationalNotes = operationalNotes?.takeIf { it.isNotBlank() },
                createdAt = createdAt,
                modifiedAt = modifiedAt
            )

        private fun requireNoDuplicateGuests(primaryGuest: Guest, accompanyingGuests: List<Guest>) {
            val ids = listOf(primaryGuest.id) + accompanyingGuests.map { it.id }
            require(ids.distinct().size == ids.size) { "reservation guest identities must be unique" }
        }

        private fun requireStatusConsistency(reservationStatus: ReservationStatus, stayStatus: StayStatus) {
            require(!(reservationStatus == ReservationStatus.CANCELLED && stayStatus == StayStatus.IN_HOUSE)) {
                "cancelled reservations cannot be in house"
            }
            require(!(reservationStatus == ReservationStatus.NO_SHOW && stayStatus != StayStatus.NOT_ARRIVED)) {
                "no-show reservations must not have an active stay"
            }
        }
    }
}

class InvalidReservationTransitionException(
    message: String
) : RuntimeException(message)
