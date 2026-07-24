package com.hotelopai.integration.apaleo

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.hotelopai.observability.OperationalObservability
import com.hotelopai.pms.application.EnvironmentPmsCredentialResolver
import com.hotelopai.pms.application.OAuth2ClientCredentialsPmsAuthenticationProperties
import com.hotelopai.pms.application.PmsAuthenticationMode
import com.hotelopai.pms.application.PmsAuthenticationProperties
import com.hotelopai.pms.application.PmsConfiguredProviderProperties
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.springframework.core.env.StandardEnvironment
import java.time.Duration

class ApaleoSandboxSmokeTest {
    @Test
    fun `read-only Apaleo sandbox smoke test is opt-in`() {
        assumeTrue(
            System.getProperty("hotelopai.apaleo.sandbox.smoke.enabled") == "true",
            "Apaleo sandbox smoke test skipped. Enable with -Photelopai.apaleo.sandbox.smoke.enabled=true."
        )

        val baseUrl = requirePlaceholderFree("APALEO_BASE_URL", "https://api.apaleo.com")
        val tokenUrl = requirePlaceholderFree("APALEO_TOKEN_URL", "https://identity.apaleo.com/connect/token")
        val propertyId = requirePlaceholderFree("APALEO_PROPERTY_ID", null)

        val client = ApaleoPmsHttpClient(
            config = PmsConfiguredProviderProperties(
                enabled = true,
                endpoint = baseUrl,
                hotelPropertyIdentifier = propertyId,
                requestTimeout = Duration.ofSeconds(5),
                authentication = PmsAuthenticationProperties(
                    mode = PmsAuthenticationMode.OAUTH2_CLIENT_CREDENTIALS,
                    oauth2ClientCredentials = OAuth2ClientCredentialsPmsAuthenticationProperties(
                        tokenUrl = tokenUrl,
                        clientIdReference = "env:APALEO_CLIENT_ID",
                        clientSecretReference = "env:APALEO_CLIENT_SECRET",
                        scopes = listOf("properties.read", "units.read", "reservations.read")
                    )
                ),
                settings = mapOf("max-pages" to "1", "page-size" to "10")
            ),
            objectMapper = jacksonObjectMapper().findAndRegisterModules(),
            credentialResolver = EnvironmentPmsCredentialResolver(StandardEnvironment()),
            observability = OperationalObservability.noop()
        )

        require(client.healthCheck()) { "Apaleo sandbox health check did not complete." }
        client.listUnits()
    }

    private fun requirePlaceholderFree(name: String, fallback: String?): String {
        val value = System.getenv(name)?.takeIf { it.isNotBlank() } ?: fallback
        require(!value.isNullOrBlank()) { "$name is required for the Apaleo sandbox smoke test." }
        require(!value.contains("placeholder", ignoreCase = true)) { "$name must not be a placeholder." }
        return value
    }
}
