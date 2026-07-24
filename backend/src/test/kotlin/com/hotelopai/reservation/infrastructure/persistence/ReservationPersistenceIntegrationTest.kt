package com.hotelopai.reservation.infrastructure.persistence

import com.fasterxml.jackson.databind.ObjectMapper
import com.hotelopai.observability.OperationalObservability
import com.hotelopai.outbox.application.OperationalOutboxRepository
import com.hotelopai.outbox.domain.OperationalOutboxEventTypes
import com.hotelopai.reservation.application.ReservationOutboxPublisher
import com.hotelopai.reservation.application.ReservationSnapshot
import com.hotelopai.reservation.application.ReservationSyncRun
import com.hotelopai.reservation.application.ReservationSyncRunFilter
import com.hotelopai.reservation.application.ReservationSyncRunLockResult
import com.hotelopai.reservation.application.ReservationSyncRunStatus
import com.hotelopai.reservation.application.ReservationSyncTriggerType
import com.hotelopai.reservation.application.ReservationSyncOperationsService
import com.hotelopai.reservation.application.ReservationSyncState
import com.hotelopai.reservation.application.ReservationSyncStatus
import com.hotelopai.reservation.application.ReservationWebhookEventCategory
import com.hotelopai.reservation.application.ReservationWebhookInboxFilter
import com.hotelopai.reservation.application.ReservationWebhookInboxId
import com.hotelopai.reservation.application.ReservationWebhookInboxRecord
import com.hotelopai.reservation.application.ReservationWebhookInsertResult
import com.hotelopai.reservation.application.ReservationWebhookStatus
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
import com.hotelopai.support.PostgresIntegrationTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate

@SpringBootTest
@ActiveProfiles("test")
class ReservationPersistenceIntegrationTest : PostgresIntegrationTestSupport() {
    @Autowired
    private lateinit var reservationRepository: ReservationJdbcRepository

    @Autowired
    private lateinit var syncStateRepository: ReservationSyncStateJdbcRepository

    @Autowired
    private lateinit var syncRunRepository: ReservationSyncRunJdbcRepository

    @Autowired
    private lateinit var syncRunLockRepository: ReservationSyncRunLockJdbcRepository

    @Autowired
    private lateinit var scheduleStateRepository: ReservationSyncScheduleStateJdbcRepository

    @Autowired
    private lateinit var scheduleLeaseStatusRepository: ReservationSyncScheduleLeaseStatusJdbcRepository

    @Autowired
    private lateinit var webhookInboxRepository: ReservationWebhookInboxJdbcRepository

    @Autowired
    private lateinit var outboxRepository: OperationalOutboxRepository

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun cleanReservationTables() {
        jdbcTemplate.update("delete from operational_outbox where aggregate_type = 'RESERVATION'")
        jdbcTemplate.update("delete from scheduler_lock where job_name = 'reservation_sync_scheduler'")
        jdbcTemplate.update("delete from reservation_webhook_inbox")
        jdbcTemplate.update("delete from reservation_sync_schedule_state")
        jdbcTemplate.update("delete from reservation_sync_run_lock")
        jdbcTemplate.update("delete from reservation_sync_run")
        jdbcTemplate.update("delete from reservation_sync_state")
        jdbcTemplate.update("delete from reservation_snapshot")
    }

    @Test
    fun `repository persists canonical snapshot and reconstructs aggregate without guest names`() {
        val saved = reservationRepository.saveSnapshot(
            ReservationSnapshot(
                providerId = "apaleo",
                reservation = reservation("RES-1", guestName = "Ada Lovelace", notes = "local ops note"),
                pmsSourceUpdatedAt = Instant.parse("2026-07-24T09:00:00Z")
            )
        )

        val reloaded = reservationRepository.findSnapshotByMatch("apaleo", ExternalReservationReference("RES-1"), PropertyId("MUC"))!!

        assertEquals(saved.reservation.id, reloaded.reservation.id)
        assertEquals("RES-1", reloaded.reservation.externalReference.value)
        assertEquals("guest-RES-1", reloaded.reservation.primaryGuest.id.value)
        assertEquals(null, reloaded.reservation.primaryGuest.displayName)
        assertEquals("local ops note", reloaded.reservation.operationalNotes)
        assertEquals(0L, reloaded.localVersion)
        assertEquals(0, countSensitiveRows("Ada"))
    }

