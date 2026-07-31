package com.hotelopai.reservation.automation

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.hotelopai.outbox.domain.OperationalOutboxAggregateTypes
import com.hotelopai.outbox.domain.OperationalOutboxEvent
import com.hotelopai.outbox.domain.OperationalOutboxEventTypes
import com.hotelopai.outbox.domain.OperationalOutboxStatus
import com.hotelopai.reservation.application.ReservationOutboxPayload
import com.hotelopai.reservation.domain.DateRange
import com.hotelopai.reservation.domain.ExternalReservationReference
import com.hotelopai.reservation.domain.Guest
import com.hotelopai.reservation.domain.GuestId
import com.hotelopai.reservation.domain.Occupancy
import com.hotelopai.reservation.domain.PropertyId
import com.hotelopai.reservation.domain.Reservation
import com.hotelopai.reservation.domain.ReservationId
import com.hotelopai.reservation.domain.ReservationStatus
import com.hotelopai.reservation.domain.RoomAssignment
import com.hotelopai.reservation.domain.RoomId
import com.hotelopai.reservation.domain.StayStatus
import com.hotelopai.reservation.infrastructure.InMemoryReservationRepository
import com.hotelopai.task.application.TaskLifecycleService
import com.hotelopai.task.infrastructure.InMemoryTaskRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

class ReservationTaskAutomationServiceTest {
    private val clock = Clock.fixed(Instant.parse("2026-07-24T10:00:00Z"), ZoneId.of("UTC"))
    private val hotelId = UUID.fromString("00000000-0000-0000-0000-00000000b001")

    @Test
    fun `automation is disabled by default`() {
        val fixture = fixture(properties = ReservationTaskAutomationProperties())
        fixture.automationRepository.enqueue(outboxEvent(fixture.reservation.id))

        val summary = fixture.service.processBatch(UUID.randomUUID())

        assertThat(summary.processedEvents).isZero()
        assertThat(fixture.taskRepository.findAll()).isEmpty()
    }

    @Test
    fun `arrival rule creates one task and repeated event is idempotent`() {
        val fixture = fixture()
        fixture.automationRepository.enqueue(outboxEvent(fixture.reservation.id))

        val first = fixture.service.processBatch(UUID.randomUUID())
        fixture.automationRepository.enqueue(outboxEvent(fixture.reservation.id))
        val second = fixture.service.processBatch(UUID.randomUUID())

        assertThat(first.tasksCreated).isEqualTo(1)
        assertThat(second.alreadyExists).isEqualTo(1)
        assertThat(fixture.taskRepository.findAll()).hasSize(1)
        val task = fixture.taskRepository.findAll().single()
        assertThat(task.title).isEqualTo("Prepare arrival room")
        assertThat(task.description).doesNotContain("Ada", "RES-", "MUC")
        assertThat(fixture.service.history(ReservationTaskAutomationExecutionFilter()).totalElements).isEqualTo(1)
    }

    @Test
    fun `rule failures are isolated from other rules`() {
        val fixture = fixture(
            rules = listOf(
                FailingRule(),
                UpcomingArrivalPreparationRule()
            )
        )
        fixture.automationRepository.enqueue(outboxEvent(fixture.reservation.id))

        val summary = fixture.service.processBatch(UUID.randomUUID())

        assertThat(summary.failed).isEqualTo(1)
        assertThat(summary.tasksCreated).isEqualTo(1)
        assertThat(fixture.taskRepository.findAll()).hasSize(1)
    }

    @Test
    fun `no show rule produces safe operational review task`() {
        val reservation = reservation(status = ReservationStatus.NO_SHOW, stayStatus = StayStatus.NOT_ARRIVED, room = null)
        val fixture = fixture(reservation = reservation, rules = listOf(NoShowOperationalReviewRule()))
        fixture.automationRepository.enqueue(outboxEvent(reservation.id, eventType = OperationalOutboxEventTypes.RESERVATION_MARKED_NO_SHOW))

        val summary = fixture.service.processBatch(UUID.randomUUID())

        assertThat(summary.tasksCreated).isEqualTo(1)
        assertThat(fixture.taskRepository.findAll().single().title).isEqualTo("Review no-show reservation")
        assertThat(fixture.taskRepository.findAll().single().roomNumber).isNull()
    }

