package com.hotelopai.knowledge.application

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.hotelopai.observability.OperationalObservability
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component
import java.net.URI
import java.time.Clock
import java.time.Duration

class KnowledgeAnswerProviderException(
    val category: KnowledgeAnswerFailureCategory
) : RuntimeException("Knowledge answer provider failed: $category")

object KnowledgeAnswerSmokeFixtureScope {
    private val current = ThreadLocal<KnowledgeAnswerSmokeFixtureMode?>()

    fun <T> withFixture(mode: KnowledgeAnswerSmokeFixtureMode?, block: () -> T): T {
        val previous = current.get()
        current.set(mode)
        return try {
            block()
        } finally {
            current.set(previous)
        }
    }

    fun current(): KnowledgeAnswerSmokeFixtureMode? = current.get()
}

@Component
@EnableConfigurationProperties(KnowledgeProperties::class)
class OpenAiKnowledgeAnswerProvider(
    private val properties: KnowledgeProperties,
    private val objectMapper: ObjectMapper,
    private val credentialResolver: KnowledgeEmbeddingCredentialResolver,
    private val httpClient: KnowledgeEmbeddingHttpClient,
    private val environment: Environment,
    private val clock: Clock,
    private val observability: OperationalObservability = OperationalObservability.noop()
) : ExternalKnowledgeAnswerProvider {
    private val openAi: KnowledgeExternalAnswerProviderProperties
        get() = properties.answers.providers.openai

    override val providerId: String = "openai"
    override val modelId: String
        get() = openAi.model

    init {
        validateIfEnabled()
    }

    override fun readiness(): KnowledgeEmbeddingProviderReadiness {
        if (!openAi.enabled) return KnowledgeEmbeddingProviderReadiness.DISABLED
        return runCatching {
            validateIfEnabled()
            KnowledgeEmbeddingProviderReadiness.READY
        }.getOrDefault(KnowledgeEmbeddingProviderReadiness.MISCONFIGURED)
    }

    override fun generate(prompt: KnowledgePrompt): KnowledgeAnswerProviderResponse {
        val fixture = KnowledgeAnswerSmokeFixtureScope.current()
        if (fixture != null) return fixtureResponse(fixture, prompt)
        if (!openAi.enabled) throw KnowledgeAnswerProviderException(KnowledgeAnswerFailureCategory.PROVIDER_DISABLED)
        validateIfEnabled()
        var attempt = 0
        var last: KnowledgeAnswerProviderException? = null
        while (attempt < openAi.maxAttempts) {
            attempt += 1
            try {
                val started = clock.instant()
                val response = httpClient.postJson(request(prompt))
                val elapsed = Duration.between(started, clock.instant()).toMillis().coerceAtLeast(0)
                record("received", null, elapsed)
                return parse(response.statusCode, response.body)
            } catch (exception: KnowledgeAnswerProviderException) {
                last = exception
                record("failed", exception.category, 0)
                if (!exception.category.retryable || attempt >= openAi.maxAttempts) throw exception
                Thread.sleep(backoffMillis(attempt))
            }
        }
        throw last ?: KnowledgeAnswerProviderException(KnowledgeAnswerFailureCategory.PROVIDER_UNAVAILABLE)
    }

    private fun request(prompt: KnowledgePrompt): KnowledgeEmbeddingHttpRequest {
        val token = credentialResolver.resolve(
            openAi.credentialReference
                ?: throw KnowledgeAnswerProviderException(KnowledgeAnswerFailureCategory.CONFIGURATION_ERROR)
        )
        val body = objectMapper.writeValueAsString(
            OpenAiKnowledgeAnswerRequest(
                model = openAi.model,
                temperature = openAi.temperature,
                topP = openAi.topP,
                maxTokens = openAi.maximumTokens,
                messages = listOf(
                    OpenAiKnowledgeAnswerMessage("system", prompt.systemInstructions),
                    OpenAiKnowledgeAnswerMessage("user", objectMapper.writeValueAsString(OpenAiKnowledgePromptEnvelope(prompt)))
                )
            )
        )
        return KnowledgeEmbeddingHttpRequest(
            endpoint = URI.create(openAi.endpoint),
            timeout = openAi.timeout,
            headers = mapOf(
                "Authorization" to "Bearer $token",
                "Content-Type" to "application/json",
                "X-HotelOpAI-Prompt-Version" to prompt.version
            ),
            body = body
        )
    }

    private fun parse(statusCode: Int, body: String): KnowledgeAnswerProviderResponse {
        when {
            statusCode == 401 -> throw KnowledgeAnswerProviderException(KnowledgeAnswerFailureCategory.AUTHENTICATION_FAILURE)
            statusCode == 403 -> throw KnowledgeAnswerProviderException(KnowledgeAnswerFailureCategory.AUTHORIZATION_FAILURE)
            statusCode == 429 -> throw KnowledgeAnswerProviderException(KnowledgeAnswerFailureCategory.RATE_LIMITED)
            statusCode in 500..599 -> throw KnowledgeAnswerProviderException(KnowledgeAnswerFailureCategory.PROVIDER_UNAVAILABLE)
            statusCode !in 200..299 -> throw KnowledgeAnswerProviderException(KnowledgeAnswerFailureCategory.INVALID_RESPONSE)
        }
        val root = runCatching { objectMapper.readTree(body) }
            .getOrElse { throw KnowledgeAnswerProviderException(KnowledgeAnswerFailureCategory.INVALID_RESPONSE) }
        val content = root.at("/choices/0/message/content").asText(null)
            ?: throw KnowledgeAnswerProviderException(KnowledgeAnswerFailureCategory.INVALID_RESPONSE)
        val parsed = runCatching { objectMapper.readTree(content) }
            .getOrElse { throw KnowledgeAnswerProviderException(KnowledgeAnswerFailureCategory.INVALID_RESPONSE) }
        return structured(parsed)
    }

    private fun structured(node: JsonNode): KnowledgeAnswerProviderResponse =
        runCatching {
            val status = KnowledgeAnswerStatus.valueOf(node.path("status").asText())
            KnowledgeAnswerProviderResponse(
                status = status,
                answerText = node.path("answer").asText(null),
                confidence = node.path("confidence").asText(null)?.takeIf { it.isNotBlank() }?.let(KnowledgeAnswerConfidence::valueOf),
                citationIds = node.path("citationIds").map { it.asText() },
                failureCategory = if (status == KnowledgeAnswerStatus.INSUFFICIENT_CONTEXT) KnowledgeAnswerFailureCategory.INSUFFICIENT_CONTEXT else null
            )
        }.getOrElse {
            throw KnowledgeAnswerProviderException(KnowledgeAnswerFailureCategory.INVALID_RESPONSE)
        }

    private fun fixtureResponse(mode: KnowledgeAnswerSmokeFixtureMode, prompt: KnowledgePrompt): KnowledgeAnswerProviderResponse {
        if (!openAi.fixtureModeEnabled) throw KnowledgeAnswerProviderException(KnowledgeAnswerFailureCategory.CONFIGURATION_ERROR)
        return when (mode) {
            KnowledgeAnswerSmokeFixtureMode.SUCCESS -> KnowledgeAnswerProviderResponse(
                status = KnowledgeAnswerStatus.ANSWERED,
                answerText = "Use the cited operating procedure and verify completion with the shift lead.",
                confidence = KnowledgeAnswerConfidence.HIGH,
                citationIds = prompt.contextItems.take(1).map { it.citationId }
            )
            KnowledgeAnswerSmokeFixtureMode.EMPTY_SUCCESS -> KnowledgeAnswerProviderResponse(
                status = KnowledgeAnswerStatus.INSUFFICIENT_CONTEXT,
                answerText = null,
                confidence = KnowledgeAnswerConfidence.LOW,
                citationIds = emptyList(),
                failureCategory = KnowledgeAnswerFailureCategory.INSUFFICIENT_CONTEXT
            )
            KnowledgeAnswerSmokeFixtureMode.MALFORMED_RESPONSE -> throw KnowledgeAnswerProviderException(KnowledgeAnswerFailureCategory.INVALID_RESPONSE)
            KnowledgeAnswerSmokeFixtureMode.TIMEOUT -> throw KnowledgeAnswerProviderException(KnowledgeAnswerFailureCategory.PROVIDER_TIMEOUT)
            KnowledgeAnswerSmokeFixtureMode.RATE_LIMITED -> throw KnowledgeAnswerProviderException(KnowledgeAnswerFailureCategory.RATE_LIMITED)
            KnowledgeAnswerSmokeFixtureMode.AUTHENTICATION_FAILURE -> throw KnowledgeAnswerProviderException(KnowledgeAnswerFailureCategory.AUTHENTICATION_FAILURE)
            KnowledgeAnswerSmokeFixtureMode.PROVIDER_UNAVAILABLE -> throw KnowledgeAnswerProviderException(KnowledgeAnswerFailureCategory.PROVIDER_UNAVAILABLE)
        }
    }

    private fun validateIfEnabled() {
        if (!openAi.enabled) return
        val activeProfiles = environment.activeProfiles.toSet()
        if (properties.answers.providers.externalPolicy.productionProhibited && activeProfiles.any { it == "prod" || it == "production" }) {
            throw IllegalStateException("OpenAI knowledge answer provider is blocked in production.")
        }
        val allowed = openAi.allowedProfiles.ifEmpty { properties.answers.providers.externalPolicy.allowedProfiles }
        if (allowed.isNotEmpty() && activeProfiles.none { it in allowed }) {
            throw IllegalStateException("OpenAI knowledge answer provider is not allowed for the active profile.")
        }
        val endpoint = runCatching { URI.create(openAi.endpoint) }.getOrNull()
            ?: throw IllegalStateException("OpenAI knowledge answer endpoint is invalid.")
        val local = properties.answers.providers.externalPolicy.localEndpointAllowlist.any { openAi.endpoint.startsWith(it) }
        if (!local && properties.answers.providers.externalPolicy.requireHttpsOutsideLocal && endpoint.scheme != "https") {
            throw IllegalStateException("OpenAI knowledge answer endpoint must use HTTPS outside local development.")
        }
        if (local && !openAi.smokeTestEnabled) {
            throw IllegalStateException("OpenAI knowledge answer local endpoint requires smoke-test enablement.")
        }
        if (openAi.credentialReference.isNullOrBlank()) {
            throw IllegalStateException("OpenAI knowledge answer credential reference must be configured.")
        }
    }

    private fun backoffMillis(attempt: Int): Long = (250L * (1L shl (attempt - 1).coerceAtMost(4))).coerceAtMost(2_000L)

    private fun record(outcome: String, failure: KnowledgeAnswerFailureCategory?, elapsedMillis: Long) {
        observability.incrementCounter(
            "knowledge_answer_provider_requests_total",
            "provider" to providerId,
            "model" to modelId,
            "outcome" to outcome,
            "failure_category" to (failure?.name ?: "none")
        )
        observability.recordTimer(
            "knowledge_answer_provider_request_duration",
            Duration.ofMillis(elapsedMillis),
            "provider" to providerId,
            "model" to modelId,
            "outcome" to outcome
        )
    }
}

