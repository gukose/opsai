package com.hotelopai.support.failure

import java.util.EnumMap
import java.util.concurrent.atomic.AtomicInteger

class FailureInjection {
    private val failures = EnumMap<FailurePoint, AtomicInteger>(FailurePoint::class.java)

    fun failNext(point: FailurePoint, times: Int = 1) {
        require(times > 0) { "times must be positive" }
        failures.computeIfAbsent(point) { AtomicInteger(0) }.addAndGet(times)
    }

    fun maybeFail(point: FailurePoint) {
        val remaining = failures[point] ?: return
        while (true) {
            val current = remaining.get()
            if (current <= 0) return
            if (remaining.compareAndSet(current, current - 1)) {
                throw InjectedFailureException(point)
            }
        }
    }

    fun remaining(point: FailurePoint): Int = failures[point]?.get() ?: 0

    fun clear() {
        failures.clear()
    }
}

enum class FailurePoint {
    BEFORE_OUTBOX_PUBLISH,
    AFTER_OUTBOX_PUBLISH,
    OUTBOX_CLAIM,
    OUTBOX_COMPLETE,
    OUTBOX_RETRY,
    OUTBOX_CLEANUP,
    NOTIFICATION_SAVE,
    SCHEDULER_ACQUIRE,
    SCHEDULER_RENEW,
    SCHEDULER_RELEASE
}

class InjectedFailureException(
    val point: FailurePoint
) : RuntimeException("Injected deterministic failure at $point")
