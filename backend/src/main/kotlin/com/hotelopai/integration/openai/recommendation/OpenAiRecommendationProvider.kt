package com.hotelopai.integration.openai.recommendation

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.hotelopai.observability.OperationalObservability
import com.hotelopai.reservation.recommendation.ExternalLlmRecommendationProvider
import com.hotelopai.reservation.recommendation.OpenAiRecommendationProviderProperties
import com.hotelopai.reservation.recommendation.OutboundRecommendationContext
import com.hotelopai.reservation.recommendation.RecommendationCategory
import com.hotelopai.reservation.recommendation.RecommendationConfidence
import com.hotelopai.reservation.recommendation.RecommendationCredentialResolver
import com.hotelopai.reservation.recommendation.RecommendationFailureCategory
import com.hotelopai.reservation.recommendation.RecommendationHttpClient
import com.hotelopai.reservation.recommendation.RecommendationHttpRequest
import com.hotelopai.reservation.recommendation.RecommendationProviderCapability
import com.hotelopai.reservation.recommendation.RecommendationProviderException
import com.hotelopai.reservation.recommendation.RecommendationProviderId
import com.hotelopai.reservation.recommendation.RecommendationProviderType
import com.hotelopai.reservation.recommendation.RecommendationTaskProposal
import com.hotelopai.reservation.recommendation.RecommendationPrompt
import com.hotelopai.reservation.recommendation.RecommendationPromptFactory
import com.hotelopai.reservation.recommendation.RecommendationPrivacyGateway
import com.hotelopai.reservation.recommendation.ReservationTaskRecommendationProperties
import com.hotelopai.reservation.recommendation.SanitizedReservationRecommendationContext
import com.hotelopai.reservation.recommendation.StructuredRecommendationItem
import com.hotelopai.reservation.recommendation.StructuredRecommendationResponse
import com.hotelopai.reservation.recommendation.StructuredRecommendationResponseValidator
import com.hotelopai.reservation.recommendation.TaskRecommendationProviderRegistry
import com.hotelopai.task.domain.TaskIntentType
import com.hotelopai.task.domain.TaskPriority
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.stereotype.Component
import java.net.URI
import java.security.MessageDigest
import java.time.Clock

