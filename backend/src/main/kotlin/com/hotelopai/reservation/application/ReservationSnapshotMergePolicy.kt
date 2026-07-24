package com.hotelopai.reservation.application

import com.hotelopai.reservation.domain.Guest
import com.hotelopai.reservation.domain.Reservation
import com.hotelopai.reservation.domain.RoomAssignment
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class ReservationSnapshotMergePolicy {
    fun merge(
        providerId: String,
        incoming: Reservation,
        existing: ReservationSnapshot?,
        sourceDataTimestamp: Instant?,
        now: Instant
    ): ReservationSnapshotMergeDecision {
        if (existing == null) {
            val created = incoming.withPersistenceIdentity(createdAt = now, modifiedAt = now)
            return ReservationSnapshotMergeDecision(
                outcome = ReservationSyncOutcome.CREATED,
                snapshot = ReservationSnapshot(providerId, created, sourceDataTimestamp),
                previousReservation = null
            )
        }

        if (sourceDataTimestamp != null && existing.pmsSourceUpdatedAt != null && sourceDataTimestamp.isBefore(existing.pmsSourceUpdatedAt)) {
            return ReservationSnapshotMergeDecision(ReservationSyncOutcome.SKIPPED_STALE, null, existing.reservation)
        }

        if (hasLocalOperationalConflict(existing.reservation, incoming)) {
            return ReservationSnapshotMergeDecision(ReservationSyncOutcome.CONFLICT, null, existing.reservation)
        }

        val merged = incoming.withExistingSnapshotIdentity(existing.reservation, now)
        if (sourceDataTimestamp == existing.pmsSourceUpdatedAt && hasSamePmsOwnedFields(existing.reservation, merged)) {
            return ReservationSnapshotMergeDecision(ReservationSyncOutcome.UNCHANGED, null, existing.reservation)
        }
        if (sourceDataTimestamp == null && existing.pmsSourceUpdatedAt == null && hasSamePmsOwnedFields(existing.reservation, merged)) {
            return ReservationSnapshotMergeDecision(ReservationSyncOutcome.UNCHANGED, null, existing.reservation)
        }

        return ReservationSnapshotMergeDecision(
            outcome = ReservationSyncOutcome.UPDATED,
            snapshot = existing.copy(
                reservation = merged,
                pmsSourceUpdatedAt = sourceDataTimestamp ?: existing.pmsSourceUpdatedAt
            ),
            previousReservation = existing.reservation
        )
    }

    private fun Reservation.withPersistenceIdentity(createdAt: Instant, modifiedAt: Instant): Reservation =
        Reservation.create(
            id = id,
            externalReference = externalReference,
            propertyId = propertyId,
            primaryGuest = primaryGuest.withoutPersonalDisplay(),
            accompanyingGuests = accompanyingGuests.map { it.withoutPersonalDisplay() },
            stayPeriod = stayPeriod,
            reservationStatus = reservationStatus,
            stayStatus = stayStatus,
            roomAssignment = roomAssignment,
            occupancy = occupancy,
            source = source,
            specialRequests = null,
            operationalNotes = operationalNotes,
            createdAt = createdAt,
            modifiedAt = modifiedAt
        )

    private fun Reservation.withExistingSnapshotIdentity(existing: Reservation, modifiedAt: Instant): Reservation =
        Reservation.create(
            id = existing.id,
            externalReference = externalReference,
            propertyId = propertyId,
            primaryGuest = primaryGuest.withoutPersonalDisplay(),
            accompanyingGuests = accompanyingGuests.map { it.withoutPersonalDisplay() },
            stayPeriod = stayPeriod,
            reservationStatus = reservationStatus,
            stayStatus = stayStatus,
            roomAssignment = roomAssignment,
            occupancy = occupancy,
            source = source,
            specialRequests = null,
            operationalNotes = existing.operationalNotes,
            createdAt = existing.createdAt,
            modifiedAt = modifiedAt
        )

    private fun Guest.withoutPersonalDisplay(): Guest =
        Guest(id = id)

    private fun hasSamePmsOwnedFields(existing: Reservation, incoming: Reservation): Boolean =
        existing.externalReference == incoming.externalReference &&
            existing.propertyId == incoming.propertyId &&
            existing.primaryGuest.id == incoming.primaryGuest.id &&
            existing.accompanyingGuests.map { it.id }.sortedBy { it.value } ==
            incoming.accompanyingGuests.map { it.id }.sortedBy { it.value } &&
            existing.stayPeriod == incoming.stayPeriod &&
            existing.reservationStatus == incoming.reservationStatus &&
            existing.stayStatus == incoming.stayStatus &&
            sameRoomAssignment(existing.roomAssignment, incoming.roomAssignment) &&
            existing.occupancy == incoming.occupancy &&
            existing.source == incoming.source

    private fun sameRoomAssignment(left: RoomAssignment?, right: RoomAssignment?): Boolean =
        left?.roomId == right?.roomId && left?.period == right?.period

    private fun hasLocalOperationalConflict(existing: Reservation, incoming: Reservation): Boolean =
        existing.operationalNotes != null &&
            incoming.operationalNotes != null &&
            existing.operationalNotes != incoming.operationalNotes
}

data class ReservationSnapshotMergeDecision(
    val outcome: ReservationSyncOutcome,
    val snapshot: ReservationSnapshot?,
    val previousReservation: Reservation?
)
