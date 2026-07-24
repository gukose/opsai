package com.hotelopai.reservation.application

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.hotelopai.observability.OperationalObservability
import com.hotelopai.outbox.application.OperationalOutboxRepository
import com.hotelopai.outbox.application.OperationalOutboxStateCounts
import com.hotelopai.outbox.domain.OperationalOutboxEvent
import com.hotelopai.pms.application.PmsCapabilities
import com.hotelopai.pms.application.PmsConfiguredProviderProperties
import com.hotelopai.pms.application.PmsProvider
import com.hotelopai.pms.application.PmsProviderProperties
import com.hotelopai.pms.application.PmsProviderRegistry
import com.hotelopai.pms.application.UnsupportedPmsCapabilityException
import com.hotelopai.pms.domain.HousekeepingTaskStatusUpdate
import com.hotelopai.pms.domain.MaintenanceUpdate
import com.hotelopai.pms.domain.PmsAsset
import com.hotelopai.pms.domain.PmsEvent
import com.hotelopai.pms.domain.PmsEventCreateCommand
import com.hotelopai.pms.domain.PmsGuest
import com.hotelopai.pms.domain.PmsHousekeepingTask
import com.hotelopai.pms.domain.PmsIssueType
import com.hotelopai.pms.domain.PmsProviderId
import com.hotelopai.pms.domain.PmsReservation
import com.hotelopai.pms.domain.PmsRoom
import com.hotelopai.pms.domain.PmsRoomStatus
import com.hotelopai.pms.domain.PmsStay
import com.hotelopai.pms.domain.PmsUpdateResult
import com.hotelopai.pms.domain.RoomStatusUpdate
import com.hotelopai.reservation.domain.DateRange
import com.hotelopai.reservation.domain.ExternalReservationReference
import com.hotelopai.reservation.domain.Guest
import com.hotelopai.reservation.domain.GuestId
import com.hotelopai.reservation.domain.Occupancy
import com.hotelopai.reservation.domain.PropertyId
import com.hotelopai.reservation.domain.Reservation
import com.hotelopai.reservation.domain.ReservationSource
import com.hotelopai.reservation.domain.RoomAssignment
import com.hotelopai.reservation.infrastructure.InMemoryReservationRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

class ReservationSynchronizationServiceTest {
    private val clock = Clock.fixed(Instant.parse("2026-07-24T10:00:00Z"), ZoneId.of("UTC"))
    private val propertyId = PropertyId("MUC")
    private val window = DateRange(LocalDate.parse("2026-07-24"), LocalDate.parse("2026-07-30"))
    private val timestamp = Instant.parse("2026-07-24T09:00:00Z")

    @Test
    fun `repeated synchronization is idempotent and preserves reservation id`() {
        val repository = InMemoryReservationRepository()
        val service = service(repository = repository, reservations = listOf(pmsReservation("RES-1")))

        val first = service.synchronize(command(timestamp))
        val persistedId = repository.findByExternalReference(ExternalReservationReference("RES-1"))!!.id
        val second = service.synchronize(command(timestamp))

        assertEquals(1, first.createdCount)
        assertEquals(1, second.unchangedCount)
        assertEquals(persistedId, repository.findByExternalReference(ExternalReservationReference("RES-1"))!!.id)
        assertEquals(3, service.syncStates.last().version)
    }

    @Test
    fun `newer PMS snapshot updates PMS owned fields and preserves local operational notes`() {
        val repository = InMemoryReservationRepository()
        service(repository = repository, reservations = listOf(pmsReservation("RES-1", roomNumber = "101")))
            .synchronize(command(timestamp))
        val existing = repository.findSnapshotByMatch("stub-pms", ExternalReservationReference("RES-1"), propertyId)!!
        repository.saveSnapshot(
            existing.copy(
                reservation = existing.reservation.withOperationalNotes("local follow-up"),
                localVersion = existing.localVersion
            )
        )

        val service = service(repository = repository, reservations = listOf(pmsReservation("RES-1", roomNumber = "202", status = "InHouse")))
        val summary = service.synchronize(command(timestamp.plusSeconds(60)))
        val updated = repository.findSnapshotByMatch("stub-pms", ExternalReservationReference("RES-1"), propertyId)!!.reservation

        assertEquals(1, summary.updatedCount)
        assertEquals("202", updated.roomAssignment!!.roomId.value)
        assertEquals("local follow-up", updated.operationalNotes)
        assertThat(service.outbox.events.map { it.eventType }).contains("RESERVATION_UPDATED", "GUEST_CHECKED_IN", "ROOM_ASSIGNMENT_CHANGED")
    }

