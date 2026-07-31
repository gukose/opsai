package com.hotelopai.reservation.recommendation

import com.hotelopai.shared.kernel.PersistenceInstant
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.ceil

@Repository
@Transactional
class RecommendationPilotJdbcRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate
) : RecommendationPilotRepository {
    override fun saveRun(run: RecommendationPilotRun): RecommendationPilotRun {
        val normalized = run.normalized()
        jdbcTemplate.update(
            """
            insert into recommendation_pilot_run (
                id, provider_id, trigger_type, status, started_at, completed_at,
                candidates_selected, candidates_processed, provider_calls,
                recommendations_generated, duplicates_prevented, skipped_count,
                failed_count, request_budget_used, recommendation_budget_used,
                token_budget_used, model_identifier, prompt_version,
                context_schema_version, failure_category, created_at, updated_at, version
            ) values (
                :id, :providerId, :triggerType, :status, :startedAt, :completedAt,
                :candidatesSelected, :candidatesProcessed, :providerCalls,
                :recommendationsGenerated, :duplicatesPrevented, :skippedCount,
                :failedCount, :requestBudgetUsed, :recommendationBudgetUsed,
                :tokenBudgetUsed, :modelIdentifier, :promptVersion,
                :contextSchemaVersion, :failureCategory, :createdAt, :updatedAt, :version
            )
            on conflict (id) do update set
                status = :status,
                completed_at = :completedAt,
                candidates_selected = :candidatesSelected,
                candidates_processed = :candidatesProcessed,
                provider_calls = :providerCalls,
                recommendations_generated = :recommendationsGenerated,
                duplicates_prevented = :duplicatesPrevented,
                skipped_count = :skippedCount,
                failed_count = :failedCount,
                request_budget_used = :requestBudgetUsed,
                recommendation_budget_used = :recommendationBudgetUsed,
                token_budget_used = :tokenBudgetUsed,
                failure_category = :failureCategory,
                updated_at = :updatedAt,
                version = recommendation_pilot_run.version + 1
            """.trimIndent(),
            normalized.toParams()
        )
        return requireNotNull(findRun(normalized.id))
    }

    @Transactional(readOnly = true)
    override fun findRun(id: RecommendationPilotRunId): RecommendationPilotRun? =
        jdbcTemplate.query(
            "select * from recommendation_pilot_run where id = :id",
            mapOf("id" to id.value),
            ::mapRun
        ).firstOrNull()

    @Transactional(readOnly = true)
    override fun findRuns(filter: RecommendationPilotRunFilter): RecommendationPilotRunPage {
        val page = filter.page.coerceAtLeast(0)
        val size = filter.size.coerceIn(1, 100)
        val params = MapSqlParameterSource().addValue("limit", size).addValue("offset", page * size)
        val where = mutableListOf<String>()
        filter.status?.let {
            where += "status = :status"
            params.addValue("status", it.name)
        }
        filter.trigger?.let {
            where += "trigger_type = :trigger"
            params.addValue("trigger", it.name)
        }
        val whereSql = if (where.isEmpty()) "" else "where ${where.joinToString(" and ")}"
        val total = jdbcTemplate.queryForObject("select count(*) from recommendation_pilot_run $whereSql", params, Long::class.java) ?: 0L
        val content = jdbcTemplate.query(
            """
            select *
            from recommendation_pilot_run
            $whereSql
            order by started_at desc, id desc
            limit :limit offset :offset
            """.trimIndent(),
            params,
            ::mapRun
        )
        return RecommendationPilotRunPage(content, page, size, total, if (total == 0L) 0 else ceil(total.toDouble() / size.toDouble()).toInt())
    }

    override fun budgetStatus(
        providerId: RecommendationProviderId,
        budgetDate: LocalDate,
        requestLimit: Int,
        recommendationLimit: Int,
        tokenLimit: Long?
    ): RecommendationPilotBudgetStatus {
        val row = jdbcTemplate.query(
            """
            select request_count, recommendation_count, token_count
            from recommendation_pilot_budget_daily
            where provider_id = :providerId and budget_date = :budgetDate
            """.trimIndent(),
            mapOf("providerId" to providerId.value, "budgetDate" to budgetDate),
        ) { rs, _ ->
            Triple(rs.getInt("request_count"), rs.getInt("recommendation_count"), rs.getLong("token_count"))
        }.firstOrNull() ?: Triple(0, 0, 0L)
        return RecommendationPilotBudgetStatus(
            providerId = providerId,
            budgetDate = budgetDate,
            requestLimit = requestLimit,
            requestUsed = row.first,
            recommendationLimit = recommendationLimit,
            recommendationUsed = row.second,
            tokenLimit = tokenLimit,
            tokenUsed = row.third,
            exhausted = row.first >= requestLimit || row.second >= recommendationLimit || (tokenLimit != null && row.third >= tokenLimit)
        )
    }

    override fun reserveRequest(
        providerId: RecommendationProviderId,
        budgetDate: LocalDate,
        requestLimit: Int,
        tokenLimit: Long?,
        expectedTokens: Long,
        now: Instant
    ): Boolean {
        jdbcTemplate.update(
            """
            insert into recommendation_pilot_budget_daily (
                provider_id, budget_date, request_count, recommendation_count, token_count, updated_at
            ) values (
                :providerId, :budgetDate, 0, 0, 0, :updatedAt
            )
            on conflict (provider_id, budget_date) do nothing
            """.trimIndent(),
            mapOf(
                "providerId" to providerId.value,
                "budgetDate" to budgetDate,
                "updatedAt" to Timestamp.from(PersistenceInstant.toPersistencePrecision(now))
            )
        )
        val updated = jdbcTemplate.update(
            """
            update recommendation_pilot_budget_daily
            set request_count = request_count + 1,
                token_count = token_count + :expectedTokens,
                updated_at = :updatedAt
            where provider_id = :providerId
              and budget_date = :budgetDate
              and request_count < :requestLimit
              and (:tokenLimit is null or token_count + :expectedTokens <= :tokenLimit)
            """.trimIndent(),
            mapOf(
                "providerId" to providerId.value,
                "budgetDate" to budgetDate,
                "requestLimit" to requestLimit,
                "tokenLimit" to tokenLimit,
                "expectedTokens" to expectedTokens.coerceAtLeast(0),
                "updatedAt" to Timestamp.from(PersistenceInstant.toPersistencePrecision(now))
            )
        )
        return updated == 1
    }

    override fun recordGeneratedRecommendations(providerId: RecommendationProviderId, budgetDate: LocalDate, count: Int, now: Instant) {
        if (count <= 0) return
        jdbcTemplate.update(
            """
            update recommendation_pilot_budget_daily
            set recommendation_count = recommendation_count + :count,
                updated_at = :updatedAt
            where provider_id = :providerId and budget_date = :budgetDate
            """.trimIndent(),
            mapOf(
                "providerId" to providerId.value,
                "budgetDate" to budgetDate,
                "count" to count,
                "updatedAt" to Timestamp.from(PersistenceInstant.toPersistencePrecision(now))
            )
        )
    }

    override fun releaseFailedRequest(providerId: RecommendationProviderId, budgetDate: LocalDate, now: Instant) {
        jdbcTemplate.update(
            """
            update recommendation_pilot_budget_daily
            set request_count = greatest(request_count - 1, 0),
                updated_at = :updatedAt
            where provider_id = :providerId and budget_date = :budgetDate
            """.trimIndent(),
            mapOf(
                "providerId" to providerId.value,
                "budgetDate" to budgetDate,
                "updatedAt" to Timestamp.from(PersistenceInstant.toPersistencePrecision(now))
            )
        )
    }

    override fun getOrCreateState(stateId: String, now: Instant): RecommendationPilotState {
        val persistedNow = PersistenceInstant.toPersistencePrecision(now)
        jdbcTemplate.update(
            """
            insert into recommendation_pilot_state (state_id, disabled, updated_at)
            values (:stateId, false, :updatedAt)
            on conflict (state_id) do nothing
            """.trimIndent(),
            mapOf("stateId" to stateId, "updatedAt" to Timestamp.from(persistedNow))
        )
        return requireNotNull(findState(stateId))
    }

    override fun disable(stateId: String, now: Instant): RecommendationPilotState {
        val persistedNow = PersistenceInstant.toPersistencePrecision(now)
        jdbcTemplate.update(
            """
            insert into recommendation_pilot_state (state_id, disabled, disabled_at, updated_at)
            values (:stateId, true, :now, :now)
            on conflict (state_id) do update set
                disabled = true,
                disabled_at = :now,
                updated_at = :now
            """.trimIndent(),
            mapOf("stateId" to stateId, "now" to Timestamp.from(persistedNow))
        )
        return requireNotNull(findState(stateId))
    }

    override fun rollback(stateId: String, now: Instant): RecommendationPilotState {
        val persistedNow = PersistenceInstant.toPersistencePrecision(now)
        jdbcTemplate.update(
            """
            insert into recommendation_pilot_state (state_id, disabled, disabled_at, last_rollback_at, updated_at)
            values (:stateId, true, :now, :now, :now)
            on conflict (state_id) do update set
                disabled = true,
                disabled_at = coalesce(recommendation_pilot_state.disabled_at, :now),
                last_rollback_at = :now,
                updated_at = :now
            """.trimIndent(),
            mapOf("stateId" to stateId, "now" to Timestamp.from(persistedNow))
        )
        return requireNotNull(findState(stateId))
    }

    override fun pauseSchedule(stateId: String, now: Instant): RecommendationPilotState {
        val persistedNow = PersistenceInstant.toPersistencePrecision(now)
        jdbcTemplate.update(
            """
            insert into recommendation_pilot_state (state_id, disabled, schedule_paused, schedule_paused_at, updated_at)
            values (:stateId, false, true, :now, :now)
            on conflict (state_id) do update set
                schedule_paused = true,
                schedule_paused_at = :now,
                updated_at = :now
            """.trimIndent(),
            mapOf("stateId" to stateId, "now" to Timestamp.from(persistedNow))
        )
        return requireNotNull(findState(stateId))
    }

    override fun resumeSchedule(stateId: String, now: Instant): RecommendationPilotState {
        val persistedNow = PersistenceInstant.toPersistencePrecision(now)
        jdbcTemplate.update(
            """
            insert into recommendation_pilot_state (state_id, disabled, schedule_paused, schedule_resumed_at, updated_at)
            values (:stateId, false, false, :now, :now)
            on conflict (state_id) do update set
                schedule_paused = false,
                schedule_resumed_at = :now,
                updated_at = :now
            """.trimIndent(),
            mapOf("stateId" to stateId, "now" to Timestamp.from(persistedNow))
        )
        return requireNotNull(findState(stateId))
    }

    override fun recordScheduleAttempt(
        stateId: String,
        run: RecommendationPilotRun?,
        budgetRejections: Int,
        now: Instant
    ): RecommendationPilotState {
        val persistedNow = PersistenceInstant.toPersistencePrecision(now)
        val success = run?.status in setOf(RecommendationPilotRunStatus.SUCCEEDED, RecommendationPilotRunStatus.PARTIALLY_SUCCEEDED)
        jdbcTemplate.update(
            """
            insert into recommendation_pilot_state (
                state_id, disabled, last_schedule_attempted_at, last_schedule_successful_at,
                last_schedule_outcome, last_selected_candidate_count,
                last_generated_recommendation_count, last_budget_rejection_count,
                last_schedule_failure_category, updated_at
            ) values (
                :stateId, false, :attemptedAt, :successfulAt,
                :outcome, :selectedCount, :generatedCount, :budgetRejections,
                :failureCategory, :updatedAt
            )
            on conflict (state_id) do update set
                last_schedule_attempted_at = :attemptedAt,
                last_schedule_successful_at = coalesce(:successfulAt, recommendation_pilot_state.last_schedule_successful_at),
                last_schedule_outcome = :outcome,
                last_selected_candidate_count = :selectedCount,
                last_generated_recommendation_count = :generatedCount,
                last_budget_rejection_count = :budgetRejections,
                last_schedule_failure_category = :failureCategory,
                updated_at = :updatedAt
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("stateId", stateId)
                .addValue("attemptedAt", Timestamp.from(persistedNow))
                .addValue("successfulAt", if (success) Timestamp.from(persistedNow) else null)
                .addValue("outcome", run?.status?.name)
                .addValue("selectedCount", run?.candidatesSelected?.coerceAtLeast(0) ?: 0)
                .addValue("generatedCount", run?.recommendationsGenerated?.coerceAtLeast(0) ?: 0)
                .addValue("budgetRejections", budgetRejections.coerceAtLeast(0))
                .addValue("failureCategory", run?.failureCategory?.name)
                .addValue("updatedAt", Timestamp.from(persistedNow))
        )
        return requireNotNull(findState(stateId))
    }

    @Transactional(readOnly = true)
    override fun scheduledRunCount(providerId: RecommendationProviderId, budgetDate: LocalDate): Long =
        jdbcTemplate.queryForObject(
            """
            select count(*)
            from recommendation_pilot_run
            where provider_id = :providerId
              and trigger_type = 'SCHEDULED'
              and started_at >= :startOfDay
              and started_at < :endOfDay
              and status not in ('REJECTED')
            """.trimIndent(),
            mapOf(
                "providerId" to providerId.value,
                "startOfDay" to Timestamp.from(budgetDate.atStartOfDay(ZoneOffset.UTC).toInstant()),
                "endOfDay" to Timestamp.from(budgetDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant())
            ),
            Long::class.java
        ) ?: 0L

    override fun cleanupPilotRuns(completedBefore: Instant, limit: Int): Int =
        jdbcTemplate.update(
            """
            delete from recommendation_pilot_run
            where id in (
                select id
                from recommendation_pilot_run
                where status in ('SUCCEEDED', 'PARTIALLY_SUCCEEDED', 'FAILED', 'REJECTED', 'BUDGET_EXHAUSTED')
                  and completed_at is not null
                  and completed_at < :completedBefore
                order by completed_at asc, id asc
                limit :limit
            )
            """.trimIndent(),
            mapOf(
                "completedBefore" to Timestamp.from(PersistenceInstant.toPersistencePrecision(completedBefore)),
                "limit" to limit.coerceIn(1, 1_000)
            )
        )

    @Transactional(readOnly = true)
    override fun analytics(filter: RecommendationPilotAnalyticsFilter, now: Instant): RecommendationPilotAnalytics {
        val params = MapSqlParameterSource()
            .addValue("now", Timestamp.from(PersistenceInstant.toPersistencePrecision(now)))
        val where = analyticsWhere(filter, params)
        val summary = jdbcTemplate.query(
            """
            select
                count(*) as generated_count,
                count(*) filter (where status = 'APPROVED') as approved_count,
                count(*) filter (where status = 'REJECTED') as rejected_count,
                count(*) filter (where status = 'EXPIRED') as expired_count,
                count(*) filter (where status = 'APPLIED') as applied_count,
                coalesce(avg(extract(epoch from (coalesce(reviewed_at, updated_at) - created_at))) filter (
                    where status in ('APPROVED', 'REJECTED', 'APPLIED', 'EXPIRED')
                ), 0) as average_review_seconds
            from reservation_task_recommendation
            $where
            """.trimIndent(),
            params
        ) { rs, _ ->
            val generated = rs.getLong("generated_count")
            val approved = rs.getLong("approved_count")
            val rejected = rs.getLong("rejected_count")
            val applied = rs.getLong("applied_count")
            RecommendationPilotAnalyticsSummary(
                generatedCount = generated,
                approvedCount = approved,
                rejectedCount = rejected,
                expiredCount = rs.getLong("expired_count"),
                appliedCount = applied,
                approvalRate = rate(approved, generated),
                rejectionRate = rate(rejected, generated),
                applyRate = rate(applied, generated),
                averageReviewTimeBand = reviewTimeBand(rs.getDouble("average_review_seconds")),
                duplicatePreventionCount = duplicatePreventionCount(filter),
                failureCount = failureCount(filter)
            )
        }.firstOrNull() ?: RecommendationPilotAnalyticsSummary(0, 0, 0, 0, 0, 0.0, 0.0, 0.0, "none", 0, 0)
        return RecommendationPilotAnalytics(
            summary = summary,
            reviewOutcomes = groupedBreakdown("status", where, params),
            confidenceDistribution = groupedBreakdown("confidence", where, params),
            categoryDistribution = groupedBreakdown("category", where, params),
            providerModelDistribution = providerModelBreakdown(where, params),
            recommendationAgeBands = ageBandBreakdown(where, params)
        )
    }

    private fun mapRun(rs: ResultSet, @Suppress("UNUSED_PARAMETER") rowNum: Int): RecommendationPilotRun =
        RecommendationPilotRun(
            id = RecommendationPilotRunId(rs.getObject("id", java.util.UUID::class.java)),
            providerId = RecommendationProviderId(rs.getString("provider_id")),
            trigger = RecommendationPilotTrigger.valueOf(rs.getString("trigger_type")),
            status = RecommendationPilotRunStatus.valueOf(rs.getString("status")),
            startedAt = rs.getTimestamp("started_at").toInstant(),
            completedAt = rs.getTimestamp("completed_at")?.toInstant(),
            candidatesSelected = rs.getInt("candidates_selected"),
            candidatesProcessed = rs.getInt("candidates_processed"),
            providerCalls = rs.getInt("provider_calls"),
            recommendationsGenerated = rs.getInt("recommendations_generated"),
            duplicatesPrevented = rs.getInt("duplicates_prevented"),
            skippedCount = rs.getInt("skipped_count"),
            failedCount = rs.getInt("failed_count"),
            requestBudgetUsed = rs.getInt("request_budget_used"),
            recommendationBudgetUsed = rs.getInt("recommendation_budget_used"),
            tokenBudgetUsed = rs.getLong("token_budget_used"),
            modelIdentifier = rs.getString("model_identifier"),
            promptVersion = rs.getString("prompt_version"),
            contextSchemaVersion = rs.getString("context_schema_version"),
            failureCategory = rs.getString("failure_category")?.let(RecommendationFailureCategory::valueOf),
            createdAt = rs.getTimestamp("created_at").toInstant(),
            updatedAt = rs.getTimestamp("updated_at").toInstant(),
            version = rs.getLong("version")
        )

    private fun findState(stateId: String): RecommendationPilotState? =
        jdbcTemplate.query(
            "select * from recommendation_pilot_state where state_id = :stateId",
            mapOf("stateId" to stateId),
        ) { rs, _ ->
            RecommendationPilotState(
                stateId = rs.getString("state_id"),
                disabled = rs.getBoolean("disabled"),
                disabledAt = rs.getTimestamp("disabled_at")?.toInstant(),
                lastRollbackAt = rs.getTimestamp("last_rollback_at")?.toInstant(),
                schedulePaused = rs.getBoolean("schedule_paused"),
                schedulePausedAt = rs.getTimestamp("schedule_paused_at")?.toInstant(),
                scheduleResumedAt = rs.getTimestamp("schedule_resumed_at")?.toInstant(),
                lastScheduleAttemptedAt = rs.getTimestamp("last_schedule_attempted_at")?.toInstant(),
                lastScheduleSuccessfulAt = rs.getTimestamp("last_schedule_successful_at")?.toInstant(),
                lastScheduleOutcome = rs.getString("last_schedule_outcome")?.let(RecommendationPilotRunStatus::valueOf),
                lastSelectedCandidateCount = rs.getInt("last_selected_candidate_count"),
                lastGeneratedRecommendationCount = rs.getInt("last_generated_recommendation_count"),
                lastBudgetRejectionCount = rs.getInt("last_budget_rejection_count"),
                lastScheduleFailureCategory = rs.getString("last_schedule_failure_category")?.let(RecommendationFailureCategory::valueOf),
                updatedAt = rs.getTimestamp("updated_at").toInstant()
            )
        }.firstOrNull()

    private fun RecommendationPilotRun.normalized(): RecommendationPilotRun =
        copy(
            startedAt = PersistenceInstant.toPersistencePrecision(startedAt),
            completedAt = PersistenceInstant.toPersistencePrecisionOrNull(completedAt),
            createdAt = PersistenceInstant.toPersistencePrecision(createdAt),
            updatedAt = PersistenceInstant.toPersistencePrecision(updatedAt)
        )

    private fun RecommendationPilotRun.toParams(): MapSqlParameterSource =
        MapSqlParameterSource()
            .addValue("id", id.value)
            .addValue("providerId", providerId.value)
            .addValue("triggerType", trigger.name)
            .addValue("status", status.name)
            .addValue("startedAt", Timestamp.from(startedAt))
            .addValue("completedAt", completedAt?.let(Timestamp::from))
            .addValue("candidatesSelected", candidatesSelected)
            .addValue("candidatesProcessed", candidatesProcessed)
            .addValue("providerCalls", providerCalls)
            .addValue("recommendationsGenerated", recommendationsGenerated)
            .addValue("duplicatesPrevented", duplicatesPrevented)
            .addValue("skippedCount", skippedCount)
            .addValue("failedCount", failedCount)
            .addValue("requestBudgetUsed", requestBudgetUsed)
            .addValue("recommendationBudgetUsed", recommendationBudgetUsed)
            .addValue("tokenBudgetUsed", tokenBudgetUsed)
            .addValue("modelIdentifier", modelIdentifier)
            .addValue("promptVersion", promptVersion)
            .addValue("contextSchemaVersion", contextSchemaVersion)
            .addValue("failureCategory", failureCategory?.name)
            .addValue("createdAt", Timestamp.from(createdAt))
            .addValue("updatedAt", Timestamp.from(updatedAt))
            .addValue("version", version)

    private fun analyticsWhere(filter: RecommendationPilotAnalyticsFilter, params: MapSqlParameterSource): String {
        val where = mutableListOf("pilot_run_id is not null")
        filter.generatedFrom?.let {
            where += "created_at >= :generatedFrom"
            params.addValue("generatedFrom", Timestamp.from(PersistenceInstant.toPersistencePrecision(it)))
        }
        filter.generatedTo?.let {
            where += "created_at < :generatedTo"
            params.addValue("generatedTo", Timestamp.from(PersistenceInstant.toPersistencePrecision(it)))
        }
        filter.providerId?.let {
            where += "provider_name = :providerId"
            params.addValue("providerId", it.value)
        }
        filter.modelIdentifier?.let {
            where += "model_identifier = :modelIdentifier"
            params.addValue("modelIdentifier", it)
        }
        filter.category?.let {
            where += "category = :category"
            params.addValue("category", it.name)
        }
        filter.confidence?.let {
            where += "confidence = :confidence"
            params.addValue("confidence", it.name)
        }
        filter.status?.let {
            where += "status = :status"
            params.addValue("status", it.name)
        }
        filter.pilotRunId?.let {
            where += "pilot_run_id = :pilotRunId"
            params.addValue("pilotRunId", it.value)
        }
        return "where ${where.joinToString(" and ")}"
    }

    private fun groupedBreakdown(column: String, where: String, params: MapSqlParameterSource): List<RecommendationPilotBreakdown> =
        jdbcTemplate.query(
            """
            select $column as key, count(*) as count
            from reservation_task_recommendation
            $where
            group by $column
            order by count desc, key asc
            """.trimIndent(),
            params
        ) { rs, _ -> RecommendationPilotBreakdown(rs.getString("key"), rs.getLong("count")) }

    private fun providerModelBreakdown(where: String, params: MapSqlParameterSource): List<RecommendationPilotBreakdown> =
        jdbcTemplate.query(
            """
            select provider_name || ':' || coalesce(model_identifier, 'none') as key, count(*) as count
            from reservation_task_recommendation
            $where
            group by provider_name, model_identifier
            order by count desc, key asc
            """.trimIndent(),
            params
        ) { rs, _ -> RecommendationPilotBreakdown(rs.getString("key"), rs.getLong("count")) }

    private fun ageBandBreakdown(where: String, params: MapSqlParameterSource): List<RecommendationPilotBreakdown> =
        jdbcTemplate.query(
            """
            select
                case
                    when extract(epoch from (:now - created_at)) < 1800 then 'under_30_minutes'
                    when extract(epoch from (:now - created_at)) < 7200 then '30_minutes_to_2_hours'
                    when extract(epoch from (:now - created_at)) < 86400 then '2_to_24_hours'
                    when extract(epoch from (:now - created_at)) < 604800 then '1_to_7_days'
                    else 'over_7_days'
                end as key,
                count(*) as count
            from reservation_task_recommendation
            $where
            group by key
            order by key asc
            """.trimIndent(),
            params
        ) { rs, _ -> RecommendationPilotBreakdown(rs.getString("key"), rs.getLong("count")) }

    private fun duplicatePreventionCount(filter: RecommendationPilotAnalyticsFilter): Long {
        val params = MapSqlParameterSource()
        val where = pilotRunWhere(filter, params)
        return jdbcTemplate.queryForObject(
            "select coalesce(sum(duplicates_prevented), 0) from recommendation_pilot_run $where",
            params,
            Long::class.java
        ) ?: 0L
    }

    private fun failureCount(filter: RecommendationPilotAnalyticsFilter): Long {
        val params = MapSqlParameterSource()
        val where = pilotRunWhere(filter, params)
        return jdbcTemplate.queryForObject(
            "select coalesce(sum(failed_count), 0) from recommendation_pilot_run $where",
            params,
            Long::class.java
        ) ?: 0L
    }

    private fun pilotRunWhere(filter: RecommendationPilotAnalyticsFilter, params: MapSqlParameterSource): String {
        val where = mutableListOf<String>()
        filter.generatedFrom?.let {
            where += "started_at >= :generatedFrom"
            params.addValue("generatedFrom", Timestamp.from(PersistenceInstant.toPersistencePrecision(it)))
        }
        filter.generatedTo?.let {
            where += "started_at < :generatedTo"
            params.addValue("generatedTo", Timestamp.from(PersistenceInstant.toPersistencePrecision(it)))
        }
        filter.providerId?.let {
            where += "provider_id = :providerId"
            params.addValue("providerId", it.value)
        }
        filter.pilotRunId?.let {
            where += "id = :pilotRunId"
            params.addValue("pilotRunId", it.value)
        }
        return if (where.isEmpty()) "" else "where ${where.joinToString(" and ")}"
    }

    private fun rate(numerator: Long, denominator: Long): Double =
        if (denominator <= 0) 0.0 else numerator.toDouble() / denominator.toDouble()

    private fun reviewTimeBand(seconds: Double): String =
        when {
            seconds <= 0.0 -> "none"
            seconds < 300.0 -> "under_5_minutes"
            seconds < 1800.0 -> "5_to_30_minutes"
            seconds < 7200.0 -> "30_minutes_to_2_hours"
            seconds < 86400.0 -> "2_to_24_hours"
            else -> "over_24_hours"
        }
}
