package com.hotelopai.scheduler.infrastructure.persistence

import com.hotelopai.scheduler.application.SchedulerLockRepository
import com.hotelopai.shared.kernel.PersistenceInstant
import org.springframework.dao.DuplicateKeyException
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant

@Repository
class JdbcSchedulerLockRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate
) : SchedulerLockRepository {
    @Transactional
    override fun tryAcquire(jobName: String, ownerId: String, lockTimeout: Duration, now: Instant): Boolean {
        val persistedNow = PersistenceInstant.toPersistencePrecision(now)
        val lockedUntil = PersistenceInstant.toPersistencePrecision(persistedNow.plus(lockTimeout))
        val updated = jdbcTemplate.update(
            """
            update scheduler_lock
            set locked_until = :lockedUntil,
                locked_by = :ownerId,
                acquired_at = :now,
                updated_at = :now
            where job_name = :jobName
              and locked_until <= :now
            """.trimIndent(),
            mapOf(
                "jobName" to jobName,
                "ownerId" to ownerId.take(128),
                "lockedUntil" to Timestamp.from(lockedUntil),
                "now" to Timestamp.from(persistedNow)
            )
        )
        if (updated == 1) return true

        return try {
            jdbcTemplate.update(
                """
                insert into scheduler_lock (
                    job_name, locked_until, locked_by, acquired_at, updated_at
                ) values (
                    :jobName, :lockedUntil, :ownerId, :now, :now
                )
                """.trimIndent(),
                mapOf(
                    "jobName" to jobName,
                    "ownerId" to ownerId.take(128),
                    "lockedUntil" to Timestamp.from(lockedUntil),
                    "now" to Timestamp.from(persistedNow)
                )
            )
            true
        } catch (_: DuplicateKeyException) {
            false
        }
    }

    @Transactional
    override fun renew(jobName: String, ownerId: String, newLockedUntil: Instant, now: Instant): Boolean {
        val persistedNow = PersistenceInstant.toPersistencePrecision(now)
        val persistedLockedUntil = PersistenceInstant.toPersistencePrecision(newLockedUntil)
        val updated = jdbcTemplate.update(
            """
            update scheduler_lock
            set locked_until = :lockedUntil,
                updated_at = :now
            where job_name = :jobName
              and locked_by = :ownerId
              and locked_until > :now
            """.trimIndent(),
            mapOf(
                "jobName" to jobName,
                "ownerId" to ownerId.take(128),
                "lockedUntil" to Timestamp.from(persistedLockedUntil),
                "now" to Timestamp.from(persistedNow)
            )
        )
        return updated == 1
    }

    @Transactional
    override fun release(jobName: String, ownerId: String, now: Instant) {
        val persistedNow = PersistenceInstant.toPersistencePrecision(now)
        jdbcTemplate.update(
            """
            update scheduler_lock
            set locked_until = :now,
                updated_at = :now
            where job_name = :jobName
              and locked_by = :ownerId
            """.trimIndent(),
            mapOf(
                "jobName" to jobName,
                "ownerId" to ownerId.take(128),
                "now" to Timestamp.from(persistedNow)
            )
        )
    }
}