    @Test
    fun `older PMS snapshots are skipped and cannot overwrite newer persisted data`() {
        val repository = InMemoryReservationRepository()
        service(repository = repository, reservations = listOf(pmsReservation("RES-1", roomNumber = "101")))
            .synchronize(command(timestamp))

        val staleService = service(repository = repository, reservations = listOf(pmsReservation("RES-1", roomNumber = "303")))
        val summary = staleService.synchronize(command(timestamp.minusSeconds(60)))
        val persisted = repository.findSnapshotByMatch("stub-pms", ExternalReservationReference("RES-1"), propertyId)!!.reservation

        assertEquals(1, summary.staleCount)
        assertEquals("101", persisted.roomAssignment!!.roomId.value)
    }

    @Test
    fun `merge policy reports conflicts when PMS attempts to replace local operational notes`() {
        val existing = ReservationSnapshot(
            providerId = "stub-pms",
            reservation = reservationWithNotes("RES-CONFLICT", "local note"),
            pmsSourceUpdatedAt = timestamp
        )
        val incoming = reservationWithNotes("RES-CONFLICT", "provider note")

        val decision = ReservationSnapshotMergePolicy().merge(
            providerId = "stub-pms",
            incoming = incoming,
            existing = existing,
            sourceDataTimestamp = timestamp.plusSeconds(60),
            now = clock.instant()
        )

        assertEquals(ReservationSyncOutcome.CONFLICT, decision.outcome)
        assertEquals(null, decision.snapshot)
    }

    @Test
    fun `bounded full-window synchronization fails before unbounded writes`() {
        val service = service(
            reservations = listOf(pmsReservation("RES-1"), pmsReservation("RES-2")),
            properties = ReservationSyncProperties(maxReservationsPerRun = 1)
        )

        assertThrows(ReservationSyncBoundExceededException::class.java) {
            service.synchronize(command(timestamp))
        }
        assertEquals(ReservationSyncStatus.FAILED, service.syncStates.last().status)
    }

    @Test
    fun `provider without reservation capability fails through capability guard`() {
        val provider = StubPmsProvider(
            capabilities = PmsCapabilities(reservationLookup = false, guestLookup = true),
            reservations = listOf(pmsReservation("RES-1"))
        )
        val service = service(provider = provider)

        assertThrows(UnsupportedPmsCapabilityException::class.java) {
            service.synchronize(command(timestamp))
        }
        assertEquals(ReservationSyncStatus.FAILED, service.syncStates.last().status)
    }

    private fun service(
        repository: InMemoryReservationRepository = InMemoryReservationRepository(),
        provider: StubPmsProvider = StubPmsProvider(reservations = emptyList()),
        reservations: List<PmsReservation> = emptyList(),
        properties: ReservationSyncProperties = ReservationSyncProperties()
    ): TestSyncService {
        val actualProvider = if (reservations.isEmpty()) provider else provider.copy(reservations = reservations)
        val registry = PmsProviderRegistry(
            providers = listOf(actualProvider),
            properties = PmsProviderProperties(
                activeProvider = "stub-pms",
                providers = mapOf("stub-pms" to PmsConfiguredProviderProperties(enabled = true, hotelPropertyIdentifier = propertyId.value))
            )
        )
        val states = InMemoryReservationSyncStateRepository()
        val outbox = InMemoryOperationalOutboxRepository()
        val service = ReservationSynchronizationService(
            pmsProviderRegistry = registry,
            mapper = PmsReservationMapper(),
            reservationRepository = repository,
            syncStateRepository = states,
            outboxPublisher = ReservationOutboxPublisher(outbox, jacksonObjectMapper(), OperationalObservability.noop()),
            mergePolicy = ReservationSnapshotMergePolicy(),
            clock = clock,
            properties = properties,
            observability = OperationalObservability.noop()
        )
        return TestSyncService(service, states, outbox)
    }

    private fun command(sourceDataTimestamp: Instant? = null): ReservationSynchronizationCommand =
        ReservationSynchronizationCommand(propertyId = propertyId, dateRange = window, sourceDataTimestamp = sourceDataTimestamp)

    private fun pmsReservation(
        id: String,
        roomNumber: String = "101",
        status: String = "Confirmed"
    ): PmsReservation =
        PmsReservation(
            id = id,
            guestId = "guest-$id",
            roomNumber = roomNumber,
            arrivalDate = "2026-07-24",
            departureDate = "2026-07-26",
            status = status
        )

    private fun Reservation.withOperationalNotes(notes: String): Reservation =
        reservationWithNotes(externalReference.value, notes, this)

    private fun reservationWithNotes(reference: String, notes: String, base: Reservation? = null): Reservation =
        Reservation.create(
            id = base?.id ?: com.hotelopai.reservation.domain.ReservationId.generate(),
            externalReference = ExternalReservationReference(reference),
            propertyId = base?.propertyId ?: propertyId,
            primaryGuest = base?.primaryGuest ?: Guest(GuestId("guest-$reference")),
            accompanyingGuests = base?.accompanyingGuests ?: emptyList(),
            stayPeriod = base?.stayPeriod ?: DateRange(LocalDate.parse("2026-07-24"), LocalDate.parse("2026-07-26")),
            reservationStatus = base?.reservationStatus ?: com.hotelopai.reservation.domain.ReservationStatus.CONFIRMED,
            stayStatus = base?.stayStatus ?: com.hotelopai.reservation.domain.StayStatus.NOT_ARRIVED,
            roomAssignment = base?.roomAssignment,
            occupancy = base?.occupancy ?: Occupancy(adults = 1),
            source = base?.source ?: ReservationSource.PMS,
            specialRequests = base?.specialRequests,
            operationalNotes = notes,
            createdAt = base?.createdAt ?: clock.instant(),
            modifiedAt = base?.modifiedAt ?: clock.instant()
        )

