package com.hotelopai.integration.openai

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.hotelopai.assistant.application.AiInterpreter
import com.hotelopai.assistant.application.AssistantInterpretationRequest
import com.hotelopai.assistant.application.AssistantPromptCatalog
import com.hotelopai.assistant.application.ConversationInterpreterPromptBuilder
import com.hotelopai.assistant.application.InterpretationResult
import com.hotelopai.assistant.application.StructuredInterpretationPayload
import com.hotelopai.assistant.application.StructuredInterpretationValidator
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.math.min

class OpenAiInterpreter(
    private val properties: OpenAiProperties,
    private val objectMapper: ObjectMapper,
    private val fallbackInterpreter: AiInterpreter? = null,
    private val fallbackEnabled: Boolean = false,
    private val validator: StructuredInterpretationValidator = StructuredInterpretationValidator()
) : AiInterpreter {
    private val logger = LoggerFactory.getLogger(OpenAiInterpreter::class.java)
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(properties.requestTimeout)
        .build()
    private val schemaNode: JsonNode = objectMapper.readTree(AssistantPromptCatalog.current.schemaJson)

    override fun interpret(request: AssistantInterpretationRequest): InterpretationResult {
        val openAiResult = runCatching {
            interpretWithOpenAi(request)
        }

        return openAiResult.getOrElse { exception ->
            val failure = normalizeFailure(exception)
            if (shouldFallback(failure)) {
                return fallbackInterpretation(request, failure)
            }

            throw failure
        }
    }

    private fun interpretWithOpenAi(
        request: AssistantInterpretationRequest
    ): InterpretationResult {
        ensureConfigIsValid()
        val httpRequest = buildRequest(request)
        val response = executeWithRetry(httpRequest)
        val payload = parsePayload(response)
        return validator.validate(payload)
    }

    private fun ensureConfigIsValid() {
        if (properties.apiKey.isBlank()) {
            throw OpenAiConfigurationException("OpenAI API key is missing")
        }
        if (properties.model.isBlank()) {
            throw OpenAiConfigurationException("OpenAI model is missing")
        }
    }

    private fun buildRequest(
        request: AssistantInterpretationRequest
    ): HttpRequest {
        val requestBody = OpenAiChatCompletionRequestDto(
            model = properties.model,
            messages = listOf(
                OpenAiMessageDto(
                    role = "system",
                    content = AssistantPromptCatalog.current.systemPrompt
                ),
                OpenAiMessageDto(
                    role = "user",
                    content = ConversationInterpreterPromptBuilder.build(request)
                )
            ),
            responseFormat = OpenAiResponseFormatDto(
                jsonSchema = OpenAiJsonSchemaDto(
                    name = "hotel_opai_conversation_interpretation",
                    strict = true,
                    schema = schemaNode
                )
            ),
            maxCompletionTokens = properties.maxOutputTokens,
            temperature = properties.temperature,
            store = false
        )

        return HttpRequest.newBuilder()
            .uri(URI.create("${properties.baseUrl.trimEnd('/')}/v1/chat/completions"))
            .timeout(properties.requestTimeout)
            .header("Authorization", "Bearer ${properties.apiKey}")
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)))
            .build()
    }

    private fun executeWithRetry(request: HttpRequest): HttpResponse<String> {
        var attempt = 1
        val maxAttempts = properties.retries.coerceAtLeast(1)
        var lastFailure: Throwable? = null

        while (attempt <= maxAttempts) {
            try {
                val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
                if (response.statusCode() in 200..299) {
                    return response
                }

                val failure = failureForStatus(response.statusCode())
                if (failure is OpenAiAuthenticationException) {
                    throw failure
                }

                if (!shouldRetryStatus(response.statusCode(), attempt, maxAttempts)) {
                    throw failure
                }
                lastFailure = failure
            } catch (exception: Exception) {
                lastFailure = exception
                val failure = normalizeFailure(exception)
                if (failure is OpenAiAuthenticationException) {
                    throw failure
                }

                if (!shouldRetryException(failure, attempt, maxAttempts)) {
                    throw failure
                }
            }

            sleepBackoff(attempt)
            attempt += 1
        }

        throw normalizeFailure(lastFailure ?: IllegalStateException("OpenAI request failed"))
    }

    private fun parsePayload(response: HttpResponse<String>): StructuredInterpretationPayload {
        val body = response.body()
        if (body.isBlank()) {
            throw OpenAiMalformedResponseException("OpenAI returned an empty response")
        }

        val responseJson = objectMapper.readTree(body)
        val messageNode = responseJson.path("choices").path(0).path("message")
        val refusal = messageNode.path("refusal")
        if (!refusal.isMissingNode && !refusal.isNull && refusal.asText().isNotBlank()) {
            throw OpenAiMalformedResponseException("OpenAI refused to produce an interpretation")
        }

        val content = messageNode.path("content").takeIf { !it.isMissingNode && !it.isNull }?.asText()
            ?: throw OpenAiMalformedResponseException("OpenAI response did not include content")

        if (content.isBlank()) {
            throw OpenAiMalformedResponseException("OpenAI response content was blank")
        }

        return try {
            objectMapper.treeToValue(objectMapper.readTree(content), StructuredInterpretationPayload::class.java)
        } catch (exception: Exception) {
            throw OpenAiMalformedResponseException("OpenAI structured output was malformed", exception)
        }
    }

    private fun normalizeFailure(exception: Throwable): RuntimeException =
        when (exception) {
            is RuntimeException -> exception
            else -> OpenAiProviderException("OpenAI request failed", exception)
        }

    private fun failureForStatus(statusCode: Int): RuntimeException =
        when (statusCode) {
            401, 403 -> OpenAiAuthenticationException("OpenAI authentication failed with status $statusCode")
            429 -> OpenAiRateLimitException("OpenAI rate limit exceeded", statusCode = statusCode)
            in 500..599 -> OpenAiTransientProviderException("OpenAI service returned $statusCode", statusCode = statusCode)
            else -> OpenAiProviderException("OpenAI request failed with status $statusCode")
        }

    private fun shouldRetryStatus(
        statusCode: Int,
        attempt: Int,
        maxAttempts: Int
    ): Boolean =
        attempt < maxAttempts && (statusCode == 429 || statusCode in 500..599)

    private fun shouldRetryException(
        failure: RuntimeException,
        attempt: Int,
        maxAttempts: Int
    ): Boolean =
        attempt < maxAttempts && when (failure) {
            is OpenAiTransientProviderException,
            is OpenAiRateLimitException,
            is OpenAiMalformedResponseException -> false
            is OpenAiAuthenticationException,
            is OpenAiConfigurationException -> false
            else -> failure.cause is IOException ||
                failure.cause is java.net.http.HttpTimeoutException ||
                failure.cause is InterruptedException ||
                failure is OpenAiProviderException && failure !is OpenAiMalformedResponseException
        }

    private fun shouldFallback(failure: RuntimeException): Boolean =
        fallbackEnabled &&
            fallbackInterpreter != null &&
            when (failure) {
                is OpenAiAuthenticationException,
                is OpenAiConfigurationException -> false
                else -> true
            }

    private fun fallbackInterpretation(
        request: AssistantInterpretationRequest,
        failure: RuntimeException
    ): InterpretationResult {
        if (!fallbackEnabled || fallbackInterpreter == null) {
            throw failure
        }

        logger.warn(
            "OpenAI interpretation failed; falling back to deterministic interpreter. reason={}, fallbackEnabled={}",
            failure::class.java.simpleName,
            fallbackEnabled
        )
        return fallbackInterpreter.interpret(request)
    }

    private fun sleepBackoff(attempt: Int) {
        if (attempt >= properties.retries) {
            return
        }

        val delayMillis = min(1000L, 200L * (1L shl (attempt - 1)))
        try {
            Thread.sleep(delayMillis)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }
}

open class OpenAiProviderException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)

class OpenAiConfigurationException(message: String) : OpenAiProviderException(message)

class OpenAiAuthenticationException(message: String) : OpenAiProviderException(message)

class OpenAiRateLimitException(
    message: String,
    val statusCode: Int
) : OpenAiProviderException(message)

class OpenAiTransientProviderException(
    message: String,
    val statusCode: Int
) : OpenAiProviderException(message)

class OpenAiMalformedResponseException(
    message: String,
    cause: Throwable? = null
) : OpenAiProviderException(message, cause)
