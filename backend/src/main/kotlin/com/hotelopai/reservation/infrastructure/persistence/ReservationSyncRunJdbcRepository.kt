package com.hotelopai.reservation.infrastructure.persistence

import com.hotelopai.pms.application.PmsFailureCategory
import com.hotelopai.reservation.application.ReservationSyncRun
import com.hotelopai.reservation.application.ReservationSyncRunFilter
import com.hotelopai.reservation.application.ReservationSyncRunId
import com.hotelopai.reservation.application.ReservationSyncRunPage
import com.hotelopai.reservation.application.ReservationSyncRunRepository
import com.hotelopai.reservation.application.ReservationSyncRunStatus
import com.hotelopai.reservation.application.ReservationSyncTriggerType
import com.hotelopai.reservation.domain.DateRange
import com.hotelopai.shared.kernel.PersistenceInstant
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlin.math.ceil

@Repository
@Transactional
class ReservationSyncRunJdbcRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate
) : ReservationSyncRunRepository {
    override fun save(run: ReservationSyncRun): ReservationSyncRun {
        val existing = findById(run.id)
        val normalized = run.normalized().copy(version = existing?.version?.plus(1) ?: run.version)
        jdbcTemplate.update(
            """
            insert into reservation_sync_run (
                id, provider_id, property_scope_hash, property_scope_label,
                requested_start_date, requested_end_date, trigger_type, run_status,
                started_at, completed_at, fetched_count, created_count, updated_count,
                unchanged_count, stale_count, conflict_count, bounded_page_count,
                failure_category, actor_user_id, created_at, updated_at, version
            ) values (
                :id, :providerId, :propertyScopeHash, :propertyScopeLabel,
                :requestedStartDate, :requestedEndDate, :triggerType, :runStatus,
                :startedAt, :completedAt, :fetchedCount, :createdCount, :updatedCount,
                :unchangedCount, :staleCount, :conflictCount, :boundedPageCount,
                :failureCategory, :actorUserId, :createdAt, :updatedAt, :version
            )
            on conflict (id) do update set
                run_status = excluded.run_status,
                completed_at = excluded.completed_at,
                fetched_count = excluded.fetched_count,
                created_count = excluded.created_count,
                updated_count = excluded.updated_count,
                unchanged_count = excluded.unchanged_count,
                stale_count = excluded.stale_count,
                conflict_count = excluded.conflict_count,
                bounded_page_count = excluded.bounded_page_count,
                failure_category = excluded.failure_category,
                updated_at = excluded.updated_at,
                version = reservation_sync_run.version + 1
            """.trimIndent(),
            normalized.toParams()
        )
        return requireNotNull(findById(run.id))
    }

    @Transactional(readOnly = true)
    override fun findById(id: ReservationSyncRunId): ReservationSyncRun? =
        jdbcTemplate.query(
            "select * from reservation_sync_run where id = :id",
            mapOf("id" to id.value),
            ::mapRun
        ).firstOrNull()

    @Transactional(readOnly = true)
    override fun find(filter: ReservationSyncRunFilter): ReservationSyncRunPage {
        val page = filter.page.coerceAtLeast(0)
        val size = filter.size.coerceIn(1, 500)
        val where = mutableListOf<String>()
        val params = MapSqlParameterSource()
            .addValue("limit", size)
            .addValue("offset", page * size)
        filter.providerId?.takeIf { it.isNotBlank() }?.let {
            where += "provider_id = :providerId"
            params.addValue("providerId", it)
        }
        filter.status?.let {
            where += "run_status = :runStatus"
            params.addValue("runStatus", it.name)
        }
        val whereSql = if (where.isEmpty()) "" else "where ${where.joinToString(" and ")}"
        val total = jdbcTemplate.queryForObject(
            "select count(*) from reservation_sync_run $whereSql",
            params,
            Long::class.java
        ) ?: 0L
        val runs = jdbcTemplate.query(
            """
            select *
            from reservation_sync_run
            $whereSql
            order by started_at desc, id desc
            limit :limit offset :offset
            """.trimIndent(),
            params,
            ::mapRun
        )
        return ReservationSyncRunPage(
            content = runs,
            page = page,
            size = size,
            totalElements = total,
            totalPages = if (total == 0L) 0 else ceil(total.toDouble() / size.toDouble()).toInt()
        )
    }

    override fun deleteCompletedBefore(cutoff: Instant, limit: Int): Int =
        jdbcTemplate.update(
            """
            delete from reservation_sync_run
            where id in (
                select id
                from reservation_sync_run
                where completed_at is not null
                  and completed_at < :cutoff
                  and run_status not in ('REQUESTED', 'RUNNING')
                order by completed_at asc, id asc
                limit :limit
            )
            """.trimIndent(),
            mapOf(
                "cutoff" to PersistenceInstant.toPersistencePrecision(cutoff).toTimestamp(),
                "limit" to limit.coerceAtLeast(0)
            )
        )

    private fun mapRun(rs: ResultSet, @Suppress("UNUSED_PARAMETER") rowNum: Int): ReservationSyncRun =
        ReservationSyncRun(
            id = ReservationSyncRunId(rs.getObject("id", UUID::class.java)),
            providerId = rs.getString("provider_id"),
            propertyScopeHash = rs.getString("property_scope_hash"),
            propertyScopeLabel = rs.getString("property_scope_label"),
            requestedDateRange = DateRange(
                rs.getObject("requested_start_date", LocalDate::class.java),
                rs.getObject("requested_end_date", LocalDate::class.java)
            ),
            triggerType = ReservationSyncTriggerType.valueOf(rs.getString("trigger_type")),
            status = ReservationSyncRunStatus.valueOf(rs.getString("run_status")),
            startedAt = rs.getTimestamp("started_at").toInstant(),
            completedAt = rs.getTimestamp("completed_at")?.toInstant(),
            fetchedCount = rs.getInt("fetched_count"),
            createdCount = rs.getInt("created_count"),
            updatedCount = rs.getInt("updated_count"),
            unchangedCount = rs.getInt("unchanged_count"),
            staleCount = rs.getInt("stale_count"),
            conflictCount = rs.getInt("conflict_count"),
            boundedPageCount = rs.getInt("bounded_page_count"),
            failureCategory = rs.getString("failure_category")?.let(PmsFailureCategory::valueOf),
            actorUserId = rs.getObject("actor_user_id", UUID::class.java),
            createdAt = rs.getTimestamp("created_at").toInstant(),
            updatedAt = rs.getTimestamp("updated_at").toInstant(),
            version = rs.getLong("version")
        )

    private fun ReservationSyncRun.normalized(): ReservationSyncRun =
        copy(
            startedAt = PersistenceInstant.toPersistencePrecision(startedAt),
            completedAt = PersistenceInstant.toPersistencePrecisionOrNull(completedAt),
            createdAt = PersistenceInstant.toPersistencePrecision(createdAt),
            updatedAt = PersistenceInstant.toPersistencePrecision(updatedAt)
        )

    private fun ReservationSyncRun.toParams(): MapSqlParameterSource =
        MapSqlParameterSource()
            .addValue("id", id.value)
            .addValue("providerId", providerId)
            .addValue("propertyScopeHash", propertyScopeHash)
            .addValue("propertyScopeLabel", propertyScopeLabel)
            .addValue("requestedStartDate", requestedDateRange.arrival)
            .addValue("requestedEndDate", requestedDateRange.departure)
            .addValue("triggerType", triggerType.name)
            .addValue("runStatus", status.name)
            .addValue("startedAt", startedAt.toTimestamp())
            .addValue("completedAt", completedAt?.toTimestamp())
            .addValue("fetchedCount", fetchedCount)
            .addValue("createdCount", createdCount)
            .addValue("updatedCount", updatedCount)
            .addValue("unchangedCount", unchangedCount)
            .addValue("staleCount", staleCount)
            .addValue("conflictCount", conflictCount)
            .addValue("boundedPageCount", boundedPageCount)
            .addValue("failureCategory", failureCategory?.name)
            .addValue("actorUserId", actorUserId)
            .addValue("createdAt", createdAt.toTimestamp())
            .addValue("updatedAt", updatedAt.toTimestamp())
            .addValue("version", version)

    private fun Instant.toTimestamp(): Timestamp = Timestamp.from(this)
}
