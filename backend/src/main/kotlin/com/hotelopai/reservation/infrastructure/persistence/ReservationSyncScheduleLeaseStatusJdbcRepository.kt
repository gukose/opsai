package com.hotelopai.reservation.infrastructure.persistence

import com.hotelopai.reservation.application.ReservationSyncScheduleLeaseState
import com.hotelopai.reservation.application.ReservationSyncScheduleLeaseStatusRepository
import com.hotelopai.shared.kernel.PersistenceInstant
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Repository
class ReservationSyncScheduleLeaseStatusJdbcRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate
) : ReservationSyncScheduleLeaseStatusRepository {
    @Transactional(readOnly = true)
    override fun state(jobName: String, now: Instant): ReservationSyncScheduleLeaseState {
        val lockedUntil = jdbcTemplate.query(
            "select locked_until from scheduler_lock where job_name = :jobName",
            mapOf("jobName" to jobName)
        ) { rs, _ -> rs.getTimestamp("locked_until").toInstant() }.firstOrNull()
        val persistedNow = PersistenceInstant.toPersistencePrecision(now)
        return if (lockedUntil != null && lockedUntil.isAfter(persistedNow)) {
            ReservationSyncScheduleLeaseState.HELD
        } else {
            ReservationSyncScheduleLeaseState.AVAILABLE
        }
    }
}
