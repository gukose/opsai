package com.hotelopai.pms.application

import java.time.Instant

enum class PmsRolloutReadinessState {
    NOT_CONFIGURED,
    DISABLED,
    BLOCKED,
    DEGRADED,
    READY_FOR_SANDBOX,
    READY_FOR_PRODUCTION_REVIEW
}

data class PmsProviderSummary(
    val providerId: String,
    val displayName: String,
    val active: Boolean,
    val enabled: Boolean,
    val configured: Boolean,
    val healthState: PmsHealthState,
    val circuitState: PmsCircuitState,
    val capabilities: PmsCapabilities
)

data class PmsRolloutReadiness(
    val providerId: String,
    val state: PmsRolloutReadinessState,
    val productionApproved: Boolean,
    val activeProfiles: List<String>,
    val blockingReasons: List<String>,
    val health: PmsProviderHealth,
    val capabilities: PmsCapabilities
)

data class PmsCredentialRefreshResult(
    val providerId: String,
    val refreshedAt: Instant,
    val success: Boolean,
    val failureCategory: PmsFailureCategory = PmsFailureCategory.NONE,
    val message: String? = null
)

enum class PmsOperationsAuditAction {
    HEALTH_CHECK_REQUESTED,
    CREDENTIAL_REFRESH_REQUESTED,
    ROLLOUT_READINESS_INSPECTED,
    PROVIDER_ACTIVATION_REJECTED
}

data class PmsOperationsAuditEvent(
    val actorUserId: String?,
    val providerId: String,
    val action: PmsOperationsAuditAction,
    val outcome: String,
    val occurredAt: Instant,
    val failureCategory: PmsFailureCategory? = null
)

interface PmsOperationsAuditSink {
    fun record(event: PmsOperationsAuditEvent)
}