    @Test
    fun `rule policy applies priority override due offset timezone and past due clamping`() {
        val properties = ReservationTaskAutomationProperties(
            enabled = true,
            hotelId = hotelId,
            timezone = ZoneId.of("Europe/Berlin"),
            minimumLeadTime = Duration.ofHours(3),
            rules = mapOf(
                "upcoming-arrival-preparation" to ReservationTaskAutomationRulePolicyProperties(
                    priority = com.hotelopai.task.domain.TaskPriority.HIGH,
                    dueTime = LocalTime.of(8, 30),
                    dueDateOffsetDays = -1
                )
            )
        )
        val fixture = fixture(properties = properties)
        fixture.automationRepository.enqueue(outboxEvent(fixture.reservation.id))

        fixture.service.processBatch(UUID.randomUUID())

        val task = fixture.taskRepository.findAll().single()
        assertThat(task.priority).isEqualTo(com.hotelopai.task.domain.TaskPriority.HIGH)
        assertThat(task.slaDeadline).isEqualTo(Instant.parse("2026-07-24T13:00:00Z"))
    }

    @Test
    fun `due-date policy uses local dates across daylight saving boundaries`() {
        val dstClock = Clock.fixed(Instant.parse("2026-03-28T10:00:00Z"), ZoneId.of("UTC"))
        val properties = ReservationTaskAutomationProperties(
            enabled = true,
            hotelId = hotelId,
            timezone = ZoneId.of("Europe/Berlin"),
            minimumLeadTime = Duration.ZERO,
            rules = mapOf(
                "upcoming-arrival-preparation" to ReservationTaskAutomationRulePolicyProperties(
                    dueTime = LocalTime.of(8, 0)
                )
            )
        )
        val policy = ReservationTaskAutomationDueDatePolicy(properties, dstClock)

        val due = policy.dueForDate(
            ReservationTaskAutomationRuleId("upcoming-arrival-preparation"),
            LocalDate.parse("2026-03-29"),
            ReservationTaskAutomationDueKind.DEFAULT
        )

        assertThat(due).isEqualTo(Instant.parse("2026-03-29T06:00:00Z"))
    }

    @Test
    fun `operator run now records sanitized schedule state`() {
        val scheduleRepository = InMemoryScheduleStateRepository()
        val fixture = fixture(scheduleStateRepository = scheduleRepository)
        fixture.automationRepository.enqueue(outboxEvent(fixture.reservation.id))

        val summary = fixture.service.processOperatorBatch(UUID.randomUUID())
        val status = fixture.service.schedulerStatus(UUID.randomUUID())

        assertThat(summary.tasksCreated).isEqualTo(1)
        assertThat(status.lastProcessedCount).isEqualTo(1)
        assertThat(status.lastCreatedTaskCount).isEqualTo(1)
        assertThat(status.eligibleBacklogCount).isZero()
        assertThat(status.deadLetterExecutionCount).isZero()
    }

    @Test
    fun `pause and resume state survives service calls`() {
        val scheduleRepository = InMemoryScheduleStateRepository()
        val fixture = fixture(scheduleStateRepository = scheduleRepository)

        val paused = fixture.service.pauseScheduler(UUID.randomUUID())
        val resumed = fixture.service.resumeScheduler(UUID.randomUUID())

        assertThat(paused.paused).isTrue()
        assertThat(resumed.paused).isFalse()
    }

    @Test
    fun `enabled automation rejects unknown rule ids`() {
        assertThrows(ReservationTaskAutomationRejectedException::class.java) {
            fixture(
                properties = ReservationTaskAutomationProperties(
                    enabled = true,
                    hotelId = hotelId,
                    enabledRuleIds = setOf("missing-rule")
                )
            )
        }
    }

    @Test
    fun `reprocessing preserves manually edited completed task and does not duplicate`() {
        val fixture = fixture()
        fixture.automationRepository.enqueue(outboxEvent(fixture.reservation.id))
        fixture.service.processBatch(UUID.randomUUID())
        val created = fixture.taskRepository.findAll().single()
        fixture.taskRepository.save(
            created.copy(
                title = "Manual title",
                description = "Manual description",
                status = com.hotelopai.task.domain.TaskStatus.COMPLETED,
                priority = com.hotelopai.task.domain.TaskPriority.URGENT
            )
        )

        fixture.automationRepository.enqueue(outboxEvent(fixture.reservation.id))
        val second = fixture.service.processBatch(UUID.randomUUID())

        assertThat(second.alreadyExists).isEqualTo(1)
        assertThat(fixture.taskRepository.findAll()).hasSize(1)
        val reloaded = fixture.taskRepository.findAll().single()
        assertThat(reloaded.title).isEqualTo("Manual title")
        assertThat(reloaded.description).isEqualTo("Manual description")
        assertThat(reloaded.status).isEqualTo(com.hotelopai.task.domain.TaskStatus.COMPLETED)
        assertThat(reloaded.priority).isEqualTo(com.hotelopai.task.domain.TaskPriority.URGENT)
    }

