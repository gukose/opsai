package com.hotelopai.resilience

import com.hotelopai.hotel.application.HotelRepository
import com.hotelopai.hotel.domain.Hotel
import com.hotelopai.notification.application.NotificationRepository
import com.hotelopai.notification.domain.Notification
import com.hotelopai.notification.infrastructure.persistence.NotificationPersistenceRepository
import com.hotelopai.outbox.application.OperationalOutboxRepository
import com.hotelopai.outbox.application.OperationalOutboxStateCounts
import com.hotelopai.outbox.application.OperationalOutboxProcessor
import com.hotelopai.outbox.application.TaskCreatedOutboxPublisher
import com.hotelopai.outbox.domain.OperationalOutboxAggregateTypes
import com.hotelopai.outbox.domain.OperationalOutboxEvent
import com.hotelopai.outbox.domain.OperationalOutboxEventTypes
import com.hotelopai.outbox.domain.OperationalOutboxStatus
import com.hotelopai.outbox.infrastructure.persistence.OperationalOutboxJdbcRepository
import com.hotelopai.shared.kernel.UuidV7Generator
import com.hotelopai.support.PostgresIntegrationTestSupport
import com.hotelopai.support.failure.FailureInjection
import com.hotelopai.support.failure.FailurePoint
import com.hotelopai.task.application.CreateTaskCommand
import com.hotelopai.task.application.TaskLifecycleService
import com.hotelopai.task.application.TaskNotificationPublisher
import com.hotelopai.task.application.TaskRepository
import com.hotelopai.task.domain.Task
import com.hotelopai.task.domain.TaskIntentType
import com.hotelopai.task.domain.TaskPriority
import com.hotelopai.task.domain.TaskSource
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Statistic
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.test.context.ActiveProfiles
import java.time.Clock
import java.time.Instant
import java.util.UUID

@SpringBootTest
@ActiveProfiles("test")
class OperationalResilienceIntegrationTest : PostgresIntegrationTestSupport() {
    @Autowired
    private lateinit var failureInjection: FailureInjection

    @Autowired
    private lateinit var hotelRepository: HotelRepository

    @Autowired
    private lateinit var taskLifecycleService: TaskLifecycleService

    @Autowired
    private lateinit var taskRepository: TaskRepository

    @Autowired
    private lateinit var outboxRepository: OperationalOutboxRepository

    @Autowired
    private lateinit var outboxProcessor: OperationalOutboxProcessor

    @Autowired
    private lateinit var notificationRepository: NotificationRepository

    @Autowired
    private lateinit var clock: Clock

    @Autowired
    private lateinit var meterRegistry: MeterRegistry

    @BeforeEach
    fun clearFailures() {
        failureInjection.clear()
    }

    @Test
    fun `failure before outbox publish rolls back task transaction`() {
        val hotel = hotel()
        failureInjection.failNext(FailurePoint.BEFORE_OUTBOX_PUBLISH)

        assertThatThrownBy { createTask(hotel.id, "Before outbox failure") }
            .hasMessageContaining("BEFORE_OUTBOX_PUBLISH")

        assertThat(taskRepository.findAllByHotelId(hotel.id)).isEmpty()
    }

    @Test
    fun `failure after outbox publish still rolls back task and outbox atomically`() {
        val hotel = hotel()
        failureInjection.failNext(FailurePoint.AFTER_OUTBOX_PUBLISH)

        assertThatThrownBy { createTask(hotel.id, "After outbox failure") }
            .hasMessageContaining("AFTER_OUTBOX_PUBLISH")

        assertThat(taskRepository.findAllByHotelId(hotel.id)).isEmpty()
    }

