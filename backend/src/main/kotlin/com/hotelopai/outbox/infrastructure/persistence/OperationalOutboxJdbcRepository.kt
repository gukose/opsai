package com.hotelopai.outbox.infrastructure.persistence

import com.hotelopai.outbox.application.OperationalOutboxRepository
import com.hotelopai.outbox.domain.OperationalOutboxEvent
import com.hotelopai.outbox.domain.OperationalOutboxStatus
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
            event.toParams()
        )
        return event
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
            with candidates as (
                select id
                from operational_outbox
                where status = 'PENDING'
                  and next_attempt_at <= :now
                order by created_at asc
                limit :batchSize
                for update skip locked
            )
            update operational_outbox event
            set status = 'PROCESSING',
                locked_at = :now,
                locked_by = :processorId,
                updated_at = :now
            from candidates
            where event.id = candidates.id
            returning event.*
            """.trimIndent(),
            mapOf(
                "now" to now.toTimestamp(),
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
            mapOf("id" to id, "now" to now.toTimestamp())
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
                "nextAttemptAt" to nextAttemptAt.toTimestamp(),
                "failureCode" to failureCode,
                "failureMessage" to failureMessage,
                "now" to now.toTimestamp()
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
                "now" to now.toTimestamp()
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
            mapOf("cutoff" to cutoff.toTimestamp(), "now" to now.toTimestamp())
        )

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
