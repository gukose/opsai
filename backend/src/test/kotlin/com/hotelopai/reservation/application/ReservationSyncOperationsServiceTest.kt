package com.hotelopai.reservation.application

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.hotelopai.observability.OperationalObservability
import com.hotelopai.outbox.application.OperationalOutboxRepository
import com.hotelopai.outbox.application.OperationalOutboxStateCounts
import com.hotelopai.outbox.domain.OperationalOutboxEvent
import com.hotelopai.pms.application.PmsCapabilities
import com.hotelopai.pms.application.PmsConfiguredProviderProperties
import com.hotelopai.pms.application.PmsFailureCategory
import com.hotelopai.pms.application.PmsProvider
import com.hotelopai.pms.application.PmsProviderProperties
import com.hotelopai.pms.application.PmsProviderRegistry
import com.hotelopai.pms.domain.HousekeepingTaskStatusUpdate
import com.hotelopai.pms.domain.MaintenanceUpdate
import com.hotelopai.pms.domain.PmsAsset
import com.hotelopai.pms.domain.PmsEvent
import com.hotelopai.pms.domain.PmsEventCreateCommand
import com.hotelopai.pms.domain.PmsGuest
import com.hotelopai.pms.domain.PmsHousekeepingTask
import com.hotelopai.pms.domain.PmsIssueType
import com.hotelopai.pms.domain.PmsProviderId
import com.hotelopai.pms.domain.PmsProviderUnavailableException
import com.hotelopai.pms.domain.PmsReservation
import com.hotelopai.pms.domain.PmsRoom
import com.hotelopai.pms.domain.PmsRoomStatus
import com.hotelopai.pms.domain.PmsStay
import com.hotelopai.pms.domain.PmsUpdateResult
import com.hotelopai.pms.domain.RoomStatusUpdate
import com.hotelopai.reservation.domain.DateRange
import com.hotelopai.reservation.domain.PropertyId
import com.hotelopai.reservation.infrastructure.InMemoryReservationRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId
import java.util.UUID

class ReservationSyncOperationsServiceTest {
    private val clock = Clock.fixed(Instant.parse("2026-07-24T10:00:00Z"), ZoneId.of("UTC"))
    private val actorUserId = UUID.fromString("00000000-0000-0000-0000-00000000a001")

    @Test
    fun `valid manual sync creates and completes sanitized run history`() {
        val fixture = fixture(reservations = listOf(pmsReservation("RES-1")))

        val run = fixture.operations.runManualSync(request(), actorUserId)

        assertEquals(ReservationSyncRunStatus.SUCCEEDED, run.status)
        assertEquals(1, run.fetchedCount)
        assertEquals(1, run.createdCount)
        assertThat(run.propertyScopeLabel).startsWith("configured:")
        assertThat(run.propertyScopeLabel).doesNotContain("MUC")
        assertThat(fixture.runRepository.find(ReservationSyncRunFilter()).content).hasSize(1)
        assertThat(fixture.audit.events.map { it.action }).contains(
            ReservationSyncOperationsAuditAction.SYNC_REQUESTED,
            ReservationSyncOperationsAuditAction.SYNC_STARTED,
            ReservationSyncOperationsAuditAction.SYNC_COMPLETED
        )
    }

    @Test
    fun `automatic triggers are rejected while disabled`() {
        val fixture = fixture(reservations = listOf(pmsReservation("RES-1")))

        val run = fixture.operations.runManualSync(
            request(triggerType = ReservationSyncTriggerType.SCHEDULED),
            actorUserId
        )

        assertEquals(ReservationSyncRunStatus.REJECTED, run.status)
        assertThat(fixture.provider.reservationCalls).isZero()
        assertThat(fixture.audit.events.map { it.outcome }).contains("automatic_trigger_disabled")
    }

    @Test
    fun `unsupported provider capability creates rejected run without PMS traffic`() {
        val fixture = fixture(
            capabilities = PmsCapabilities(reservationLookup = false, guestLookup = true),
            reservations = listOf(pmsReservation("RES-1"))
        )

        val run = fixture.operations.runManualSync(request(), actorUserId)

        assertEquals(ReservationSyncRunStatus.REJECTED, run.status)
        assertThat(fixture.provider.reservationCalls).isZero()
    }

