package com.hotelopai.outbox.application

import com.hotelopai.observability.OperationalObservability
import com.hotelopai.outbox.config.OutboxProperties
import com.hotelopai.outbox.domain.OperationalOutboxEvent
import com.hotelopai.outbox.domain.OperationalOutboxEventTypes
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Instant
import kotlin.math.min

@Component
@EnableConfigurationProperties(OutboxProperties::class)
class OperationalOutboxProcessor(
    private val outboxRepository: OperationalOutboxRepository,
    private val taskCreatedHandler: TaskCreatedOutboxEventHandler,
    private val properties: OutboxProperties,
    private val clock: Clock,
    private val observability: OperationalObservability = OperationalObservability.noop()
) {
    @Scheduled(fixedDelayString = "\${ops.ai.outbox.poll-interval:PT5S}")
    fun scheduledProcess() {
        if (properties.enabled) {
            processBatch()
        }
    }

    fun processBatch(): Int {
        val now = clock.instant()
        recoverStale(now)
        val claimed = outboxRepository.claimDue(
            now = now,
            batchSize = properties.normalizedBatchSize(),
            processorId = properties.processorId
        )
        claimed.forEach(::processClaimedEvent)
        return claimed.size
    }

    fun recoverStale(now: Instant = clock.instant()): Int {
        val recovered = outboxRepository.recoverStale(
            cutoff = now.minus(properties.lockTimeout),
            now = now
        )
        repeat(recovered) {
            recordOutbox(operation = "recover", outcome = "recovered", reasonCode = "stale_lock")
        }
        if (recovered > 0) {
            logger.warn("event=outbox_recovery operation=recover outcome=recovered reasonCode=stale_lock")
        }
        return recovered
    }

    private fun processClaimedEvent(event: OperationalOutboxEvent) {
        val timer = observability.startTimer()
        var timerOutcome = "failure"
        try {
            val outcome = when (event.eventType) {
                OperationalOutboxEventTypes.TASK_CREATED -> taskCreatedHandler.handle(event)
                else -> throw OutboxEventHandlingException("unsupported_event")
            }
            outboxRepository.markCompleted(event.id, clock.instant())
            timerOutcome = "success"
            val metricOutcome = when (outcome) {
                TaskCreatedHandleOutcome.SUCCESS -> "success"
                TaskCreatedHandleOutcome.DUPLICATE -> "duplicate"
            }
            recordOutbox(operation = "process", outcome = metricOutcome, reasonCode = "none")
        } catch (exception: RuntimeException) {
            val reasonCode = exception.toReasonCode()
            val now = clock.instant()
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
                logger.warn("event=outbox_process operation=process outcome=failed reasonCode=$reasonCode")
            } else {
                outboxRepository.markRetryable(
                    id = event.id,
                    attemptCount = nextAttemptCount,
                    nextAttemptAt = now.plus(backoff(nextAttemptCount)),
                    failureCode = reasonCode,
                    failureMessage = sanitizedFailureMessage(reasonCode),
                    now = now
                )
                recordOutbox(operation = "process", outcome = "retry", reasonCode = reasonCode)
                logger.warn("event=outbox_process operation=process outcome=retry reasonCode=$reasonCode")
            }
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
        val multiplier = 1L shl exponent
        val rawMillis = properties.initialRetryDelay.toMillis().coerceAtLeast(1L) * multiplier
        return Duration.ofMillis(min(rawMillis, properties.maxRetryDelay.toMillis().coerceAtLeast(1L)))
    }

    private fun RuntimeException.toReasonCode(): String =
        when (this) {
            is OutboxEventHandlingException -> reasonCode
            else -> "handler_failure"
        }

    private fun sanitizedFailureMessage(reasonCode: String): String =
        "Outbox processing failed with reason code: $reasonCode"

    private fun recordOutbox(operation: String, outcome: String, reasonCode: String) {
        observability.incrementCounter(
            "hotelopai.outbox.event.total",
            "operation" to operation,
            "event_type" to "task_created",
            "outcome" to outcome,
            "reason_code" to reasonCode
        )
    }

    companion object {
        private val logger = LoggerFactory.getLogger(OperationalOutboxProcessor::class.java)
    }
}
