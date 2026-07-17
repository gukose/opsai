package com.hotelopai.outbox

import com.fasterxml.jackson.databind.ObjectMapper
import com.hotelopai.hotel.application.HotelRepository
import com.hotelopai.hotel.domain.Hotel
import com.hotelopai.notification.application.NotificationRepository
import com.hotelopai.notification.domain.Notification
import com.hotelopai.notification.domain.NotificationRecipient
import com.hotelopai.notification.domain.NotificationType
import com.hotelopai.outbox.application.OperationalOutboxProcessor
import com.hotelopai.outbox.application.OperationalOutboxRepository
import com.hotelopai.outbox.domain.OperationalOutboxAggregateTypes
import com.hotelopai.outbox.domain.OperationalOutboxEvent
import com.hotelopai.outbox.domain.OperationalOutboxEventTypes
import com.hotelopai.outbox.domain.OperationalOutboxStatus
import com.hotelopai.outbox.domain.TaskCreatedOutboxPayload
import com.hotelopai.shared.kernel.UuidV7Generator
import com.hotelopai.support.PostgresIntegrationTestSupport
import com.hotelopai.task.application.CreateTaskCommand
import com.hotelopai.task.application.TaskLifecycleService
import com.hotelopai.task.domain.TaskIntentType
import com.hotelopai.task.domain.TaskPriority
import com.hotelopai.task.domain.TaskSource
import io.micrometer.core.instrument.MeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.test.context.ActiveProfiles
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID

@SpringBootTest
@ActiveProfiles("test")
class OperationalOutboxIntegrationTest : PostgresIntegrationTestSupport() {
    @Autowired
    private lateinit var hotelRepository: HotelRepository

    @Autowired
    private lateinit var taskLifecycleService: TaskLifecycleService

    @Autowired
    private lateinit var outboxRepository: OperationalOutboxRepository

    @Autowired
    private lateinit var outboxProcessor: OperationalOutboxProcessor

    @Autowired
    private lateinit var notificationRepository: NotificationRepository

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var meterRegistry: MeterRegistry

    @Autowired
    private lateinit var clock: Clock

    @Test
    fun `task creation persists a single pending outbox event and no synchronous notification`() {
        val hotel = hotel()
        val task = createTask(hotel.id, "Outbox pending")

        val event = outboxRepository.findByEventAggregate(
            eventType = OperationalOutboxEventTypes.TASK_CREATED,
            aggregateType = OperationalOutboxAggregateTypes.TASK,
            aggregateId = task.id
        ) ?: error("missing task-created outbox event")

        assertThat(event.status).isEqualTo(OperationalOutboxStatus.PENDING)
        assertThat(event.hotelId).isEqualTo(hotel.id)
        assertThat(notificationRepository.countBySourceTaskId(task.id)).isEqualTo(0)

        val payload = objectMapper.readValue(event.payloadJson, TaskCreatedOutboxPayload::class.java)
        assertThat(payload.payloadVersion).isEqualTo(TaskCreatedOutboxPayload.VERSION)
        assertThat(payload.taskId).isEqualTo(task.id)
        assertThat(payload.hotelId).isEqualTo(hotel.id)
        assertThat(Instant.parse(payload.createdAt)).isNotNull
    }