    @Test
    fun `overlapping run is rejected and non-overlapping run can proceed`() {
        val fixture = fixture(reservations = listOf(pmsReservation("RES-1")))
        fixture.lockRepository.forceConflict = true

        val rejected = fixture.operations.runManualSync(request(), actorUserId)
        fixture.lockRepository.forceConflict = false
        val accepted = fixture.operations.runManualSync(
            request(start = LocalDate.parse("2026-08-01"), end = LocalDate.parse("2026-08-03")),
            actorUserId
        )

        assertEquals(ReservationSyncRunStatus.REJECTED, rejected.status)
        assertEquals(ReservationSyncRunStatus.SUCCEEDED, accepted.status)
        assertThat(fixture.lockRepository.releasedRunIds).contains(accepted.id)
    }

    @Test
    fun `failed sync finalizes run and releases lock`() {
        val fixture = fixture(providerFailure = PmsProviderUnavailableException("Provider unavailable."))

        val run = fixture.operations.runManualSync(request(), actorUserId)

        assertEquals(ReservationSyncRunStatus.FAILED, run.status)
        assertThat(fixture.lockRepository.releasedRunIds).contains(run.id)
        assertThat(fixture.audit.events.map { it.outcome }).contains("failed")
    }

    @Test
    fun `history pagination and retention cleanup are bounded`() {
        val fixture = fixture(reservations = listOf(pmsReservation("RES-1")))
        val old = fixture.runRepository.save(
            ReservationSyncRun(
                providerId = "stub-pms",
                propertyScopeHash = "hash",
                propertyScopeLabel = "configured:hash",
                requestedDateRange = DateRange(LocalDate.parse("2026-01-01"), LocalDate.parse("2026-01-02")),
                triggerType = ReservationSyncTriggerType.MANUAL,
                status = ReservationSyncRunStatus.SUCCEEDED,
                startedAt = Instant.parse("2025-01-01T00:00:00Z"),
                completedAt = Instant.parse("2025-01-01T00:01:00Z"),
                createdAt = Instant.parse("2025-01-01T00:00:00Z"),
                updatedAt = Instant.parse("2025-01-01T00:01:00Z")
            )
        )
        fixture.operations.runManualSync(request(), actorUserId)

        val page = fixture.operations.history(ReservationSyncRunFilter(page = 0, size = 1), actorUserId)
        val deleted = fixture.operations.cleanupCompletedRuns(actorUserId)

        assertEquals(1, page.content.size)
        assertEquals(2L, page.totalElements)
        assertEquals(1, deleted)
        assertThat(fixture.runRepository.findById(old.id)).isNull()
    }

    @Test
    fun `scheduled sync uses schedule window and scheduled trigger without enabling manual automatic triggers`() {
        val fixture = fixture(
            reservations = listOf(pmsReservation("RES-SCHEDULED")),
            scheduleProperties = ReservationSyncScheduleProperties(
                enabled = true,
                lookbackDays = 2,
                lookaheadDays = 3,
                executionInterval = Duration.ofMinutes(15)
            )
        )

        val run = fixture.operations.runScheduledPolicy()

        assertThat(run).isNotNull
        assertEquals(ReservationSyncTriggerType.SCHEDULED, run!!.triggerType)
        assertEquals(LocalDate.parse("2026-07-22"), run.requestedDateRange.arrival)
        assertEquals(LocalDate.parse("2026-07-28"), run.requestedDateRange.departure)
        assertEquals(1, fixture.provider.reservationCalls)
        assertThat(fixture.scheduleState.state.lastSuccessfulAt).isNotNull()
    }

    @Test
    fun `scheduled sync is skipped when disabled or paused`() {
        val disabled = fixture(reservations = listOf(pmsReservation("RES-DISABLED")))

        assertThat(disabled.operations.runScheduledPolicy()).isNull()
        assertThat(disabled.provider.reservationCalls).isZero()

        val paused = fixture(
            reservations = listOf(pmsReservation("RES-PAUSED")),
            scheduleProperties = ReservationSyncScheduleProperties(enabled = true)
        )
        paused.operations.pauseScheduler(actorUserId)

        assertThat(paused.operations.runScheduledPolicy()).isNull()
        assertThat(paused.provider.reservationCalls).isZero()
        assertThat(paused.operations.schedulerStatus(actorUserId).paused).isTrue()
        paused.operations.resumeScheduler(actorUserId)
        assertThat(paused.operations.schedulerStatus(actorUserId).paused).isFalse()
    }