    @Test
    fun `repository enforces optimistic locking for stale snapshot writes`() {
        val saved = reservationRepository.saveSnapshot(ReservationSnapshot("apaleo", reservation("RES-LOCK")))
        val current = reservationRepository.saveSnapshot(saved.copy(reservation = saved.reservation.withRoom("102")))

        assertThrows(OptimisticLockingFailureException::class.java) {
            reservationRepository.saveSnapshot(saved.copy(reservation = current.reservation.withRoom("103")))
        }
        assertEquals("102", reservationRepository.findSnapshotById(saved.reservation.id)!!.reservation.roomAssignment!!.roomId.value)
    }

    @Test
    fun `sync state persists safe progress metadata`() {
        val now = Instant.parse("2026-07-24T10:00:00Z")
        val saved = syncStateRepository.save(
            ReservationSyncState(
                providerId = "apaleo",
                propertyId = PropertyId("MUC"),
                status = ReservationSyncStatus.SUCCEEDED,
                lastAttemptedAt = now,
                lastSuccessfulAt = now,
                sourceDataTimestamp = now.minusSeconds(30),
                window = DateRange(LocalDate.parse("2026-07-24"), LocalDate.parse("2026-07-30")),
                fetchedCount = 2,
                createdCount = 1,
                updatedCount = 1,
                unchangedCount = 0,
                staleCount = 0,
                conflictCount = 0,
                createdAt = now,
                updatedAt = now
            )
        )

        val reloaded = syncStateRepository.find("apaleo", PropertyId("MUC"))!!

        assertEquals(saved.status, reloaded.status)
        assertEquals(2, reloaded.fetchedCount)
        assertEquals(null, reloaded.lastFailureCategory)
    }

    @Test
    fun `sync run history persists sanitized pages and retention skips active runs`() {
        val oldCompleted = syncRunRepository.save(
            syncRun(
                status = ReservationSyncRunStatus.SUCCEEDED,
                startedAt = Instant.parse("2025-01-01T00:00:00Z"),
                completedAt = Instant.parse("2025-01-01T00:01:00Z")
            )
        )
        syncRunRepository.save(
            syncRun(
                status = ReservationSyncRunStatus.RUNNING,
                startedAt = Instant.parse("2025-01-01T00:00:00Z"),
                completedAt = null
            )
        )
        syncRunRepository.save(syncRun(status = ReservationSyncRunStatus.SUCCEEDED))

        val page = syncRunRepository.find(ReservationSyncRunFilter(page = 0, size = 2))
        val deleted = syncRunRepository.deleteCompletedBefore(Instant.parse("2026-01-01T00:00:00Z"))

        assertEquals(2, page.content.size)
        assertEquals(3L, page.totalElements)
        assertEquals(1, deleted)
        assertThat(syncRunRepository.findById(oldCompleted.id)).isNull()
        assertThat(syncRunRepository.find(ReservationSyncRunFilter()).content.map { it.status })
            .contains(ReservationSyncRunStatus.RUNNING, ReservationSyncRunStatus.SUCCEEDED)
        assertThat(page.content.first().propertyScopeLabel).startsWith("configured:")
        assertThat(page.content.first().propertyScopeLabel).doesNotContain("MUC")
    }

