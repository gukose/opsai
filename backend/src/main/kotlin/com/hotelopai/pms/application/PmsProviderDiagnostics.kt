package com.hotelopai.pms.application

import java.net.URI

data class PmsProviderDiagnostic(
    val providerId: String,
    val displayName: String,
    val enabled: Boolean,
    val configured: Boolean,
    val endpointHost: String?,
    val hotelPropertyIdentifierConfigured: Boolean,
    val authentication: PmsAuthenticationDiagnostic,
    val capabilities: PmsCapabilities,
    val settingsKeys: List<String>,
    val healthState: PmsHealthState? = null,
    val circuitState: PmsCircuitState? = null,
    val retryPolicy: PmsRetryPolicySummary? = null,
    val lastFailureCategory: PmsFailureCategory? = null,
    val readinessMessage: String?
)

fun safeEndpointHost(endpoint: String?): String? =
    endpoint
        ?.takeIf { it.isNotBlank() }
        ?.let { value ->
            runCatching { URI.create(value).host }
                .getOrNull()
                ?: value.substringBefore('/').substringBefore(':').takeIf { it.isNotBlank() }
        }