    @Test
    fun `scheduled policy run now records manual operational trigger`() {
        val fixture = fixture(
            reservations = listOf(pmsReservation("RES-NOW")),
            scheduleProperties = ReservationSyncScheduleProperties(lookbackDays = 0, lookaheadDays = 0)
        )

        val run = fixture.operations.runScheduledPolicyNow(actorUserId)

        assertEquals(ReservationSyncTriggerType.MANUAL, run.triggerType)
        assertEquals(LocalDate.parse("2026-07-24"), run.requestedDateRange.arrival)
        assertEquals(LocalDate.parse("2026-07-25"), run.requestedDateRange.departure)
    }

    @Test
    fun `scheduled retention cleanup is bounded`() {
        val fixture = fixture()
        repeat(2) {
            fixture.runRepository.save(
                ReservationSyncRun(
                    providerId = "stub-pms",
                    propertyScopeHash = "hash-$it",
                    propertyScopeLabel = "configured:hash-$it",
                    requestedDateRange = DateRange(LocalDate.parse("2026-01-01"), LocalDate.parse("2026-01-02")),
                    triggerType = ReservationSyncTriggerType.MANUAL,
                    status = ReservationSyncRunStatus.SUCCEEDED,
                    startedAt = Instant.parse("2025-01-01T00:00:0${it}Z"),
                    completedAt = Instant.parse("2025-01-01T00:01:0${it}Z"),
                    createdAt = Instant.parse("2025-01-01T00:00:0${it}Z"),
                    updatedAt = Instant.parse("2025-01-01T00:01:0${it}Z")
                )
            )
        }

        val deleted = fixture.operations.cleanupCompletedRuns(actorUserId, limit = 1)

        assertEquals(1, deleted)
        assertEquals(1L, fixture.runRepository.find(ReservationSyncRunFilter()).totalElements)
    }

    @Test
    fun `window policy uses local dates across daylight saving transitions`() {
        val dstClock = Clock.fixed(Instant.parse("2026-03-29T01:30:00Z"), ZoneId.of("UTC"))
        val policy = ReservationSyncWindowPolicy(dstClock)

        val window = policy.window(
            ReservationSyncScheduleProperties(
                enabled = true,
                timezone = ZoneId.of("Europe/Berlin"),
                lookbackDays = 1,
                lookaheadDays = 1
            )
        )

        assertEquals(LocalDate.parse("2026-03-28"), window.arrival)
        assertEquals(LocalDate.parse("2026-03-31"), window.departure)
    }

    private fun fixture(
        capabilities: PmsCapabilities = PmsCapabilities(reservationLookup = true, guestLookup = true),
        reservations: List<PmsReservation> = emptyList(),
        providerFailure: RuntimeException? = null,
        scheduleProperties: ReservationSyncScheduleProperties = ReservationSyncScheduleProperties()
    ): Fixture {
        val provider = StubPmsProvider(capabilities, reservations, providerFailure)
        val registry = PmsProviderRegistry(
            providers = listOf(provider),
            properties = PmsProviderProperties(
                activeProvider = "stub-pms",
                providers = mapOf(
                    "stub-pms" to PmsConfiguredProviderProperties(
                        enabled = true,
                        hotelPropertyIdentifier = "MUC"
                    )
                )
            )
        )
        val reservationRepository = InMemoryReservationRepository()
        val syncStateRepository = InMemoryReservationSyncStateRepository()
        val outboxRepository = InMemoryOperationalOutboxRepository()
        val syncService = ReservationSynchronizationService(
            pmsProviderRegistry = registry,
            mapper = PmsReservationMapper(),
            reservationRepository = reservationRepository,
            syncStateRepository = syncStateRepository,
            outboxPublisher = ReservationOutboxPublisher(outboxRepository, jacksonObjectMapper(), OperationalObservability.noop()),
            mergePolicy = ReservationSnapshotMergePolicy(),
            clock = clock,
            observability = OperationalObservability.noop()
        )
        val runRepository = InMemoryReservationSyncRunRepository()
        val lockRepository = InMemoryReservationSyncRunLockRepository()
        val scheduleState = InMemoryReservationSyncScheduleStateRepository(clock.instant())
        val audit = CapturingAuditSink()
        val operations = ReservationSyncOperationsService(
            pmsProviderRegistry = registry,
            synchronizationService = syncService,
            syncStateRepository = syncStateRepository,
            runRepository = runRepository,
            lockRepository = lockRepository,
            auditSink = audit,
            clock = clock,
            properties = ReservationSyncOperationsProperties(historyRetention = Period.ofDays(30)),
            scheduleStateRepository = scheduleState,
            scheduleProperties = scheduleProperties,
            windowPolicy = ReservationSyncWindowPolicy(clock),
            observability = OperationalObservability.noop()
        )
        return Fixture(operations, provider, runRepository, lockRepository, scheduleState, audit)
    }

