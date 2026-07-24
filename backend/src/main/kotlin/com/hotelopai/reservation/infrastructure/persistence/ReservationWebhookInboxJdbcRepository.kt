package com.hotelopai.reservation.infrastructure.persistence

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.hotelopai.pms.application.PmsFailureCategory
import com.hotelopai.reservation.application.ReservationSyncRunId
import com.hotelopai.reservation.application.ReservationWebhookBacklogCounts
import com.hotelopai.reservation.application.ReservationWebhookEventCategory
import com.hotelopai.reservation.application.ReservationWebhookInboxFilter
import com.hotelopai.reservation.application.ReservationWebhookInboxId
import com.hotelopai.reservation.application.ReservationWebhookInboxPage
import com.hotelopai.reservation.application.ReservationWebhookInboxRecord
import com.hotelopai.reservation.application.ReservationWebhookInboxRepository
import com.hotelopai.reservation.application.ReservationWebhookInsertResult
import com.hotelopai.reservation.application.ReservationWebhookStatus
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
class ReservationWebhookInboxJdbcRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate,
    private val objectMapper: ObjectMapper
) : ReservationWebhookInboxRepository {
    override fun insertIfAbsent(record: ReservationWebhookInboxRecord): ReservationWebhookInsertResult =
        if (saveNewIfAbsent(record)) {
            ReservationWebhookInsertResult.Inserted(requireNotNull(findById(record.id)))
        } else {
            ReservationWebhookInsertResult.Duplicate(
                requireNotNull(findByProviderEventId(record.providerId, record.providerEventId))
            )
        }

    override fun save(record: ReservationWebhookInboxRecord): ReservationWebhookInboxRecord {
        val existing = findById(record.id)
        if (existing == null) return saveNew(record)
        val normalized = record.normalized().copy(version = existing.version + 1)
        jdbcTemplate.update(
            """
            update reservation_webhook_inbox
            set processing_status = :status,
                failure_category = :failureCategory,
                attempt_count = :attemptCount,
                next_attempt_at = :nextAttemptAt,
                processing_started_at = :processingStartedAt,
                completed_at = :completedAt,
                sync_run_id = :syncRunId,
                updated_at = :updatedAt,
                version = version + 1
            where id = :id
            """.trimIndent(),
            normalized.toParams()
        )
        return requireNotNull(findById(record.id))
    }

    @Transactional(readOnly = true)
    override fun findById(id: ReservationWebhookInboxId): ReservationWebhookInboxRecord? =
        jdbcTemplate.query("select * from reservation_webhook_inbox where id = :id", mapOf("id" to id.value), ::mapRecord)
            .firstOrNull()

    @Transactional(readOnly = true)
    override fun find(filter: ReservationWebhookInboxFilter): ReservationWebhookInboxPage {
        val page = filter.page.coerceAtLeast(0)
        val size = filter.size.coerceIn(1, 100)
        val where = mutableListOf<String>()
        val params = MapSqlParameterSource().addValue("limit", size).addValue("offset", page * size)
        filter.providerId?.takeIf { it.isNotBlank() }?.let {
            where += "provider_id = :providerId"
            params.addValue("providerId", it)
        }
        filter.status?.let {
            where += "processing_status = :status"
            params.addValue("status", it.name)
        }
        val whereSql = if (where.isEmpty()) "" else "where ${where.joinToString(" and ")}"
        val total = jdbcTemplate.queryForObject("select count(*) from reservation_webhook_inbox $whereSql", params, Long::class.java) ?: 0L
        val content = jdbcTemplate.query(
            """
            select *
            from reservation_webhook_inbox
            $whereSql
            order by received_at desc, id desc
            limit :limit offset :offset
            """.trimIndent(),
            params,
            ::mapRecord
        )
        return ReservationWebhookInboxPage(content, page, size, total, if (total == 0L) 0 else ceil(total.toDouble() / size.toDouble()).toInt())
    }

    override fun claimReady(limit: Int, now: Instant, maxAttempts: Int): List<ReservationWebhookInboxRecord> {
        val persistedNow = PersistenceInstant.toPersistencePrecision(now)
        val claimedIds = jdbcTemplate.query(
            """
            update reservation_webhook_inbox
            set processing_status = 'PROCESSING',
                processing_started_at = :now,
                updated_at = :now,
                version = version + 1
            where id in (
                select id
                from reservation_webhook_inbox
                where (
                    processing_status = 'VERIFIED'
                    or (processing_status = 'FAILED' and attempt_count < :maxAttempts)
                )
                  and (next_attempt_at is null or next_attempt_at <= :now)
                order by coalesce(next_attempt_at, received_at) asc, received_at asc, id asc
                limit :limit
                for update skip locked
            )
            returning id
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("now", Timestamp.from(persistedNow))
                .addValue("limit", limit.coerceIn(1, 100))
                .addValue("maxAttempts", maxAttempts.coerceIn(1, 10))
        ) { rs, _ -> ReservationWebhookInboxId(rs.getObject("id", UUID::class.java)) }
        return claimedIds.mapNotNull { findById(it) }
    }

    override fun recoverAbandoned(cutoff: Instant, now: Instant): Int =
        jdbcTemplate.update(
            """
            update reservation_webhook_inbox
            set processing_status = 'FAILED',
                failure_category = 'UNKNOWN',
                next_attempt_at = :now,
                updated_at = :now,
                version = version + 1
            where processing_status = 'PROCESSING'
              and processing_started_at < :cutoff
            """.trimIndent(),
            mapOf(
                "cutoff" to Timestamp.from(PersistenceInstant.toPersistencePrecision(cutoff)),
                "now" to Timestamp.from(PersistenceInstant.toPersistencePrecision(now))
            )
        )

    override fun deleteCompletedBefore(completedCutoff: Instant, rejectedCutoff: Instant, deadLetterCutoff: Instant, limit: Int): Int =
        jdbcTemplate.update(
            """
            delete from reservation_webhook_inbox
            where id in (
                select id
                from reservation_webhook_inbox
                where completed_at is not null
                  and (
                    (processing_status in ('SUCCEEDED', 'FAILED', 'DUPLICATE', 'IGNORED') and completed_at < :completedCutoff)
                    or (processing_status = 'REJECTED' and completed_at < :rejectedCutoff)
                    or (processing_status = 'DEAD_LETTER' and completed_at < :deadLetterCutoff)
                  )
                order by completed_at asc, id asc
                limit :limit
            )
            """.trimIndent(),
            mapOf(
                "completedCutoff" to Timestamp.from(PersistenceInstant.toPersistencePrecision(completedCutoff)),
                "rejectedCutoff" to Timestamp.from(PersistenceInstant.toPersistencePrecision(rejectedCutoff)),
                "deadLetterCutoff" to Timestamp.from(PersistenceInstant.toPersistencePrecision(deadLetterCutoff)),
                "limit" to limit.coerceIn(1, 1_000)
            )
        )

    @Transactional(readOnly = true)
    override fun backlogCounts(): ReservationWebhookBacklogCounts {
        val counts = jdbcTemplate.query(
            """
            select processing_status, count(*) as count
            from reservation_webhook_inbox
            group by processing_status
            """.trimIndent(),
            emptyMap<String, Any>()
        ) { rs, _ -> ReservationWebhookStatus.valueOf(rs.getString("processing_status")) to rs.getLong("count") }
            .toMap()
        return ReservationWebhookBacklogCounts(counts)
    }

    private fun saveNew(record: ReservationWebhookInboxRecord): ReservationWebhookInboxRecord {
        saveNewIfAbsent(record)
        return requireNotNull(findById(record.id))
    }

    private fun saveNewIfAbsent(record: ReservationWebhookInboxRecord): Boolean {
        val normalized = record.normalized()
        val inserted = jdbcTemplate.update(
            """
            insert into reservation_webhook_inbox (
                id, provider_id, provider_event_id, event_type, property_scope_hash,
                property_scope_label, external_entity_hash, provider_event_timestamp,
                received_at, processing_status, failure_category, attempt_count,
                next_attempt_at, processing_started_at, completed_at, payload_fingerprint,
                safe_metadata, sync_run_id, created_at, updated_at, version
            ) values (
                :id, :providerId, :providerEventId, :eventType, :propertyScopeHash,
                :propertyScopeLabel, :externalEntityHash, :providerEventTimestamp,
                :receivedAt, :status, :failureCategory, :attemptCount,
                :nextAttemptAt, :processingStartedAt, :completedAt, :payloadFingerprint,
                cast(:safeMetadata as jsonb), :syncRunId, :createdAt, :updatedAt, :version
            )
            on conflict (provider_id, provider_event_id) do nothing
            """.trimIndent(),
            normalized.toParams()
        )
        return inserted == 1
    }

    private fun findByProviderEventId(providerId: String, eventId: String): ReservationWebhookInboxRecord? =
        jdbcTemplate.query(
            "select * from reservation_webhook_inbox where provider_id = :providerId and provider_event_id = :eventId",
            mapOf("providerId" to providerId, "eventId" to eventId),
            ::mapRecord
        ).firstOrNull()

    private fun mapRecord(rs: ResultSet, @Suppress("UNUSED_PARAMETER") rowNum: Int): ReservationWebhookInboxRecord =
        ReservationWebhookInboxRecord(
            id = ReservationWebhookInboxId(rs.getObject("id", UUID::class.java)),
            providerId = rs.getString("provider_id"),
            providerEventId = rs.getString("provider_event_id"),
            eventCategory = ReservationWebhookEventCategory.valueOf(rs.getString("event_type")),
            propertyScopeHash = rs.getString("property_scope_hash"),
            propertyScopeLabel = rs.getString("property_scope_label"),
            externalEntityHash = rs.getString("external_entity_hash"),
            providerEventTimestamp = rs.getTimestamp("provider_event_timestamp")?.toInstant(),
            receivedAt = rs.getTimestamp("received_at").toInstant(),
            status = ReservationWebhookStatus.valueOf(rs.getString("processing_status")),
            failureCategory = rs.getString("failure_category")?.let(PmsFailureCategory::valueOf),
            attemptCount = rs.getInt("attempt_count"),
            nextAttemptAt = rs.getTimestamp("next_attempt_at")?.toInstant(),
            processingStartedAt = rs.getTimestamp("processing_started_at")?.toInstant(),
            completedAt = rs.getTimestamp("completed_at")?.toInstant(),
            payloadFingerprint = rs.getString("payload_fingerprint"),
            safeMetadata = objectMapper.readValue(rs.getString("safe_metadata"), MAP_TYPE),
            syncRunId = rs.getObject("sync_run_id", UUID::class.java)?.let(::ReservationSyncRunId),
            createdAt = rs.getTimestamp("created_at").toInstant(),
            updatedAt = rs.getTimestamp("updated_at").toInstant(),
            version = rs.getLong("version")
        )

    private fun ReservationWebhookInboxRecord.normalized(): ReservationWebhookInboxRecord =
        copy(
            receivedAt = PersistenceInstant.toPersistencePrecision(receivedAt),
            providerEventTimestamp = PersistenceInstant.toPersistencePrecisionOrNull(providerEventTimestamp),
            nextAttemptAt = PersistenceInstant.toPersistencePrecisionOrNull(nextAttemptAt),
            processingStartedAt = PersistenceInstant.toPersistencePrecisionOrNull(processingStartedAt),
            completedAt = PersistenceInstant.toPersistencePrecisionOrNull(completedAt),
            createdAt = PersistenceInstant.toPersistencePrecision(createdAt),
            updatedAt = PersistenceInstant.toPersistencePrecision(updatedAt)
        )

    private fun ReservationWebhookInboxRecord.toParams(): MapSqlParameterSource =
        MapSqlParameterSource()
            .addValue("id", id.value)
            .addValue("providerId", providerId)
            .addValue("providerEventId", providerEventId)
            .addValue("eventType", eventCategory.name)
            .addValue("propertyScopeHash", propertyScopeHash)
            .addValue("propertyScopeLabel", propertyScopeLabel)
            .addValue("externalEntityHash", externalEntityHash)
            .addValue("providerEventTimestamp", providerEventTimestamp?.let(Timestamp::from))
            .addValue("receivedAt", Timestamp.from(receivedAt))
            .addValue("status", status.name)
            .addValue("failureCategory", failureCategory?.name)
            .addValue("attemptCount", attemptCount)
            .addValue("nextAttemptAt", nextAttemptAt?.let(Timestamp::from))
            .addValue("processingStartedAt", processingStartedAt?.let(Timestamp::from))
            .addValue("completedAt", completedAt?.let(Timestamp::from))
            .addValue("payloadFingerprint", payloadFingerprint)
            .addValue("safeMetadata", objectMapper.writeValueAsString(safeMetadata))
            .addValue("syncRunId", syncRunId?.value)
            .addValue("createdAt", Timestamp.from(createdAt))
            .addValue("updatedAt", Timestamp.from(updatedAt))
            .addValue("version", version)

    companion object {
        private val MAP_TYPE = object : TypeReference<Map<String, String>>() {}
    }
}
