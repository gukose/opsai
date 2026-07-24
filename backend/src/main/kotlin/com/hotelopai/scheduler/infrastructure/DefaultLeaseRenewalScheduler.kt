package com.hotelopai.scheduler.infrastructure

import com.hotelopai.scheduler.application.LeaseRenewalHandle
import com.hotelopai.scheduler.application.LeaseRenewalScheduler
import jakarta.annotation.PreDestroy
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@Component
class DefaultLeaseRenewalScheduler : LeaseRenewalScheduler {
    private val threadNumber = AtomicInteger(0)
    private val executor: ScheduledExecutorService = Executors.newScheduledThreadPool(2) { runnable ->
        Thread(runnable, "hotelopai-lease-renewal-${threadNumber.incrementAndGet()}").apply {
            isDaemon = true
        }
    }

    override fun schedule(interval: Duration, task: () -> Unit): LeaseRenewalHandle {
        val intervalMillis = interval.toMillis().coerceAtLeast(1L)
        val future = executor.scheduleWithFixedDelay(
            task,
            intervalMillis,
            intervalMillis,
            TimeUnit.MILLISECONDS
        )
        return ScheduledFutureLeaseRenewalHandle(future)
    }

    @PreDestroy
    override fun shutdown() {
        executor.shutdownNow()
    }
}

private class ScheduledFutureLeaseRenewalHandle(
    private val future: ScheduledFuture<*>
) : LeaseRenewalHandle {
    override fun cancel() {
        future.cancel(false)
    }
}
