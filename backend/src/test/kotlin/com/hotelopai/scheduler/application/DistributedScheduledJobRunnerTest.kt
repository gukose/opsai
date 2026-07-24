package com.hotelopai.scheduler.application

import com.hotelopai.observability.OperationalObservability
import com.hotelopai.scheduler.config.SchedulerProperties
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class DistributedScheduledJobRunnerTest {
    private val clock = Clock.fixed(Instant.parse("2026-07-17T10:00:00Z"), ZoneOffset.UTC)

    @Test
    fun `overlapping local execution is skipped without duplicate work`() {
        val meterRegistry = SimpleMeterRegistry()
        val lockRepository = InMemoryLockRepository()
        val runner = runner(lockRepository, meterRegistry, ManualLeaseRenewalScheduler())
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val executions = AtomicInteger(0)
        val executor = Executors.newSingleThreadExecutor()

        val first = executor.submit<Boolean> {
            runner.runSingleton("overlap_job") {
                executions.incrementAndGet()
                started.countDown()
                assertThat(release.await(5, TimeUnit.SECONDS)).isTrue()
            }
        }
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue()

        val second = runner.runSingleton("overlap_job") {
            executions.incrementAndGet()
        }

        release.countDown()
        assertThat(first.get(5, TimeUnit.SECONDS)).isTrue()
        executor.shutdownNow()
        assertThat(second).isFalse()
        assertThat(executions.get()).isEqualTo(1)
        assertThat(counter(meterRegistry, "hotelopai.scheduler.job.total", "outcome" to "skipped", "reason_code" to "overlap"))
            .isEqualTo(1.0)
    }

    @Test
    fun `held distributed lock skips execution`() {
        val meterRegistry = SimpleMeterRegistry()
        val lockRepository = InMemoryLockRepository(acquire = false)
        val renewalScheduler = ManualLeaseRenewalScheduler()
        val runner = runner(lockRepository, meterRegistry, renewalScheduler)
        val executions = AtomicInteger(0)

        val result = runner.runSingleton("locked_job") {
            executions.incrementAndGet()
        }

        assertThat(result).isFalse()
        assertThat(executions.get()).isEqualTo(0)
        assertThat(renewalScheduler.activeCount()).isEqualTo(0)
        assertThat(counter(meterRegistry, "hotelopai.scheduler.job.total", "outcome" to "skipped", "reason_code" to "lock_held"))
            .isEqualTo(1.0)
    }

    @Test
    fun `unexpected failure is isolated and later executions can still run`() {
        val meterRegistry = SimpleMeterRegistry()
        val lockRepository = InMemoryLockRepository()
        val runner = runner(lockRepository, meterRegistry, ManualLeaseRenewalScheduler())

        val failed = runner.runSingleton("failure_job") {
            throw IllegalStateException("boom")
        }
        val succeeded = runner.runSingleton("failure_job") {
            Unit
        }

        assertThat(failed).isFalse()
        assertThat(succeeded).isTrue()
        assertThat(counter(meterRegistry, "hotelopai.scheduler.job.total", "outcome" to "failure")).isEqualTo(1.0)
        assertThat(counter(meterRegistry, "hotelopai.scheduler.job.total", "outcome" to "success")).isEqualTo(1.0)
    }

    @Test
    fun `shutdown stops accepting new work`() {
        val meterRegistry = SimpleMeterRegistry()
        val runner = runner(InMemoryLockRepository(), meterRegistry, ManualLeaseRenewalScheduler())

        runner.stopAcceptingWork()
        val result = runner.runSingleton("shutdown_job") {
            Unit
        }

        assertThat(result).isFalse()
        assertThat(counter(meterRegistry, "hotelopai.scheduler.job.total", "outcome" to "skipped", "reason_code" to "shutdown"))
            .isEqualTo(1.0)
    }

    @Test
    fun `renewal begins after acquisition and stops after completion`() {
        val meterRegistry = SimpleMeterRegistry()
        val lockRepository = InMemoryLockRepository()
        val renewalScheduler = ManualLeaseRenewalScheduler()
        val runner = runner(lockRepository, meterRegistry, renewalScheduler)
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()

        val result = executor.submit<Boolean> {
            runner.runSingleton("renew_job", Duration.ofMinutes(5)) {
                started.countDown()
                assertThat(release.await(5, TimeUnit.SECONDS)).isTrue()
            }
        }
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue()

        assertThat(renewalScheduler.activeCount()).isEqualTo(1)
        renewalScheduler.runAll()
        release.countDown()

        assertThat(result.get(5, TimeUnit.SECONDS)).isTrue()
        executor.shutdownNow()
        assertThat(lockRepository.renewCount.get()).isEqualTo(1)
        assertThat(lockRepository.releaseCount.get()).isEqualTo(1)
        assertThat(renewalScheduler.activeCount()).isEqualTo(0)
        assertThat(counter(meterRegistry, "hotelopai.scheduler.lease.renewal.total", "outcome" to "attempt")).isEqualTo(1.0)
        assertThat(counter(meterRegistry, "hotelopai.scheduler.lease.renewal.total", "outcome" to "success")).isEqualTo(1.0)
    }

    @Test
    fun `renewal stops after job failure`() {
        val lockRepository = InMemoryLockRepository()
        val renewalScheduler = ManualLeaseRenewalScheduler()
        val runner = runner(lockRepository, SimpleMeterRegistry(), renewalScheduler)

        val result = runner.runSingleton("failure_renew_job", Duration.ofMinutes(5)) {
            throw IllegalStateException("failed")
        }

        assertThat(result).isFalse()
        assertThat(renewalScheduler.activeCount()).isEqualTo(0)
        assertThat(lockRepository.releaseCount.get()).isEqualTo(1)
    }

    @Test
    fun `renewal failure records ownership loss and does not prevent job completion`() {
        val meterRegistry = SimpleMeterRegistry()
        val lockRepository = InMemoryLockRepository(renew = false)
        val renewalScheduler = ManualLeaseRenewalScheduler()
        val runner = runner(lockRepository, meterRegistry, renewalScheduler)
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()

        val result = executor.submit<Boolean> {
            runner.runSingleton("lost_lease_job", Duration.ofMinutes(5)) {
                started.countDown()
                assertThat(release.await(5, TimeUnit.SECONDS)).isTrue()
            }
        }
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue()

        renewalScheduler.runAll()
        release.countDown()

        assertThat(result.get(5, TimeUnit.SECONDS)).isTrue()
        executor.shutdownNow()
        assertThat(counter(meterRegistry, "hotelopai.scheduler.lease.renewal.total", "outcome" to "failure", "reason_code" to "ownership_lost"))
            .isEqualTo(1.0)
        assertThat(counter(meterRegistry, "hotelopai.scheduler.lease.renewal.total", "outcome" to "ownership_lost", "reason_code" to "renewal_failed"))
            .isEqualTo(1.0)
    }

    @Test
    fun `disabled renewal preserves execution behavior without scheduling renewal`() {
        val lockRepository = InMemoryLockRepository()
        val renewalScheduler = ManualLeaseRenewalScheduler()
        val runner = DistributedScheduledJobRunner(
            lockRepository = lockRepository,
            leaseRenewalScheduler = renewalScheduler,
            properties = SchedulerProperties(instanceId = "test", leaseRenewalEnabled = false),
            clock = clock,
            observability = OperationalObservability(SimpleMeterRegistry())
        )

        val result = runner.runSingleton("no_renew_job") {
            Unit
        }

        assertThat(result).isTrue()
        assertThat(renewalScheduler.activeCount()).isEqualTo(0)
        assertThat(lockRepository.renewCount.get()).isEqualTo(0)
    }

    @Test
    fun `shutdown timeout stops further renewal`() {
        val lockRepository = InMemoryLockRepository()
        val renewalScheduler = ManualLeaseRenewalScheduler()
        val runner = runner(lockRepository, SimpleMeterRegistry(), renewalScheduler)
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)

        val job = executor.submit<Boolean> {
            runner.runSingleton("shutdown_timeout_job", Duration.ofMinutes(5)) {
                started.countDown()
                assertThat(release.await(5, TimeUnit.SECONDS)).isTrue()
            }
        }
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue()

        val shutdown = executor.submit {
            runner.stopAcceptingWork()
        }
        shutdown.get(5, TimeUnit.SECONDS)
        renewalScheduler.runAll()
        release.countDown()

        assertThat(job.get(5, TimeUnit.SECONDS)).isTrue()
        executor.shutdownNow()
        assertThat(lockRepository.renewCount.get()).isEqualTo(0)
        assertThat(renewalScheduler.activeCount()).isEqualTo(0)
    }

    @Test
    fun `two runners preserve singleton execution while active owner renews`() {
        val mutableClock = MutableClock(Instant.parse("2026-07-17T10:00:00Z"))
        val lockRepository = TimeAwareLockRepository()
        val renewalA = ManualLeaseRenewalScheduler()
        val renewalB = ManualLeaseRenewalScheduler()
        val runnerA = runner("instance-a", lockRepository, SimpleMeterRegistry(), renewalA, mutableClock)
        val runnerB = runner("instance-b", lockRepository, SimpleMeterRegistry(), renewalB, mutableClock)
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()

        val first = executor.submit<Boolean> {
            runnerA.runSingleton("cluster_job", Duration.ofMinutes(5)) {
                started.countDown()
                assertThat(release.await(5, TimeUnit.SECONDS)).isTrue()
            }
        }
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue()

        mutableClock.current = Instant.parse("2026-07-17T10:04:00Z")
        renewalA.runAll()
        mutableClock.current = Instant.parse("2026-07-17T10:06:00Z")
        val deniedWhileRenewed = runnerB.runSingleton("cluster_job", Duration.ofMinutes(5)) {
            error("second instance must not execute while renewed lease is active")
        }
        release.countDown()

        assertThat(first.get(5, TimeUnit.SECONDS)).isTrue()
        mutableClock.current = Instant.parse("2026-07-17T10:07:00Z")
        val acquiredAfterRelease = runnerB.runSingleton("cluster_job", Duration.ofMinutes(5)) {
            Unit
        }
        executor.shutdownNow()
        assertThat(deniedWhileRenewed).isFalse()
        assertThat(acquiredAfterRelease).isTrue()
    }

    @Test
    fun `stale owner cannot renew or release after another runner acquires expired lease`() {
        val mutableClock = MutableClock(Instant.parse("2026-07-17T11:00:00Z"))
        val lockRepository = TimeAwareLockRepository()

        assertThat(lockRepository.tryAcquire("crash_job", "owner-a", Duration.ofMinutes(5), mutableClock.instant())).isTrue()
        mutableClock.current = Instant.parse("2026-07-17T11:06:00Z")
        assertThat(lockRepository.tryAcquire("crash_job", "owner-b", Duration.ofMinutes(5), mutableClock.instant())).isTrue()
        assertThat(
            lockRepository.renew(
                jobName = "crash_job",
                ownerId = "owner-a",
                newLockedUntil = Instant.parse("2026-07-17T11:12:00Z"),
                now = Instant.parse("2026-07-17T11:07:00Z")
            )
        ).isFalse()

        lockRepository.release("crash_job", "owner-a", Instant.parse("2026-07-17T11:07:00Z"))

        assertThat(lockRepository.owner("crash_job")).isEqualTo("owner-b")
        assertThat(lockRepository.tryAcquire("crash_job", "owner-c", Duration.ofMinutes(5), Instant.parse("2026-07-17T11:08:00Z"))).isFalse()
    }

    private fun runner(
        lockRepository: SchedulerLockRepository,
        meterRegistry: SimpleMeterRegistry,
        renewalScheduler: LeaseRenewalScheduler
    ): DistributedScheduledJobRunner =
        runner("test", lockRepository, meterRegistry, renewalScheduler, clock)

    private fun runner(
        instanceId: String,
        lockRepository: SchedulerLockRepository,
        meterRegistry: SimpleMeterRegistry,
        renewalScheduler: LeaseRenewalScheduler,
        clock: Clock
    ): DistributedScheduledJobRunner =
        DistributedScheduledJobRunner(
            lockRepository = lockRepository,
            leaseRenewalScheduler = renewalScheduler,
            properties = SchedulerProperties(instanceId = instanceId, shutdownTimeout = Duration.ZERO),
            clock = clock,
            observability = OperationalObservability(meterRegistry)
        )

    private fun counter(
        meterRegistry: SimpleMeterRegistry,
        name: String,
        vararg tags: Pair<String, String>
    ): Double =
        meterRegistry.find(name)
            .tags(*tags.flatMap { listOf(it.first, it.second) }.toTypedArray())
            .counter()
            ?.count() ?: 0.0

    private class InMemoryLockRepository(
        private val acquire: Boolean = true,
        private val renew: Boolean = true
    ) : SchedulerLockRepository {
        val renewCount = AtomicInteger(0)
        val releaseCount = AtomicInteger(0)

        override fun tryAcquire(jobName: String, ownerId: String, lockTimeout: Duration, now: Instant): Boolean = acquire

        override fun renew(jobName: String, ownerId: String, newLockedUntil: Instant, now: Instant): Boolean {
            renewCount.incrementAndGet()
            return renew
        }

        override fun release(jobName: String, ownerId: String, now: Instant) {
            releaseCount.incrementAndGet()
        }
    }

    private class ManualLeaseRenewalScheduler : LeaseRenewalScheduler {
        private val handles = mutableListOf<ManualHandle>()
        private val shutdown = AtomicBoolean(false)

        override fun schedule(interval: Duration, task: () -> Unit): LeaseRenewalHandle {
            if (shutdown.get()) return ManualHandle {}
            val handle = ManualHandle(task)
            handles += handle
            return handle
        }

        override fun shutdown() {
            shutdown.set(true)
            handles.forEach(ManualHandle::cancel)
        }

        fun runAll() {
            handles.toList().forEach(ManualHandle::run)
        }

        fun activeCount(): Int = handles.count { it.active.get() }
    }

    private class ManualHandle(
        private val task: () -> Unit
    ) : LeaseRenewalHandle {
        val active = AtomicBoolean(true)

        fun run() {
            if (active.get()) {
                task()
            }
        }

        override fun cancel() {
            active.set(false)
        }
    }

    private class MutableClock(
        var current: Instant
    ) : Clock() {
        override fun getZone() = ZoneOffset.UTC

        override fun withZone(zone: java.time.ZoneId): Clock = this

        override fun instant(): Instant = current
    }

    private class TimeAwareLockRepository : SchedulerLockRepository {
        private val locks = ConcurrentHashMap<String, LockRow>()

        override fun tryAcquire(jobName: String, ownerId: String, lockTimeout: Duration, now: Instant): Boolean {
            val updated = locks.compute(jobName) { _, existing ->
                if (existing == null || !existing.lockedUntil.isAfter(now)) {
                    LockRow(ownerId = ownerId, lockedUntil = now.plus(lockTimeout))
                } else {
                    existing
                }
            }
            return updated?.ownerId == ownerId && updated.lockedUntil == now.plus(lockTimeout)
        }

        override fun renew(jobName: String, ownerId: String, newLockedUntil: Instant, now: Instant): Boolean {
            var renewed = false
            locks.computeIfPresent(jobName) { _, existing ->
                if (existing.ownerId == ownerId && existing.lockedUntil.isAfter(now)) {
                    renewed = true
                    existing.copy(lockedUntil = newLockedUntil)
                } else {
                    existing
                }
            }
            return renewed
        }

        override fun release(jobName: String, ownerId: String, now: Instant) {
            locks.computeIfPresent(jobName) { _, existing ->
                if (existing.ownerId == ownerId) existing.copy(lockedUntil = now) else existing
            }
        }

        fun owner(jobName: String): String? = locks[jobName]?.ownerId

        private data class LockRow(
            val ownerId: String,
            val lockedUntil: Instant
        )
    }
}
