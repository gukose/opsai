package com.hotelopai.pms.application

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.mock.env.MockEnvironment
import java.time.Duration

class PmsProviderConfigurationTest {
    @Test
    fun `properties bind provider configuration without secret values`() {
        val environment = MockEnvironment()
            .withProperty("ops.ai.pms.active-provider", "internal-demo")
            .withProperty("ops.ai.pms.providers[internal-demo].enabled", "true")
            .withProperty("ops.ai.pms.providers[internal-demo].display-name", "Internal Demo PMS")
            .withProperty("ops.ai.pms.providers[internal-demo].hotel-property-identifier", "demo-hotel")
            .withProperty("ops.ai.pms.providers[internal-demo].endpoint", "https://pms.example.test/api")
            .withProperty("ops.ai.pms.providers[internal-demo].request-timeout", "250ms")
            .withProperty("ops.ai.pms.providers[internal-demo].authentication.mode", "API_KEY")
            .withProperty("ops.ai.pms.providers[internal-demo].authentication.api-key.header-name", "X-Api-Key")
            .withProperty(
                "ops.ai.pms.providers[internal-demo].authentication.api-key.credential-reference",
                "secret://hotelopai/pms/internal-demo/api-key"
            )
            .withProperty("ops.ai.pms.providers[internal-demo].settings.vendor-region", "eu")

        val properties = Binder.get(environment)
            .bind("ops.ai.pms", PmsProviderProperties::class.java)
            .get()

        val provider = properties.provider("internal-demo")!!
        val auth = provider.authenticationConfig()

        assertEquals("internal-demo", properties.activeProvider)
        assertEquals(true, provider.enabled)
        assertEquals(Duration.ofMillis(250), provider.requestTimeout)
        assertEquals(PmsAuthenticationMode.API_KEY, auth.mode)
        assertEquals(listOf("secret://hotelopai/pms/internal-demo/api-key"), auth.credentialReferences())
        assertEquals(false, provider.safeDiagnostic(
            providerId = "internal-demo",
            fallbackDisplayName = "Internal Demo PMS",
            capabilities = PmsCapabilities(roomListing = true),
            readiness = PmsProviderReadiness(configured = true, enabled = true)
        ).toString().contains("X-Api-Key:"))
    }

    @Test
    fun `default properties keep internal demo enabled with no authentication`() {
        val properties = PmsProviderProperties()
        val provider = properties.provider("internal-demo")!!

        assertEquals("internal-demo", properties.activeProvider)
        assertEquals(true, provider.enabled)
        assertEquals(PmsAuthenticationMode.NONE, provider.authenticationConfig().mode)
        assertEquals(true, provider.authenticationConfig().isConfigured())
    }

    @Test
    fun `authentication modes convert to type-safe configs`() {
        assertThat(PmsAuthenticationProperties().toConfig()).isEqualTo(NoPmsAuthenticationConfig)

        assertThat(
            PmsAuthenticationProperties(
                mode = PmsAuthenticationMode.BEARER_TOKEN,
                bearerToken = BearerTokenPmsAuthenticationProperties("secret://token")
            ).toConfig()
        ).isEqualTo(BearerTokenPmsAuthenticationConfig("secret://token"))

        assertThat(
            PmsAuthenticationProperties(
                mode = PmsAuthenticationMode.BASIC,
                basic = BasicPmsAuthenticationProperties("secret://username", "secret://password")
            ).toConfig()
        ).isEqualTo(BasicPmsAuthenticationConfig("secret://username", "secret://password"))

        assertThat(
            PmsAuthenticationProperties(
                mode = PmsAuthenticationMode.OAUTH2_CLIENT_CREDENTIALS,
                oauth2ClientCredentials = OAuth2ClientCredentialsPmsAuthenticationProperties(
                    tokenUrl = "https://auth.example.test/token",
                    clientIdReference = "secret://client-id",
                    clientSecretReference = "secret://client-secret",
                    scopes = listOf("rooms.read")
                )
            ).toConfig()
        ).isEqualTo(
            OAuth2ClientCredentialsPmsAuthenticationConfig(
                tokenUrl = "https://auth.example.test/token",
                clientIdReference = "secret://client-id",
                clientSecretReference = "secret://client-secret",
                scopes = listOf("rooms.read")
            )
        )
    }

    @Test
    fun `secret redaction diagnostics never include configured secret values`() {
        val provider = PmsConfiguredProviderProperties(
            enabled = true,
            endpoint = "https://user:password@pms.example.test/api",
            authentication = PmsAuthenticationProperties(
                mode = PmsAuthenticationMode.BASIC,
                basic = BasicPmsAuthenticationProperties(
                    usernameReference = "secret://pms/user",
                    passwordReference = "secret://pms/password"
                )
            ),
            settings = mapOf("api-key" to "super-secret-value")
        )

        val diagnostic = provider.safeDiagnostic(
            providerId = "internal-demo",
            fallbackDisplayName = "Internal Demo PMS",
            capabilities = PmsCapabilities(roomListing = true),
            readiness = PmsProviderReadiness(configured = true, enabled = true)
        ).toString()

        assertThat(diagnostic).contains("pms.example.test")
        assertThat(diagnostic).contains("secret://pms/password")
        assertThat(diagnostic).doesNotContain("super-secret-value")
        assertThat(diagnostic).doesNotContain("user:password")
    }

    @Test
    fun `invalid timeout fails validation before provider registration`() {
        assertThrows(IllegalArgumentException::class.java) {
            PmsConfiguredProviderProperties(
                enabled = true,
                requestTimeout = Duration.ZERO
            )
        }
    }

    @Test
    fun `environment credential resolver resolves env references without exposing values in failures`() {
        val resolver = EnvironmentPmsCredentialResolver(
            MockEnvironment().withProperty("PMS_SECRET", "resolved-secret-value")
        )

        assertEquals("resolved-secret-value", resolver.resolve("env:PMS_SECRET"))

        val unresolved = assertThrows(com.hotelopai.pms.domain.PmsProviderConfigurationFailureException::class.java) {
            resolver.resolve("env:MISSING_SECRET")
        }
        assertThat(unresolved.message).contains("env:MISSING_SECRET")
        assertThat(unresolved.message).doesNotContain("resolved-secret-value")

        assertThrows(com.hotelopai.pms.domain.PmsProviderConfigurationFailureException::class.java) {
            resolver.resolve("secret://unsupported")
        }
    }
}