private val KnowledgeAnswerFailureCategory.retryable: Boolean
    get() = this in setOf(
        KnowledgeAnswerFailureCategory.PROVIDER_TIMEOUT,
        KnowledgeAnswerFailureCategory.PROVIDER_UNAVAILABLE,
        KnowledgeAnswerFailureCategory.RATE_LIMITED
    )

private data class OpenAiKnowledgePromptEnvelope(
    val templateId: String,
    val templateVersion: String,
    val query: String,
    val context: List<OpenAiKnowledgeContextItem>,
    val outputSchema: String
) {
    constructor(prompt: KnowledgePrompt) : this(
        templateId = prompt.templateId,
        templateVersion = prompt.version,
        query = prompt.operatorQuery,
        context = prompt.contextItems.map {
            OpenAiKnowledgeContextItem(
                citationId = it.citationId,
                category = it.citation.category.name,
                title = it.citation.title,
                chunkPosition = it.citation.chunkPosition,
                text = it.text
            )
        },
        outputSchema = prompt.outputSchema
    )
}

private data class OpenAiKnowledgeContextItem(
    val citationId: String,
    val category: String,
    val title: String,
    val chunkPosition: Int,
    val text: String
)

private data class OpenAiKnowledgeAnswerRequest(
    val model: String,
    val temperature: Double,
    @com.fasterxml.jackson.annotation.JsonProperty("top_p")
    val topP: Double,
    @com.fasterxml.jackson.annotation.JsonProperty("max_tokens")
    val maxTokens: Int,
    val messages: List<OpenAiKnowledgeAnswerMessage>
)

private data class OpenAiKnowledgeAnswerMessage(
    val role: String,
    val content: String
)
