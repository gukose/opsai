package com.hotelopai.reservation.infrastructure

import com.hotelopai.reservation.application.ReservationRepository
import com.hotelopai.reservation.domain.DateRange
import com.hotelopai.reservation.domain.ExternalReservationReference
import com.hotelopai.reservation.domain.PropertyId
import com.hotelopai.reservation.domain.Reservation
import com.hotelopai.reservation.domain.ReservationId
import org.springframework.dao.OptimisticLockingFailureException
import java.util.concurrent.ConcurrentHashMap

class InMemoryReservationRepository : ReservationRepository {
    private val snapshots = ConcurrentHashMap<ReservationId, com.hotelopai.reservation.application.ReservationSnapshot>()

    override fun findById(id: ReservationId): Reservation? =
        findSnapshotById(id)?.reservation

    override fun findSnapshotById(id: ReservationId): com.hotelopai.reservation.application.ReservationSnapshot? =
        snapshots[id]

    override fun findByExternalReference(reference: ExternalReservationReference): Reservation? =
        snapshots.values.firstOrNull { it.reservation.externalReference == reference }?.reservation

    override fun findSnapshotByMatch(
        providerId: String,
        externalReference: ExternalReservationReference,
        propertyId: PropertyId
    ): com.hotelopai.reservation.application.ReservationSnapshot? =
        snapshots.values.firstOrNull {
            it.providerId == providerId &&
                it.reservation.externalReference == externalReference &&
                it.reservation.propertyId == propertyId
        }

    override fun findByPropertyAndDateRange(propertyId: PropertyId, dateRange: DateRange): List<Reservation> =
        snapshots.values.map { it.reservation }
            .filter { it.propertyId == propertyId && it.stayPeriod.overlaps(dateRange) }
            .sortedWith(compareBy<Reservation> { it.stayPeriod.arrival }.thenBy { it.externalReference.value })

    override fun save(reservation: Reservation): Reservation {
        saveSnapshot(com.hotelopai.reservation.application.ReservationSnapshot("local", reservation))
        return reservation
    }

    override fun saveSnapshot(
        snapshot: com.hotelopai.reservation.application.ReservationSnapshot
    ): com.hotelopai.reservation.application.ReservationSnapshot {
        val existing = snapshots[snapshot.reservation.id]
        if (existing != null && snapshot.localVersion != existing.localVersion) {
            throw OptimisticLockingFailureException("Reservation snapshot was modified concurrently.")
        }
        val saved = snapshot.copy(localVersion = existing?.localVersion?.plus(1) ?: snapshot.localVersion)
        snapshots[snapshot.reservation.id] = saved
        return saved
    }
}
