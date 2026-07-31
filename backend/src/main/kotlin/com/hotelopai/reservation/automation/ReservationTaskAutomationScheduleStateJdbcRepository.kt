package com.hotelopai.reservation.automation

import com.hotelopai.shared.kernel.PersistenceInstant
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant

@Repository
@Transactional
class ReservationTaskAutomationScheduleStateJdbcRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate
) : ReservationTaskAutomationScheduleStateRepository {
    override fun getOrCreate(scheduleId: String, now: Instant): ReservationTaskAutomationScheduleState {
        find(scheduleId)?.let { return it }
        val persistedNow = PersistenceInstant.toPersistencePrecision(now)
        jdbcTemplate.update(
            """
            insert into reservation_task_automation_schedule_state (
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

    override fun markPaused(scheduleId: String, now: Instant): ReservationTaskAutomationScheduleState {
        val persistedNow = PersistenceInstant.toPersistencePrecision(now)
        jdbcTemplate.update(
            """
            insert into reservation_task_automation_schedule_state (
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

    override fun markResumed(scheduleId: String, now: Instant): ReservationTaskAutomationScheduleState {
        val persistedNow = PersistenceInstant.toPersistencePrecision(now)
        jdbcTemplate.update(
            """
            insert into reservation_task_automation_schedule_state (
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
        summary: ReservationTaskAutomationBatchSummary?,
        now: Instant,
        failureCategory: ReservationTaskAutomationSkipReason?
    ): ReservationTaskAutomationScheduleState {
        val persistedNow = PersistenceInstant.toPersistencePrecision(now)
        val success = failureCategory == null
        jdbcTemplate.update(
            """
            insert into reservation_task_automation_schedule_state (
                schedule_id, paused, last_attempted_at, last_successful_at,
                last_processed_count, last_created_task_count, last_failure_category, updated_at
            ) values (
                :scheduleId, false, :attemptedAt, :successfulAt,
                :processedCount, :createdTaskCount, :failureCategory, :updatedAt
            )
            on conflict (schedule_id) do update set
                last_attempted_at = :attemptedAt,
                last_successful_at = coalesce(:successfulAt, reservation_task_automation_schedule_state.last_successful_at),
                last_processed_count = :processedCount,
                last_created_task_count = :createdTaskCount,
                last_failure_category = :failureCategory,
                updated_at = :updatedAt
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("scheduleId", scheduleId)
                .addValue("attemptedAt", Timestamp.from(persistedNow))
                .addValue("successfulAt", if (success) Timestamp.from(persistedNow) else null)
                .addValue("processedCount", summary?.processedEvents?.coerceAtLeast(0) ?: 0)
                .addValue("createdTaskCount", summary?.tasksCreated?.coerceAtLeast(0) ?: 0)
                .addValue("failureCategory", failureCategory?.name)
                .addValue("updatedAt", Timestamp.from(persistedNow))
        )
        return requireNotNull(find(scheduleId))
    }

    private fun find(scheduleId: String): ReservationTaskAutomationScheduleState? =
        jdbcTemplate.query(
            "select * from reservation_task_automation_schedule_state where schedule_id = :scheduleId",
            mapOf("scheduleId" to scheduleId),
            ::mapState
        ).firstOrNull()

    private fun mapState(rs: ResultSet, @Suppress("UNUSED_PARAMETER") rowNum: Int): ReservationTaskAutomationScheduleState =
        ReservationTaskAutomationScheduleState(
            scheduleId = rs.getString("schedule_id"),
            paused = rs.getBoolean("paused"),
            pausedAt = rs.getTimestamp("paused_at")?.toInstant(),
            resumedAt = rs.getTimestamp("resumed_at")?.toInstant(),
            lastAttemptedAt = rs.getTimestamp("last_attempted_at")?.toInstant(),
            lastSuccessfulAt = rs.getTimestamp("last_successful_at")?.toInstant(),
            lastProcessedCount = rs.getInt("last_processed_count"),
            lastCreatedTaskCount = rs.getInt("last_created_task_count"),
            lastFailureCategory = rs.getString("last_failure_category")?.let(ReservationTaskAutomationSkipReason::valueOf),
            updatedAt = rs.getTimestamp("updated_at").toInstant()
        )
}
