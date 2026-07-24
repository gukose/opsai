package com.hotelopai.pms.application

import java.time.Instant

enum class PmsHealthState {
    READY,
    DEGRADED,
    UNAVAILABLE,
    MISCONFIGURED,
    DISABLED
}

enum class PmsFailureCategory {
    NONE,
    AUTHENTICATION,
    PERMISSION,
    VALIDATION,
    NOT_FOUND,
    RATE_LIMIT,
    TIMEOUT,
    TRANSPORT,
    PROVIDER_UNAVAILABLE,
    MALFORMED_RESPONSE,
    CONFIGURATION,
    CIRCUIT_OPEN,
    UNKNOWN
}

enum class PmsCircuitState {
    CLOSED,
    OPEN,
    HALF_OPEN
}

data class PmsRetryPolicySummary(
    val maxAttempts: Int,
    val initialBackoffMillis: Long,
    val maxBackoffMillis: Long,
    val maxRateLimitDelayMillis: Long
)

data class PmsProviderHealth(
    val providerId: String,
    val state: PmsHealthState,
    val configured: Boolean,
    val enabled: Boolean,
    val checkedAt: Instant,
    val lastSuccessfulCheck: Instant? = null,
    val lastFailedCheck: Instant? = null,
    val failureCategory: PmsFailureCategory = PmsFailureCategory.NONE,
    val capabilities: PmsCapabilities,
    val circuitState: PmsCircuitState = PmsCircuitState.CLOSED,
    val retryPolicy: PmsRetryPolicySummary? = null
)