    @Test
    fun `sync run retention cleanup can delete bounded batches`() {
        repeat(3) {
            syncRunRepository.save(
                syncRun(
                    status = ReservationSyncRunStatus.SUCCEEDED,
                    startedAt = Instant.parse("2025-01-01T00:00:0${it}Z"),
                    completedAt = Instant.parse("2025-01-01T00:01:0${it}Z")
                )
            )
        }

        val deleted = syncRunRepository.deleteCompletedBefore(Instant.parse("2026-01-01T00:00:00Z"), limit = 2)

        assertEquals(2, deleted)
        assertEquals(1L, syncRunRepository.find(ReservationSyncRunFilter()).totalElements)
    }

    @Test
    fun `sync schedule state persists pause resume and safe attempts`() {
        val now = Instant.parse("2026-07-24T10:00:00Z")
        val run = syncRunRepository.save(syncRun(status = ReservationSyncRunStatus.SUCCEEDED, completedAt = now))

        val created = scheduleStateRepository.getOrCreate(ReservationSyncOperationsService.SCHEDULE_ID, now)
        val paused = scheduleStateRepository.markPaused(created.scheduleId, now.plusSeconds(60))
        val resumed = scheduleStateRepository.markResumed(created.scheduleId, now.plusSeconds(120))
        val attempted = scheduleStateRepository.recordAttempt(created.scheduleId, run, now.plusSeconds(180), null)

        assertThat(created.paused).isFalse()
        assertThat(paused.paused).isTrue()
        assertThat(resumed.paused).isFalse()
        assertEquals(run.id, attempted.lastRunId)
        assertEquals(run.completedAt, attempted.lastSuccessfulAt)
    }

    @Test
    fun `sync schedule lease status reads shared scheduler lock safely`() {
        val now = Instant.parse("2026-07-24T10:00:00Z")
        assertThat(scheduleLeaseStatusRepository.state("reservation_sync_scheduler", now).name).isEqualTo("AVAILABLE")

        jdbcTemplate.update(
            """
            insert into scheduler_lock (job_name, locked_until, locked_by, acquired_at, updated_at)
            values (?, ?, ?, ?, ?)
            """.trimIndent(),
            "reservation_sync_scheduler",
            Timestamp.from(now.plusSeconds(300)),
            "test-owner",
            Timestamp.from(now),
            Timestamp.from(now)
        )

        assertThat(scheduleLeaseStatusRepository.state("reservation_sync_scheduler", now).name).isEqualTo("HELD")
    }

    @Test
    fun `webhook inbox persists sanitized deduplicated records and bounded cleanup`() {
        val now = Instant.parse("2026-07-24T10:00:00Z")
        val record = webhookInboxRecord(now)

        val inserted = webhookInboxRepository.insertIfAbsent(record)
        val duplicate = webhookInboxRepository.insertIfAbsent(record.copy(id = ReservationWebhookInboxId.generate()))

        assertThat(inserted).isInstanceOf(ReservationWebhookInsertResult.Inserted::class.java)
        assertThat(duplicate).isInstanceOf(ReservationWebhookInsertResult.Duplicate::class.java)
        val page = webhookInboxRepository.find(ReservationWebhookInboxFilter(page = 0, size = 10))
        assertEquals(1L, page.totalElements)
        assertThat(page.content.first().propertyScopeLabel).doesNotContain("MUC")
        assertThat(page.content.first().safeMetadata).containsEntry("topic", "reservation")
        assertThat(countWebhookRowsContaining("Ada")).isZero()

        webhookInboxRepository.save(
            page.content.first().copy(
                status = ReservationWebhookStatus.SUCCEEDED,
                completedAt = now.minusSeconds(3600),
                updatedAt = now
            )
        )
        assertEquals(
            1,
            webhookInboxRepository.deleteCompletedBefore(
                completedCutoff = now,
                rejectedCutoff = now,
                deadLetterCutoff = now,
                limit = 1
            )
        )
    }

