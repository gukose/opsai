package com.hotelopai.reservation.recommendation

import com.hotelopai.shared.kernel.PersistenceInstant
import com.hotelopai.task.domain.TaskIntentType
import com.hotelopai.task.domain.TaskPriority
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
class ReservationTaskRecommendationJdbcRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate
) : ReservationTaskRecommendationRepository {
    override fun insert(recommendation: ReservationTaskRecommendation): ReservationTaskRecommendationInsertResult {
        val normalized = recommendation.normalized()
        val inserted = jdbcTemplate.update(
            """
            insert into reservation_task_recommendation (
                id, reservation_id, source, provider_name, model_identifier,
                prompt_version, context_schema_version, category, confidence, explanation_situation,
                explanation_rationale, explanation_signals, task_intent_type,
                task_title, task_description, task_priority, task_due_at,
                deduplication_key, status, reviewed_by, reviewed_at,
                decision_reason, decision_note, applied_task_id, pilot_run_id, attempt_count, next_attempt_at, failure_category,
                created_at, updated_at, expires_at, version
            ) values (
                :id, :reservationId, :source, :providerName, :modelIdentifier,
                :promptVersion, :contextSchemaVersion, :category, :confidence, :explanationSituation,
                :explanationRationale, :explanationSignals, :intentType,
                :title, :description, :priority, :dueAt,
                :deduplicationKey, :status, :reviewedBy, :reviewedAt,
                :decisionReason, :decisionNote, :appliedTaskId, :pilotRunId, :attemptCount, :nextAttemptAt, :failureCategory,
                :createdAt, :updatedAt, :expiresAt, :version
            )
            on conflict (deduplication_key) do nothing
            """.trimIndent(),
            normalized.toParams()
        )
        return if (inserted == 1) {
            ReservationTaskRecommendationInsertResult.Inserted(requireNotNull(find(normalized.id)))
        } else {
            ReservationTaskRecommendationInsertResult.Duplicate(requireNotNull(findByDeduplicationKey(normalized.deduplicationKey)))
        }
    }

    override fun save(recommendation: ReservationTaskRecommendation): ReservationTaskRecommendation {
        val existing = find(recommendation.id) ?: return when (val inserted = insert(recommendation)) {
            is ReservationTaskRecommendationInsertResult.Inserted -> inserted.recommendation
            is ReservationTaskRecommendationInsertResult.Duplicate -> inserted.existing
        }
        val normalized = recommendation.normalized().copy(version = existing.version + 1)
        jdbcTemplate.update(
            """
            update reservation_task_recommendation
            set status = :status,
                reviewed_by = :reviewedBy,
                reviewed_at = :reviewedAt,
                decision_reason = :decisionReason,
                decision_note = :decisionNote,
                applied_task_id = :appliedTaskId,
                attempt_count = :attemptCount,
                next_attempt_at = :nextAttemptAt,
                failure_category = :failureCategory,
                updated_at = :updatedAt,
                expires_at = :expiresAt,
                version = version + 1
            where id = :id
            """.trimIndent(),
            normalized.toParams()
        )
        return requireNotNull(find(recommendation.id))
    }

    @Transactional(readOnly = true)
    override fun find(id: RecommendationId): ReservationTaskRecommendation? =
        jdbcTemplate.query(
            "select * from reservation_task_recommendation where id = :id",
            mapOf("id" to id.value),
            ::mapRecommendation
        ).firstOrNull()

    private fun findByDeduplicationKey(deduplicationKey: String): ReservationTaskRecommendation? =
        jdbcTemplate.query(
            "select * from reservation_task_recommendation where deduplication_key = :deduplicationKey",
            mapOf("deduplicationKey" to deduplicationKey),
            ::mapRecommendation
        ).firstOrNull()

    @Transactional(readOnly = true)
    override fun find(filter: RecommendationFilter): RecommendationPage {
        val page = filter.page.coerceAtLeast(0)
        val size = filter.size.coerceIn(1, 100)
        val where = mutableListOf<String>()
        val params = MapSqlParameterSource().addValue("limit", size).addValue("offset", page * size)
        filter.status?.let {
            where += "status = :status"
            params.addValue("status", it.name)
        }
        filter.category?.let {
            where += "category = :category"
            params.addValue("category", it.name)
        }
        val whereSql = if (where.isEmpty()) "" else "where ${where.joinToString(" and ")}"
        val total = jdbcTemplate.queryForObject("select count(*) from reservation_task_recommendation $whereSql", params, Long::class.java) ?: 0L
        val content = jdbcTemplate.query(
            """
            select *
            from reservation_task_recommendation
            $whereSql
            order by created_at desc, id desc
            limit :limit offset :offset
            """.trimIndent(),
            params,
            ::mapRecommendation
        )
        return RecommendationPage(content, page, size, total, if (total == 0L) 0 else ceil(total.toDouble() / size.toDouble()).toInt())
    }

    @Transactional(readOnly = true)
    override fun findPilotReviewQueue(filter: RecommendationPilotReviewQueueFilter, now: Instant): RecommendationPage {
        val page = filter.page.coerceAtLeast(0)
        val size = filter.size.coerceIn(1, 100)
        val params = MapSqlParameterSource()
            .addValue("limit", size)
            .addValue("offset", page * size)
            .addValue("now", Timestamp.from(PersistenceInstant.toPersistencePrecision(now)))
        val where = mutableListOf("pilot_run_id is not null")
        filter.status?.let { where += "status = :status"; params.addValue("status", it.name) }
        filter.category?.let { where += "category = :category"; params.addValue("category", it.name) }
        filter.confidence?.let { where += "confidence = :confidence"; params.addValue("confidence", it.name) }
        filter.providerId?.let { where += "provider_name = :providerId"; params.addValue("providerId", it.value) }
        filter.modelIdentifier?.let { where += "model_identifier = :modelIdentifier"; params.addValue("modelIdentifier", it) }
        filter.pilotRunId?.let { where += "pilot_run_id = :pilotRunId"; params.addValue("pilotRunId", it.value) }
        filter.generatedFrom?.let { where += "created_at >= :generatedFrom"; params.addValue("generatedFrom", Timestamp.from(PersistenceInstant.toPersistencePrecision(it))) }
        filter.generatedTo?.let { where += "created_at < :generatedTo"; params.addValue("generatedTo", Timestamp.from(PersistenceInstant.toPersistencePrecision(it))) }
        filter.ageBand?.let {
            where += when (it) {
                "under_30_minutes" -> "extract(epoch from (:now - created_at)) < 1800"
                "30_minutes_to_2_hours" -> "extract(epoch from (:now - created_at)) >= 1800 and extract(epoch from (:now - created_at)) < 7200"
                "2_to_24_hours" -> "extract(epoch from (:now - created_at)) >= 7200 and extract(epoch from (:now - created_at)) < 86400"
                "1_to_7_days" -> "extract(epoch from (:now - created_at)) >= 86400 and extract(epoch from (:now - created_at)) < 604800"
                "over_7_days" -> "extract(epoch from (:now - created_at)) >= 604800"
                else -> "1 = 0"
            }
        }
        val whereSql = "where ${where.joinToString(" and ")}"
        val total = jdbcTemplate.queryForObject("select count(*) from reservation_task_recommendation $whereSql", params, Long::class.java) ?: 0L
        val content = jdbcTemplate.query(
            """
            select *
            from reservation_task_recommendation
            $whereSql
            order by created_at asc, id asc
            limit :limit offset :offset
            """.trimIndent(),
            params,
            ::mapRecommendation
        )
        return RecommendationPage(content, page, size, total, if (total == 0L) 0 else ceil(total.toDouble() / size.toDouble()).toInt())
    }

    override fun claimEligibleAutomationExecutions(now: Instant, batchSize: Int, createdAfter: Instant): List<RecommendationSourceExecution> =
        jdbcTemplate.query(
            """
            select execution.outbox_event_id,
                   execution.reservation_id,
                   execution.trigger_event_type,
                   execution.outcome,
                   execution.created_task_id is not null as task_created,
                   execution.created_at
            from reservation_task_automation_execution execution
            where execution.outcome in ('CREATED', 'ALREADY_EXISTS', 'NOT_APPLICABLE', 'SKIPPED')
              and execution.created_at >= :createdAfter
              and not exists (
                  select 1
                  from reservation_task_recommendation recommendation
                  where recommendation.reservation_id = execution.reservation_id
                    and recommendation.status in ('GENERATED', 'REVIEW_REQUIRED', 'APPROVED', 'APPLIED')
              )
            order by execution.created_at asc, execution.id asc
            limit :batchSize
            """.trimIndent(),
            mapOf(
                "batchSize" to batchSize.coerceIn(1, 100),
                "createdAfter" to Timestamp.from(PersistenceInstant.toPersistencePrecision(createdAfter))
            ),
            ::mapSourceExecution
        )

    override fun retry(id: RecommendationId, now: Instant): ReservationTaskRecommendation {
        val current = requireNotNull(find(id)) { "Reservation task recommendation not found." }
        return save(
            current.copy(
                status = RecommendationStatus.REVIEW_REQUIRED,
                nextAttemptAt = PersistenceInstant.toPersistencePrecision(now),
                failureCategory = null,
                updatedAt = PersistenceInstant.toPersistencePrecision(now)
            )
        )
    }

    override fun saveRun(run: RecommendationGenerationRun): RecommendationGenerationRun {
        val normalized = run.normalized()
        jdbcTemplate.update(
            """
            insert into reservation_task_recommendation_generation_run (
                id, trigger_type, provider_id, status, started_at, completed_at,
                candidates_selected, candidates_processed, recommendations_generated,
                duplicates_prevented, skipped_count, failed_count, failure_category,
                created_at, updated_at, version
            ) values (
                :id, :triggerType, :providerId, :status, :startedAt, :completedAt,
                :candidatesSelected, :candidatesProcessed, :recommendationsGenerated,
                :duplicatesPrevented, :skippedCount, :failedCount, :failureCategory,
                :createdAt, :updatedAt, :version
            )
            on conflict (id) do update set
                status = :status,
                completed_at = :completedAt,
                candidates_selected = :candidatesSelected,
                candidates_processed = :candidatesProcessed,
                recommendations_generated = :recommendationsGenerated,
                duplicates_prevented = :duplicatesPrevented,
                skipped_count = :skippedCount,
                failed_count = :failedCount,
                failure_category = :failureCategory,
                updated_at = :updatedAt,
                version = reservation_task_recommendation_generation_run.version + 1
            """.trimIndent(),
            normalized.toParams()
        )
        return requireNotNull(findRun(normalized.id))
    }

    @Transactional(readOnly = true)
    override fun findRun(id: RecommendationGenerationRunId): RecommendationGenerationRun? =
        jdbcTemplate.query(
            "select * from reservation_task_recommendation_generation_run where id = :id",
            mapOf("id" to id.value),
            ::mapRun
        ).firstOrNull()

    @Transactional(readOnly = true)
    override fun findRuns(filter: RecommendationGenerationRunFilter): RecommendationGenerationRunPage {
        val page = filter.page.coerceAtLeast(0)
        val size = filter.size.coerceIn(1, 100)
        val where = mutableListOf<String>()
        val params = MapSqlParameterSource().addValue("limit", size).addValue("offset", page * size)
        filter.status?.let {
            where += "status = :status"
            params.addValue("status", it.name)
        }
        filter.trigger?.let {
            where += "trigger_type = :trigger"
            params.addValue("trigger", it.name)
        }
        val whereSql = if (where.isEmpty()) "" else "where ${where.joinToString(" and ")}"
        val total = jdbcTemplate.queryForObject("select count(*) from reservation_task_recommendation_generation_run $whereSql", params, Long::class.java) ?: 0L
        val content = jdbcTemplate.query(
            """
            select *
            from reservation_task_recommendation_generation_run
            $whereSql
            order by started_at desc, id desc
            limit :limit offset :offset
            """.trimIndent(),
            params,
            ::mapRun
        )
        return RecommendationGenerationRunPage(content, page, size, total, if (total == 0L) 0 else ceil(total.toDouble() / size.toDouble()).toInt())
    }

    @Transactional(readOnly = true)
    override fun runCount(statuses: Set<RecommendationGenerationRunStatus>): Long {
        if (statuses.isEmpty()) return 0
        return jdbcTemplate.queryForObject(
            """
            select count(*)
            from reservation_task_recommendation_generation_run
            where status in (:statuses)
            """.trimIndent(),
            mapOf("statuses" to statuses.map { it.name }),
            Long::class.java
        ) ?: 0
    }

    override fun getOrCreateScheduleState(scheduleId: String, now: Instant): RecommendationScheduleState {
        findScheduleState(scheduleId)?.let { return it }
        val persistedNow = PersistenceInstant.toPersistencePrecision(now)
        jdbcTemplate.update(
            """
            insert into reservation_task_recommendation_schedule_state (
                schedule_id, paused, updated_at
            ) values (
                :scheduleId, false, :updatedAt
            )
            on conflict (schedule_id) do nothing
            """.trimIndent(),
            mapOf("scheduleId" to scheduleId, "updatedAt" to Timestamp.from(persistedNow))
        )
        return requireNotNull(findScheduleState(scheduleId))
    }

    override fun markSchedulePaused(scheduleId: String, now: Instant): RecommendationScheduleState {
        val persistedNow = PersistenceInstant.toPersistencePrecision(now)
        jdbcTemplate.update(
            """
            insert into reservation_task_recommendation_schedule_state (
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
        return requireNotNull(findScheduleState(scheduleId))
    }

    override fun markScheduleResumed(scheduleId: String, now: Instant): RecommendationScheduleState {
        val persistedNow = PersistenceInstant.toPersistencePrecision(now)
        jdbcTemplate.update(
            """
            insert into reservation_task_recommendation_schedule_state (
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
        return requireNotNull(findScheduleState(scheduleId))
    }

    override fun recordScheduleAttempt(
        scheduleId: String,
        run: RecommendationGenerationRun?,
        now: Instant,
        failureCategory: RecommendationFailureCategory?
    ): RecommendationScheduleState {
        val persistedNow = PersistenceInstant.toPersistencePrecision(now)
        val success = failureCategory == null && run?.status in setOf(
            RecommendationGenerationRunStatus.SUCCEEDED,
            RecommendationGenerationRunStatus.PARTIALLY_SUCCEEDED
        )
        jdbcTemplate.update(
            """
            insert into reservation_task_recommendation_schedule_state (
                schedule_id, paused, last_attempted_at, last_successful_at,
                last_processed_candidate_count, last_generated_recommendation_count,
                last_failure_category, updated_at
            ) values (
                :scheduleId, false, :attemptedAt, :successfulAt,
                :processedCount, :generatedCount, :failureCategory, :updatedAt
            )
            on conflict (schedule_id) do update set
                last_attempted_at = :attemptedAt,
                last_successful_at = coalesce(:successfulAt, reservation_task_recommendation_schedule_state.last_successful_at),
                last_processed_candidate_count = :processedCount,
                last_generated_recommendation_count = :generatedCount,
                last_failure_category = :failureCategory,
                updated_at = :updatedAt
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("scheduleId", scheduleId)
                .addValue("attemptedAt", Timestamp.from(persistedNow))
                .addValue("successfulAt", if (success) Timestamp.from(persistedNow) else null)
                .addValue("processedCount", run?.candidatesProcessed?.coerceAtLeast(0) ?: 0)
                .addValue("generatedCount", run?.recommendationsGenerated?.coerceAtLeast(0) ?: 0)
                .addValue("failureCategory", failureCategory?.name)
                .addValue("updatedAt", Timestamp.from(persistedNow))
        )
        return requireNotNull(findScheduleState(scheduleId))
    }

    @Transactional(readOnly = true)
    override fun eligibleCandidateBacklogCount(now: Instant, createdAfter: Instant): Long =
        jdbcTemplate.queryForObject(
            """
            select count(*)
            from reservation_task_automation_execution execution
            where execution.outcome in ('CREATED', 'ALREADY_EXISTS', 'NOT_APPLICABLE', 'SKIPPED')
              and execution.created_at >= :createdAfter
              and not exists (
                  select 1
                  from reservation_task_recommendation recommendation
                  where recommendation.reservation_id = execution.reservation_id
                    and recommendation.status in ('GENERATED', 'REVIEW_REQUIRED', 'APPROVED', 'APPLIED')
              )
            """.trimIndent(),
            mapOf("createdAfter" to Timestamp.from(PersistenceInstant.toPersistencePrecision(createdAfter))),
            Long::class.java
        ) ?: 0

    @Transactional(readOnly = true)
    override fun activeRecommendationCount(reservationId: UUID): Long =
        jdbcTemplate.queryForObject(
            """
            select count(*)
            from reservation_task_recommendation
            where reservation_id = :reservationId
              and status in ('GENERATED', 'REVIEW_REQUIRED', 'APPROVED')
            """.trimIndent(),
            mapOf("reservationId" to reservationId),
            Long::class.java
        ) ?: 0

    @Transactional(readOnly = true)
    override fun unresolvedAutomationFailureExists(reservationId: UUID): Boolean =
        (jdbcTemplate.queryForObject(
            """
            select count(*)
            from reservation_task_automation_execution
            where reservation_id = :reservationId
              and outcome in ('FAILED', 'DEAD_LETTER')
            """.trimIndent(),
            mapOf("reservationId" to reservationId),
            Long::class.java
        ) ?: 0) > 0

    override fun expireEligibleRecommendations(now: Instant, olderThan: Instant, limit: Int): Int =
        jdbcTemplate.update(
            """
            update reservation_task_recommendation
            set status = 'EXPIRED',
                updated_at = :now
            where id in (
                select id
                from reservation_task_recommendation
                where status in ('GENERATED', 'REVIEW_REQUIRED', 'APPROVED')
                  and created_at < :olderThan
                order by created_at asc, id asc
                limit :limit
            )
            """.trimIndent(),
            mapOf("now" to Timestamp.from(PersistenceInstant.toPersistencePrecision(now)), "olderThan" to Timestamp.from(PersistenceInstant.toPersistencePrecision(olderThan)), "limit" to limit.coerceIn(1, 1_000))
        )

    override fun expirePilotRecommendations(now: Instant, limit: Int): Int =
        jdbcTemplate.update(
            """
            update reservation_task_recommendation
            set status = 'EXPIRED',
                updated_at = :now
            where id in (
                select id
                from reservation_task_recommendation
                where pilot_run_id is not null
                  and status in ('GENERATED', 'REVIEW_REQUIRED', 'APPROVED')
                order by created_at asc, id asc
                limit :limit
            )
            """.trimIndent(),
            mapOf("now" to Timestamp.from(PersistenceInstant.toPersistencePrecision(now)), "limit" to limit.coerceIn(1, 1_000))
        )

    override fun cleanupTerminalRecords(
        runOlderThan: Instant,
        recommendationOlderThan: Instant,
        appliedOlderThan: Instant,
        limit: Int
    ): Int {
        val boundedLimit = limit.coerceIn(1, 1_000)
        val deletedRuns = jdbcTemplate.update(
            """
            delete from reservation_task_recommendation_generation_run
            where id in (
                select id
                from reservation_task_recommendation_generation_run
                where status in ('SUCCEEDED', 'PARTIALLY_SUCCEEDED', 'FAILED', 'REJECTED')
                  and completed_at is not null
                  and completed_at < :runOlderThan
                order by completed_at asc, id asc
                limit :limit
            )
            """.trimIndent(),
            mapOf(
                "runOlderThan" to Timestamp.from(PersistenceInstant.toPersistencePrecision(runOlderThan)),
                "limit" to boundedLimit
            )
        )
        val remaining = (boundedLimit - deletedRuns).coerceAtLeast(0)
        if (remaining == 0) return deletedRuns
        val deletedRecommendations = jdbcTemplate.update(
            """
            delete from reservation_task_recommendation
            where id in (
                select id
                from reservation_task_recommendation
                where (
                    status in ('REJECTED', 'EXPIRED', 'FAILED')
                    and updated_at < :recommendationOlderThan
                ) or (
                    status = 'APPLIED'
                    and updated_at < :appliedOlderThan
                )
                order by updated_at asc, id asc
                limit :limit
            )
            """.trimIndent(),
            mapOf(
                "recommendationOlderThan" to Timestamp.from(PersistenceInstant.toPersistencePrecision(recommendationOlderThan)),
                "appliedOlderThan" to Timestamp.from(PersistenceInstant.toPersistencePrecision(appliedOlderThan)),
                "limit" to remaining
            )
        )
        return deletedRuns + deletedRecommendations
    }

    private fun ReservationTaskRecommendation.normalized(): ReservationTaskRecommendation =
        copy(
            dueAt = PersistenceInstant.toPersistencePrecision(dueAt),
            reviewedAt = PersistenceInstant.toPersistencePrecisionOrNull(reviewedAt),
            nextAttemptAt = PersistenceInstant.toPersistencePrecisionOrNull(nextAttemptAt),
            createdAt = PersistenceInstant.toPersistencePrecision(createdAt),
            updatedAt = PersistenceInstant.toPersistencePrecision(updatedAt),
            expiresAt = PersistenceInstant.toPersistencePrecisionOrNull(expiresAt)
        )

    private fun ReservationTaskRecommendation.toParams(): MapSqlParameterSource =
        MapSqlParameterSource()
            .addValue("id", id.value)
            .addValue("reservationId", reservationId)
            .addValue("source", source.name)
            .addValue("providerName", providerName)
            .addValue("modelIdentifier", modelIdentifier)
            .addValue("promptVersion", promptVersion)
            .addValue("contextSchemaVersion", contextSchemaVersion)
            .addValue("category", category.name)
            .addValue("confidence", confidence.name)
            .addValue("explanationSituation", explanation.situation)
            .addValue("explanationRationale", explanation.rationale)
            .addValue("explanationSignals", explanation.supportingSignals.joinToString(","))
            .addValue("intentType", intentType.name)
            .addValue("title", title)
            .addValue("description", description)
            .addValue("priority", priority.name)
            .addValue("dueAt", Timestamp.from(dueAt))
            .addValue("deduplicationKey", deduplicationKey)
            .addValue("status", status.name)
            .addValue("reviewedBy", reviewedBy)
            .addValue("reviewedAt", reviewedAt?.let(Timestamp::from))
            .addValue("decisionReason", decisionReason?.name)
            .addValue("decisionNote", decisionNote)
            .addValue("appliedTaskId", appliedTaskId)
            .addValue("pilotRunId", pilotRunId?.value)
            .addValue("attemptCount", attemptCount)
            .addValue("nextAttemptAt", nextAttemptAt?.let(Timestamp::from))
            .addValue("failureCategory", failureCategory?.name)
            .addValue("createdAt", Timestamp.from(createdAt))
            .addValue("updatedAt", Timestamp.from(updatedAt))
            .addValue("expiresAt", expiresAt?.let(Timestamp::from))
            .addValue("version", version)

    private fun mapRecommendation(rs: ResultSet, @Suppress("UNUSED_PARAMETER") rowNum: Int): ReservationTaskRecommendation =
        ReservationTaskRecommendation(
            id = RecommendationId(rs.getObject("id", UUID::class.java)),
            reservationId = rs.getObject("reservation_id", UUID::class.java),
            source = RecommendationSource.valueOf(rs.getString("source")),
            providerName = rs.getString("provider_name"),
            modelIdentifier = rs.getString("model_identifier"),
            promptVersion = rs.getString("prompt_version"),
            contextSchemaVersion = runCatching { rs.getString("context_schema_version") }.getOrNull()
                ?: RECOMMENDATION_CONTEXT_SCHEMA_VERSION,
            category = RecommendationCategory.valueOf(rs.getString("category")),
            confidence = RecommendationConfidence.valueOf(rs.getString("confidence")),
            explanation = RecommendationExplanation(
                situation = rs.getString("explanation_situation"),
                rationale = rs.getString("explanation_rationale"),
                supportingSignals = rs.getString("explanation_signals").split(",").filter { it.isNotBlank() }
            ),
            intentType = TaskIntentType.valueOf(rs.getString("task_intent_type")),
            title = rs.getString("task_title"),
            description = rs.getString("task_description"),
            priority = TaskPriority.valueOf(rs.getString("task_priority")),
            dueAt = rs.getTimestamp("task_due_at").toInstant(),
            deduplicationKey = rs.getString("deduplication_key"),
            status = RecommendationStatus.valueOf(rs.getString("status")),
            reviewedBy = rs.getObject("reviewed_by", UUID::class.java),
            reviewedAt = rs.getTimestamp("reviewed_at")?.toInstant(),
            decisionReason = runCatching { rs.getString("decision_reason") }.getOrNull()?.let(RecommendationDecisionReason::valueOf),
            decisionNote = runCatching { rs.getString("decision_note") }.getOrNull(),
            appliedTaskId = rs.getObject("applied_task_id", UUID::class.java),
            pilotRunId = runCatching { rs.getObject("pilot_run_id", UUID::class.java) }.getOrNull()
                ?.let(::RecommendationPilotRunId),
            attemptCount = rs.getInt("attempt_count"),
            nextAttemptAt = rs.getTimestamp("next_attempt_at")?.toInstant(),
            failureCategory = rs.getString("failure_category")?.let(RecommendationFailureCategory::valueOf),
            createdAt = rs.getTimestamp("created_at").toInstant(),
            updatedAt = rs.getTimestamp("updated_at").toInstant(),
            expiresAt = rs.getTimestamp("expires_at")?.toInstant(),
            version = rs.getLong("version")
        )

    private fun RecommendationGenerationRun.normalized(): RecommendationGenerationRun =
        copy(
            startedAt = PersistenceInstant.toPersistencePrecision(startedAt),
            completedAt = PersistenceInstant.toPersistencePrecisionOrNull(completedAt),
            createdAt = PersistenceInstant.toPersistencePrecision(createdAt),
            updatedAt = PersistenceInstant.toPersistencePrecision(updatedAt)
        )

    private fun RecommendationGenerationRun.toParams(): MapSqlParameterSource =
        MapSqlParameterSource()
            .addValue("id", id.value)
            .addValue("triggerType", trigger.name)
            .addValue("providerId", providerId.value)
            .addValue("status", status.name)
            .addValue("startedAt", Timestamp.from(startedAt))
            .addValue("completedAt", completedAt?.let(Timestamp::from))
            .addValue("candidatesSelected", candidatesSelected)
            .addValue("candidatesProcessed", candidatesProcessed)
            .addValue("recommendationsGenerated", recommendationsGenerated)
            .addValue("duplicatesPrevented", duplicatesPrevented)
            .addValue("skippedCount", skippedCount)
            .addValue("failedCount", failedCount)
            .addValue("failureCategory", failureCategory?.name)
            .addValue("createdAt", Timestamp.from(createdAt))
            .addValue("updatedAt", Timestamp.from(updatedAt))
            .addValue("version", version)

    private fun mapRun(rs: ResultSet, @Suppress("UNUSED_PARAMETER") rowNum: Int): RecommendationGenerationRun =
        RecommendationGenerationRun(
            id = RecommendationGenerationRunId(rs.getObject("id", UUID::class.java)),
            trigger = RecommendationGenerationTrigger.valueOf(rs.getString("trigger_type")),
            providerId = RecommendationProviderId(rs.getString("provider_id")),
            status = RecommendationGenerationRunStatus.valueOf(rs.getString("status")),
            startedAt = rs.getTimestamp("started_at").toInstant(),
            completedAt = rs.getTimestamp("completed_at")?.toInstant(),
            candidatesSelected = rs.getInt("candidates_selected"),
            candidatesProcessed = rs.getInt("candidates_processed"),
            recommendationsGenerated = rs.getInt("recommendations_generated"),
            duplicatesPrevented = rs.getInt("duplicates_prevented"),
            skippedCount = rs.getInt("skipped_count"),
            failedCount = rs.getInt("failed_count"),
            failureCategory = rs.getString("failure_category")?.let(RecommendationFailureCategory::valueOf),
            createdAt = rs.getTimestamp("created_at").toInstant(),
            updatedAt = rs.getTimestamp("updated_at").toInstant(),
            version = rs.getLong("version")
        )

    private fun findScheduleState(scheduleId: String): RecommendationScheduleState? =
        jdbcTemplate.query(
            "select * from reservation_task_recommendation_schedule_state where schedule_id = :scheduleId",
            mapOf("scheduleId" to scheduleId),
            ::mapScheduleState
        ).firstOrNull()

    private fun mapScheduleState(rs: ResultSet, @Suppress("UNUSED_PARAMETER") rowNum: Int): RecommendationScheduleState =
        RecommendationScheduleState(
            scheduleId = rs.getString("schedule_id"),
            paused = rs.getBoolean("paused"),
            pausedAt = rs.getTimestamp("paused_at")?.toInstant(),
            resumedAt = rs.getTimestamp("resumed_at")?.toInstant(),
            lastAttemptedAt = rs.getTimestamp("last_attempted_at")?.toInstant(),
            lastSuccessfulAt = rs.getTimestamp("last_successful_at")?.toInstant(),
            lastProcessedCandidateCount = rs.getInt("last_processed_candidate_count"),
            lastGeneratedRecommendationCount = rs.getInt("last_generated_recommendation_count"),
            lastFailureCategory = rs.getString("last_failure_category")?.let(RecommendationFailureCategory::valueOf),
            updatedAt = rs.getTimestamp("updated_at").toInstant()
        )

    private fun mapSourceExecution(rs: ResultSet, @Suppress("UNUSED_PARAMETER") rowNum: Int): RecommendationSourceExecution =
        RecommendationSourceExecution(
            outboxEventId = rs.getObject("outbox_event_id", UUID::class.java),
            reservationId = rs.getObject("reservation_id", UUID::class.java),
            triggerEventType = rs.getString("trigger_event_type"),
            automationOutcome = rs.getString("outcome"),
            taskCreated = rs.getBoolean("task_created"),
            createdAt = rs.getTimestamp("created_at").toInstant()
        )
}
