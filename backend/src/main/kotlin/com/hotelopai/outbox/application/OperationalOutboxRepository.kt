package com.hotelopai.outbox.application

import com.hotelopai.outbox.domain.OperationalOutboxEvent
import java.time.Instant
import java.util.UUID

interface OperationalOutboxRepository {
    fun save(event: OperationalOutboxEvent): OperationalOutboxEvent

    fun findById(id: UUID): OperationalOutboxEvent?

    fun findByEventAggregate(eventType: String, aggregateType: String, aggregateId: UUID): OperationalOutboxEvent?

    fun claimDue(now: Instant, batchSize: Int, processorId: String): List<OperationalOutboxEvent>

    fun markCompleted(id: UUID, now: Instant)

    fun markRetryable(
        id: UUID,
        attemptCount: Int,
        nextAttemptAt: Instant,
        failureCode: String,
        failureMessage: String?,
        now: Instant
    )

    fun markFailed(
        id: UUID,
        attemptCount: Int,
        failureCode: String,
        failureMessage: String?,
        now: Instant
    )

    fun recoverStale(cutoff: Instant, now: Instant): Int
}