    @Test
    fun `sync run lock rejects overlapping active windows and recovers abandoned locks`() {
        val firstRun = syncRunRepository.save(syncRun(status = ReservationSyncRunStatus.RUNNING))
        val overlappingRun = syncRunRepository.save(syncRun(status = ReservationSyncRunStatus.REQUESTED))
        val nonOverlappingRun = syncRunRepository.save(
            syncRun(
                status = ReservationSyncRunStatus.REQUESTED,
                start = LocalDate.parse("2026-08-01"),
                end = LocalDate.parse("2026-08-03")
            )
        )
        val now = Instant.parse("2026-07-24T10:00:00Z")
        val window = DateRange(LocalDate.parse("2026-07-24"), LocalDate.parse("2026-07-30"))

        assertThat(
            syncRunLockRepository.acquire("apaleo", PROPERTY_HASH, window, firstRun.id, now.plusSeconds(300), now)
        ).isEqualTo(ReservationSyncRunLockResult.Acquired)
        assertThat(
            syncRunLockRepository.acquire("apaleo", PROPERTY_HASH, window, overlappingRun.id, now.plusSeconds(300), now)
        ).isInstanceOf(ReservationSyncRunLockResult.Rejected::class.java)
        assertThat(
            syncRunLockRepository.acquire(
                "apaleo",
                PROPERTY_HASH,
                DateRange(LocalDate.parse("2026-08-01"), LocalDate.parse("2026-08-03")),
                nonOverlappingRun.id,
                now.plusSeconds(300),
                now
            )
        ).isEqualTo(ReservationSyncRunLockResult.Acquired)

        syncRunLockRepository.release(firstRun.id)
        assertThat(
            syncRunLockRepository.acquire("apaleo", PROPERTY_HASH, window, overlappingRun.id, now.plusSeconds(300), now)
        ).isEqualTo(ReservationSyncRunLockResult.Acquired)

        val expiredRun = syncRunRepository.save(syncRun(status = ReservationSyncRunStatus.REQUESTED))
        val replacementRun = syncRunRepository.save(syncRun(status = ReservationSyncRunStatus.REQUESTED))
        assertThat(
            syncRunLockRepository.acquire("apaleo", "expired-$PROPERTY_HASH", window, expiredRun.id, now.minusSeconds(1), now.minusSeconds(60))
        ).isEqualTo(ReservationSyncRunLockResult.Acquired)
        assertThat(
            syncRunLockRepository.acquire("apaleo", "expired-$PROPERTY_HASH", window, replacementRun.id, now.plusSeconds(300), now)
        ).isEqualTo(ReservationSyncRunLockResult.Acquired)
    }

    @Test
    fun `reservation outbox events contain only safe identifiers and statuses`() {
        val reservation = reservation("RES-EVENT", guestName = "Ada Lovelace", notes = "sensitive note")
        val publisher = ReservationOutboxPublisher(outboxRepository, objectMapper, OperationalObservability.noop())

        publisher.imported("apaleo", reservation, Instant.parse("2026-07-24T10:00:00Z"), Instant.parse("2026-07-24T09:00:00Z"))

        val event = outboxRepository.findByEventAggregate(
            OperationalOutboxEventTypes.RESERVATION_IMPORTED,
            "RESERVATION",
            reservation.id.value
        )!!
        assertThat(event.payloadJson).contains(reservation.id.value.toString(), "CONFIRMED", "NOT_ARRIVED")
        assertThat(event.payloadJson).doesNotContain("Ada", "Lovelace", "sensitive note")
    }

    private fun countSensitiveRows(value: String): Int =
        jdbcTemplate.queryForObject(
            """
            select (
                select count(*) from reservation_snapshot where coalesce(operational_notes, '') like ?
            ) + (
                select count(*) from reservation_guest_snapshot where guest_id like ?
            )
            """.trimIndent(),
            Int::class.java,
            "%$value%",
            "%$value%"
        ) ?: 0

