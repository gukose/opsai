package com.hotelopai.scheduler.infrastructure.persistence

import com.hotelopai.scheduler.application.SchedulerLockRepository
import com.hotelopai.support.PostgresIntegrationTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.test.context.ActiveProfiles
import java.time.Duration
import java.time.Instant

@SpringBootTest
@ActiveProfiles("test")
class JdbcSchedulerLockRepositoryIntegrationTest : PostgresIntegrationTestSupport() {
    @Autowired
    private lateinit var lockRepository: SchedulerLockRepository

    @Autowired
    private lateinit var jdbcTemplate: NamedParameterJdbcTemplate

    @Test
    fun `lock can be acquired once and then reacquired only after expiration`() {
        val jobName = "scheduler-lock-${System.nanoTime()}"
        val now = Instant.parse("2026-07-17T10:00:00Z")

        assertThat(lockRepository.tryAcquire(jobName, "owner-a", Duration.ofMinutes(5), now)).isTrue()
        assertThat(lockRepository.tryAcquire(jobName, "owner-b", Duration.ofMinutes(5), now.plus(Duration.ofMinutes(1)))).isFalse()
        assertThat(lockRepository.tryAcquire(jobName, "owner-b", Duration.ofMinutes(5), now.plus(Duration.ofMinutes(6)))).isTrue()
    }

    @Test
    fun `release only releases the current owner lock`() {
        val jobName = "scheduler-release-${System.nanoTime()}"
        val now = Instant.parse("2026-07-17T11:00:00Z")

        assertThat(lockRepository.tryAcquire(jobName, "owner-a", Duration.ofMinutes(5), now)).isTrue()
        lockRepository.release(jobName, "owner-b", now.plus(Duration.ofMinutes(1)))
        assertThat(lockRepository.tryAcquire(jobName, "owner-c", Duration.ofMinutes(5), now.plus(Duration.ofMinutes(2)))).isFalse()

        lockRepository.release(jobName, "owner-a", now.plus(Duration.ofMinutes(3)))
        assertThat(lockRepository.tryAcquire(jobName, "owner-c", Duration.ofMinutes(5), now.plus(Duration.ofMinutes(4)))).isTrue()
    }

    @Test
    fun `owner can renew active lease without changing owner`() {
        val jobName = "scheduler-renew-${System.nanoTime()}"
        val now = Instant.parse("2026-07-17T12:00:00Z")

        assertThat(lockRepository.tryAcquire(jobName, "owner-a", Duration.ofMinutes(5), now)).isTrue()
        assertThat(
            lockRepository.renew(
                jobName = jobName,
                ownerId = "owner-a",
                newLockedUntil = now.plus(Duration.ofMinutes(8)),
                now = now.plus(Duration.ofMinutes(2))
            )
        ).isTrue()

        val lock = findLock(jobName)
        assertThat(lock.lockedBy).isEqualTo("owner-a")
        assertThat(lock.lockedUntil).isEqualTo(Instant.parse("2026-07-17T12:08:00Z"))
    }

    @Test
    fun `non owner missing row and expired owner cannot renew`() {
        val jobName = "scheduler-renew-denied-${System.nanoTime()}"
        val now = Instant.parse("2026-07-17T13:00:00Z")

        assertThat(lockRepository.renew("missing-$jobName", "owner-a", now.plus(Duration.ofMinutes(5)), now)).isFalse()
        assertThat(lockRepository.tryAcquire(jobName, "owner-a", Duration.ofMinutes(5), now)).isTrue()
        assertThat(lockRepository.renew(jobName, "owner-b", now.plus(Duration.ofMinutes(8)), now.plus(Duration.ofMinutes(1)))).isFalse()
        assertThat(lockRepository.renew(jobName, "owner-a", now.plus(Duration.ofMinutes(12)), now.plus(Duration.ofMinutes(6)))).isFalse()
    }

    @Test
    fun `expired owner cannot overwrite a newly acquired owner or release that lock`() {
        val jobName = "scheduler-transfer-${System.nanoTime()}"
        val now = Instant.parse("2026-07-17T14:00:00Z")

        assertThat(lockRepository.tryAcquire(jobName, "owner-a", Duration.ofMinutes(5), now)).isTrue()
        assertThat(lockRepository.tryAcquire(jobName, "owner-b", Duration.ofMinutes(5), now.plus(Duration.ofMinutes(6)))).isTrue()

        assertThat(lockRepository.renew(jobName, "owner-a", now.plus(Duration.ofMinutes(12)), now.plus(Duration.ofMinutes(7)))).isFalse()
        lockRepository.release(jobName, "owner-a", now.plus(Duration.ofMinutes(7)))

        val lock = findLock(jobName)
        assertThat(lock.lockedBy).isEqualTo("owner-b")
        assertThat(lock.lockedUntil).isEqualTo(Instant.parse("2026-07-17T14:11:00Z"))
    }

    @Test
    fun `successful renewal prevents another owner acquiring until renewed expiry`() {
        val jobName = "scheduler-renew-protects-${System.nanoTime()}"
        val now = Instant.parse("2026-07-17T15:00:00Z")

        assertThat(lockRepository.tryAcquire(jobName, "owner-a", Duration.ofMinutes(5), now)).isTrue()
        assertThat(lockRepository.renew(jobName, "owner-a", now.plus(Duration.ofMinutes(10)), now.plus(Duration.ofMinutes(4)))).isTrue()

        assertThat(lockRepository.tryAcquire(jobName, "owner-b", Duration.ofMinutes(5), now.plus(Duration.ofMinutes(6)))).isFalse()
        assertThat(lockRepository.tryAcquire(jobName, "owner-b", Duration.ofMinutes(5), now.plus(Duration.ofMinutes(11)))).isTrue()
    }

    @Test
    fun `two concurrent renewals by the same owner are ownership safe`() {
        val jobName = "scheduler-concurrent-renew-${System.nanoTime()}"
        val now = Instant.parse("2026-07-17T16:00:00Z")

        assertThat(lockRepository.tryAcquire(jobName, "owner-a", Duration.ofMinutes(5), now)).isTrue()
        assertThat(lockRepository.renew(jobName, "owner-a", now.plus(Duration.ofMinutes(7)), now.plus(Duration.ofMinutes(1)))).isTrue()
        assertThat(lockRepository.renew(jobName, "owner-a", now.plus(Duration.ofMinutes(8)), now.plus(Duration.ofMinutes(1)))).isTrue()

        val lock = findLock(jobName)
        assertThat(lock.lockedBy).isEqualTo("owner-a")
        assertThat(lock.lockedUntil).isEqualTo(Instant.parse("2026-07-17T16:08:00Z"))
    }

    private fun findLock(jobName: String): SchedulerLockRow =
        jdbcTemplate.query(
            """
            select job_name, locked_until, locked_by
            from scheduler_lock
            where job_name = :jobName
            """.trimIndent(),
            mapOf("jobName" to jobName)
        ) { rs, _ ->
            SchedulerLockRow(
                jobName = rs.getString("job_name"),
                lockedUntil = rs.getTimestamp("locked_until").toInstant(),
                lockedBy = rs.getString("locked_by")
            )
        }.single()

    private data class SchedulerLockRow(
        val jobName: String,
        val lockedUntil: Instant,
        val lockedBy: String
    )
}
