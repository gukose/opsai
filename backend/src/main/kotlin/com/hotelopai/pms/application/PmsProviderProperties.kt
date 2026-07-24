package com.hotelopai.pms.application

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated
import java.time.Duration

@ConfigurationProperties(prefix = "ops.ai.pms")
@Validated
data class PmsProviderProperties(
    @field:NotBlank
    val activeProvider: String = "internal-demo",
    @field:Valid
    val providers: Map<String, PmsConfiguredProviderProperties> = mapOf(
        "internal-demo" to PmsConfiguredProviderProperties(
            enabled = true,
            displayName = "Internal Demo PMS",
            authentication = PmsAuthenticationProperties(mode = PmsAuthenticationMode.NONE)
        ),
        "apaleo" to PmsConfiguredProviderProperties(
            enabled = false,
            displayName = "Apaleo Sandbox PMS",
            endpoint = "https://api.apaleo.com",
            authentication = PmsAuthenticationProperties(
                mode = PmsAuthenticationMode.OAUTH2_CLIENT_CREDENTIALS,
                oauth2ClientCredentials = OAuth2ClientCredentialsPmsAuthenticationProperties(
                    tokenUrl = "https://identity.apaleo.com/connect/token",
                    clientIdReference = "env:APALEO_CLIENT_ID",
                    clientSecretReference = "env:APALEO_CLIENT_SECRET",
                    scopes = listOf("properties.read", "units.read", "reservations.read")
                )
            ),
            settings = mapOf(
                "max-pages" to "2",
                "page-size" to "100"
            )
        )
    )
) {
    fun provider(providerId: String): PmsConfiguredProviderProperties? =
        providers[providerId]
}

data class PmsConfiguredProviderProperties(
    val enabled: Boolean = false,
    val displayName: String? = null,
    val hotelPropertyIdentifier: String? = null,
    val endpoint: String? = null,
    val productionApproved: Boolean = false,
    val allowedProfiles: List<String> = emptyList(),
    @field:Valid
    val authentication: PmsAuthenticationProperties = PmsAuthenticationProperties(),
    val requestTimeout: Duration = Duration.ofSeconds(5),
    val settings: Map<String, String> = emptyMap()
) {
    init {
        require(!requestTimeout.isNegative && !requestTimeout.isZero) {
            "PMS provider request timeout must be positive"
        }
    }

    fun authenticationConfig(): PmsAuthenticationConfig =
        authentication.toConfig()

    fun safeDiagnostic(
        providerId: String,
        fallbackDisplayName: String,
        capabilities: PmsCapabilities,
        readiness: PmsProviderReadiness,
        health: PmsProviderHealth? = null
    ): PmsProviderDiagnostic =
        PmsProviderDiagnostic(
            providerId = providerId,
            displayName = displayName?.takeIf { it.isNotBlank() } ?: fallbackDisplayName,
            enabled = enabled,
            configured = readiness.configured,
            endpointHost = safeEndpointHost(endpoint),
            hotelPropertyIdentifierConfigured = !hotelPropertyIdentifier.isNullOrBlank(),
            authentication = authenticationConfig().safeDiagnostic(),
            capabilities = capabilities,
            settingsKeys = settings.keys.sorted(),
            healthState = health?.state,
            circuitState = health?.circuitState,
            retryPolicy = health?.retryPolicy,
            lastFailureCategory = health?.failureCategory,
            readinessMessage = readiness.message
        )
}

data class PmsAuthenticationProperties(
    val mode: PmsAuthenticationMode = PmsAuthenticationMode.NONE,
    val apiKey: ApiKeyPmsAuthenticationProperties = ApiKeyPmsAuthenticationProperties(),
    val bearerToken: BearerTokenPmsAuthenticationProperties = BearerTokenPmsAuthenticationProperties(),
    val basic: BasicPmsAuthenticationProperties = BasicPmsAuthenticationProperties(),
    val oauth2ClientCredentials: OAuth2ClientCredentialsPmsAuthenticationProperties =
        OAuth2ClientCredentialsPmsAuthenticationProperties()
) {
    fun toConfig(): PmsAuthenticationConfig =
        when (mode) {
            PmsAuthenticationMode.NONE -> NoPmsAuthenticationConfig
            PmsAuthenticationMode.API_KEY -> ApiKeyPmsAuthenticationConfig(
                headerName = apiKey.headerName,
                credentialReference = apiKey.credentialReference
            )
            PmsAuthenticationMode.BEARER_TOKEN -> BearerTokenPmsAuthenticationConfig(
                tokenReference = bearerToken.tokenReference
            )
            PmsAuthenticationMode.BASIC -> BasicPmsAuthenticationConfig(
                usernameReference = basic.usernameReference,
                passwordReference = basic.passwordReference
            )
            PmsAuthenticationMode.OAUTH2_CLIENT_CREDENTIALS -> OAuth2ClientCredentialsPmsAuthenticationConfig(
                tokenUrl = oauth2ClientCredentials.tokenUrl,
                clientIdReference = oauth2ClientCredentials.clientIdReference,
                clientSecretReference = oauth2ClientCredentials.clientSecretReference,
                scopes = oauth2ClientCredentials.scopes
            )
        }
}

data class ApiKeyPmsAuthenticationProperties(
    val headerName: String = "",
    val credentialReference: String = ""
)

data class BearerTokenPmsAuthenticationProperties(
    val tokenReference: String = ""
)

data class BasicPmsAuthenticationProperties(
    val usernameReference: String = "",
    val passwordReference: String = ""
)

data class OAuth2ClientCredentialsPmsAuthenticationProperties(
    val tokenUrl: String = "",
    val clientIdReference: String = "",
    val clientSecretReference: String = "",
    val scopes: List<String> = emptyList()
)