    @Test
    fun `notification save failure retries and later completes without duplicate notification`() {
        val hotel = hotel()
        val task = createTask(hotel.id, "Notification retry")
        val event = eventFor(task)
        val notificationFailuresBefore = counter(
            "hotelopai.notification.operation.total",
            "operation" to "create",
            "outcome" to "failure"
        )
        val retryBefore = counter(
            "hotelopai.outbox.event.total",
            "operation" to "process",
            "outcome" to "retry"
        )
        failureInjection.failNext(FailurePoint.NOTIFICATION_SAVE)

        assertThat(outboxProcessor.processBatch()).isGreaterThanOrEqualTo(1)

        val retryable = outboxRepository.findById(event.id) ?: error("missing event")
        assertThat(retryable.status).isEqualTo(OperationalOutboxStatus.PENDING)
        assertThat(retryable.attemptCount).isEqualTo(1)
        assertThat(notificationRepository.countBySourceEventId(event.id)).isEqualTo(0)
        assertThat(
            counter(
                "hotelopai.notification.operation.total",
                "operation" to "create",
                "outcome" to "failure"
            )
        ).isGreaterThanOrEqualTo(notificationFailuresBefore + 1.0)
        assertThat(
            counter(
                "hotelopai.outbox.event.total",
                "operation" to "process",
                "outcome" to "retry"
            )
        ).isGreaterThanOrEqualTo(retryBefore + 1.0)

        outboxRepository.markRetryable(
            id = event.id,
            attemptCount = retryable.attemptCount,
            nextAttemptAt = clock.instant(),
            failureCode = retryable.lastFailureCode ?: "operation_failed",
            failureMessage = retryable.lastFailureMessage,
            now = clock.instant()
        )
        assertThat(outboxProcessor.processBatch()).isGreaterThanOrEqualTo(1)

        assertThat(outboxRepository.findById(event.id)?.status).isEqualTo(OperationalOutboxStatus.COMPLETED)
        assertThat(notificationRepository.countBySourceEventId(event.id)).isEqualTo(1)
    }

    @Test
    fun `crash after notification persistence before outbox completion is idempotently recoverable`() {
        val hotel = hotel()
        val task = createTask(hotel.id, "Completion failure")
        val event = eventFor(task)
        failureInjection.failNext(FailurePoint.OUTBOX_COMPLETE)

        assertThat(outboxProcessor.processBatch()).isGreaterThanOrEqualTo(1)

        val retryable = outboxRepository.findById(event.id) ?: error("missing event")
        assertThat(retryable.status).isEqualTo(OperationalOutboxStatus.PENDING)
        assertThat(notificationRepository.countBySourceEventId(event.id)).isEqualTo(1)

        outboxRepository.markRetryable(
            id = event.id,
            attemptCount = retryable.attemptCount,
            nextAttemptAt = clock.instant(),
            failureCode = retryable.lastFailureCode ?: "operation_failed",
            failureMessage = retryable.lastFailureMessage,
            now = clock.instant()
        )
        assertThat(outboxProcessor.processBatch()).isGreaterThanOrEqualTo(1)

        assertThat(outboxRepository.findById(event.id)?.status).isEqualTo(OperationalOutboxStatus.COMPLETED)
        assertThat(notificationRepository.countBySourceEventId(event.id)).isEqualTo(1)
    }

    @Test
    fun `claim failure leaves pending event recoverable on next run`() {
        val hotel = hotel()
        val task = createTask(hotel.id, "Claim failure")
        val event = eventFor(task)
        failureInjection.failNext(FailurePoint.OUTBOX_CLAIM)

        assertThatThrownBy { outboxProcessor.processBatch() }
            .hasMessageContaining("OUTBOX_CLAIM")

        assertThat(outboxRepository.findById(event.id)?.status).isEqualTo(OperationalOutboxStatus.PENDING)
        assertThat(outboxProcessor.processBatch()).isGreaterThanOrEqualTo(1)
        assertThat(outboxRepository.findById(event.id)?.status).isEqualTo(OperationalOutboxStatus.COMPLETED)
        assertThat(notificationRepository.countBySourceEventId(event.id)).isEqualTo(1)
    }

