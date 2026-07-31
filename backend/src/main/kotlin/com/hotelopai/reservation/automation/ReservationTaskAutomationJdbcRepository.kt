package com.hotelopai.reservation.automation

import com.hotelopai.outbox.domain.OperationalOutboxAggregateTypes
import com.hotelopai.outbox.domain.OperationalOutboxEvent
import com.hotelopai.outbox.domain.OperationalOutboxEventTypes
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
import kotlin.math.ceil

@Repository
@Transactional
class ReservationTaskAutomationJdbcRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate
) : ReservationTaskAutomationRepository {
    override fun insertExecution(execution: ReservationTaskAutomationExecution): ReservationTaskAutomationInsertResult {
        val normalized = execution.normalized()
        val inserted = jdbcTemplate.update(
            """
            insert into reservation_task_automation_execution (
                id, outbox_event_id, reservation_id, rule_id, rule_version,
                trigger_event_type, deduplication_key, outcome, created_task_id,
                failure_category, skip_reason, attempt_count, next_attempt_at,
                created_at, updated_at, completed_at, version
            ) values (
                :id, :outboxEventId, :reservationId, :ruleId, :ruleVersion,
                :triggerEventType, :deduplicationKey, :outcome, :createdTaskId,
                :failureCategory, :skipReason, :attemptCount, :nextAttemptAt,
                :createdAt, :updatedAt, :completedAt, :version
            )
            on conflict (deduplication_key) do nothing
            """.trimIndent(),
            normalized.toParams()
        )
        return if (inserted == 1) {
            ReservationTaskAutomationInsertResult.Inserted(requireNotNull(findExecution(normalized.id)))
        } else {
            ReservationTaskAutomationInsertResult.Duplicate(requireNotNull(findExecutionByDeduplicationKey(execution.deduplicationKey)))
        }
    }

    override fun saveExecution(execution: ReservationTaskAutomationExecution): ReservationTaskAutomationExecution {
        val existing = findExecution(execution.id) ?: return when (val inserted = insertExecution(execution)) {
            is ReservationTaskAutomationInsertResult.Inserted -> inserted.execution
            is ReservationTaskAutomationInsertResult.Duplicate -> inserted.existing
        }
        val normalized = execution.normalized().copy(version = existing.version + 1)
        jdbcTemplate.update(
            """
            update reservation_task_automation_execution
            set outcome = :outcome,
                created_task_id = :createdTaskId,
                failure_category = :failureCategory,
                skip_reason = :skipReason,
                attempt_count = :attemptCount,
                next_attempt_at = :nextAttemptAt,
                updated_at = :updatedAt,
                completed_at = :completedAt,
                version = version + 1
            where id = :id
            """.trimIndent(),
            normalized.toParams()
        )
        return requireNotNull(findExecution(execution.id))
    }

    @Transactional(readOnly = true)
    override fun findExecution(id: ReservationTaskAutomationExecutionId): ReservationTaskAutomationExecution? =
        jdbcTemplate.query(
            "select * from reservation_task_automation_execution where id = :id",
            mapOf("id" to id.value),
            ::mapExecution
        ).firstOrNull()

    @Transactional(readOnly = true)
    override fun findExecutionByDeduplicationKey(deduplicationKey: String): ReservationTaskAutomationExecution? =
        jdbcTemplate.query(
            "select * from reservation_task_automation_execution where deduplication_key = :deduplicationKey",
            mapOf("deduplicationKey" to deduplicationKey),
            ::mapExecution
        ).firstOrNull()

    @Transactional(readOnly = true)
    override fun findExecutions(filter: ReservationTaskAutomationExecutionFilter): ReservationTaskAutomationExecutionPage {
        val page = filter.page.coerceAtLeast(0)
        val size = filter.size.coerceIn(1, 100)
        val where = mutableListOf<String>()
        val params = MapSqlParameterSource().addValue("limit", size).addValue("offset", page * size)
        filter.outcome?.let {
            where += "outcome = :outcome"
            params.addValue("outcome", it.name)
        }
        filter.ruleId?.takeIf { it.isNotBlank() }?.let {
            where += "rule_id = :ruleId"
            params.addValue("ruleId", it)
        }
        val whereSql = if (where.isEmpty()) "" else "where ${where.joinToString(" and ")}"
        val total = jdbcTemplate.queryForObject(
            "select count(*) from reservation_task_automation_execution $whereSql",
            params,
            Long::class.java
        ) ?: 0L
        val content = jdbcTemplate.query(
            """
            select *
            from reservation_task_automation_execution
            $whereSql
            order by created_at desc, id desc
            limit :limit offset :offset
            """.trimIndent(),
            params,
            ::mapExecution
        )
        return ReservationTaskAutomationExecutionPage(content, page, size, total, if (total == 0L) 0 else ceil(total.toDouble() / size.toDouble()).toInt())
    }

    override fun claimReservationEvents(now: Instant, batchSize: Int, processorId: String): List<OperationalOutboxEvent> =
        jdbcTemplate.query(
            """
            with locked as (
                select id, created_at
                from operational_outbox
                where status = 'PENDING'
                  and aggregate_type = :aggregateType
                  and event_type in (:eventTypes)
                  and next_attempt_at <= :now
                order by created_at asc, id asc
                limit :batchSize
                for update skip locked
            ),
            candidates as (
                select id, row_number() over (order by created_at asc, id asc) as claim_order
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
            MapSqlParameterSource()
                .addValue("aggregateType", OperationalOutboxAggregateTypes.RESERVATION)
                .addValue("eventTypes", SUPPORTED_EVENT_TYPES)
                .addValue("now", Timestamp.from(PersistenceInstant.toPersistencePrecision(now)))
                .addValue("batchSize", batchSize.coerceIn(1, 100))
                .addValue("processorId", processorId.take(128)),
            ::mapOutbox
        )

    override fun markOutboxCompleted(id: UUID, now: Instant) {
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
            mapOf("id" to id, "now" to Timestamp.from(PersistenceInstant.toPersistencePrecision(now)))
        )
    }

    override fun markOutboxRetryable(id: UUID, attemptCount: Int, nextAttemptAt: Instant, failureCode: String, now: Instant) {
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
                "nextAttemptAt" to Timestamp.from(PersistenceInstant.toPersistencePrecision(nextAttemptAt)),
                "failureCode" to failureCode,
                "failureMessage" to "Reservation task automation failed with reason code: $failureCode",
                "now" to Timestamp.from(PersistenceInstant.toPersistencePrecision(now))
            )
        )
    }

    override fun markOutboxFailed(id: UUID, attemptCount: Int, failureCode: String, now: Instant) {
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
                "failureMessage" to "Reservation task automation failed with reason code: $failureCode",
                "now" to Timestamp.from(PersistenceInstant.toPersistencePrecision(now))
            )
        )
    }

    override fun retryExecution(id: ReservationTaskAutomationExecutionId, now: Instant): ReservationTaskAutomationExecution {
        val current = requireNotNull(findExecution(id)) { "Reservation task automation execution not found." }
        val retried = current.copy(
            outcome = ReservationTaskAutomationOutcome.FAILED,
            nextAttemptAt = PersistenceInstant.toPersistencePrecision(now),
            updatedAt = PersistenceInstant.toPersistencePrecision(now),
            completedAt = null
        )
        return saveExecution(retried)
    }

    @Transactional(readOnly = true)
    override fun backlogCount(now: Instant): Long =
        jdbcTemplate.queryForObject(
            """
            select count(*)
            from operational_outbox
            where status = 'PENDING'
              and aggregate_type = :aggregateType
              and event_type in (:eventTypes)
              and next_attempt_at <= :now
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("aggregateType", OperationalOutboxAggregateTypes.RESERVATION)
                .addValue("eventTypes", SUPPORTED_EVENT_TYPES)
                .addValue("now", Timestamp.from(PersistenceInstant.toPersistencePrecision(now))),
            Long::class.java
        ) ?: 0L

    @Transactional(readOnly = true)
    override fun executionCount(outcomes: Set<ReservationTaskAutomationOutcome>): Long {
        if (outcomes.isEmpty()) return 0
        return jdbcTemplate.queryForObject(
            """
            select count(*)
            from reservation_task_automation_execution
            where outcome in (:outcomes)
            """.trimIndent(),
            mapOf("outcomes" to outcomes.map { it.name }),
            Long::class.java
        ) ?: 0L
    }

    private fun ReservationTaskAutomationExecution.normalized(): ReservationTaskAutomationExecution =
        copy(
            nextAttemptAt = PersistenceInstant.toPersistencePrecisionOrNull(nextAttemptAt),
            createdAt = PersistenceInstant.toPersistencePrecision(createdAt),
            updatedAt = PersistenceInstant.toPersistencePrecision(updatedAt),
            completedAt = PersistenceInstant.toPersistencePrecisionOrNull(completedAt)
        )

    private fun ReservationTaskAutomationExecution.toParams(): MapSqlParameterSource =
        MapSqlParameterSource()
            .addValue("id", id.value)
            .addValue("outboxEventId", outboxEventId)
            .addValue("reservationId", reservationId)
            .addValue("ruleId", ruleId.value)
            .addValue("ruleVersion", ruleVersion)
            .addValue("triggerEventType", triggerEventType)
            .addValue("deduplicationKey", deduplicationKey)
            .addValue("outcome", outcome.name)
            .addValue("createdTaskId", createdTaskId)
            .addValue("failureCategory", failureCategory?.name)
            .addValue("skipReason", skipReason?.name)
            .addValue("attemptCount", attemptCount)
            .addValue("nextAttemptAt", nextAttemptAt?.let(Timestamp::from))
            .addValue("createdAt", Timestamp.from(createdAt))
            .addValue("updatedAt", Timestamp.from(updatedAt))
            .addValue("completedAt", completedAt?.let(Timestamp::from))
            .addValue("version", version)

    private fun mapExecution(rs: ResultSet, @Suppress("UNUSED_PARAMETER") rowNum: Int): ReservationTaskAutomationExecution =
        ReservationTaskAutomationExecution(
            id = ReservationTaskAutomationExecutionId(rs.getObject("id", UUID::class.java)),
            outboxEventId = rs.getObject("outbox_event_id", UUID::class.java),
            reservationId = rs.getObject("reservation_id", UUID::class.java),
            ruleId = ReservationTaskAutomationRuleId(rs.getString("rule_id")),
            ruleVersion = rs.getInt("rule_version"),
            triggerEventType = rs.getString("trigger_event_type"),
            deduplicationKey = rs.getString("deduplication_key"),
            outcome = ReservationTaskAutomationOutcome.valueOf(rs.getString("outcome")),
            createdTaskId = rs.getObject("created_task_id", UUID::class.java),
            failureCategory = rs.getString("failure_category")?.let(ReservationTaskAutomationSkipReason::valueOf),
            skipReason = rs.getString("skip_reason")?.let(ReservationTaskAutomationSkipReason::valueOf),
            attemptCount = rs.getInt("attempt_count"),
            nextAttemptAt = rs.getTimestamp("next_attempt_at")?.toInstant(),
            createdAt = rs.getTimestamp("created_at").toInstant(),
            updatedAt = rs.getTimestamp("updated_at").toInstant(),
            completedAt = rs.getTimestamp("completed_at")?.toInstant(),
            version = rs.getLong("version")
        )

    private fun mapOutbox(rs: ResultSet, @Suppress("UNUSED_PARAMETER") rowNum: Int): OperationalOutboxEvent =
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

    companion object {
        val SUPPORTED_EVENT_TYPES = listOf(
            OperationalOutboxEventTypes.RESERVATION_IMPORTED,
            OperationalOutboxEventTypes.RESERVATION_UPDATED,
            OperationalOutboxEventTypes.RESERVATION_STATUS_CHANGED,
            OperationalOutboxEventTypes.GUEST_CHECKED_IN,
            OperationalOutboxEventTypes.GUEST_CHECKED_OUT,
            OperationalOutboxEventTypes.RESERVATION_CANCELLED,
            OperationalOutboxEventTypes.RESERVATION_MARKED_NO_SHOW,
            OperationalOutboxEventTypes.ROOM_ASSIGNMENT_CHANGED
        )
    }
}
