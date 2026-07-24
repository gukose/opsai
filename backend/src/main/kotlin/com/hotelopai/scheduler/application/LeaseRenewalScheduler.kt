package com.hotelopai.scheduler.application

import java.time.Duration

interface LeaseRenewalScheduler {
    fun schedule(interval: Duration, task: () -> Unit): LeaseRenewalHandle

    fun shutdown()
}

interface LeaseRenewalHandle {
    fun cancel()
}
