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
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId
import java.util.UUID

class ReservationWebhookProcessingServiceTest {
    private val clock: Clock = Clock.fixed(Instant.parse("2026-07-24T10:00:00Z"), ZoneId.of("UTC"))

    @Test
    fun `scheduled webhook processing is disabled by default`() {
        val fixture = fixture(
            webhookProperties = ReservationWebhookProperties(),
            scheduleProperties = ReservationWebhookScheduleProperties()
        )

        val summary = fixture.webhooks.processScheduledBatch()

        assertThat(summary.processedCount).isZero()
        assertThat(fixture.provider.reservationCalls).isZero()
    }

    @Test
    fun `pause resume and status persist safe scheduler state`() {
        val fixture = fixture()

        val paused = fixture.webhooks.pauseScheduler(UUID.randomUUID())
        val resumed = fixture.webhooks.resumeScheduler(UUID.randomUUID())
        val status = fixture.webhooks.schedulerStatus(UUID.randomUUID())

        assertThat(paused.paused).isTrue()
        assertThat(resumed.paused).isFalse()
        assertThat(status.scheduleId).isEqualTo(ReservationWebhookProcessingService.WEBHOOK_PROCESSING_SCHEDULE_ID)
        assertThat(status.effectiveEnabled).isTrue()
        assertThat(status.backlogCounts.byStatus).isEmpty()
    }

    @Test
    fun `enabled scheduler requires enabled webhook ingestion and processing`() {
        assertThrows(ReservationWebhookRejectedException::class.java) {
            fixture(
                webhookProperties = ReservationWebhookProperties(enabled = false, processingEnabled = false),
                scheduleProperties = ReservationWebhookScheduleProperties(enabled = true)
            )
        }
    }

    @Test
    fun `retry exhaustion moves event to dead letter and manual retry preserves attempt count`() {
        val fixture = fixture(providerFailure = PmsProviderUnavailableException("Provider unavailable."))
        val event = fixture.inbox.save(webhookRecord())

        val summary = fixture.webhooks.processOperatorBatch(UUID.randomUUID())
        val deadLetter = fixture.webhooks.retry(event.id, UUID.randomUUID())

        assertThat(summary.processedCount).isEqualTo(1)
        assertThat(summary.deadLetterCount).isEqualTo(1)
        assertThat(fixture.webhooks.find(event.id).status).isEqualTo(ReservationWebhookStatus.VERIFIED)
        assertThat(deadLetter.attemptCount).isEqualTo(1)
        assertThat(fixture.audit.events.map { it.action }).contains(
            ReservationSyncOperationsAuditAction.WEBHOOK_DEAD_LETTER_CREATED,
            ReservationSyncOperationsAuditAction.WEBHOOK_DEAD_LETTER_RETRY_REQUESTED
        )
    }

    @Test
    fun `bounded processing uses deterministic oldest eligible ordering`() {
        val fixture = fixture(reservations = listOf(pmsReservation("RES-1")))
        val newer = fixture.inbox.save(webhookRecord(id = "event-new", receivedAt = Instant.parse("2026-07-24T10:00:02Z")))
        val older = fixture.inbox.save(webhookRecord(id = "event-old", receivedAt = Instant.parse("2026-07-24T10:00:01Z")))

        val summary = fixture.webhooks.processOperatorBatch(UUID.randomUUID())

        assertThat(summary.processedCount).isEqualTo(1)
        assertThat(fixture.webhooks.find(older.id).status).isEqualTo(ReservationWebhookStatus.SUCCEEDED)
        assertThat(fixture.webhooks.find(newer.id).status).isEqualTo(ReservationWebhookStatus.VERIFIED)
    }

