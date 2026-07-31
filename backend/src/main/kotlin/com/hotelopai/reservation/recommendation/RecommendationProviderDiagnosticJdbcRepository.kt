package com.hotelopai.reservation.recommendation

import com.hotelopai.shared.kernel.PersistenceInstant
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import kotlin.math.ceil

@Repository
@Transactional
class RecommendationProviderDiagnosticJdbcRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate
) : RecommendationProviderDiagnosticRepository {
    override fun save(diagnostic: RecommendationProviderDiagnostic): RecommendationProviderDiagnostic {
        val normalized = diagnostic.normalized()
        jdbcTemplate.update(
            """
            insert into recommendation_provider_diagnostic (
                id, provider_id, diagnostic_type, trigger_type, started_at,
                completed_at, outcome, failure_category, latency_band, retry_count,
                response_validation_outcome, prompt_version, model_identifier,
                environment_class, endpoint_classification, created_at
            ) values (
                :id, :providerId, :diagnosticType, :triggerType, :startedAt,
                :completedAt, :outcome, :failureCategory, :latencyBand, :retryCount,
                :responseValidationOutcome, :promptVersion, :modelIdentifier,
                :environmentClass, :endpointClassification, :createdAt
            )
            on conflict (id) do update set
                completed_at = :completedAt,
                outcome = :outcome,
                failure_category = :failureCategory,
                latency_band = :latencyBand,
                retry_count = :retryCount,
                response_validation_outcome = :responseValidationOutcome
            """.trimIndent(),
            normalized.toParams()
        )
        return requireNotNull(find(normalized.id))
    }

    @Transactional(readOnly = true)
    override fun find(id: RecommendationProviderDiagnosticId): RecommendationProviderDiagnostic? =
        jdbcTemplate.query(
            "select * from recommendation_provider_diagnostic where id = :id",
            mapOf("id" to id.value),
            ::mapDiagnostic
        ).firstOrNull()

    @Transactional(readOnly = true)
    override fun find(filter: RecommendationProviderDiagnosticFilter): RecommendationProviderDiagnosticPage {
        val page = filter.page.coerceAtLeast(0)
        val size = filter.size.coerceIn(1, 100)
        val params = MapSqlParameterSource().addValue("limit", size).addValue("offset", page * size)
        val where = mutableListOf<String>()
        filter.providerId?.let {
            where += "provider_id = :providerId"
            params.addValue("providerId", it.value)
        }
        filter.outcome?.let {
            where += "outcome = :outcome"
            params.addValue("outcome", it.name)
        }
        val whereSql = if (where.isEmpty()) "" else "where ${where.joinToString(" and ")}"
        val total = jdbcTemplate.queryForObject(
            "select count(*) from recommendation_provider_diagnostic $whereSql",
            params,
            Long::class.java
        ) ?: 0L
        val content = jdbcTemplate.query(
            """
            select *
            from recommendation_provider_diagnostic
            $whereSql
            order by started_at desc, id desc
            limit :limit offset :offset
            """.trimIndent(),
            params,
            ::mapDiagnostic
        )
        return RecommendationProviderDiagnosticPage(
            content = content,
            page = page,
            size = size,
            totalElements = total,
            totalPages = if (total == 0L) 0 else ceil(total.toDouble() / size.toDouble()).toInt()
        )
    }

    @Transactional(readOnly = true)
    override fun latest(providerId: RecommendationProviderId): RecommendationProviderDiagnostic? =
        jdbcTemplate.query(
            """
            select *
            from recommendation_provider_diagnostic
            where provider_id = :providerId
            order by started_at desc, id desc
            limit 1
            """.trimIndent(),
            mapOf("providerId" to providerId.value),
            ::mapDiagnostic
        ).firstOrNull()

    @Transactional(readOnly = true)
    override fun latestSuccessful(providerId: RecommendationProviderId): RecommendationProviderDiagnostic? =
        jdbcTemplate.query(
            """
            select *
            from recommendation_provider_diagnostic
            where provider_id = :providerId
              and outcome = 'SUCCEEDED'
            order by started_at desc, id desc
            limit 1
            """.trimIndent(),
            mapOf("providerId" to providerId.value),
            ::mapDiagnostic
        ).firstOrNull()

    override fun cleanupCompleted(olderThan: Instant, limit: Int): Int =
        jdbcTemplate.update(
            """
            delete from recommendation_provider_diagnostic diagnostic
            where diagnostic.id in (
                select candidate.id
                from recommendation_provider_diagnostic candidate
                where candidate.completed_at is not null
                  and candidate.completed_at < :olderThan
                  and candidate.id not in (
                    select distinct on (provider_id) id
                    from recommendation_provider_diagnostic
                    order by provider_id, started_at desc, id desc
                  )
                order by candidate.completed_at asc, candidate.id asc
                limit :limit
            )
            """.trimIndent(),
            mapOf(
                "olderThan" to Timestamp.from(PersistenceInstant.toPersistencePrecision(olderThan)),
                "limit" to limit.coerceIn(1, 1_000)
            )
        )

    private fun mapDiagnostic(rs: ResultSet, @Suppress("UNUSED_PARAMETER") rowNum: Int): RecommendationProviderDiagnostic =
        RecommendationProviderDiagnostic(
            id = RecommendationProviderDiagnosticId(rs.getObject("id", java.util.UUID::class.java)),
            providerId = RecommendationProviderId(rs.getString("provider_id")),
            diagnosticType = RecommendationProviderDiagnosticType.valueOf(rs.getString("diagnostic_type")),
            triggerType = RecommendationProviderDiagnosticTrigger.valueOf(rs.getString("trigger_type")),
            startedAt = rs.getTimestamp("started_at").toInstant(),
            completedAt = rs.getTimestamp("completed_at")?.toInstant(),
            outcome = RecommendationProviderDiagnosticOutcome.valueOf(rs.getString("outcome")),
            failureCategory = rs.getString("failure_category")?.let(RecommendationFailureCategory::valueOf),
            latencyBand = rs.getString("latency_band"),
            retryCount = rs.getInt("retry_count"),
            responseValidationOutcome = RecommendationResponseValidationOutcome.valueOf(rs.getString("response_validation_outcome")),
            promptVersion = rs.getString("prompt_version"),
            modelIdentifier = rs.getString("model_identifier"),
            environmentClass = rs.getString("environment_class"),
            endpointClassification = RecommendationEndpointClassification.valueOf(rs.getString("endpoint_classification")),
            createdAt = rs.getTimestamp("created_at").toInstant()
        )

    private fun RecommendationProviderDiagnostic.normalized(): RecommendationProviderDiagnostic {
        val started = PersistenceInstant.toPersistencePrecision(startedAt)
        return copy(
            startedAt = started,
            completedAt = completedAt?.let(PersistenceInstant::toPersistencePrecision),
            createdAt = PersistenceInstant.toPersistencePrecision(createdAt)
        )
    }

    private fun RecommendationProviderDiagnostic.toParams(): MapSqlParameterSource =
        MapSqlParameterSource()
            .addValue("id", id.value)
            .addValue("providerId", providerId.value)
            .addValue("diagnosticType", diagnosticType.name)
            .addValue("triggerType", triggerType.name)
            .addValue("startedAt", Timestamp.from(startedAt))
            .addValue("completedAt", completedAt?.let(Timestamp::from))
            .addValue("outcome", outcome.name)
            .addValue("failureCategory", failureCategory?.name)
            .addValue("latencyBand", latencyBand)
            .addValue("retryCount", retryCount)
            .addValue("responseValidationOutcome", responseValidationOutcome.name)
            .addValue("promptVersion", promptVersion)
            .addValue("modelIdentifier", modelIdentifier)
            .addValue("environmentClass", environmentClass)
            .addValue("endpointClassification", endpointClassification.name)
            .addValue("createdAt", Timestamp.from(createdAt))
}
