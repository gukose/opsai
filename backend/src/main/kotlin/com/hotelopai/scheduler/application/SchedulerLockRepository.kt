package com.hotelopai.scheduler.application

import java.time.Duration
import java.time.Instant

interface SchedulerLockRepository {
    fun tryAcquire(jobName: String, ownerId: String, lockTimeout: Duration, now: Instant): Boolean

    fun renew(jobName: String, ownerId: String, newLockedUntil: Instant, now: Instant): Boolean

    fun release(jobName: String, ownerId: String, now: Instant)
}
