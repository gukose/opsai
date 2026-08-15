package com.hotelopai.knowledge.application

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

fun interface KnowledgeEmbeddingCredentialResolver {
    fun resolve(reference: String): String
}

@Component
class EnvironmentKnowledgeEmbeddingCredentialResolver : KnowledgeEmbeddingCredentialResolver {
    override fun resolve(reference: String): String =
        System.getenv(reference)?.takeIf { it.isNotBlank() }
            ?: throw KnowledgeEmbeddingProviderException(KnowledgeEmbeddingFailureCategory.CONFIGURATION_ERROR)
}

data class KnowledgeEmbeddingHttpRequest(
    val endpoint: URI,
    val timeout: Duration,
    val headers: Map<String, String>,
    val body: String
)

data class KnowledgeEmbeddingHttpResponse(
    val statusCode: Int,
    val body: String
)

interface KnowledgeEmbeddingHttpClient {
    fun postJson(request: KnowledgeEmbeddingHttpRequest): KnowledgeEmbeddingHttpResponse
}

@Component
class JdkKnowledgeEmbeddingHttpClient : KnowledgeEmbeddingHttpClient {
    private val client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build()

    override fun postJson(request: KnowledgeEmbeddingHttpRequest): KnowledgeEmbeddingHttpResponse =
        try {
            val builder = HttpRequest.newBuilder()
                .uri(request.endpoint)
                .timeout(request.timeout)
                .POST(HttpRequest.BodyPublishers.ofString(request.body))
            request.headers.forEach { (key, value) -> builder.header(key, value) }
            val response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
            KnowledgeEmbeddingHttpResponse(response.statusCode(), response.body())
        } catch (_: java.net.http.HttpTimeoutException) {
            throw KnowledgeEmbeddingProviderException(KnowledgeEmbeddingFailureCategory.TIMEOUT)
        } catch (_: java.io.IOException) {
            throw KnowledgeEmbeddingProviderException(KnowledgeEmbeddingFailureCategory.PROVIDER_UNAVAILABLE)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            throw KnowledgeEmbeddingProviderException(KnowledgeEmbeddingFailureCategory.PROVIDER_UNAVAILABLE)
        }
}

class KnowledgeEmbeddingProviderException(
    val category: KnowledgeEmbeddingFailureCategory
) : RuntimeException("Knowledge embedding provider failed: $category")