    @Test
    fun `duplicate aggregate event is rejected by persistence constraint`() {
        val hotel = hotel()
        val taskId = UuidV7Generator.generate()
        val first = eventFor(taskId = taskId, hotelId = hotel.id)
        val duplicate = eventFor(taskId = taskId, hotelId = hotel.id)

        outboxRepository.save(first)

        assertThatThrownBy { outboxRepository.save(duplicate) }
            .isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `notification source event uniqueness allows null legacy rows and rejects duplicate event ids`() {
        val hotel = hotel()
        val sourceEventId = UuidV7Generator.generate()

        notificationRepository.save(notification(hotel.id, sourceEventId = null))
        notificationRepository.save(notification(hotel.id, sourceEventId = null))
        notificationRepository.save(notification(hotel.id, sourceEventId = sourceEventId))

        assertThatThrownBy {
            notificationRepository.save(notification(hotel.id, sourceEventId = sourceEventId))
        }.isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `processor claims due events creates one notification and completes the event`() {
        val hotel = hotel()
        val task = createTask(hotel.id, "Processor success")
        val event = outboxRepository.findByEventAggregate(
            eventType = OperationalOutboxEventTypes.TASK_CREATED,
            aggregateType = OperationalOutboxAggregateTypes.TASK,
            aggregateId = task.id
        ) ?: error("missing event")
        val before = counter("hotelopai.outbox.event.total", "operation" to "process", "outcome" to "success")

        assertThat(outboxProcessor.processBatch()).isGreaterThanOrEqualTo(1)

        val completed = outboxRepository.findById(event.id) ?: error("missing completed event")
        assertThat(completed.status).isEqualTo(OperationalOutboxStatus.COMPLETED)
        assertThat(completed.processedAt).isNotNull
        assertThat(completed.lockedAt).isNull()
        assertThat(completed.lockedBy).isNull()
        assertThat(notificationRepository.countBySourceTaskId(task.id)).isEqualTo(1)
        assertThat(notificationRepository.countBySourceEventId(event.id)).isEqualTo(1)
        assertThat(
            notificationRepository.findAccessible(
                hotelId = hotel.id,
                userId = UuidV7Generator.generate(),
                roleCodes = setOf("ADMIN")
            )
        ).hasSize(1)
        assertThat(counter("hotelopai.outbox.event.total", "operation" to "process", "outcome" to "success"))
            .isGreaterThanOrEqualTo(before + 1.0)
        assertThat(timerCount("hotelopai.outbox.processing.duration")).isGreaterThanOrEqualTo(1)

        outboxProcessor.processBatch()
        assertThat(notificationRepository.countBySourceTaskId(task.id)).isEqualTo(1)
    }

    @Test
    fun `claiming respects due time batch size and processing status`() {
        val hotel = hotel()
        val due = outboxRepository.save(eventFor(taskId = UuidV7Generator.generate(), hotelId = hotel.id))
        val future = outboxRepository.save(
            eventFor(
                taskId = UuidV7Generator.generate(),
                hotelId = hotel.id,
                nextAttemptAt = clock.instant().plus(Duration.ofHours(1))
            )
        )

        val firstClaim = outboxRepository.claimDue(clock.instant(), batchSize = 100, processorId = "processor-a")
        val secondClaim = outboxRepository.claimDue(clock.instant(), batchSize = 100, processorId = "processor-b")

        assertThat(firstClaim.map { it.id }).contains(due.id)
        assertThat(firstClaim.map { it.id }).doesNotContain(future.id)
        assertThat(secondClaim.map { it.id }).doesNotContain(due.id)
        assertThat(outboxRepository.findById(due.id)?.status).isEqualTo(OperationalOutboxStatus.PROCESSING)
    }

    @Test
    fun `handler failure schedules retry then eventually fails with sanitized reason`() {
        val hotel = hotel()
        val retryEvent = outboxRepository.save(
            eventFor(
                taskId = UuidV7Generator.generate(),
                hotelId = hotel.id,
                payloadJson = "{}"
            )
        )
        val retryBefore = counter("hotelopai.outbox.event.total", "operation" to "process", "outcome" to "retry")

        assertThat(outboxProcessor.processBatch()).isGreaterThanOrEqualTo(1)

        val retryable = outboxRepository.findById(retryEvent.id) ?: error("missing retryable event")
        assertThat(retryable.status).isEqualTo(OperationalOutboxStatus.PENDING)
        assertThat(retryable.attemptCount).isEqualTo(1)
        assertThat(retryable.nextAttemptAt).isAfter(retryable.updatedAt)
        assertThat(retryable.lastFailureCode).isEqualTo("malformed_payload")
        assertThat(retryable.lastFailureMessage).doesNotContain("{")
        assertThat(counter("hotelopai.outbox.event.total", "operation" to "process", "outcome" to "retry"))
            .isGreaterThanOrEqualTo(retryBefore + 1.0)

        val failedEvent = outboxRepository.save(
            eventFor(
                taskId = UuidV7Generator.generate(),
                hotelId = hotel.id,
                payloadJson = "{}",
                attemptCount = 4
            )
        )
        assertThat(outboxProcessor.processBatch()).isGreaterThanOrEqualTo(1)

        val failed = outboxRepository.findById(failedEvent.id) ?: error("missing failed event")
        assertThat(failed.status).isEqualTo(OperationalOutboxStatus.FAILED)
        assertThat(failed.attemptCount).isEqualTo(5)
        assertThat(failed.lastFailureCode).isEqualTo("malformed_payload")
    }

    @Test
    fun `stale processing locks recover while completed and failed events remain untouched`() {
        val hotel = hotel()
        val stale = outboxRepository.save(
            eventFor(
                taskId = UuidV7Generator.generate(),
                hotelId = hotel.id,
                status = OperationalOutboxStatus.PROCESSING,
                lockedAt = clock.instant().minus(Duration.ofMinutes(10)),
                lockedBy = "stale"
            )
        )
        val fresh = outboxRepository.save(
            eventFor(
                taskId = UuidV7Generator.generate(),
                hotelId = hotel.id,
                status = OperationalOutboxStatus.PROCESSING,
                lockedAt = clock.instant(),
                lockedBy = "fresh"
            )
        )
        val completed = outboxRepository.save(
            eventFor(
                taskId = UuidV7Generator.generate(),
                hotelId = hotel.id,
                status = OperationalOutboxStatus.COMPLETED,
                processedAt = clock.instant(),
                lockedAt = clock.instant().minus(Duration.ofMinutes(10)),
                lockedBy = "done"
            )
        )
        val failed = outboxRepository.save(
            eventFor(
                taskId = UuidV7Generator.generate(),
                hotelId = hotel.id,
                status = OperationalOutboxStatus.FAILED,
                lockedAt = clock.instant().minus(Duration.ofMinutes(10)),
                lockedBy = "failed"
            )
        )
        val before = counter("hotelopai.outbox.event.total", "operation" to "recover", "outcome" to "recovered")

        assertThat(outboxProcessor.recoverStale()).isGreaterThanOrEqualTo(1)

        assertThat(outboxRepository.findById(stale.id)?.status).isEqualTo(OperationalOutboxStatus.PENDING)
        assertThat(outboxRepository.findById(stale.id)?.lockedAt).isNull()
        assertThat(outboxRepository.findById(fresh.id)?.status).isEqualTo(OperationalOutboxStatus.PROCESSING)
        assertThat(outboxRepository.findById(completed.id)?.status).isEqualTo(OperationalOutboxStatus.COMPLETED)
        assertThat(outboxRepository.findById(failed.id)?.status).isEqualTo(OperationalOutboxStatus.FAILED)
        assertThat(counter("hotelopai.outbox.event.total", "operation" to "recover", "outcome" to "recovered"))
            .isGreaterThanOrEqualTo(before + 1.0)
    }

    @Test
    fun `crash after notification persistence recovers without duplicate notification`() {
        val hotel = hotel()
        val task = createTask(hotel.id, "Crash recovery")
        val event = outboxRepository.findByEventAggregate(
            eventType = OperationalOutboxEventTypes.TASK_CREATED,
            aggregateType = OperationalOutboxAggregateTypes.TASK,
            aggregateId = task.id
        ) ?: error("missing event")

        notificationRepository.save(
            notification(
                hotelId = hotel.id,
                sourceTaskId = task.id,
                sourceEventId = event.id
            )
        )
        assertThat(
            outboxRepository.claimDue(clock.instant(), batchSize = 100, processorId = "crashed").map { it.id }
        ).contains(event.id)
        assertThat(
            outboxRepository.recoverStale(
                cutoff = clock.instant().plus(Duration.ofMinutes(10)),
                now = clock.instant()
            )
        ).isGreaterThanOrEqualTo(1)

        assertThat(outboxProcessor.processBatch()).isGreaterThanOrEqualTo(1)

        assertThat(notificationRepository.countBySourceEventId(event.id)).isEqualTo(1)
        assertThat(outboxRepository.findById(event.id)?.status).isEqualTo(OperationalOutboxStatus.COMPLETED)
    }

    @Test
    fun `outbox metric tags stay low cardinality`() {
        val hotel = hotel()
        val task = createTask(hotel.id, "Metric tags")

        outboxProcessor.processBatch()

        val forbidden = setOf("eventId", "taskId", "hotelId", "userId", "notificationId", "processorId", "payload")
        val keys = meterRegistry.meters
            .filter { it.id.name.startsWith("hotelopai.outbox.") }
            .flatMap { meter -> meter.id.tags.map { it.key } }
            .toSet()
        assertThat(keys).doesNotContainAnyElementsOf(forbidden)
        assertThat(notificationRepository.countBySourceTaskId(task.id)).isEqualTo(1)
    }

    private fun hotel(): Hotel =
        hotelRepository.save(
            Hotel(
                code = "outbox-${UuidV7Generator.generate()}",
                name = "Outbox Hotel"
            )
        )

    private fun createTask(hotelId: UUID, title: String) =
        taskLifecycleService.createTask(
            CreateTaskCommand(
                hotelId = hotelId,
                intentType = TaskIntentType.MAINTENANCE,
                source = TaskSource.MANUAL,
                title = title,
                description = "$title description",
                priority = TaskPriority.HIGH,
                slaDeadline = clock.instant().plus(Duration.ofHours(1))
            ),
            clock.instant()
        )

    private fun eventFor(
        taskId: UUID,
        hotelId: UUID,
        payloadJson: String = objectMapper.writeValueAsString(
            TaskCreatedOutboxPayload(
                payloadVersion = TaskCreatedOutboxPayload.VERSION,
                taskId = taskId,
                hotelId = hotelId,
                createdAt = clock.instant().toString()
            )
        ),
        status: OperationalOutboxStatus = OperationalOutboxStatus.PENDING,
        attemptCount: Int = 0,
        nextAttemptAt: Instant = clock.instant(),
        lockedAt: Instant? = null,
        lockedBy: String? = null,
        processedAt: Instant? = null
    ): OperationalOutboxEvent =
        OperationalOutboxEvent(
            eventType = OperationalOutboxEventTypes.TASK_CREATED,
            aggregateType = OperationalOutboxAggregateTypes.TASK,
            aggregateId = taskId,
            hotelId = hotelId,
            payloadJson = payloadJson,
            status = status,
            attemptCount = attemptCount,
            nextAttemptAt = nextAttemptAt,
            lockedAt = lockedAt,
            lockedBy = lockedBy,
            processedAt = processedAt,
            createdAt = clock.instant(),
            updatedAt = clock.instant()
        )

    private fun notification(
        hotelId: UUID,
        sourceTaskId: UUID? = null,
        sourceEventId: UUID?
    ): Notification =
        Notification(
            hotelId = hotelId,
            recipient = NotificationRecipient.Role("ADMIN"),
            type = NotificationType.TASK_CREATED,
            title = "Task created",
            body = "Task was created.",
            sourceTaskId = sourceTaskId,
            sourceEventId = sourceEventId,
            createdAt = clock.instant(),
            updatedAt = clock.instant()
        )

    private fun counter(name: String, vararg tags: Pair<String, String>): Double =
        meterRegistry.find(name)
            .tags(*tags.flatMap { listOf(it.first, it.second) }.toTypedArray())
            .counter()
            ?.count() ?: 0.0

    private fun timerCount(name: String): Long =
        meterRegistry.find(name).timer()?.count() ?: 0L
}
