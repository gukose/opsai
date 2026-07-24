package com.hotelopai.scheduler.application

import com.hotelopai.observability.OperationalObservability
import com.hotelopai.scheduler.config.SchedulerProperties
import com.hotelopai.shared.kernel.PersistenceInstant
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Phaser
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.UUID

@Component
@EnableConfigurationProperties(SchedulerProperties::class)
class DistributedScheduledJobRunner(
    private val lockRepository: SchedulerLockRepository,
    private val leaseRenewalScheduler: LeaseRenewalScheduler,
    private val properties: SchedulerProperties,
    private val clock: Clock,
    private val observability: OperationalObservability = OperationalObservability.noop()
) {
    private val runningJobs = ConcurrentHashMap<String, AtomicBoolean>()
    private val activeExecutions = AtomicInteger(0)
    private val shuttingDown = AtomicBoolean(false)
    private val activePhaser = Phaser(1)
    private val ownerId = "${properties.instanceId}-${UUID.randomUUID()}"

    init {
        if (properties.leaseRenewalEnabled) {
            properties.validatedRenewalInterval(properties.normalizedDefaultLockTimeout())
        }
    }

    fun runSingleton(jobName: String, lockTimeout: Duration? = null, action: () -> Unit): Boolean {
        if (!properties.enabled) {
            record(jobName, "skipped", "scheduler_disabled")
            return false
        }
        val localRunning = runningJobs.computeIfAbsent(jobName) { AtomicBoolean(false) }
        if (!localRunning.compareAndSet(false, true)) {
            record(jobName, "skipped", "overlap")
            logger.warn("event=scheduler_job operation=run outcome=skipped jobName={} reasonCode=overlap", jobName)
            return false
        }

        val timeout = lockTimeout ?: properties.normalizedDefaultLockTimeout()
        val timer = observability.startTimer()
        var outcome = "failure"
        var acquired = false
        var leaseRenewal: ActiveLeaseRenewal? = null
        activePhaser.register()
        if (shuttingDown.get()) {
            activePhaser.arriveAndDeregister()
            localRunning.set(false)
            record(jobName, "skipped", "shutdown")
            logger.warn("event=scheduler_job operation=run outcome=skipped jobName={} reasonCode=shutdown", jobName)
            return false
        }
        activeExecutions.incrementAndGet()
        setActiveGauge()
        try {
            acquired = lockRepository.tryAcquire(jobName, ownerId, timeout, PersistenceInstant.now(clock))
            if (!acquired) {
                outcome = "skipped"
                record(jobName, "skipped", "lock_held")
                logger.warn("event=scheduler_job operation=run outcome=skipped jobName={} reasonCode=lock_held", jobName)
                return false
            }
            leaseRenewal = startLeaseRenewal(jobName, ownerId, timeout)

            record(jobName, "started", "none")
            logger.info("event=scheduler_job operation=run outcome=started jobName={} reasonCode=none", jobName)
            action()
            outcome = "success"
            record(jobName, "success", "none")
            logger.info("event=scheduler_job operation=run outcome=success jobName={} reasonCode=none", jobName)
            return true
        } catch (exception: Exception) {
            outcome = "failure"
            record(jobName, "failure", "unexpected_failure")
            logger.error(
                "event=scheduler_job operation=run outcome=failure jobName={} reasonCode=unexpected_failure",
                jobName,
                exception
            )
            return false
        } finally {
            leaseRenewal?.stop()
            if (leaseRenewal != null) {
                observability.setGauge("hotelopai.scheduler.lease.renewal.active", 0, "job" to jobName)
            }
            if (acquired) {
                lockRepository.release(jobName, ownerId, PersistenceInstant.now(clock))
            }
            localRunning.set(false)
            activeExecutions.decrementAndGet()
            setActiveGauge()
            activePhaser.arriveAndDeregister()
            observability.stopTimer(
                timer,
                "hotelopai.scheduler.job.duration",
                "job" to jobName,
                "outcome" to outcome
            )
            if (leaseRenewal?.leaseLost() == true) {
                recordLeaseRenewal(jobName, "ownership_lost", "renewal_failed")
            }
        }
    }

    @PreDestroy
    fun stopAcceptingWork() {
        shuttingDown.set(true)
        val timeout = properties.normalizedShutdownTimeout()
        val phase = activePhaser.arrive()
        try {
            activePhaser.awaitAdvanceInterruptibly(phase, timeout.toMillis().coerceAtLeast(1L), java.util.concurrent.TimeUnit.MILLISECONDS)
        } catch (_: TimeoutException) {
            logger.warn("event=scheduler_shutdown operation=shutdown outcome=timeout reasonCode=active_jobs_remaining")
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            logger.warn("event=scheduler_shutdown operation=shutdown outcome=interrupted reasonCode=active_jobs_remaining")
        } finally {
            leaseRenewalScheduler.shutdown()
        }
    }

    private fun startLeaseRenewal(jobName: String, ownerId: String, lockTimeout: Duration): ActiveLeaseRenewal? {
        if (!properties.leaseRenewalEnabled) return null
        val interval = properties.validatedRenewalInterval(lockTimeout)
        val active = ActiveLeaseRenewal(jobName)
        observability.setGauge("hotelopai.scheduler.lease.renewal.active", 1, "job" to jobName)
        val handle = leaseRenewalScheduler.schedule(interval) {
            if (!active.isRunning()) return@schedule
            recordLeaseRenewal(jobName, "attempt", "none")
            try {
                val now = PersistenceInstant.now(clock)
                val renewed = lockRepository.renew(
                    jobName = jobName,
                    ownerId = ownerId,
                    newLockedUntil = PersistenceInstant.toPersistencePrecision(now.plus(lockTimeout)),
                    now = now
                )
                if (renewed) {
                    recordLeaseRenewal(jobName, "success", "none")
                    logger.debug("event=scheduler_lease_renewal operation=renew outcome=success jobName={} reasonCode=none", jobName)
                } else {
                    active.markLeaseLost()
                    recordLeaseRenewal(jobName, "failure", "ownership_lost")
                    logger.error(
                        "event=scheduler_lease_renewal operation=renew outcome=failure jobName={} reasonCode=ownership_lost",
                        jobName
                    )
                }
            } catch (exception: Exception) {
                active.markLeaseLost()
                recordLeaseRenewal(jobName, "failure", "unexpected_failure")
                logger.error(
                    "event=scheduler_lease_renewal operation=renew outcome=failure jobName={} reasonCode=unexpected_failure",
                    jobName,
                    exception
                )
            }
        }
        active.attach(handle)
        logger.debug("event=scheduler_lease_renewal operation=start outcome=success jobName={} reasonCode=none", jobName)
        return active
    }

    private fun record(jobName: String, outcome: String, reasonCode: String) {
        observability.incrementCounter(
            "hotelopai.scheduler.job.total",
            "job" to jobName,
            "outcome" to outcome,
            "reason_code" to reasonCode
        )
    }

    private fun recordLeaseRenewal(jobName: String, outcome: String, reasonCode: String) {
        observability.incrementCounter(
            "hotelopai.scheduler.lease.renewal.total",
            "job" to jobName,
            "outcome" to outcome,
            "reason_code" to reasonCode
        )
    }

    private fun setActiveGauge() {
        observability.setGauge(
            "hotelopai.scheduler.job.active",
            activeExecutions.get().toLong(),
            "status" to "active"
        )
    }

    companion object {
        private val logger = LoggerFactory.getLogger(DistributedScheduledJobRunner::class.java)
    }

    private class ActiveLeaseRenewal(
        private val jobName: String
    ) {
        private val running = AtomicBoolean(true)
        private val leaseLost = AtomicBoolean(false)
        private var handle: LeaseRenewalHandle? = null

        fun attach(handle: LeaseRenewalHandle) {
            this.handle = handle
            if (!running.get()) {
                handle.cancel()
            }
        }

        fun isRunning(): Boolean = running.get()

        fun markLeaseLost() {
            leaseLost.set(true)
            stop()
        }

        fun leaseLost(): Boolean = leaseLost.get()

        fun stop() {
            if (running.getAndSet(false)) {
                handle?.cancel()
                logger.debug("event=scheduler_lease_renewal operation=stop outcome=success jobName={} reasonCode=none", jobName)
            }
        }
    }
}