@Component
@EnableConfigurationProperties(ReservationTaskRecommendationProperties::class)
class OpenAiRecommendationProvider(
    private val properties: ReservationTaskRecommendationProperties,
    private val privacyGateway: RecommendationPrivacyGateway,
    private val promptFactory: RecommendationPromptFactory,
    private val responseValidator: StructuredRecommendationResponseValidator,
    private val credentialResolver: RecommendationCredentialResolver,
    private val httpClient: RecommendationHttpClient,
    private val objectMapper: ObjectMapper,
    private val clock: Clock,
    private val observability: OperationalObservability = OperationalObservability.noop()
) : ExternalLlmRecommendationProvider {
    private val openAi: OpenAiRecommendationProviderProperties
        get() = properties.providers.openai

    override val providerName: String = TaskRecommendationProviderRegistry.OPENAI_PROVIDER_ID
    override val id: RecommendationProviderId = RecommendationProviderId(TaskRecommendationProviderRegistry.OPENAI_PROVIDER_ID)
    override val displayName: String
        get() = openAi.displayName
    override val providerType: RecommendationProviderType = RecommendationProviderType.EXTERNAL
    override val modelIdentifier: String?
        get() = openAi.model
    override val promptVersion: String
        get() = openAi.promptVersion
    override val capabilities: Set<RecommendationProviderCapability> = setOf(
        RecommendationProviderCapability.BATCH_GENERATION,
        RecommendationProviderCapability.STRUCTURED_EXPLANATIONS,
        RecommendationProviderCapability.CONFIDENCE_SCORING,
        RecommendationProviderCapability.RETRYABLE_EXECUTION,
        RecommendationProviderCapability.MODEL_METADATA
    )

    override fun recommend(context: SanitizedReservationRecommendationContext): List<RecommendationTaskProposal> {
        if (!openAi.enabled) {
            throw RecommendationProviderException(RecommendationFailureCategory.INVALID_CONFIGURATION)
        }
        val outbound = privacyGateway.outboundContext(context)
        val prompt = promptFactory.create(
            context = outbound,
            templateId = openAi.promptTemplateId,
            version = openAi.promptVersion,
            maxRecommendations = properties.maxRecommendationsPerReservation
        )
        val structured = executeWithRetry(prompt)
        return responseValidator.validate(structured).recommendations
            .take(properties.maxRecommendationsPerReservation)
            .map { it.toProposal(context, prompt) }
    }

    private fun executeWithRetry(prompt: RecommendationPrompt): StructuredRecommendationResponse {
        var attempt = 0
        var lastFailure: RecommendationProviderException? = null
        while (attempt < openAi.retryPolicy.maxAttempts) {
            attempt += 1
            try {
                val started = clock.instant()
                val response = httpClient.postJson(request(prompt))
                val elapsedMillis = java.time.Duration.between(started, clock.instant()).toMillis().coerceAtLeast(0)
                recordRequestMetric("received", null, elapsedMillis)
                return parseResponse(response.statusCode, response.body)
            } catch (exception: RecommendationProviderException) {
                lastFailure = exception
                recordRequestMetric("failed", exception.failureCategory, 0)
                if (!exception.failureCategory.retryable || attempt >= openAi.retryPolicy.maxAttempts) {
                    throw exception
                }
                Thread.sleep(backoffMillis(attempt))
            }
        }
        throw lastFailure ?: RecommendationProviderException(RecommendationFailureCategory.INTERNAL_PROVIDER_ERROR)
    }

    private fun request(prompt: RecommendationPrompt): RecommendationHttpRequest {
        val token = credentialResolver.resolve(
            openAi.credentialReference
                ?: throw RecommendationProviderException(RecommendationFailureCategory.INVALID_CONFIGURATION)
        )
        val body = objectMapper.writeValueAsString(
            OpenAiChatCompletionRequest(
                model = requireNotNull(openAi.model) { "OpenAI recommendation model must be configured." },
                temperature = openAi.temperature,
                topP = openAi.topP,
                maxTokens = openAi.maximumTokens,
                messages = listOf(
                    OpenAiChatMessage("system", prompt.systemInstructions),
                    OpenAiChatMessage("user", objectMapper.writeValueAsString(OpenAiPromptEnvelope(prompt)))
                )
            )
        )
        return RecommendationHttpRequest(
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

    private fun parseResponse(statusCode: Int, body: String): StructuredRecommendationResponse {
        if (statusCode == 401) throw RecommendationProviderException(RecommendationFailureCategory.AUTHENTICATION)
        if (statusCode == 403) throw RecommendationProviderException(RecommendationFailureCategory.AUTHORIZATION)
        if (statusCode == 429) throw RecommendationProviderException(RecommendationFailureCategory.RATE_LIMIT)
        if (statusCode in 500..599) throw RecommendationProviderException(RecommendationFailureCategory.PROVIDER_UNAVAILABLE)
        if (statusCode !in 200..299) throw RecommendationProviderException(RecommendationFailureCategory.INVALID_RESPONSE)
        val root = runCatching { objectMapper.readTree(body) }
            .getOrElse { throw RecommendationProviderException(RecommendationFailureCategory.INVALID_RESPONSE) }
        val content = root.at("/choices/0/message/content").asText(null)
            ?: throw RecommendationProviderException(RecommendationFailureCategory.INVALID_RESPONSE)
        val parsed = runCatching { objectMapper.readTree(content) }
            .getOrElse { throw RecommendationProviderException(RecommendationFailureCategory.INVALID_RESPONSE) }
        if (!parsed.path("recommendations").isArray) {
            throw RecommendationProviderException(RecommendationFailureCategory.INVALID_RESPONSE)
        }
        return StructuredRecommendationResponse(
            recommendations = parsed.path("recommendations").map(::mapItem)
        )
    }

    private fun mapItem(node: JsonNode): StructuredRecommendationItem =
        runCatching {
            StructuredRecommendationItem(
                category = RecommendationCategory.valueOf(node.path("category").asText()),
                priority = TaskPriority.valueOf(node.path("priority").asText()),
                confidence = RecommendationConfidence.valueOf(node.path("confidence").asText()),
                explanation = com.hotelopai.reservation.recommendation.RecommendationExplanation(
                    situation = node.path("explanation").path("situation").asText(),
                    rationale = node.path("explanation").path("rationale").asText(),
                    supportingSignals = node.path("explanation").path("supportingSignals").map { it.asText() }
                ),
                intentType = TaskIntentType.valueOf(node.path("intentType").asText()),
                proposedTaskTitle = node.path("proposedTaskTitle").asText(),
                proposedTaskSummary = node.path("proposedTaskSummary").asText()
            )
        }.getOrElse {
            throw RecommendationProviderException(RecommendationFailureCategory.INVALID_RESPONSE)
        }

    private fun StructuredRecommendationItem.toProposal(
        context: SanitizedReservationRecommendationContext,
        prompt: RecommendationPrompt
    ): RecommendationTaskProposal {
        val dueAt = context.now.plusSeconds(3600)
        val keyInput = "${providerName}:${prompt.version}:${context.contextSchemaVersion}:${context.reservationId}:$category:${context.reservationStatus}:${context.stayStatus}:$proposedTaskTitle"
        return RecommendationTaskProposal(
            category = category,
            confidence = confidence,
            explanation = explanation,
            intentType = intentType,
            title = proposedTaskTitle,
            description = proposedTaskSummary,
            priority = priority,
            dueAt = dueAt,
            deduplicationKey = sha256(keyInput)
        )
    }

    private fun backoffMillis(attempt: Int): Long {
        val multiplier = 1L shl (attempt - 1).coerceAtMost(8)
        return (openAi.retryPolicy.initialBackoff.toMillis() * multiplier)
            .coerceAtMost(openAi.retryPolicy.maxBackoff.toMillis())
    }

    private fun recordRequestMetric(outcome: String, failureCategory: RecommendationFailureCategory?, elapsedMillis: Long) {
        observability.incrementCounter(
            "hotelopai.reservation.task_recommendation.provider_request.total",
            "provider" to providerName,
            "model" to (modelIdentifier ?: "not_configured"),
            "outcome" to outcome,
            "failure_category" to (failureCategory?.name?.lowercase() ?: "none")
        )
        observability.recordTimer(
            "hotelopai.reservation.task_recommendation.provider_request.duration",
            java.time.Duration.ofMillis(elapsedMillis),
            "provider" to providerName,
            "model" to (modelIdentifier ?: "not_configured"),
            "outcome" to outcome
        )
    }

    private fun sha256(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}

private val RecommendationFailureCategory.retryable: Boolean
    get() = this in setOf(
        RecommendationFailureCategory.NETWORK_ERROR,
        RecommendationFailureCategory.TIMEOUT,
        RecommendationFailureCategory.RATE_LIMIT,
        RecommendationFailureCategory.PROVIDER_UNAVAILABLE
    )

private data class OpenAiPromptEnvelope(
    val templateId: String,
    val templateVersion: String,
    val context: OutboundRecommendationContext,
    val outputSchema: com.hotelopai.reservation.recommendation.RecommendationOutputSchema
) {
    constructor(prompt: RecommendationPrompt) : this(
        templateId = prompt.templateId,
        templateVersion = prompt.version,
        context = prompt.context,
        outputSchema = prompt.outputSchema
    )
}

private data class OpenAiChatCompletionRequest(
    val model: String,
    val temperature: Double,
    @com.fasterxml.jackson.annotation.JsonProperty("top_p")
    val topP: Double,
    @com.fasterxml.jackson.annotation.JsonProperty("max_tokens")
    val maxTokens: Int,
    val messages: List<OpenAiChatMessage>
)

private data class OpenAiChatMessage(
    val role: String,
    val content: String
)
