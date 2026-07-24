package com.hotelopai.pms.application

import com.hotelopai.pms.domain.PmsProviderId
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.core.env.Environment
import org.springframework.stereotype.Service
import java.time.Clock

@Service
@EnableConfigurationProperties(PmsProviderProperties::class)
class PmsProviderRegistry(
    providers: List<PmsProvider>,
    private val properties: PmsProviderProperties,
    private val environment: Environment? = null,
    private val auditSink: PmsOperationsAuditSink? = null,
    private val clock: Clock = Clock.systemUTC()
) {
    private val providersById: Map<PmsProviderId, PmsProvider> =
        providers.associateBy { it.id }

    init {
        require(providersById.size == providers.size) {
            "Duplicate PMS provider ids are not allowed"
        }
        validateStartup()
    }

    fun activeProvider(): PmsProvider =
        provider(PmsProviderId(properties.activeProvider))

    fun activeProviderRequiring(capability: PmsCapability): PmsProvider {
        val provider = activeProvider()
        if (!provider.capabilities.supports(capability)) {
            throw UnsupportedPmsCapabilityException(provider.id.value, capability)
        }
        return provider
    }

    fun provider(providerId: PmsProviderId): PmsProvider =
        providersById[providerId]
            ?: throw IllegalStateException(
                "No PMS provider registered for '${providerId.value}'. Registered providers: ${registeredProviderIds().joinToString()}"
            )

    fun registeredProviderIds(): List<String> =
        providersById.keys.map { it.value }.sorted()

    fun activeProviderDiagnostic(): PmsProviderDiagnostic {
        val provider = activeProvider()
        return providerDiagnostic(provider.id.value)
    }

    fun providerDiagnostic(providerId: String): PmsProviderDiagnostic {
        val provider = provider(PmsProviderId(providerId))
        val config = properties.provider(provider.id.value)
        val readiness = provider.readiness(config)
        val health = provider.health(config)
        return (config ?: PmsConfiguredProviderProperties()).safeDiagnostic(
            providerId = provider.id.value,
            fallbackDisplayName = provider.displayName,
            capabilities = provider.capabilities,
            readiness = readiness,
            health = health
        )
    }

    fun providerConfig(providerId: String): PmsConfiguredProviderProperties? =
        properties.provider(providerId)

    fun activeProviderId(): String =
        properties.activeProvider

    fun activeProfiles(): List<String> =
        environment?.activeProfiles?.toList()?.sorted().orEmpty()

    fun registeredProviders(): List<PmsProvider> =
        providersById.values.sortedBy { it.id.value }

    private fun validateStartup() {
        val errors = mutableListOf<String>()
        val activeProviderId = PmsProviderId(properties.activeProvider)
        val activeProvider = providersById[activeProviderId]
        if (activeProvider == null) {
            errors += "No PMS provider registered for '${activeProviderId.value}'. Registered providers: ${registeredProviderIds().joinToString()}"
        } else {
            val config = properties.provider(activeProvider.id.value)
            val readiness = activeProvider.readiness(config)
            if (!readiness.enabled) {
                errors += "Active PMS provider '${activeProvider.id.value}' is disabled."
            }
            if (!readiness.configured) {
                errors += "Active PMS provider '${activeProvider.id.value}' is not fully configured: ${readiness.message ?: "configuration incomplete"}"
            }
            val productionErrors = validateProductionActivation(activeProvider, config)
            if (productionErrors.isNotEmpty()) {
                auditSink?.record(
                    PmsOperationsAuditEvent(
                        actorUserId = null,
                        providerId = activeProvider.id.value,
                        action = PmsOperationsAuditAction.PROVIDER_ACTIVATION_REJECTED,
                        outcome = "blocked",
                        occurredAt = clock.instant(),
                        failureCategory = PmsFailureCategory.CONFIGURATION
                    )
                )
            }
            errors += productionErrors
        }

        providersById.values.forEach { provider ->
            if (properties.provider(provider.id.value) == null) {
                errors += "PMS provider '${provider.id.value}' is registered but has no configuration entry."
            }
            errors += provider.capabilities.validateConsistency(provider.id.value)
        }

        if (errors.isNotEmpty()) {
            throw PmsProviderConfigurationException(errors.joinToString(separator = "\n"))
        }
    }

    private fun validateProductionActivation(
        provider: PmsProvider,
        config: PmsConfiguredProviderProperties?
    ): List<String> {
        if (provider.id.value == "internal-demo" || config == null || !isProductionProfile()) {
            return emptyList()
        }
        val errors = mutableListOf<String>()
        if (!config.productionApproved) {
            errors += "External PMS provider '${provider.id.value}' cannot be active in production without explicit production approval."
        }
        if (config.allowedProfiles.isNotEmpty() && activeProfiles().none { it in config.allowedProfiles }) {
            errors += "External PMS provider '${provider.id.value}' is not allowed for active profiles ${activeProfiles().joinToString()}."
        }
        return errors
    }

    private fun isProductionProfile(): Boolean =
        activeProfiles().any { it == "prod" || it == "production" }
}

class PmsProviderConfigurationException(
    message: String
) : RuntimeException(message)