    @Test
    fun `retry persistence failure leaves processing event recoverable through stale lock recovery`() {
        val hotel = hotel()
        val task = createTask(hotel.id, "Retry persistence failure")
        val event = eventFor(task)
        failureInjection.failNext(FailurePoint.NOTIFICATION_SAVE)
        failureInjection.failNext(FailurePoint.OUTBOX_RETRY)

        assertThatThrownBy { outboxProcessor.processBatch() }
            .hasMessageContaining("OUTBOX_RETRY")

        val processing = outboxRepository.findById(event.id) ?: error("missing event")
        assertThat(processing.status).isEqualTo(OperationalOutboxStatus.PROCESSING)
        assertThat(notificationRepository.countBySourceEventId(event.id)).isEqualTo(0)

        outboxRepository.recoverStale(
            cutoff = clock.instant().plusSeconds(3600),
            now = clock.instant()
        )
        assertThat(outboxRepository.findById(event.id)?.status).isEqualTo(OperationalOutboxStatus.PENDING)
        assertThat(outboxProcessor.processBatch()).isGreaterThanOrEqualTo(1)

        assertThat(outboxRepository.findById(event.id)?.status).isEqualTo(OperationalOutboxStatus.COMPLETED)
        assertThat(notificationRepository.countBySourceEventId(event.id)).isEqualTo(1)
    }

    @Test
    fun `cleanup failure does not affect completed delivery and cleanup can run later`() {
        val hotel = hotel()
        val task = createTask(hotel.id, "Cleanup failure")
        val event = eventFor(task)
        failureInjection.failNext(FailurePoint.OUTBOX_CLEANUP)

        assertThatThrownBy { outboxProcessor.processBatch() }
            .hasMessageContaining("OUTBOX_CLEANUP")

        assertThat(outboxRepository.findById(event.id)?.status).isEqualTo(OperationalOutboxStatus.COMPLETED)
        assertThat(notificationRepository.countBySourceEventId(event.id)).isEqualTo(1)
        assertThat(outboxProcessor.cleanup()).isGreaterThanOrEqualTo(0)
    }

    private fun hotel(): Hotel =
        hotelRepository.save(
            Hotel(
                code = "resilience-${UuidV7Generator.generate()}",
                name = "Resilience Hotel"
            )
        )

    private fun createTask(hotelId: UUID, title: String): Task =
        taskLifecycleService.createTask(
            CreateTaskCommand(
                hotelId = hotelId,
                intentType = TaskIntentType.MAINTENANCE,
                source = TaskSource.MANUAL,
                title = title,
                description = "$title description",
                priority = TaskPriority.HIGH,
                slaDeadline = clock.instant().plusSeconds(3600)
            ),
            clock.instant()
        )

    private fun eventFor(task: Task): OperationalOutboxEvent =
        outboxRepository.findByEventAggregate(
            eventType = OperationalOutboxEventTypes.TASK_CREATED,
            aggregateType = OperationalOutboxAggregateTypes.TASK,
            aggregateId = task.id
        ) ?: error("missing task-created outbox event")

    private fun counter(name: String, vararg tags: Pair<String, String>): Double =
        meterRegistry.meters
            .asSequence()
            .filter { meter -> meter.id.name == name }
            .filter { meter -> tags.all { (key, value) -> meter.id.getTag(key) == value } }
            .sumOf { meter ->
                meter.measure()
                    .firstOrNull { measurement -> measurement.statistic == Statistic.COUNT }
                    ?.value ?: 0.0
            }

    @TestConfiguration
    class FailureInjectionConfiguration {
        @Bean
        fun failureInjection(): FailureInjection = FailureInjection()

        @Bean
        @Primary
        fun injectedTaskNotificationPublisher(
            delegate: TaskCreatedOutboxPublisher,
            failureInjection: FailureInjection
        ): TaskNotificationPublisher =
            object : TaskNotificationPublisher {
                override fun taskCreated(task: Task, now: Instant) {
                    failureInjection.maybeFail(FailurePoint.BEFORE_OUTBOX_PUBLISH)
                    delegate.taskCreated(task, now)
                    failureInjection.maybeFail(FailurePoint.AFTER_OUTBOX_PUBLISH)
                }
            }

