package com.hotelopai.outbox.infrastructure.persistence

import com.hotelopai.outbox.application.OperationalOutboxRepository
import com.hotelopai.outbox.application.OperationalOutboxStateCounts
import com.hotelopai.outbox.domain.OperationalOutboxEvent
import com.hotelopai.outbox.domain.OperationalOutboxStatus
import com.hotelopai.shared.kernel.PersistenceInstant
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@Repository
@Transactional
class OperationalOutboxJdbcRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate
) : OperationalOutboxRepository {
    override fun save(event: OperationalOutboxEvent): OperationalOutboxEvent {
        val normalized = event.normalizedForPersistence()
        jdbcTemplate.update(
            """
            insert into operational_outbox (
                id, event_type, aggregate_type, aggregate_id, hotel_id, payload_json, status,
                attempt_count, next_attempt_at, locked_at, locked_by, processed_at,
                last_failure_code, last_failure_message, created_at, updated_at
            ) values (
                :id, :eventType, :aggregateType, :aggregateId, :hotelId, cast(:payloadJson as jsonb), :status,
                :attemptCount, :nextAttemptAt, :lockedAt, :lockedBy, :processedAt,
                :lastFailureCode, :lastFailureMessage, :createdAt, :updatedAt
            )
            """.trimIndent(),
            normalized.toParams()
        )
        return normalized
    }

    @Transactional(readOnly = true)
    override fun findById(id: UUID): OperationalOutboxEvent? =
        jdbcTemplate.query(
            "select * from operational_outbox where id = :id",
            mapOf("id" to id),
            ::mapEvent
        ).firstOrNull()

    @Transactional(readOnly = true)
    override fun findByEventAggregate(eventType: String, aggregateType: String, aggregateId: UUID): OperationalOutboxEvent? =
        jdbcTemplate.query(
            """
            select *
            from operational_outbox
            where event_type = :eventType
              and aggregate_type = :aggregateType
              and aggregate_id = :aggregateId
            """.trimIndent(),
            mapOf(
                "eventType" to eventType,
                "aggregateType" to aggregateType,
                "aggregateId" to aggregateId
            ),
            ::mapEvent
        ).firstOrNull()

    override fun claimDue(now: Instant, batchSize: Int, processorId: String): List<OperationalOutboxEvent> =
        jdbcTemplate.query(
            """
            with locked as (
                select
                    id,
                    created_at
                from operational_outbox
                where status = 'PENDING'
                  and next_attempt_at <= :now
                order by created_at asc, id asc
                limit :batchSize
                for update skip locked
            ),
            candidates as (
                select
                    id,
                    row_number() over (order by created_at asc, id asc) as claim_order
                from locked
            ),
            updated as (
                update operational_outbox event
                set status = 'PROCESSING',
                    locked_at = :now,
                    locked_by = :processorId,
                    updated_at = :now
                from candidates
                where event.id = candidates.id
                returning event.*, candidates.claim_order
            )
            select *
            from updated
            order by claim_order asc
            """.trimIndent(),
            mapOf(
                "now" to PersistenceInstant.toPersistencePrecision(now).toTimestamp(),
                "batchSize" to batchSize,
                "processorId" to processorId.take(128)
            ),
            ::mapEvent
        )

    override fun markCompleted(id: UUID, now: Instant) {
        jdbcTemplate.update(
            """
            update operational_outbox
            set status = 'COMPLETED',
                processed_at = :now,
                locked_at = null,
                locked_by = null,
                last_failure_code = null,
                last_failure_message = null,
                updated_at = :now
            where id = :id
            """.trimIndent(),
            mapOf("id" to id, "now" to PersistenceInstant.toPersistencePrecision(now).toTimestamp())
        )
    }

    override fun markRetryable(
        id: UUID,
        attemptCount: Int,
        nextAttemptAt: Instant,
        failureCode: String,
        failureMessage: String?,
        now: Instant
    ) {
        jdbcTemplate.update(
            """
            update operational_outbox
            set status = 'PENDING',
                attempt_count = :attemptCount,
                next_attempt_at = :nextAttemptAt,
                locked_at = null,
                locked_by = null,
                last_failure_code = :failureCode,
                last_failure_message = :failureMessage,
                updated_at = :now
            where id = :id
            """.trimIndent(),
            mapOf(
                "id" to id,
                "attemptCount" to attemptCount,
                "nextAttemptAt" to PersistenceInstant.toPersistencePrecision(nextAttemptAt).toTimestamp(),
                "failureCode" to failureCode,
                "failureMessage" to failureMessage,
                "now" to PersistenceInstant.toPersistencePrecision(now).toTimestamp()
            )
        )
    }

    override fun markFailed(
        id: UUID,
        attemptCount: Int,
        failureCode: String,
        failureMessage: String?,
        now: Instant
    ) {
        jdbcTemplate.update(
            """
            update operational_outbox
            set status = 'FAILED',
                attempt_count = :attemptCount,
                locked_at = null,
                locked_by = null,
                last_failure_code = :failureCode,
                last_failure_message = :failureMessage,
                updated_at = :now
            where id = :id
            """.trimIndent(),
            mapOf(
                "id" to id,
                "attemptCount" to attemptCount,
                "failureCode" to failureCode,
                "failureMessage" to failureMessage,
                "now" to PersistenceInstant.toPersistencePrecision(now).toTimestamp()
            )
        )
    }

    override fun recoverStale(cutoff: Instant, now: Instant): Int =
        jdbcTemplate.update(
            """
            update operational_outbox
            set status = 'PENDING',
                next_attempt_at = :now,
                locked_at = null,
                locked_by = null,
                updated_at = :now
            where status = 'PROCESSING'
              and locked_at < :cutoff
            """.trimIndent(),
            mapOf(
                "cutoff" to PersistenceInstant.toPersistencePrecision(cutoff).toTimestamp(),
                "now" to PersistenceInstant.toPersistencePrecision(now).toTimestamp()
            )
        )

    override fun cleanupTerminal(completedBefore: Instant, failedBefore: Instant, batchSize: Int): Int =
        jdbcTemplate.update(
            """
            with candidates as (
                select id
                from operational_outbox
                where (status = 'COMPLETED' and processed_at < :completedBefore)
                   or (status = 'FAILED' and updated_at < :failedBefore)
                order by updated_at asc
                limit :batchSize
            )
            delete from operational_outbox event
            using candidates
            where event.id = candidates.id
            """.trimIndent(),
            mapOf(
                "completedBefore" to PersistenceInstant.toPersistencePrecision(completedBefore).toTimestamp(),
                "failedBefore" to PersistenceInstant.toPersistencePrecision(failedBefore).toTimestamp(),
                "batchSize" to batchSize
            )
        )

    @Transactional(readOnly = true)
    override fun countStates(): OperationalOutboxStateCounts =
        jdbcTemplate.query(
            """
            select
                count(*) filter (where status = 'PENDING' and attempt_count = 0) as pending,
                count(*) filter (where status = 'PENDING' and attempt_count > 0) as retrying,
                count(*) filter (where status = 'PROCESSING') as locked,
                count(*) filter (where status = 'COMPLETED') as completed,
                count(*) filter (where status = 'FAILED') as dead_letter
            from operational_outbox
            """.trimIndent(),
            { rs, _ ->
                OperationalOutboxStateCounts(
                    pending = rs.getLong("pending"),
                    retrying = rs.getLong("retrying"),
                    locked = rs.getLong("locked"),
                    completed = rs.getLong("completed"),
                    deadLetter = rs.getLong("dead_letter")
                )
            }
        ).single()

    private fun OperationalOutboxEvent.toParams(): MapSqlParameterSource =
        MapSqlParameterSource()
            .addValue("id", id)
            .addValue("eventType", eventType)
            .addValue("aggregateType", aggregateType)
            .addValue("aggregateId", aggregateId)
            .addValue("hotelId", hotelId)
            .addValue("payloadJson", payloadJson)
            .addValue("status", status.name)
            .addValue("attemptCount", attemptCount)
            .addValue("nextAttemptAt", nextAttemptAt.toTimestamp())
            .addValue("lockedAt", lockedAt?.toTimestamp())
            .addValue("lockedBy", lockedBy)
            .addValue("processedAt", processedAt?.toTimestamp())
            .addValue("lastFailureCode", lastFailureCode)
            .addValue("lastFailureMessage", lastFailureMessage)
            .addValue("createdAt", createdAt.toTimestamp())
            .addValue("updatedAt", updatedAt.toTimestamp())

    private fun OperationalOutboxEvent.normalizedForPersistence(): OperationalOutboxEvent =
        copy(
            nextAttemptAt = PersistenceInstant.toPersistencePrecision(nextAttemptAt),
            lockedAt = PersistenceInstant.toPersistencePrecisionOrNull(lockedAt),
            processedAt = PersistenceInstant.toPersistencePrecisionOrNull(processedAt),
            createdAt = PersistenceInstant.toPersistencePrecision(createdAt),
            updatedAt = PersistenceInstant.toPersistencePrecision(updatedAt)
        )

    private fun mapEvent(rs: ResultSet, @Suppress("UNUSED_PARAMETER") rowNum: Int): OperationalOutboxEvent =
        OperationalOutboxEvent(
            id = rs.getObject("id", UUID::class.java),
            eventType = rs.getString("event_type"),
            aggregateType = rs.getString("aggregate_type"),
            aggregateId = rs.getObject("aggregate_id", UUID::class.java),
            hotelId = rs.getObject("hotel_id", UUID::class.java),
            payloadJson = rs.getString("payload_json"),
            status = OperationalOutboxStatus.valueOf(rs.getString("status")),
            attemptCount = rs.getInt("attempt_count"),
            nextAttemptAt = rs.getTimestamp("next_attempt_at").toInstant(),
            lockedAt = rs.getTimestamp("locked_at")?.toInstant(),
            lockedBy = rs.getString("locked_by"),
            processedAt = rs.getTimestamp("processed_at")?.toInstant(),
            lastFailureCode = rs.getString("last_failure_code"),
            lastFailureMessage = rs.getString("last_failure_message"),
            createdAt = rs.getTimestamp("created_at").toInstant(),
            updatedAt = rs.getTimestamp("updated_at").toInstant()
        )

    private fun Instant.toTimestamp(): Timestamp = Timestamp.from(this)
}