    private fun fixture(
        reservation: Reservation = reservation(),
        rules: List<ReservationTaskAutomationRule> = listOf(UpcomingArrivalPreparationRule()),
        properties: ReservationTaskAutomationProperties = ReservationTaskAutomationProperties(enabled = true, hotelId = hotelId),
        scheduleStateRepository: ReservationTaskAutomationScheduleStateRepository? = null
    ): Fixture {
        val reservationRepository = InMemoryReservationRepository()
        reservationRepository.save(reservation)
        val taskRepository = InMemoryTaskRepository()
        val automationRepository = InMemoryAutomationRepository()
        val service = ReservationTaskAutomationService(
            rules = rules,
            repository = automationRepository,
            reservationRepository = reservationRepository,
            taskApplicationPort = TaskLifecycleService(taskRepository),
            objectMapper = jacksonObjectMapper(),
            properties = properties,
            clock = clock,
            scheduleStateRepository = scheduleStateRepository
        )
        return Fixture(service, automationRepository, taskRepository, reservation)
    }

    private fun reservation(
        status: ReservationStatus = ReservationStatus.CONFIRMED,
        stayStatus: StayStatus = StayStatus.NOT_ARRIVED,
        room: String? = "101"
    ): Reservation =
        Reservation.create(
            externalReference = ExternalReservationReference("RES-123"),
            propertyId = PropertyId("MUC"),
            primaryGuest = Guest(GuestId("guest-1"), "Ada Lovelace"),
            stayPeriod = DateRange(LocalDate.parse("2026-07-25"), LocalDate.parse("2026-07-27")),
            reservationStatus = status,
            stayStatus = stayStatus,
            roomAssignment = room?.let { RoomAssignment(RoomId(it), DateRange(LocalDate.parse("2026-07-25"), LocalDate.parse("2026-07-27"))) },
            occupancy = Occupancy(adults = 1),
            createdAt = clock.instant(),
            modifiedAt = clock.instant()
        )

    private fun outboxEvent(
        reservationId: ReservationId,
        eventType: String = OperationalOutboxEventTypes.RESERVATION_IMPORTED
    ): OperationalOutboxEvent {
        val payload = ReservationOutboxPayload(
            payloadVersion = ReservationOutboxPayload.VERSION,
            reservationId = reservationId.value,
            providerId = "internal-demo",
            propertyReference = "MUC",
            occurredAt = clock.instant().toString(),
            sourceDataTimestamp = clock.instant().toString(),
            previousReservationStatus = null,
            nextReservationStatus = ReservationStatus.CONFIRMED.name,
            previousStayStatus = null,
            nextStayStatus = StayStatus.NOT_ARRIVED.name
        )
        return OperationalOutboxEvent(
            eventType = eventType,
            aggregateType = OperationalOutboxAggregateTypes.RESERVATION,
            aggregateId = reservationId.value,
            hotelId = UUID(0L, 0L),
            payloadJson = jacksonObjectMapper().writeValueAsString(payload),
            nextAttemptAt = clock.instant(),
            createdAt = clock.instant()
        )
    }

    private data class Fixture(
        val service: ReservationTaskAutomationService,
        val automationRepository: InMemoryAutomationRepository,
        val taskRepository: InMemoryTaskRepository,
        val reservation: Reservation
    )

    private class FailingRule : ReservationTaskAutomationRule {
        override val id = ReservationTaskAutomationRuleId("failing-rule")
        override val version = 1
        override val supportedEventTypes = setOf(OperationalOutboxEventTypes.RESERVATION_IMPORTED)
        override fun evaluate(context: ReservationTaskAutomationContext): List<ReservationTaskProposal> =
            throw IllegalStateException("failure")
    }