    private fun request(
        start: LocalDate = LocalDate.parse("2026-07-24"),
        end: LocalDate = LocalDate.parse("2026-07-30"),
        triggerType: ReservationSyncTriggerType = ReservationSyncTriggerType.MANUAL
    ): ReservationSyncOperationRequest =
        ReservationSyncOperationRequest(start, end, triggerType)

    private fun pmsReservation(id: String): PmsReservation =
        PmsReservation(id = id, guestId = "guest-$id", roomNumber = "101", arrivalDate = "2026-07-24", departureDate = "2026-07-26", status = "Confirmed")

    private data class Fixture(
        val operations: ReservationSyncOperationsService,
        val provider: StubPmsProvider,
        val runRepository: InMemoryReservationSyncRunRepository,
        val lockRepository: InMemoryReservationSyncRunLockRepository,
        val scheduleState: InMemoryReservationSyncScheduleStateRepository,
        val audit: CapturingAuditSink
    )

    private class StubPmsProvider(
        override val capabilities: PmsCapabilities,
        private val reservations: List<PmsReservation>,
        private val failure: RuntimeException?
    ) : PmsProvider {
        var reservationCalls = 0
            private set
        override val id = PmsProviderId("stub-pms")
        override val displayName = "Stub PMS"
        override fun listReservations(): List<PmsReservation> {
            reservationCalls += 1
            failure?.let { throw it }
            return reservations
        }
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
        override fun updateRoomStatus(roomNumber: String, request: RoomStatusUpdate): PmsUpdateResult = PmsUpdateResult(UUID.randomUUID(), roomNumber, "ROOM_STATUS_UPDATE", request.status)
        override fun updateMaintenance(request: MaintenanceUpdate): PmsUpdateResult = PmsUpdateResult(UUID.randomUUID(), request.roomNumber, "MAINTENANCE_UPDATE", request.status)
        override fun createEvent(command: PmsEventCreateCommand): PmsEvent = PmsEvent(command.eventId ?: "event-1", command.type, command.subject)
    }

    private class InMemoryReservationSyncStateRepository : ReservationSyncStateRepository {
        private val values = linkedMapOf<Pair<String, PropertyId>, ReservationSyncState>()
        override fun find(providerId: String, propertyId: PropertyId): ReservationSyncState? = values[providerId to propertyId]
        override fun save(state: ReservationSyncState): ReservationSyncState {
            val saved = state.copy(version = (values[state.providerId to state.propertyId]?.version ?: -1) + 1)
            values[state.providerId to state.propertyId] = saved
            return saved
        }
    }

    private class InMemoryReservationSyncRunRepository : ReservationSyncRunRepository {
        private val values = linkedMapOf<ReservationSyncRunId, ReservationSyncRun>()
        override fun save(run: ReservationSyncRun): ReservationSyncRun {
            val saved = run.copy(version = (values[run.id]?.version ?: -1) + 1)
            values[run.id] = saved
            return saved
        }
        override fun findById(id: ReservationSyncRunId): ReservationSyncRun? = values[id]
        override fun find(filter: ReservationSyncRunFilter): ReservationSyncRunPage {
            val filtered = values.values
                .filter { filter.providerId == null || it.providerId == filter.providerId }
                .filter { filter.status == null || it.status == filter.status }
                .sortedByDescending { it.startedAt }
            val content = filtered.drop(filter.page * filter.size).take(filter.size)
            return ReservationSyncRunPage(content, filter.page, filter.size, filtered.size.toLong(), if (filtered.isEmpty()) 0 else 1)
        }
        override fun deleteCompletedBefore(cutoff: Instant, limit: Int): Int {
            val ids = values.values
                .filter { it.completedAt != null && it.completedAt.isBefore(cutoff) && it.status !in setOf(ReservationSyncRunStatus.REQUESTED, ReservationSyncRunStatus.RUNNING) }
                .take(limit)
                .map { it.id }
            ids.forEach(values::remove)
            return ids.size
        }
    }

