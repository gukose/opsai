package com.hotelopai.reservation.infrastructure.persistence

import com.hotelopai.pms.application.PmsFailureCategory
import com.hotelopai.reservation.application.ReservationSyncState
import com.hotelopai.reservation.application.ReservationSyncStateRepository
import com.hotelopai.reservation.application.ReservationSyncStatus
import com.hotelopai.reservation.domain.DateRange
import com.hotelopai.reservation.domain.PropertyId
import com.hotelopai.shared.kernel.PersistenceInstant
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate

@Repository
@Transactional
class ReservationSyncStateJdbcRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate
) : ReservationSyncStateRepository {
    @Transactional(readOnly = true)
    override fun find(providerId: String, propertyId: PropertyId): ReservationSyncState? =
        jdbcTemplate.query(
            """
            select *
            from reservation_sync_state
            where provider_id = :providerId
              and property_reference = :propertyReference
            """.trimIndent(),
            mapOf("providerId" to providerId, "propertyReference" to propertyId.value),
            ::mapState
        ).firstOrNull()

    override fun save(state: ReservationSyncState): ReservationSyncState {
        val existing = find(state.providerId, state.propertyId)
        val normalized = state.normalized().copy(version = existing?.version?.plus(1) ?: state.version)
        jdbcTemplate.update(
            """
            insert into reservation_sync_state (
                provider_id, property_reference, sync_status, sync_cursor,
                last_attempted_at, last_successful_at, last_failure_category,
                source_data_timestamp, window_start, window_end,
                fetched_count, created_count, updated_count, unchanged_count,
                stale_count, conflict_count, version, created_at, updated_at
            ) values (
                :providerId, :propertyReference, :syncStatus, :syncCursor,
                :lastAttemptedAt, :lastSuccessfulAt, :lastFailureCategory,
                :sourceDataTimestamp, :windowStart, :windowEnd,
                :fetchedCount, :createdCount, :updatedCount, :unchangedCount,
                :staleCount, :conflictCount, :version, :createdAt, :updatedAt
            )
            on conflict (provider_id, property_reference) do update set
                sync_status = excluded.sync_status,
                sync_cursor = excluded.sync_cursor,
                last_attempted_at = excluded.last_attempted_at,
                last_successful_at = excluded.last_successful_at,
                last_failure_category = excluded.last_failure_category,
                source_data_timestamp = excluded.source_data_timestamp,
                window_start = excluded.window_start,
                window_end = excluded.window_end,
                fetched_count = excluded.fetched_count,
                created_count = excluded.created_count,
                updated_count = excluded.updated_count,
                unchanged_count = excluded.unchanged_count,
                stale_count = excluded.stale_count,
                conflict_count = excluded.conflict_count,
                version = reservation_sync_state.version + 1,
                updated_at = excluded.updated_at
            """.trimIndent(),
            normalized.toParams()
        )
        return requireNotNull(find(state.providerId, state.propertyId))
    }

    private fun mapState(rs: ResultSet, @Suppress("UNUSED_PARAMETER") rowNum: Int): ReservationSyncState =
        ReservationSyncState(
            providerId = rs.getString("provider_id"),
            propertyId = PropertyId(rs.getString("property_reference")),
            status = ReservationSyncStatus.valueOf(rs.getString("sync_status")),
            syncCursor = rs.getString("sync_cursor"),
            lastAttemptedAt = rs.getTimestamp("last_attempted_at")?.toInstant(),
            lastSuccessfulAt = rs.getTimestamp("last_successful_at")?.toInstant(),
            lastFailureCategory = rs.getString("last_failure_category")?.let(PmsFailureCategory::valueOf),
            sourceDataTimestamp = rs.getTimestamp("source_data_timestamp")?.toInstant(),
            window = rs.getObject("window_start", LocalDate::class.java)?.let { start ->
                DateRange(start, rs.getObject("window_end", LocalDate::class.java))
            },
            fetchedCount = rs.getInt("fetched_count"),
            createdCount = rs.getInt("created_count"),
            updatedCount = rs.getInt("updated_count"),
            unchangedCount = rs.getInt("unchanged_count"),
            staleCount = rs.getInt("stale_count"),
            conflictCount = rs.getInt("conflict_count"),
            version = rs.getLong("version"),
            createdAt = rs.getTimestamp("created_at").toInstant(),
            updatedAt = rs.getTimestamp("updated_at").toInstant()
        )

    private fun ReservationSyncState.normalized(): ReservationSyncState =
        copy(
            lastAttemptedAt = PersistenceInstant.toPersistencePrecisionOrNull(lastAttemptedAt),
            lastSuccessfulAt = PersistenceInstant.toPersistencePrecisionOrNull(lastSuccessfulAt),
            sourceDataTimestamp = PersistenceInstant.toPersistencePrecisionOrNull(sourceDataTimestamp),
            createdAt = PersistenceInstant.toPersistencePrecision(createdAt),
            updatedAt = PersistenceInstant.toPersistencePrecision(updatedAt)
        )

    private fun ReservationSyncState.toParams(): MapSqlParameterSource =
        MapSqlParameterSource()
            .addValue("providerId", providerId)
            .addValue("propertyReference", propertyId.value)
            .addValue("syncStatus", status.name)
            .addValue("syncCursor", syncCursor)
            .addValue("lastAttemptedAt", lastAttemptedAt?.toTimestamp())
            .addValue("lastSuccessfulAt", lastSuccessfulAt?.toTimestamp())
            .addValue("lastFailureCategory", lastFailureCategory?.name)
            .addValue("sourceDataTimestamp", sourceDataTimestamp?.toTimestamp())
            .addValue("windowStart", window?.arrival)
            .addValue("windowEnd", window?.departure)
            .addValue("fetchedCount", fetchedCount)
            .addValue("createdCount", createdCount)
            .addValue("updatedCount", updatedCount)
            .addValue("unchangedCount", unchangedCount)
            .addValue("staleCount", staleCount)
            .addValue("conflictCount", conflictCount)
            .addValue("version", version)
            .addValue("createdAt", createdAt.toTimestamp())
            .addValue("updatedAt", updatedAt.toTimestamp())

    private fun Instant.toTimestamp(): Timestamp = Timestamp.from(this)
}