    private data class TestSyncService(
        private val delegate: ReservationSynchronizationService,
        val stateRepository: InMemoryReservationSyncStateRepository,
        val outbox: InMemoryOperationalOutboxRepository
    ) {
        val syncStates: List<ReservationSyncState>
            get() = stateRepository.states

        fun synchronize(command: ReservationSynchronizationCommand): ReservationSyncSummary =
            delegate.synchronize(command)
    }

    private data class StubPmsProvider(
        override val capabilities: PmsCapabilities = PmsCapabilities(reservationLookup = true, guestLookup = true),
        private val reservations: List<PmsReservation> = emptyList()
    ) : PmsProvider {
        override val id = PmsProviderId("stub-pms")
        override val displayName = "Stub PMS"
        override fun listReservations(): List<PmsReservation> = reservations
        override fun listGuests(): List<PmsGuest> = reservations.map { PmsGuest(it.guestId!!, "Sensitive Guest") }
        override fun listRooms(): List<PmsRoom> = emptyList()
        override fun findRoom(roomNumber: String): PmsRoom? = null
        override fun findRoomStatus(roomNumber: String): PmsRoomStatus? = null
        override fun findStay(roomNumber: String): PmsStay? = null
        override fun getRoomAssets(roomNumber: String): List<PmsAsset> = emptyList()
        override fun findAsset(assetId: String): PmsAsset? = null
        override fun listIssueTypes(): List<PmsIssueType> = emptyList()
        override fun findHousekeepingTask(taskId: String): PmsHousekeepingTask? = null
        override fun updateHousekeepingTaskStatus(taskId: String, request: HousekeepingTaskStatusUpdate): PmsHousekeepingTask = error("not used")
        override fun updateRoomStatus(roomNumber: String, request: RoomStatusUpdate): PmsUpdateResult =
            PmsUpdateResult(UUID.randomUUID(), roomNumber, "ROOM_STATUS_UPDATE", request.status)
        override fun updateMaintenance(request: MaintenanceUpdate): PmsUpdateResult =
            PmsUpdateResult(UUID.randomUUID(), request.roomNumber, "MAINTENANCE_UPDATE", request.status)
        override fun createEvent(command: PmsEventCreateCommand): PmsEvent =
            PmsEvent(command.eventId ?: "event-1", command.type, command.subject)
    }

    private class InMemoryReservationSyncStateRepository : ReservationSyncStateRepository {
        private val stateByKey = linkedMapOf<Pair<String, PropertyId>, ReservationSyncState>()
        val states: List<ReservationSyncState>
            get() = stateByKey.values.toList()

        override fun find(providerId: String, propertyId: PropertyId): ReservationSyncState? =
            stateByKey[providerId to propertyId]

        override fun save(state: ReservationSyncState): ReservationSyncState {
            val existing = find(state.providerId, state.propertyId)
            val saved = state.copy(version = existing?.version?.plus(1) ?: state.version)
            stateByKey[state.providerId to state.propertyId] = saved
            return saved
        }
    }

    private class InMemoryOperationalOutboxRepository : OperationalOutboxRepository {
        val events = mutableListOf<OperationalOutboxEvent>()

        override fun save(event: OperationalOutboxEvent): OperationalOutboxEvent {
            events += event
            return event
        }

        override fun findById(id: UUID): OperationalOutboxEvent? =
            events.firstOrNull { it.id == id }

        override fun findByEventAggregate(eventType: String, aggregateType: String, aggregateId: UUID): OperationalOutboxEvent? =
            events.firstOrNull { it.eventType == eventType && it.aggregateType == aggregateType && it.aggregateId == aggregateId }

        override fun claimDue(now: Instant, batchSize: Int, processorId: String): List<OperationalOutboxEvent> = emptyList()
        override fun markCompleted(id: UUID, now: Instant) = Unit
        override fun markRetryable(id: UUID, attemptCount: Int, nextAttemptAt: Instant, failureCode: String, failureMessage: String?, now: Instant) = Unit
        override fun markFailed(id: UUID, attemptCount: Int, failureCode: String, failureMessage: String?, now: Instant) = Unit
        override fun recoverStale(cutoff: Instant, now: Instant): Int = 0
        override fun cleanupTerminal(completedBefore: Instant, failedBefore: Instant, batchSize: Int): Int = 0
        override fun countStates(): OperationalOutboxStateCounts = OperationalOutboxStateCounts(0, 0, 0, 0, 0)
    }
}