    private fun countWebhookRowsContaining(value: String): Int =
        jdbcTemplate.queryForObject(
            "select count(*) from reservation_webhook_inbox where safe_metadata::text like ?",
            Int::class.java,
            "%$value%"
        ) ?: 0

    private fun webhookInboxRecord(now: Instant): ReservationWebhookInboxRecord =
        ReservationWebhookInboxRecord(
            providerId = "apaleo",
            providerEventId = "event-1",
            eventCategory = ReservationWebhookEventCategory.RESERVATION_CHANGED,
            propertyScopeHash = PROPERTY_HASH,
            propertyScopeLabel = "configured:${PROPERTY_HASH.take(12)}",
            externalEntityHash = "entity-hash",
            providerEventTimestamp = now.minusSeconds(30),
            receivedAt = now,
            status = ReservationWebhookStatus.VERIFIED,
            payloadFingerprint = "payload-hash",
            safeMetadata = mapOf("topic" to "reservation", "type" to "changed")
        )

    private fun reservation(reference: String, guestName: String = "Sensitive Guest", notes: String? = null): Reservation =
        Reservation.create(
            externalReference = ExternalReservationReference(reference),
            propertyId = PropertyId("MUC"),
            primaryGuest = Guest(GuestId("guest-$reference"), guestName),
            stayPeriod = DateRange(LocalDate.parse("2026-07-24"), LocalDate.parse("2026-07-26")),
            reservationStatus = ReservationStatus.CONFIRMED,
            stayStatus = StayStatus.NOT_ARRIVED,
            roomAssignment = RoomAssignment(RoomId("101"), DateRange(LocalDate.parse("2026-07-24"), LocalDate.parse("2026-07-26"))),
            occupancy = Occupancy(adults = 1),
            source = ReservationSource.PMS,
            specialRequests = "late checkout",
            operationalNotes = notes,
            createdAt = Instant.parse("2026-07-24T09:00:00Z"),
            modifiedAt = Instant.parse("2026-07-24T09:00:00Z")
        )

    private fun syncRun(
        status: ReservationSyncRunStatus,
        startedAt: Instant = Instant.parse("2026-07-24T10:00:00Z"),
        completedAt: Instant? = Instant.parse("2026-07-24T10:01:00Z"),
        start: LocalDate = LocalDate.parse("2026-07-24"),
        end: LocalDate = LocalDate.parse("2026-07-30")
    ): ReservationSyncRun =
        ReservationSyncRun(
            providerId = "apaleo",
            propertyScopeHash = PROPERTY_HASH,
            propertyScopeLabel = "configured:${PROPERTY_HASH.take(12)}",
            requestedDateRange = DateRange(start, end),
            triggerType = ReservationSyncTriggerType.MANUAL,
            status = status,
            startedAt = startedAt,
            completedAt = completedAt,
            fetchedCount = if (status == ReservationSyncRunStatus.SUCCEEDED) 1 else 0,
            actorUserId = java.util.UUID.fromString("00000000-0000-0000-0000-00000000a001"),
            createdAt = startedAt,
            updatedAt = completedAt ?: startedAt
        )

    private fun Reservation.withRoom(roomId: String): Reservation =
        Reservation.create(
            id = id,
            externalReference = externalReference,
            propertyId = propertyId,
            primaryGuest = primaryGuest,
            stayPeriod = stayPeriod,
            reservationStatus = reservationStatus,
            stayStatus = stayStatus,
            roomAssignment = RoomAssignment(RoomId(roomId), stayPeriod),
            occupancy = occupancy,
            source = source,
            operationalNotes = operationalNotes,
            createdAt = createdAt,
            modifiedAt = Instant.parse("2026-07-24T10:00:00Z")
        )

    companion object {
        private const val PROPERTY_HASH = "2a779a66dc3fb85f0f0e8e932f7e9b79d7d55165d76bdb99bf6e690af138cd04"
    }
}
