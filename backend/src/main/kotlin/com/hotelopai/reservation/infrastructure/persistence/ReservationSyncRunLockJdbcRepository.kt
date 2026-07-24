package com.hotelopai.reservation.infrastructure.persistence

import com.hotelopai.reservation.application.ReservationSyncRunId
import com.hotelopai.reservation.application.ReservationSyncRunLockRepository
import com.hotelopai.reservation.application.ReservationSyncRunLockResult
import com.hotelopai.reservation.domain.DateRange
import com.hotelopai.shared.kernel.PersistenceInstant
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.sql.Timestamp
import java.time.Instant

@Repository
@Transactional
class ReservationSyncRunLockJdbcRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate
) : ReservationSyncRunLockRepository {
    override fun acquire(
        providerId: String,
        propertyScopeHash: String,
        dateRange: DateRange,
        runId: ReservationSyncRunId,
        lockedUntil: Instant,
        now: Instant
    ): ReservationSyncRunLockResult {
        jdbcTemplate.query(
            "select pg_advisory_xact_lock(hashtext(:providerId), hashtext(:propertyScopeHash))",
            mapOf("providerId" to providerId, "propertyScopeHash" to propertyScopeHash),
        ) { _, _ -> Unit }
        jdbcTemplate.update(
            "delete from reservation_sync_run_lock where locked_until <= :now",
            mapOf("now" to now.toTimestamp())
        )
        val conflict = jdbcTemplate.query(
            """
            select run_id
            from reservation_sync_run_lock
            where provider_id = :providerId
              and property_scope_hash = :propertyScopeHash
              and requested_start_date < :requestedEnd
              and requested_end_date > :requestedStart
              and locked_until > :now
            order by created_at asc
            limit 1
            """.trimIndent(),
            mapOf(
                "providerId" to providerId,
                "propertyScopeHash" to propertyScopeHash,
                "requestedStart" to dateRange.arrival,
                "requestedEnd" to dateRange.departure,
                "now" to now.toTimestamp()
            )
        ) { rs, _ -> ReservationSyncRunId(rs.getObject("run_id", java.util.UUID::class.java)) }.firstOrNull()

        if (conflict != null) {
            return ReservationSyncRunLockResult.Rejected(conflict)
        }

        jdbcTemplate.update(
            """
            insert into reservation_sync_run_lock (
                id, provider_id, property_scope_hash, requested_start_date,
                requested_end_date, run_id, locked_until, created_at
            ) values (
                :id, :providerId, :propertyScopeHash, :requestedStart,
                :requestedEnd, :runId, :lockedUntil, :createdAt
            )
            """.trimIndent(),
            mapOf(
                "id" to com.hotelopai.shared.kernel.UuidV7Generator.generate(),
                "providerId" to providerId,
                "propertyScopeHash" to propertyScopeHash,
                "requestedStart" to dateRange.arrival,
                "requestedEnd" to dateRange.departure,
                "runId" to runId.value,
                "lockedUntil" to PersistenceInstant.toPersistencePrecision(lockedUntil).toTimestamp(),
                "createdAt" to PersistenceInstant.toPersistencePrecision(now).toTimestamp()
            )
        )
        return ReservationSyncRunLockResult.Acquired
    }

    override fun release(runId: ReservationSyncRunId) {
        jdbcTemplate.update(
            "delete from reservation_sync_run_lock where run_id = :runId",
            mapOf("runId" to runId.value)
        )
    }

    private fun Instant.toTimestamp(): Timestamp = Timestamp.from(this)
}