    private class InMemoryReservationSyncScheduleStateRepository(
        private val initialNow: Instant
    ) : ReservationSyncScheduleStateRepository {
        var state = ReservationSyncScheduleState(
            scheduleId = ReservationSyncOperationsService.SCHEDULE_ID,
            paused = false,
            updatedAt = initialNow
        )
            private set

        override fun getOrCreate(scheduleId: String, now: Instant): ReservationSyncScheduleState = state

        override fun markPaused(scheduleId: String, now: Instant): ReservationSyncScheduleState {
            state = state.copy(paused = true, pausedAt = now, updatedAt = now)
            return state
        }

        override fun markResumed(scheduleId: String, now: Instant): ReservationSyncScheduleState {
            state = state.copy(paused = false, resumedAt = now, updatedAt = now)
            return state
        }

        override fun recordAttempt(
            scheduleId: String,
            run: ReservationSyncRun?,
            now: Instant,
            failureCategory: PmsFailureCategory?
        ): ReservationSyncScheduleState {
            state = state.copy(
                lastAttemptedAt = now,
                lastSuccessfulAt = run
                    ?.takeIf { it.status == ReservationSyncRunStatus.SUCCEEDED || it.status == ReservationSyncRunStatus.PARTIALLY_SUCCEEDED }
                    ?.completedAt ?: state.lastSuccessfulAt,
                lastFailureCategory = failureCategory,
                lastRunId = run?.id,
                updatedAt = now
            )
            return state
        }

        override fun recordProcessingAttempt(
            scheduleId: String,
            processedCount: Int,
            success: Boolean,
            now: Instant,
            failureCategory: PmsFailureCategory?
        ): ReservationSyncScheduleState {
            state = state.copy(
                scheduleId = scheduleId,
                lastAttemptedAt = now,
                lastSuccessfulAt = if (success) now else state.lastSuccessfulAt,
                lastFailureCategory = failureCategory,
                lastProcessedCount = processedCount,
                updatedAt = now
            )
            return state
        }
    }

    private class InMemoryReservationSyncRunLockRepository : ReservationSyncRunLockRepository {
        var forceConflict = false
        val releasedRunIds = mutableListOf<ReservationSyncRunId>()
        override fun acquire(providerId: String, propertyScopeHash: String, dateRange: DateRange, runId: ReservationSyncRunId, lockedUntil: Instant, now: Instant): ReservationSyncRunLockResult =
            if (forceConflict) ReservationSyncRunLockResult.Rejected(runId) else ReservationSyncRunLockResult.Acquired
        override fun release(runId: ReservationSyncRunId) {
            releasedRunIds += runId
        }
    }

    private class CapturingAuditSink : ReservationSyncOperationsAuditSink {
        val events = mutableListOf<ReservationSyncOperationsAuditEvent>()
        override fun record(event: ReservationSyncOperationsAuditEvent) {
            events += event
        }
    }

    private class InMemoryOperationalOutboxRepository : OperationalOutboxRepository {
        override fun save(event: OperationalOutboxEvent): OperationalOutboxEvent = event
        override fun findById(id: UUID): OperationalOutboxEvent? = null
        override fun findByEventAggregate(eventType: String, aggregateType: String, aggregateId: UUID): OperationalOutboxEvent? = null
        override fun claimDue(now: Instant, batchSize: Int, processorId: String): List<OperationalOutboxEvent> = emptyList()
        override fun markCompleted(id: UUID, now: Instant) = Unit
        override fun markRetryable(id: UUID, attemptCount: Int, nextAttemptAt: Instant, failureCode: String, failureMessage: String?, now: Instant) = Unit
        override fun markFailed(id: UUID, attemptCount: Int, failureCode: String, failureMessage: String?, now: Instant) = Unit
        override fun recoverStale(cutoff: Instant, now: Instant): Int = 0
        override fun cleanupTerminal(completedBefore: Instant, failedBefore: Instant, batchSize: Int): Int = 0
        override fun countStates(): OperationalOutboxStateCounts = OperationalOutboxStateCounts(0, 0, 0, 0, 0)
    }
}
