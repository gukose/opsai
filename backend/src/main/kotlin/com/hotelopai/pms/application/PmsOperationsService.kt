package com.hotelopai.pms.application

import com.hotelopai.pms.domain.PmsProviderId
import org.springframework.stereotype.Service
import java.time.Clock

@Service
class PmsOperationsService(
    private val registry: PmsProviderRegistry,
    private val auditSink: PmsOperationsAuditSink,
    private val clock: Clock
) {
    fun listProviders(): List<PmsProviderSummary> =
        registry.registeredProviders().map { provider ->
            val config = registry.providerConfig(provider.id.value)
            val readiness = provider.readiness(config)
            val health = provider.health(config)
            PmsProviderSummary(
                providerId = provider.id.value,
                displayName = config?.displayName?.takeIf { it.isNotBlank() } ?: provider.displayName,
                active = provider.id.value == registry.activeProviderId(),
                enabled = readiness.enabled,
                configured = readiness.configured,
                healthState = health.state,
                circuitState = health.circuitState,
                capabilities = provider.capabilities
            )
        }

    fun diagnostics(providerId: String): PmsProviderDiagnostic =
        registry.providerDiagnostic(providerId)

    fun runHealthCheck(providerId: String, actorUserId: String?): PmsProviderHealth {
        val provider = registry.provider(PmsProviderId(providerId))
        val health = provider.health(registry.providerConfig(providerId))
        auditSink.record(
            PmsOperationsAuditEvent(
                actorUserId = actorUserId,
                providerId = providerId,
                action = PmsOperationsAuditAction.HEALTH_CHECK_REQUESTED,
                outcome = health.state.name.lowercase(),
                occurredAt = clock.instant(),
                failureCategory = health.failureCategory
            )
        )
        return health
    }

    fun refreshCredentials(providerId: String, actorUserId: String?): PmsCredentialRefreshResult {
        val provider = registry.provider(PmsProviderId(providerId))
        val result = provider.refreshCredentials(registry.providerConfig(providerId))
        auditSink.record(
            PmsOperationsAuditEvent(
                actorUserId = actorUserId,
                providerId = providerId,
                action = PmsOperationsAuditAction.CREDENTIAL_REFRESH_REQUESTED,
                outcome = if (result.success) "success" else "failure",
                occurredAt = clock.instant(),
                failureCategory = result.failureCategory
            )
        )
        return result
    }

    fun activeRolloutReadiness(actorUserId: String?): PmsRolloutReadiness =
        rolloutReadiness(registry.activeProviderId(), actorUserId)

    fun rolloutReadiness(providerId: String, actorUserId: String?): PmsRolloutReadiness {
        val provider = registry.provider(PmsProviderId(providerId))
        val config = registry.providerConfig(providerId)
        val readiness = provider.readiness(config)
        val health = provider.health(config)
        val activeProfiles = registry.activeProfiles()
        val reasons = blockingReasons(provider, config, readiness, health, activeProfiles)
        val state = when {
            config == null -> PmsRolloutReadinessState.NOT_CONFIGURED
            !readiness.enabled -> PmsRolloutReadinessState.DISABLED
            reasons.isNotEmpty() -> PmsRolloutReadinessState.BLOCKED
            health.state == PmsHealthState.READY && provider.id.value != "internal-demo" &&
                config.productionApproved -> PmsRolloutReadinessState.READY_FOR_PRODUCTION_REVIEW
            health.state == PmsHealthState.READY -> PmsRolloutReadinessState.READY_FOR_SANDBOX
            else -> PmsRolloutReadinessState.DEGRADED
        }
        val rollout = PmsRolloutReadiness(
            providerId = providerId,
            state = state,
            productionApproved = config?.productionApproved ?: false,
            activeProfiles = activeProfiles,
            blockingReasons = reasons,
            health = health,
            capabilities = provider.capabilities
        )
        auditSink.record(
            PmsOperationsAuditEvent(
                actorUserId = actorUserId,
                providerId = providerId,
                action = PmsOperationsAuditAction.ROLLOUT_READINESS_INSPECTED,
                outcome = state.name.lowercase(),
                occurredAt = clock.instant(),
                failureCategory = health.failureCategory
            )
        )
        return rollout
    }

    private fun blockingReasons(
        provider: PmsProvider,
        config: PmsConfiguredProviderProperties?,
        readiness: PmsProviderReadiness,
        health: PmsProviderHealth,
        activeProfiles: List<String>
    ): List<String> = buildList {
        if (config == null) {
            add("Provider configuration is missing.")
            return@buildList
        }
        if (!readiness.configured) {
            add(readiness.message ?: "Provider configuration is incomplete.")
        }
        if (provider.id.value != "internal-demo" && config.hotelPropertyIdentifier.isNullOrBlank()) {
            add("External PMS provider requires a hotel or property identifier before rollout.")
        }
        if (config.allowedProfiles.isNotEmpty() && activeProfiles.none { it in config.allowedProfiles }) {
            add("Active environment is not in the provider allowlist.")
        }
        if (activeProfiles.any { it == "prod" || it == "production" } &&
            provider.id.value != "internal-demo" &&
            !config.productionApproved
        ) {
            add("External PMS provider requires explicit production approval.")
        }
        if (health.circuitState == PmsCircuitState.OPEN) {
            add("Provider circuit is open.")
        }
        if (health.state == PmsHealthState.MISCONFIGURED) {
            add("Provider health is misconfigured.")
        }
    }
}