    private fun fixture(
        webhookProperties: ReservationWebhookProperties = ReservationWebhookProperties(enabled = true, processingEnabled = true, batchSize = 1, maxAttempts = 1),
        scheduleProperties: ReservationWebhookScheduleProperties = ReservationWebhookScheduleProperties(enabled = true, batchSize = 1, maxRecordsPerExecution = 1),
        reservations: List<PmsReservation> = emptyList(),
        providerFailure: RuntimeException? = null
    ): Fixture {
        val provider = StubPmsProvider(reservations, providerFailure)
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
        val synchronizationService = ReservationSynchronizationService(
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
        val operations = ReservationSyncOperationsService(
            pmsProviderRegistry = registry,
            synchronizationService = synchronizationService,
            syncStateRepository = syncStateRepository,
            runRepository = runRepository,
            lockRepository = InMemoryReservationSyncRunLockRepository(),
            auditSink = CapturingAuditSink(),
            clock = clock,
            properties = ReservationSyncOperationsProperties(historyRetention = Period.ofDays(30)),
            observability = OperationalObservability.noop()
        )
        val inbox = InMemoryWebhookInboxRepository()
        val audit = CapturingAuditSink()
        val webhooks = ReservationWebhookProcessingService(
            adapters = listOf(StubWebhookAdapter()),
            inboxRepository = inbox,
            syncOperationsService = operations,
            pmsProviderRegistry = registry,
            auditSink = audit,
            clock = clock,
            properties = webhookProperties,
            scheduleStateRepository = InMemoryReservationSyncScheduleStateRepository(clock.instant()),
            scheduleProperties = scheduleProperties,
            observability = OperationalObservability.noop()
        )
        return Fixture(webhooks, inbox, provider, audit)
    }

    private fun webhookRecord(
        id: String = "event-1",
        receivedAt: Instant = clock.instant()
    ): ReservationWebhookInboxRecord =
        ReservationWebhookInboxRecord(
            providerId = "stub-pms",
            providerEventId = id,
            eventCategory = ReservationWebhookEventCategory.RESERVATION_CHANGED,
            propertyScopeHash = "hash",
            propertyScopeLabel = "configured:hash",
            externalEntityHash = "entity-hash",
            providerEventTimestamp = receivedAt,
            receivedAt = receivedAt,
            status = ReservationWebhookStatus.VERIFIED,
            payloadFingerprint = "payload-hash"
        )

    private fun pmsReservation(id: String): PmsReservation =
        PmsReservation(id = id, guestId = "guest-$id", roomNumber = "101", arrivalDate = "2026-07-24", departureDate = "2026-07-26", status = "Confirmed")

    private data class Fixture(
        val webhooks: ReservationWebhookProcessingService,
        val inbox: InMemoryWebhookInboxRepository,
        val provider: StubPmsProvider,
        val audit: CapturingAuditSink
    )

    private class StubWebhookAdapter : ReservationWebhookAdapter {
        override val providerId = "stub-pms"
        override fun verifyAndExtract(request: ReservationWebhookRequest): ReservationWebhookVerificationResult =
            error("not used")
    }

    private class StubPmsProvider(
        private val reservations: List<PmsReservation>,
        private val failure: RuntimeException?
    ) : PmsProvider {
        var reservationCalls = 0
            private set
        override val id = PmsProviderId("stub-pms")
        override val displayName = "Stub PMS"
        override val capabilities = PmsCapabilities(reservationLookup = true, guestLookup = true, webhooks = true)
        override fun readiness(config: PmsConfiguredProviderProperties?) =
            com.hotelopai.pms.application.PmsProviderReadiness(configured = config != null && config.enabled, enabled = config?.enabled ?: false)
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

    private class InMemoryWebhookInboxRepository : ReservationWebhookInboxRepository {
        private val values = linkedMapOf<ReservationWebhookInboxId, ReservationWebhookInboxRecord>()
        override fun insertIfAbsent(record: ReservationWebhookInboxRecord): ReservationWebhookInsertResult {
            values.values.firstOrNull { it.providerId == record.providerId && it.providerEventId == record.providerEventId }
                ?.let { return ReservationWebhookInsertResult.Duplicate(it) }
            values[record.id] = record
            return ReservationWebhookInsertResult.Inserted(record)
        }
        override fun save(record: ReservationWebhookInboxRecord): ReservationWebhookInboxRecord {
            values[record.id] = record.copy(version = (values[record.id]?.version ?: -1) + 1)
            return values.getValue(record.id)
        }
        override fun findById(id: ReservationWebhookInboxId): ReservationWebhookInboxRecord? = values[id]
        override fun find(filter: ReservationWebhookInboxFilter): ReservationWebhookInboxPage =
            ReservationWebhookInboxPage(values.values.toList(), filter.page, filter.size, values.size.toLong(), if (values.isEmpty()) 0 else 1)
        override fun claimReady(limit: Int, now: Instant, maxAttempts: Int): List<ReservationWebhookInboxRecord> {
            val claimed = values.values
                .filter {
                    (it.status == ReservationWebhookStatus.VERIFIED || (it.status == ReservationWebhookStatus.FAILED && it.attemptCount < maxAttempts)) &&
                        (it.nextAttemptAt == null || !it.nextAttemptAt.isAfter(now))
                }
                .sortedWith(compareBy<ReservationWebhookInboxRecord> { it.nextAttemptAt ?: it.receivedAt }.thenBy { it.receivedAt }.thenBy { it.id.value })
                .take(limit)
            claimed.forEach { values[it.id] = it.copy(status = ReservationWebhookStatus.PROCESSING, processingStartedAt = now, updatedAt = now) }
            return claimed.map { values.getValue(it.id) }
        }
        override fun recoverAbandoned(cutoff: Instant, now: Instant): Int = 0
        override fun deleteCompletedBefore(completedCutoff: Instant, rejectedCutoff: Instant, deadLetterCutoff: Instant, limit: Int): Int = 0
        override fun backlogCounts(): ReservationWebhookBacklogCounts =
            ReservationWebhookBacklogCounts(values.values.groupingBy { it.status }.eachCount().mapValues { it.value.toLong() })
    }

    private class InMemoryReservationSyncStateRepository : ReservationSyncStateRepository {
        private val values = linkedMapOf<Pair<String, PropertyId>, ReservationSyncState>()
        override fun find(providerId: String, propertyId: PropertyId): ReservationSyncState? = values[providerId to propertyId]
        override fun save(state: ReservationSyncState): ReservationSyncState {
            values[state.providerId to state.propertyId] = state
            return state
        }
    }

    private class InMemoryReservationSyncRunRepository : ReservationSyncRunRepository {
        private val values = linkedMapOf<ReservationSyncRunId, ReservationSyncRun>()
        override fun save(run: ReservationSyncRun): ReservationSyncRun {
            values[run.id] = run
            return run
        }
        override fun findById(id: ReservationSyncRunId): ReservationSyncRun? = values[id]
        override fun find(filter: ReservationSyncRunFilter): ReservationSyncRunPage =
            ReservationSyncRunPage(values.values.toList(), filter.page, filter.size, values.size.toLong(), if (values.isEmpty()) 0 else 1)
        override fun deleteCompletedBefore(cutoff: Instant, limit: Int): Int = 0
    }

    private class InMemoryReservationSyncRunLockRepository : ReservationSyncRunLockRepository {
        override fun acquire(providerId: String, propertyScopeHash: String, dateRange: DateRange, runId: ReservationSyncRunId, lockedUntil: Instant, now: Instant): ReservationSyncRunLockResult =
            ReservationSyncRunLockResult.Acquired
        override fun release(runId: ReservationSyncRunId) = Unit
    }

    private class InMemoryReservationSyncScheduleStateRepository(initialNow: Instant) : ReservationSyncScheduleStateRepository {
        private var state = ReservationSyncScheduleState(ReservationWebhookProcessingService.WEBHOOK_PROCESSING_SCHEDULE_ID, false, updatedAt = initialNow)
        override fun getOrCreate(scheduleId: String, now: Instant): ReservationSyncScheduleState = state.copy(scheduleId = scheduleId)
        override fun markPaused(scheduleId: String, now: Instant): ReservationSyncScheduleState {
            state = state.copy(scheduleId = scheduleId, paused = true, pausedAt = now, updatedAt = now)
            return state
        }
        override fun markResumed(scheduleId: String, now: Instant): ReservationSyncScheduleState {
            state = state.copy(scheduleId = scheduleId, paused = false, resumedAt = now, updatedAt = now)
            return state
        }
        override fun recordAttempt(scheduleId: String, run: ReservationSyncRun?, now: Instant, failureCategory: PmsFailureCategory?): ReservationSyncScheduleState =
            state
        override fun recordProcessingAttempt(scheduleId: String, processedCount: Int, success: Boolean, now: Instant, failureCategory: PmsFailureCategory?): ReservationSyncScheduleState {
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