    private class InMemoryAutomationRepository : ReservationTaskAutomationRepository {
        private val executions = linkedMapOf<ReservationTaskAutomationExecutionId, ReservationTaskAutomationExecution>()
        private val outbox = linkedMapOf<UUID, OperationalOutboxEvent>()
        fun enqueue(event: OperationalOutboxEvent) {
            outbox[event.id] = event
        }
        override fun insertExecution(execution: ReservationTaskAutomationExecution): ReservationTaskAutomationInsertResult {
            executions.values.firstOrNull { it.deduplicationKey == execution.deduplicationKey }?.let {
                return ReservationTaskAutomationInsertResult.Duplicate(it)
            }
            executions[execution.id] = execution
            return ReservationTaskAutomationInsertResult.Inserted(execution)
        }
        override fun saveExecution(execution: ReservationTaskAutomationExecution): ReservationTaskAutomationExecution {
            executions[execution.id] = execution
            return execution
        }
        override fun findExecution(id: ReservationTaskAutomationExecutionId): ReservationTaskAutomationExecution? = executions[id]
        override fun findExecutions(filter: ReservationTaskAutomationExecutionFilter): ReservationTaskAutomationExecutionPage =
            ReservationTaskAutomationExecutionPage(executions.values.toList(), filter.page, filter.size, executions.size.toLong(), if (executions.isEmpty()) 0 else 1)
        override fun findExecutionByDeduplicationKey(deduplicationKey: String): ReservationTaskAutomationExecution? =
            executions.values.firstOrNull { it.deduplicationKey == deduplicationKey }
        override fun claimReservationEvents(now: Instant, batchSize: Int, processorId: String): List<OperationalOutboxEvent> {
            val claimed = outbox.values
                .filter { it.status == OperationalOutboxStatus.PENDING && !it.nextAttemptAt.isAfter(now) }
                .sortedWith(compareBy<OperationalOutboxEvent> { it.createdAt }.thenBy { it.id })
                .take(batchSize)
            claimed.forEach { outbox[it.id] = it.copy(status = OperationalOutboxStatus.PROCESSING, lockedAt = now, lockedBy = processorId) }
            return claimed
        }
        override fun markOutboxCompleted(id: UUID, now: Instant) {
            outbox[id]?.let { outbox[id] = it.copy(status = OperationalOutboxStatus.COMPLETED, processedAt = now) }
        }
        override fun markOutboxRetryable(id: UUID, attemptCount: Int, nextAttemptAt: Instant, failureCode: String, now: Instant) {
            outbox[id]?.let { outbox[id] = it.copy(status = OperationalOutboxStatus.PENDING, attemptCount = attemptCount, nextAttemptAt = nextAttemptAt) }
        }
        override fun markOutboxFailed(id: UUID, attemptCount: Int, failureCode: String, now: Instant) {
            outbox[id]?.let { outbox[id] = it.copy(status = OperationalOutboxStatus.FAILED, attemptCount = attemptCount) }
        }
        override fun retryExecution(id: ReservationTaskAutomationExecutionId, now: Instant): ReservationTaskAutomationExecution =
            saveExecution(requireNotNull(executions[id]).copy(outcome = ReservationTaskAutomationOutcome.FAILED, nextAttemptAt = now))
        override fun backlogCount(now: Instant): Long =
            outbox.values.count { it.status == OperationalOutboxStatus.PENDING && !it.nextAttemptAt.isAfter(now) }.toLong()
        override fun executionCount(outcomes: Set<ReservationTaskAutomationOutcome>): Long =
            executions.values.count { it.outcome in outcomes }.toLong()
    }

    private class InMemoryScheduleStateRepository : ReservationTaskAutomationScheduleStateRepository {
        private val states = linkedMapOf<String, ReservationTaskAutomationScheduleState>()
        override fun getOrCreate(scheduleId: String, now: Instant): ReservationTaskAutomationScheduleState =
            states.getOrPut(scheduleId) {
                ReservationTaskAutomationScheduleState(scheduleId = scheduleId, paused = false, updatedAt = now)
            }

        override fun markPaused(scheduleId: String, now: Instant): ReservationTaskAutomationScheduleState =
            getOrCreate(scheduleId, now).copy(paused = true, pausedAt = now, updatedAt = now)
                .also { states[scheduleId] = it }

        override fun markResumed(scheduleId: String, now: Instant): ReservationTaskAutomationScheduleState =
            getOrCreate(scheduleId, now).copy(paused = false, resumedAt = now, updatedAt = now)
                .also { states[scheduleId] = it }

        override fun recordAttempt(
            scheduleId: String,
            summary: ReservationTaskAutomationBatchSummary?,
            now: Instant,
            failureCategory: ReservationTaskAutomationSkipReason?
        ): ReservationTaskAutomationScheduleState =
            getOrCreate(scheduleId, now).copy(
                lastAttemptedAt = now,
                lastSuccessfulAt = if (failureCategory == null) now else getOrCreate(scheduleId, now).lastSuccessfulAt,
                lastProcessedCount = summary?.processedEvents ?: 0,
                lastCreatedTaskCount = summary?.tasksCreated ?: 0,
                lastFailureCategory = failureCategory,
                updatedAt = now
            ).also { states[scheduleId] = it }
    }
}
