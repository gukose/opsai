package com.hotelopai.outbox.application

import com.hotelopai.observability.OperationalObservability
import com.hotelopai.outbox.config.OutboxProperties
import com.hotelopai.outbox.domain.OperationalOutboxEvent
import com.hotelopai.outbox.domain.OperationalOutboxEventTypes
import com.hotelopai.scheduler.application.DistributedScheduledJobRunner
import com.hotelopai.shared.kernel.PersistenceInstant
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.pow
import kotlin.math.min

@Component
@EnableConfigurationProperties(OutboxProperties::class)
class OperationalOutboxProcessor(
    private val outboxRepository: OperationalOutboxRepository,
    private val taskCreatedHandler: TaskCreatedOutboxEventHandler,
    private val properties: OutboxProperties,
    private val clock: Clock,
    private val scheduledJobRunner: DistributedScheduledJobRunner,
    private val observability: OperationalObservability = OperationalObservability.noop()
) {
    private val lastCleanupAt = AtomicReference(Instant.MIN)

    @Scheduled(fixedDelayString = "\${ops.ai.outbox.poll-interval:PT5S}")
    fun scheduledProcess() {
        if (properties.enabled) {
            scheduledJobRunner.runSingleton(OUTBOX_JOB_NAME, properties.lockTimeout) {
                processBatch()
            }
        }
    }

    fun processBatch(): Int {
        val startedAt = System.nanoTime()
        val now = PersistenceInstant.now(clock)
        recoverStale(now)
        val claimed = outboxRepository.claimDue(
            now = now,
            batchSize = properties.normalizedBatchSize(),
            processorId = properties.processorId
        )
        var processed = 0
        var failed = 0
        claimed.forEach { event ->
            if (processClaimedEvent(event)) processed++ else failed++
        }
        val removed = if (shouldRunCleanup(now)) cleanup(now) else 0
        if (claimed.isNotEmpty() || removed > 0) refreshStateMetrics()
        val dbPhaseMs = elapsedMillis(startedAt)
        val totalMs = elapsedMillis(startedAt)
        val message = "event=outbox_poll operation=poll outcome=success eventCount={} claimedCount={} processedCount={} failedCount={} totalMs={} dbMs={} externalMs={}"
        val values = arrayOf<Any>(claimed.size, claimed.size, processed, failed, totalMs, dbPhaseMs, 0L)
        if (claimed.isEmpty() && removed == 0 && failed == 0) logger.debug(message, *values)
        else logger.info(message, *values)
        return claimed.size
    }

    private fun shouldRunCleanup(now: Instant): Boolean {
        val interval = properties.normalizedCleanupExecutionInterval()
        while (true) {
            val previous = lastCleanupAt.get()
            if (previous != Instant.MIN && now.isBefore(previous.plus(interval))) return false
            if (lastCleanupAt.compareAndSet(previous, now)) return true
        }
    }

    private fun elapsedMillis(startedAt: Long): Long =
        (System.nanoTime() - startedAt).coerceAtLeast(0L) / 1_000_000L

    fun recoverStale(now: Instant = PersistenceInstant.now(clock)): Int {
        val persistedNow = PersistenceInstant.toPersistencePrecision(now)
        val recovered = outboxRepository.recoverStale(
            cutoff = PersistenceInstant.toPersistencePrecision(persistedNow.minus(properties.lockTimeout)),
            now = persistedNow
        )
        repeat(recovered) {
            recordOutbox(operation = "recover", outcome = "recovered", reasonCode = "stale_lock")
        }
        if (recovered > 0) {
            logger.info("event=outbox_recovery operation=recover outcome=recovered reasonCode=stale_lock count={}", recovered)
        }
        return recovered
    }

    fun cleanup(now: Instant = PersistenceInstant.now(clock)): Int {
        val persistedNow = PersistenceInstant.toPersistencePrecision(now)
        val removed = outboxRepository.cleanupTerminal(
            completedBefore = persistedNow.minus(properties.normalizedCompletedRetention()),
            failedBefore = persistedNow.minus(properties.normalizedFailedRetention()),
            batchSize = properties.normalizedCleanupBatchSize()
        )
        if (removed > 0) {
            recordOutbox(operation = "cleanup", outcome = "success", reasonCode = "retention", amount = removed.toDouble())
            logger.info("event=outbox_cleanup operation=cleanup outcome=success reasonCode=retention count={}", removed)
        }
        refreshStateMetrics()
        return removed
    }

    private fun processClaimedEvent(event: OperationalOutboxEvent): Boolean {
        val timer = observability.startTimer()
        var timerOutcome = "failure"
        try {
            val outcome = when (event.eventType) {
                OperationalOutboxEventTypes.TASK_CREATED -> taskCreatedHandler.handle(event)
                else -> throw OutboxEventHandlingException("unsupported_event")
            }
            outboxRepository.markCompleted(event.id, PersistenceInstant.now(clock))
            timerOutcome = "success"
            val metricOutcome = when (outcome) {
                TaskCreatedHandleOutcome.SUCCESS -> "success"
                TaskCreatedHandleOutcome.DUPLICATE -> "duplicate"
            }
            recordOutbox(operation = "process", outcome = metricOutcome, reasonCode = "none")
            logger.info("event=outbox_process operation=process outcome={} reasonCode=none", metricOutcome)
            return true
        } catch (exception: RuntimeException) {
            val reasonCode = exception.toReasonCode()
            val now = PersistenceInstant.now(clock)
            val nextAttemptCount = event.attemptCount + 1
            if (nextAttemptCount >= properties.normalizedMaxAttempts()) {
                outboxRepository.markFailed(
                    id = event.id,
                    attemptCount = nextAttemptCount,
                    failureCode = reasonCode,
                    failureMessage = sanitizedFailureMessage(reasonCode),
                    now = now
                )
                recordOutbox(operation = "process", outcome = "failed", reasonCode = reasonCode)
                logger.error("event=outbox_process operation=process outcome=failed reasonCode={}", reasonCode)
            } else {
                outboxRepository.markRetryable(
                    id = event.id,
                    attemptCount = nextAttemptCount,
                    nextAttemptAt = PersistenceInstant.toPersistencePrecision(now.plus(backoff(nextAttemptCount))),
                    failureCode = reasonCode,
                    failureMessage = sanitizedFailureMessage(reasonCode),
                    now = now
                )
                recordOutbox(operation = "process", outcome = "retry", reasonCode = reasonCode)
                logger.warn("event=outbox_process operation=process outcome=retry reasonCode={} attempt={}", reasonCode, nextAttemptCount)
            }
            return false
        } finally {
            observability.stopTimer(
                timer,
                "hotelopai.outbox.processing.duration",
                "event_type" to "task_created",
                "outcome" to timerOutcome
            )
        }
    }

    private fun backoff(attemptCount: Int): Duration {
        val exponent = (attemptCount - 1).coerceAtLeast(0).coerceAtMost(30)
        val multiplier = properties.normalizedRetryMultiplier().pow(exponent)
        val rawMillis = (properties.initialRetryDelay.toMillis().coerceAtLeast(1L).toDouble() * multiplier)
            .toLong()
            .coerceAtLeast(1L)
        return Duration.ofMillis(min(rawMillis, properties.maxRetryDelay.toMillis().coerceAtLeast(1L)))
    }

    private fun RuntimeException.toReasonCode(): String =
        when (this) {
            is OutboxEventHandlingException -> reasonCode
            else -> "handler_failure"
        }

    private fun sanitizedFailureMessage(reasonCode: String): String =
        "Outbox processing failed with reason code: $reasonCode"

    private fun refreshStateMetrics() {
        val counts = outboxRepository.countStates()
        observability.setGauge("hotelopai.outbox.state.current", counts.pending, "status" to "pending")
        observability.setGauge("hotelopai.outbox.state.current", counts.retrying, "status" to "retrying")
        observability.setGauge("hotelopai.outbox.state.current", counts.locked, "status" to "locked")
        observability.setGauge("hotelopai.outbox.state.current", counts.completed, "status" to "completed")
        observability.setGauge("hotelopai.outbox.state.current", counts.deadLetter, "status" to "dead_letter")
    }

    private fun recordOutbox(operation: String, outcome: String, reasonCode: String, amount: Double = 1.0) {
        observability.incrementCounter(
            "hotelopai.outbox.event.total",
            amount,
            "operation" to operation,
            "event_type" to "task_created",
            "outcome" to outcome,
            "reason_code" to reasonCode
        )
    }

    companion object {
        const val OUTBOX_JOB_NAME = "operational_outbox_processor"
        private val logger = LoggerFactory.getLogger(OperationalOutboxProcessor::class.java)
    }
}