        @Bean
        @Primary
        fun injectedOutboxRepository(
            delegate: OperationalOutboxJdbcRepository,
            failureInjection: FailureInjection
        ): OperationalOutboxRepository =
            InjectedOperationalOutboxRepository(delegate, failureInjection)

        @Bean
        @Primary
        fun injectedNotificationRepository(
            delegate: NotificationPersistenceRepository,
            failureInjection: FailureInjection
        ): NotificationRepository =
            InjectedNotificationRepository(delegate, failureInjection)
    }

    private class InjectedOperationalOutboxRepository(
        private val delegate: OperationalOutboxRepository,
        private val failureInjection: FailureInjection
    ) : OperationalOutboxRepository {
        override fun save(event: OperationalOutboxEvent): OperationalOutboxEvent = delegate.save(event)

        override fun findById(id: UUID): OperationalOutboxEvent? = delegate.findById(id)

        override fun findByEventAggregate(eventType: String, aggregateType: String, aggregateId: UUID): OperationalOutboxEvent? =
            delegate.findByEventAggregate(eventType, aggregateType, aggregateId)

        override fun claimDue(now: Instant, batchSize: Int, processorId: String): List<OperationalOutboxEvent> {
            failureInjection.maybeFail(FailurePoint.OUTBOX_CLAIM)
            return delegate.claimDue(now, batchSize, processorId)
        }

        override fun markCompleted(id: UUID, now: Instant) {
            failureInjection.maybeFail(FailurePoint.OUTBOX_COMPLETE)
            delegate.markCompleted(id, now)
        }

        override fun markRetryable(
            id: UUID,
            attemptCount: Int,
            nextAttemptAt: Instant,
            failureCode: String,
            failureMessage: String?,
            now: Instant
        ) {
            failureInjection.maybeFail(FailurePoint.OUTBOX_RETRY)
            delegate.markRetryable(id, attemptCount, nextAttemptAt, failureCode, failureMessage, now)
        }

        override fun markFailed(id: UUID, attemptCount: Int, failureCode: String, failureMessage: String?, now: Instant) {
            delegate.markFailed(id, attemptCount, failureCode, failureMessage, now)
        }

        override fun recoverStale(cutoff: Instant, now: Instant): Int = delegate.recoverStale(cutoff, now)

        override fun cleanupTerminal(completedBefore: Instant, failedBefore: Instant, batchSize: Int): Int {
            failureInjection.maybeFail(FailurePoint.OUTBOX_CLEANUP)
            return delegate.cleanupTerminal(completedBefore, failedBefore, batchSize)
        }

        override fun countStates(): OperationalOutboxStateCounts = delegate.countStates()
    }

    private class InjectedNotificationRepository(
        private val delegate: NotificationRepository,
        private val failureInjection: FailureInjection
    ) : NotificationRepository {
        override fun save(notification: Notification): Notification {
            failureInjection.maybeFail(FailurePoint.NOTIFICATION_SAVE)
            return delegate.save(notification)
        }

        override fun findById(id: UUID): Notification? = delegate.findById(id)

        override fun findBySourceEventId(sourceEventId: UUID): Notification? = delegate.findBySourceEventId(sourceEventId)

        override fun findTaskCreatedBySourceTaskId(sourceTaskId: UUID): Notification? =
            delegate.findTaskCreatedBySourceTaskId(sourceTaskId)

        override fun findAccessible(hotelId: UUID, userId: UUID, roleCodes: Set<String>): List<Notification> =
            delegate.findAccessible(hotelId, userId, roleCodes)

        override fun countBySourceTaskId(sourceTaskId: UUID): Long = delegate.countBySourceTaskId(sourceTaskId)

        override fun countBySourceEventId(sourceEventId: UUID): Long = delegate.countBySourceEventId(sourceEventId)
    }
}
