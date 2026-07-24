package com.hotelopai.reservation.application

import com.hotelopai.reservation.domain.DateRange
import com.hotelopai.reservation.domain.ExternalReservationReference
import com.hotelopai.reservation.domain.PropertyId
import com.hotelopai.reservation.domain.Reservation
import com.hotelopai.reservation.domain.ReservationId
import java.time.Instant

interface ReservationRepository {
    fun findById(id: ReservationId): Reservation?

    fun findSnapshotById(id: ReservationId): ReservationSnapshot?

    fun findByExternalReference(reference: ExternalReservationReference): Reservation?

    fun findSnapshotByMatch(
        providerId: String,
        externalReference: ExternalReservationReference,
        propertyId: PropertyId
    ): ReservationSnapshot?

    fun findByPropertyAndDateRange(propertyId: PropertyId, dateRange: DateRange): List<Reservation>

    fun save(reservation: Reservation): Reservation

    fun saveSnapshot(snapshot: ReservationSnapshot): ReservationSnapshot
}

data class ReservationSnapshot(
    val providerId: String,
    val reservation: Reservation,
    val pmsSourceUpdatedAt: Instant? = null,
    val localVersion: Long = 0
) {
    init {
        require(providerId.isNotBlank()) { "provider id must not be blank" }
        require(localVersion >= 0) { "local version must not be negative" }
    }
}
