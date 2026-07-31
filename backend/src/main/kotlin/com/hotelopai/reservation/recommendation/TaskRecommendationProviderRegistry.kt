package com.hotelopai.reservation.recommendation

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component
import java.net.URI

@Component
@EnableConfigurationProperties(ReservationTaskRecommendationProperties::class)
class TaskRecommendationProviderRegistry(
    providers: List<TaskRecommendationProvider>,
    private val properties: ReservationTaskRecommendationProperties,
    private val environment: Environment? = null
) {
    private val providersById: Map<String, TaskRecommendationProvider>

    init {
        val duplicates = providers.groupBy { it.id.value }.filterValues { it.size > 1 }.keys
        require(duplicates.isEmpty()) { "Duplicate task recommendation provider ids are not allowed." }
        providersById = providers.associateBy { it.id.value }
        validateExternalProviders()
        if (properties.enabled) {
            validateActiveProvider()
        }
        if (properties.schedule.enabled) {
            validateScheduleProvider()
        }
    }

    fun activeProvider(): TaskRecommendationProvider {
        validateActiveProvider()
        return providersById.getValue(properties.activeProvider)
    }

    fun provider(providerId: RecommendationProviderId): TaskRecommendationProvider? =
        providersById[providerId.value]

    fun summaries(): List<RecommendationProviderSummary> =
        providersById.values.sortedBy { it.id.value }.map { provider ->
            val configured = configuredProvider(provider.id)
            val profileAllowed = providerProfileAllowed(configured)
            val enabled = configured.enabled && profileAllowed
            val status = when {
                !enabled -> RecommendationProviderStatus.DISABLED
                provider.id.value == properties.activeProvider && missingRequiredCapabilities(provider).isNotEmpty() ->
                    RecommendationProviderStatus.MISCONFIGURED
                else -> RecommendationProviderStatus.ENABLED
            }
            val lifecycle = when (status) {
                RecommendationProviderStatus.ENABLED -> RecommendationProviderLifecycle.AVAILABLE
                RecommendationProviderStatus.DISABLED -> RecommendationProviderLifecycle.DISABLED
                RecommendationProviderStatus.MISCONFIGURED -> RecommendationProviderLifecycle.MISCONFIGURED
                RecommendationProviderStatus.UNAVAILABLE -> RecommendationProviderLifecycle.UNAVAILABLE
            }
            RecommendationProviderSummary(
                providerId = provider.id,
                displayName = configured.displayName.ifBlank { provider.displayName },
                providerType = provider.providerType,
                lifecycle = lifecycle,
                status = status,
                active = provider.id.value == properties.activeProvider,
                capabilities = provider.capabilities.toSortedSet(compareBy { it.name }),
                modelIdentifierPresent = provider.modelIdentifier != null || configured.modelIdentifier != null,
                activeModel = provider.modelIdentifier ?: configured.modelIdentifier,
                promptVersion = configured.promptVersion ?: provider.promptVersion,
                failureCategory = if (status == RecommendationProviderStatus.MISCONFIGURED) {
                    RecommendationFailureCategory.CAPABILITY_UNSUPPORTED
                } else {
                    null
                },
                averageResponseTimeBand = if (provider.providerType == RecommendationProviderType.EXTERNAL) "not_recorded" else "local",
                retryStatistics = if (provider.providerType == RecommendationProviderType.EXTERNAL) "configured" else "not_applicable"
            )
        }

    fun validateActiveProvider() {
        val provider = providersById[properties.activeProvider]
            ?: throw ReservationTaskRecommendationRejectedException("Reservation task recommendation active provider is not registered.")
        val configured = configuredProvider(provider.id)
        if (!configured.enabled || !providerProfileAllowed(configured)) {
            throw ReservationTaskRecommendationRejectedException("Reservation task recommendation active provider is disabled.")
        }
        val missingCapabilities = missingRequiredCapabilities(provider)
        if (missingCapabilities.isNotEmpty()) {
            throw ReservationTaskRecommendationRejectedException("Reservation task recommendation active provider is missing required capabilities.")
        }
        if (provider.providerType == RecommendationProviderType.EXTERNAL) {
            validateExternalProviderActivation(provider.id.value)
        }
    }

    fun validateScheduleProvider() {
        if (!properties.enabled) {
            throw ReservationTaskRecommendationRejectedException("Reservation task recommendations must be enabled before schedule generation is enabled.")
        }
        validateActiveProvider()
    }

    private fun missingRequiredCapabilities(provider: TaskRecommendationProvider): Set<RecommendationProviderCapability> =
        REQUIRED_GENERATION_CAPABILITIES - provider.capabilities

    private fun configuredProvider(providerId: RecommendationProviderId): RecommendationConfiguredProviderProperties =
        when (providerId.value) {
            INTERNAL_DEMO_PROVIDER_ID -> properties.providers.internalDemo
            OPENAI_PROVIDER_ID -> properties.providers.openai.toConfiguredProvider()
            else -> RecommendationConfiguredProviderProperties(enabled = false, displayName = providerId.value)
        }

    private fun providerProfileAllowed(configured: RecommendationConfiguredProviderProperties): Boolean {
        if (configured.allowedProfiles.isEmpty()) return true
        val activeProfiles = environment?.activeProfiles?.toSet().orEmpty()
        return activeProfiles.any { it in configured.allowedProfiles }
    }

    private fun validateExternalProviders() {
        if (properties.providers.openai.enabled) {
            validateExternalProviderActivation(OPENAI_PROVIDER_ID)
        }
    }

    private fun validateExternalProviderActivation(providerId: String) {
        if (providerId != OPENAI_PROVIDER_ID) return
        val openAi = properties.providers.openai
        if (!openAi.enabled) return
        val activeProfiles = environment?.activeProfiles?.toSet().orEmpty()
        if (properties.externalProviders.productionProhibited && activeProfiles.any { it == "prod" || it == "production" }) {
            throw ReservationTaskRecommendationRejectedException("External recommendation providers are blocked in production for Sprint 13F.")
        }
        val providerAllowedProfiles = openAi.allowedProfiles.ifEmpty { properties.externalProviders.allowedProfiles }
        if (providerAllowedProfiles.isNotEmpty() && activeProfiles.none { it in providerAllowedProfiles }) {
            throw ReservationTaskRecommendationRejectedException("External recommendation provider is not allowed for the active profile.")
        }
        if (openAi.activationMode == ExternalRecommendationActivationMode.RUNTIME_GENERATION && !properties.enabled) {
            throw ReservationTaskRecommendationRejectedException("External recommendation runtime generation requires recommendations to be enabled.")
        }
        val endpoint = runCatching { URI.create(openAi.endpoint) }.getOrNull()
            ?: throw ReservationTaskRecommendationRejectedException("External recommendation provider endpoint is invalid.")
        val local = properties.externalProviders.localEndpointAllowlist.any { openAi.endpoint.startsWith(it) }
        if (!local && properties.externalProviders.requireHttpsOutsideLocal && endpoint.scheme != "https") {
            throw ReservationTaskRecommendationRejectedException("External recommendation provider endpoint must use HTTPS outside local stub mode.")
        }
        if (local && !openAi.smoke.enabled) {
            throw ReservationTaskRecommendationRejectedException("External recommendation local stub activation requires explicit smoke-test configuration.")
        }
    }

    companion object {
        const val INTERNAL_DEMO_PROVIDER_ID = "internal-demo"
        const val OPENAI_PROVIDER_ID = "openai"

        val REQUIRED_GENERATION_CAPABILITIES = setOf(
            RecommendationProviderCapability.BATCH_GENERATION,
            RecommendationProviderCapability.STRUCTURED_EXPLANATIONS,
            RecommendationProviderCapability.CONFIDENCE_SCORING
        )
    }
}

private fun OpenAiRecommendationProviderProperties.toConfiguredProvider(): RecommendationConfiguredProviderProperties =
    RecommendationConfiguredProviderProperties(
        enabled = enabled,
        displayName = displayName,
        requestTimeout = timeout,
        modelIdentifier = model,
        promptVersion = promptVersion,
        deterministic = false,
        allowedProfiles = allowedProfiles
    )
