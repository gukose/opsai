package com.hotelopai.reservation.infrastructure.persistence

import com.hotelopai.pms.application.PmsFailureCategory
import com.hotelopai.reservation.application.ReservationSyncRun
import com.hotelopai.reservation.application.ReservationSyncRunId
import com.hotelopai.reservation.application.ReservationSyncRunStatus
import com.hotelopai.reservation.application.ReservationSyncScheduleState
import com.hotelopai.reservation.application.ReservationSyncScheduleStateRepository
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
class ReservationSyncScheduleStateJdbcRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate
) : ReservationSyncScheduleStateRepository {
    override fun getOrCreate(scheduleId: String, now: Instant): ReservationSyncScheduleState {
        find(scheduleId)?.let { return it }
        val persistedNow = PersistenceInstant.toPersistencePrecision(now)
        jdbcTemplate.update(
            """
            insert into reservation_sync_schedule_state (
                schedule_id, paused, updated_at
            ) values (
                :scheduleId, false, :updatedAt
            )
            on conflict (schedule_id) do nothing
            """.trimIndent(),
            mapOf("scheduleId" to scheduleId, "updatedAt" to Timestamp.from(persistedNow))
        )
        return requireNotNull(find(scheduleId))
    }

    override fun markPaused(scheduleId: String, now: Instant): ReservationSyncScheduleState {
        val persistedNow = PersistenceInstant.toPersistencePrecision(now)
        jdbcTemplate.update(
            """
            insert into reservation_sync_schedule_state (
                schedule_id, paused, paused_at, updated_at
            ) values (
                :scheduleId, true, :now, :now
            )
            on conflict (schedule_id) do update set
                paused = true,
                paused_at = :now,
                updated_at = :now
            """.trimIndent(),
            mapOf("scheduleId" to scheduleId, "now" to Timestamp.from(persistedNow))
        )
        return requireNotNull(find(scheduleId))
    }

    override fun markResumed(scheduleId: String, now: Instant): ReservationSyncScheduleState {
        val persistedNow = PersistenceInstant.toPersistencePrecision(now)
        jdbcTemplate.update(
            """
            insert into reservation_sync_schedule_state (
                schedule_id, paused, resumed_at, updated_at
            ) values (
                :scheduleId, false, :now, :now
            )
            on conflict (schedule_id) do update set
                paused = false,
                resumed_at = :now,
                updated_at = :now
            """.trimIndent(),
            mapOf("scheduleId" to scheduleId, "now" to Timestamp.from(persistedNow))
        )
        return requireNotNull(find(scheduleId))
    }

    override fun recordAttempt(
        scheduleId: String,
        run: ReservationSyncRun?,
        now: Instant,
        failureCategory: PmsFailureCategory?
    ): ReservationSyncScheduleState {
        val persistedNow = PersistenceInstant.toPersistencePrecision(now)
        val successAt = run
            ?.takeIf { it.status == ReservationSyncRunStatus.SUCCEEDED || it.status == ReservationSyncRunStatus.PARTIALLY_SUCCEEDED }
            ?.completedAt
        jdbcTemplate.update(
            """
            insert into reservation_sync_schedule_state (
                schedule_id, paused, last_attempted_at, last_successful_at,
                last_failure_category, last_run_id, updated_at
            ) values (
                :scheduleId, false, :attemptedAt, :successfulAt,
                :failureCategory, :lastRunId, :updatedAt
            )
            on conflict (schedule_id) do update set
                last_attempted_at = :attemptedAt,
                last_successful_at = coalesce(:successfulAt, reservation_sync_schedule_state.last_successful_at),
                last_failure_category = :failureCategory,
                last_run_id = :lastRunId,
                updated_at = :updatedAt
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("scheduleId", scheduleId)
                .addValue("attemptedAt", Timestamp.from(persistedNow))
                .addValue("successfulAt", successAt?.let { Timestamp.from(PersistenceInstant.toPersistencePrecision(it)) })
                .addValue("failureCategory", failureCategory?.name)
                .addValue("lastRunId", run?.id?.value)
                .addValue("updatedAt", Timestamp.from(persistedNow))
        )
        return requireNotNull(find(scheduleId))
    }

    override fun recordProcessingAttempt(
        scheduleId: String,
        processedCount: Int,
        success: Boolean,
        now: Instant,
        failureCategory: PmsFailureCategory?
    ): ReservationSyncScheduleState {
        val persistedNow = PersistenceInstant.toPersistencePrecision(now)
        jdbcTemplate.update(
            """
            insert into reservation_sync_schedule_state (
                schedule_id, paused, last_attempted_at, last_successful_at,
                last_failure_category, last_processed_count, updated_at
            ) values (
                :scheduleId, false, :attemptedAt, :successfulAt,
                :failureCategory, :processedCount, :updatedAt
            )
            on conflict (schedule_id) do update set
                last_attempted_at = :attemptedAt,
                last_successful_at = coalesce(:successfulAt, reservation_sync_schedule_state.last_successful_at),
                last_failure_category = :failureCategory,
                last_processed_count = :processedCount,
                updated_at = :updatedAt
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("scheduleId", scheduleId)
                .addValue("attemptedAt", Timestamp.from(persistedNow))
                .addValue("successfulAt", if (success) Timestamp.from(persistedNow) else null)
                .addValue("failureCategory", failureCategory?.name)
                .addValue("processedCount", processedCount.coerceAtLeast(0))
                .addValue("updatedAt", Timestamp.from(persistedNow))
        )
        return requireNotNull(find(scheduleId))
    }

    private fun find(scheduleId: String): ReservationSyncScheduleState? =
        jdbcTemplate.query(
            "select * from reservation_sync_schedule_state where schedule_id = :scheduleId",
            mapOf("scheduleId" to scheduleId),
            ::mapState
        ).firstOrNull()

    private fun mapState(rs: ResultSet, @Suppress("UNUSED_PARAMETER") rowNum: Int): ReservationSyncScheduleState =
        ReservationSyncScheduleState(
            scheduleId = rs.getString("schedule_id"),
            paused = rs.getBoolean("paused"),
            pausedAt = rs.getTimestamp("paused_at")?.toInstant(),
            resumedAt = rs.getTimestamp("resumed_at")?.toInstant(),
            lastAttemptedAt = rs.getTimestamp("last_attempted_at")?.toInstant(),
            lastSuccessfulAt = rs.getTimestamp("last_successful_at")?.toInstant(),
            lastFailureCategory = rs.getString("last_failure_category")?.let(PmsFailureCategory::valueOf),
            lastRunId = rs.getObject("last_run_id", UUID::class.java)?.let(::ReservationSyncRunId),
            lastProcessedCount = rs.getInt("last_processed_count"),
            updatedAt = rs.getTimestamp("updated_at").toInstant()
        )
}
