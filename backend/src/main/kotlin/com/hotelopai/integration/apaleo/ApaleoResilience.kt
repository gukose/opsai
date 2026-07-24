package com.hotelopai.integration.apaleo

import com.hotelopai.pms.application.PmsCircuitState
import com.hotelopai.pms.application.PmsRetryPolicySummary
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.min

data class ApaleoRetryPolicy(
    val maxAttempts: Int,
    val initialBackoff: Duration,
    val maxBackoff: Duration,
    val maxRateLimitDelay: Duration
) {
    fun summary(): PmsRetryPolicySummary =
        PmsRetryPolicySummary(
            maxAttempts = maxAttempts,
            initialBackoffMillis = initialBackoff.toMillis(),
            maxBackoffMillis = maxBackoff.toMillis(),
            maxRateLimitDelayMillis = maxRateLimitDelay.toMillis()
        )

    fun delayForAttempt(attempt: Int): Duration {
        val exponent = (attempt - 1).coerceAtLeast(0).coerceAtMost(30)
        val multiplier = 1L shl exponent
        return Duration.ofMillis(min(maxBackoff.toMillis(), initialBackoff.toMillis() * multiplier))
    }
}

data class ApaleoCircuitBreakerPolicy(
    val failureThreshold: Int,
    val openDuration: Duration,
    val halfOpenMaxAttempts: Int
)

data class ApaleoResilienceSettings(
    val retry: ApaleoRetryPolicy,
    val circuitBreaker: ApaleoCircuitBreakerPolicy,
    val healthCheckTimeout: Duration,
    val tokenExpirySafetyWindow: Duration
) {
    companion object {
        fun from(settings: Map<String, String>): ApaleoResilienceSettings =
            ApaleoResilienceSettings(
                retry = ApaleoRetryPolicy(
                    maxAttempts = settings["retry-max-attempts"]?.toIntOrNull()?.coerceAtLeast(1) ?: 2,
                    initialBackoff = parseDuration(settings["retry-initial-backoff"], Duration.ofMillis(100)),
                    maxBackoff = parseDuration(settings["retry-max-backoff"], Duration.ofSeconds(1)),
                    maxRateLimitDelay = parseDuration(settings["max-rate-limit-delay"], Duration.ofSeconds(2))
                ),
                circuitBreaker = ApaleoCircuitBreakerPolicy(
                    failureThreshold = settings["circuit-failure-threshold"]?.toIntOrNull()?.coerceAtLeast(1) ?: 3,
                    openDuration = parseDuration(settings["circuit-open-duration"], Duration.ofSeconds(30)),
                    halfOpenMaxAttempts = settings["circuit-half-open-max-attempts"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1
                ),
                healthCheckTimeout = parseDuration(settings["health-check-timeout"], Duration.ofSeconds(2)),
                tokenExpirySafetyWindow = parseDuration(settings["token-expiry-safety-window"], Duration.ofSeconds(60))
            )

        private fun parseDuration(value: String?, defaultValue: Duration): Duration =
            value?.let { runCatching { Duration.parse(it) }.getOrNull() } ?: defaultValue
    }
}

data class ApaleoCachedToken(
    val value: String,
    val expiresAt: Instant
)

class ApaleoOAuthTokenCache(
    private val clock: Clock = Clock.systemUTC()
) {
    @Volatile
    private var cached: ApaleoCachedToken? = null

    fun getOrAcquire(
        safetyWindow: Duration,
        acquire: () -> ApaleoCachedToken
    ): String =
        synchronized(this) {
            val token = cached
            if (token != null && clock.instant().isBefore(token.expiresAt.minus(safetyWindow))) {
                token.value
            } else {
                acquire().also { cached = it }.value
            }
        }

    fun invalidate() {
        synchronized(this) {
            cached = null
        }
    }
}

fun interface ApaleoSleeper {
    fun sleep(duration: Duration)
}

class ThreadApaleoSleeper : ApaleoSleeper {
    override fun sleep(duration: Duration) {
        if (!duration.isZero && !duration.isNegative) {
            Thread.sleep(duration.toMillis())
        }
    }
}

class ApaleoCircuitBreaker(
    private val providerId: String,
    private val observability: com.hotelopai.observability.OperationalObservability,
    private val clock: Clock = Clock.systemUTC()
) {
    private val stateRef = AtomicReference(PmsCircuitState.CLOSED)
    private val failureCount = AtomicInteger(0)
    private val halfOpenAttempts = AtomicInteger(0)
    @Volatile
    private var openedAt: Instant? = null
    @Volatile
    private var lastFailureCategory: String = "none"

    fun state(policy: ApaleoCircuitBreakerPolicy): PmsCircuitState {
        val state = stateRef.get()
        if (state == PmsCircuitState.OPEN && canProbe(policy)) {
            transitionTo(PmsCircuitState.HALF_OPEN, "open_duration_elapsed")
        }
        return stateRef.get()
    }

    fun beforeCall(operation: String, policy: ApaleoCircuitBreakerPolicy) {
        when (state(policy)) {
            PmsCircuitState.OPEN -> {
                observability.incrementCounter(
                    "pms_provider_circuit_rejections_total",
                    "provider" to providerId,
                    "operation" to operation,
                    "outcome" to "rejected",
                    "status" to "circuit_open"
                )
                throw ApaleoCircuitOpenException("PMS provider '$providerId' circuit is open.")
            }
            PmsCircuitState.HALF_OPEN -> {
                if (halfOpenAttempts.incrementAndGet() > policy.halfOpenMaxAttempts) {
                    throw ApaleoCircuitOpenException("PMS provider '$providerId' circuit is half-open and probe limit is reached.")
                }
            }
            PmsCircuitState.CLOSED -> Unit
        }
    }

    fun onSuccess() {
        failureCount.set(0)
        halfOpenAttempts.set(0)
        openedAt = null
        lastFailureCategory = "none"
        if (stateRef.get() != PmsCircuitState.CLOSED) {
            transitionTo(PmsCircuitState.CLOSED, "success")
        }
    }

    fun onTransientFailure(category: String, policy: ApaleoCircuitBreakerPolicy) {
        lastFailureCategory = category
        if (stateRef.get() == PmsCircuitState.HALF_OPEN) {
            open("half_open_failure")
            return
        }
        if (failureCount.incrementAndGet() >= policy.failureThreshold) {
            open("failure_threshold")
        }
    }

    fun onPermanentFailure(category: String) {
        lastFailureCategory = category
    }

    fun lastSafeFailureCategory(): String = lastFailureCategory

    private fun canProbe(policy: ApaleoCircuitBreakerPolicy): Boolean {
        val opened = openedAt ?: return false
        return !clock.instant().isBefore(opened.plus(policy.openDuration))
    }

    private fun open(reason: String) {
        openedAt = clock.instant()
        halfOpenAttempts.set(0)
        transitionTo(PmsCircuitState.OPEN, reason)
    }

    private fun transitionTo(newState: PmsCircuitState, reason: String) {
        val oldState = stateRef.getAndSet(newState)
        if (oldState == newState) {
            return
        }
        observability.incrementCounter(
            "pms_provider_circuit_transitions_total",
            "provider" to providerId,
            "operation" to "circuit",
            "outcome" to newState.name.lowercase(),
            "status" to reason
        )
        logger.warn(
            "event=pms_circuit operation=transition provider={} from={} to={} reasonCode={}",
            providerId,
            oldState,
            newState,
            reason
        )
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ApaleoCircuitBreaker::class.java)
    }
}

class ApaleoCircuitOpenException(
    message: String
) : RuntimeException(message)