@Component
class OpenAiKnowledgeEmbeddingProvider(
    private val properties: KnowledgeProperties,
    private val objectMapper: ObjectMapper,
    private val credentialResolver: KnowledgeEmbeddingCredentialResolver,
    private val httpClient: KnowledgeEmbeddingHttpClient,
    private val environment: Environment
) : ExternalKnowledgeEmbeddingProvider {
    override val providerId: KnowledgeEmbeddingProviderId = KnowledgeEmbeddingProviderId("openai")
    override val providerType: KnowledgeEmbeddingProviderType = KnowledgeEmbeddingProviderType.EXTERNAL
    override val modelIdentifier: String
        get() = properties.semanticSearch.externalProviders.openai.model
    override val embeddingDimension: Int
        get() = properties.semanticSearch.externalProviders.openai.dimensions

    init {
        validateIfEnabled()
    }

    override fun readiness(): KnowledgeEmbeddingProviderReadiness {
        val config = properties.semanticSearch.externalProviders.openai
        if (!config.enabled) return KnowledgeEmbeddingProviderReadiness.DISABLED
        return try {
            validateIfEnabled()
            KnowledgeEmbeddingProviderReadiness.READY
        } catch (_: RuntimeException) {
            KnowledgeEmbeddingProviderReadiness.MISCONFIGURED
        }
    }

    override fun embed(requests: List<KnowledgeEmbeddingRequest>): List<KnowledgeEmbeddingResponse> {
        val config = properties.semanticSearch.externalProviders.openai
        if (!config.enabled) throw KnowledgeEmbeddingProviderException(KnowledgeEmbeddingFailureCategory.PROVIDER_DISABLED)
        validateIfEnabled()
        val token = credentialResolver.resolve(requireNotNull(config.credentialReference))
        val body = objectMapper.writeValueAsString(
            mapOf(
                "model" to config.model,
                "dimensions" to config.dimensions,
                "input" to requests.map { it.text }
            )
        )
        val response = httpClient.postJson(
            KnowledgeEmbeddingHttpRequest(
                endpoint = URI.create(config.endpoint),
                timeout = config.timeout,
                headers = mapOf(
                    "Authorization" to "Bearer $token",
                    "Content-Type" to "application/json"
                ),
                body = body
            )
        )
        when (response.statusCode) {
            200 -> return parseResponse(response.body, requests)
            401 -> throw KnowledgeEmbeddingProviderException(KnowledgeEmbeddingFailureCategory.AUTHENTICATION_FAILURE)
            403 -> throw KnowledgeEmbeddingProviderException(KnowledgeEmbeddingFailureCategory.AUTHENTICATION_FAILURE)
            429 -> throw KnowledgeEmbeddingProviderException(KnowledgeEmbeddingFailureCategory.RATE_LIMITED)
            in 500..599 -> throw KnowledgeEmbeddingProviderException(KnowledgeEmbeddingFailureCategory.PROVIDER_UNAVAILABLE)
            else -> throw KnowledgeEmbeddingProviderException(KnowledgeEmbeddingFailureCategory.INVALID_RESPONSE)
        }
    }

    private fun parseResponse(body: String, requests: List<KnowledgeEmbeddingRequest>): List<KnowledgeEmbeddingResponse> {
        val root = runCatching { objectMapper.readTree(body) }.getOrElse {
            throw KnowledgeEmbeddingProviderException(KnowledgeEmbeddingFailureCategory.INVALID_RESPONSE)
        }
        val data = root.path("data")
        if (!data.isArray || data.size() != requests.size) {
            throw KnowledgeEmbeddingProviderException(KnowledgeEmbeddingFailureCategory.INVALID_RESPONSE)
        }
        return data.mapIndexed { index, node ->
            val vector = vector(node.path("embedding"))
            if (vector.size != embeddingDimension) {
                throw KnowledgeEmbeddingProviderException(KnowledgeEmbeddingFailureCategory.DIMENSION_MISMATCH)
            }
            KnowledgeEmbeddingResponse(requests[index].chunkId, KnowledgeEmbeddingVector(vector), requests[index].contentFingerprint)
        }
    }

    private fun vector(node: JsonNode): List<Double> {
        if (!node.isArray) throw KnowledgeEmbeddingProviderException(KnowledgeEmbeddingFailureCategory.INVALID_RESPONSE)
        return node.map {
            if (!it.isNumber) throw KnowledgeEmbeddingProviderException(KnowledgeEmbeddingFailureCategory.INVALID_RESPONSE)
            it.asDouble()
        }
    }

    private fun validateIfEnabled() {
        val config = properties.semanticSearch.externalProviders.openai
        if (!config.enabled) return
        val activeProfiles = environment.activeProfiles.toSet()
        if (properties.semanticSearch.externalProviders.productionProhibited && activeProfiles.any { it == "prod" || it == "production" }) {
            throw IllegalStateException("OpenAI knowledge embedding provider is blocked in production.")
        }
        val allowed = config.allowedProfiles.ifEmpty { properties.semanticSearch.externalProviders.allowedProfiles }
        if (allowed.isNotEmpty() && activeProfiles.none { it in allowed }) {
            throw IllegalStateException("OpenAI knowledge embedding provider is not allowed for the active profile.")
        }
        val endpoint = runCatching { URI.create(config.endpoint) }.getOrNull()
            ?: throw IllegalStateException("OpenAI knowledge embedding endpoint is invalid.")
        val local = properties.semanticSearch.externalProviders.localEndpointAllowlist.any { config.endpoint.startsWith(it) }
        if (!local && properties.semanticSearch.externalProviders.requireHttpsOutsideLocal && endpoint.scheme != "https") {
            throw IllegalStateException("OpenAI knowledge embedding endpoint must use HTTPS outside local development.")
        }
        if (local && !config.smokeTestEnabled) {
            throw IllegalStateException("OpenAI knowledge embedding local endpoint requires smoke-test enablement.")
        }
        if (config.credentialReference.isNullOrBlank()) {
            throw IllegalStateException("OpenAI knowledge embedding credential reference must be configured.")
        }
    }
}
