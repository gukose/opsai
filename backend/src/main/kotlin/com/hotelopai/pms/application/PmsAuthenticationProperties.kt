package com.hotelopai.pms.application

sealed interface PmsAuthenticationConfig {
    val mode: PmsAuthenticationMode
    fun credentialReferences(): List<String>
    fun isConfigured(): Boolean
    fun safeDiagnostic(): PmsAuthenticationDiagnostic =
        PmsAuthenticationDiagnostic(
            mode = mode,
            configured = isConfigured(),
            credentialReferences = credentialReferences()
        )
}

data object NoPmsAuthenticationConfig : PmsAuthenticationConfig {
    override val mode = PmsAuthenticationMode.NONE
    override fun credentialReferences(): List<String> = emptyList()
    override fun isConfigured(): Boolean = true
}

data class ApiKeyPmsAuthenticationConfig(
    val headerName: String,
    val credentialReference: String
) : PmsAuthenticationConfig {
    override val mode = PmsAuthenticationMode.API_KEY
    override fun credentialReferences(): List<String> = listOf(credentialReference)
    override fun isConfigured(): Boolean = headerName.isNotBlank() && credentialReference.isNotBlank()
}

data class BearerTokenPmsAuthenticationConfig(
    val tokenReference: String
) : PmsAuthenticationConfig {
    override val mode = PmsAuthenticationMode.BEARER_TOKEN
    override fun credentialReferences(): List<String> = listOf(tokenReference)
    override fun isConfigured(): Boolean = tokenReference.isNotBlank()
}

data class BasicPmsAuthenticationConfig(
    val usernameReference: String,
    val passwordReference: String
) : PmsAuthenticationConfig {
    override val mode = PmsAuthenticationMode.BASIC
    override fun credentialReferences(): List<String> = listOf(usernameReference, passwordReference)
    override fun isConfigured(): Boolean = usernameReference.isNotBlank() && passwordReference.isNotBlank()
}

data class OAuth2ClientCredentialsPmsAuthenticationConfig(
    val tokenUrl: String,
    val clientIdReference: String,
    val clientSecretReference: String,
    val scopes: List<String> = emptyList()
) : PmsAuthenticationConfig {
    override val mode = PmsAuthenticationMode.OAUTH2_CLIENT_CREDENTIALS
    override fun credentialReferences(): List<String> = listOf(clientIdReference, clientSecretReference)
    override fun isConfigured(): Boolean =
        tokenUrl.isNotBlank() && clientIdReference.isNotBlank() && clientSecretReference.isNotBlank()
}

enum class PmsAuthenticationMode {
    NONE,
    API_KEY,
    BEARER_TOKEN,
    BASIC,
    OAUTH2_CLIENT_CREDENTIALS
}

data class PmsAuthenticationDiagnostic(
    val mode: PmsAuthenticationMode,
    val configured: Boolean,
    val credentialReferences: List<String>
)
