package com.hotelopai.reservation.application

import com.hotelopai.pms.application.PmsProviderRegistry
import com.hotelopai.reservation.domain.DateRange
import com.hotelopai.reservation.domain.ExternalReservationReference
import com.hotelopai.reservation.domain.Guest
import com.hotelopai.reservation.domain.PropertyId
import com.hotelopai.reservation.domain.Reservation
import com.hotelopai.reservation.domain.StayStatus
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.LocalDate

@Service
class ReservationQueryService(
    private val pmsProviderRegistry: PmsProviderRegistry,
    private val mapper: PmsReservationMapper,
    private val clock: Clock
) {
    fun findByExternalReference(reference: ExternalReservationReference): Reservation? =
        loadActiveProviderReservations().firstOrNull { it.externalReference == reference }

    fun findByPropertyAndDateRange(propertyId: PropertyId, dateRange: DateRange): List<Reservation> =
        loadActiveProviderReservations()
            .filter { it.propertyId == propertyId && it.stayPeriod.overlaps(dateRange) }
            .sortedWith(compareBy<Reservation> { it.stayPeriod.arrival }.thenBy { it.externalReference.value })

    fun activeStays(propertyId: PropertyId = activePropertyId()): List<Reservation> =
        loadActiveProviderReservations(propertyId).filter { it.stayStatus == StayStatus.IN_HOUSE }

    fun arrivals(propertyId: PropertyId, date: LocalDate): List<Reservation> =
        findByPropertyAndDateRange(propertyId, DateRange(date, date.plusDays(1)))
            .filter { it.stayPeriod.arrival == date }

    fun departures(propertyId: PropertyId, date: LocalDate): List<Reservation> =
        findByPropertyAndDateRange(propertyId, DateRange(date.minusDays(1), date.plusDays(1)))
            .filter { it.stayPeriod.departure == date }

    fun inHouseGuests(propertyId: PropertyId = activePropertyId()): List<Guest> =
        activeStays(propertyId)
            .flatMap { listOf(it.primaryGuest) + it.accompanyingGuests }
            .distinctBy { it.id }

    private fun loadActiveProviderReservations(propertyId: PropertyId = activePropertyId()): List<Reservation> {
        val provider = pmsProviderRegistry.activeProvider()
        val guestsById = provider.listGuests().associateBy { it.id }
        return provider.listReservations().map { reservation ->
            mapper.toReservation(
                pmsReservation = reservation,
                guestsById = guestsById,
                propertyId = propertyId,
                now = clock.instant()
            )
        }
    }

    private fun activePropertyId(): PropertyId {
        val providerId = pmsProviderRegistry.activeProviderId()
        val propertyId = pmsProviderRegistry.providerConfig(providerId)
            ?.hotelPropertyIdentifier
            ?.takeIf { it.isNotBlank() }
            ?: throw ReservationMappingException("Active PMS provider has no configured property identifier.")
        return PropertyId(propertyId)
    }
}
