package com.hotelopai.integration.openai

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.hotelopai.assistant.application.AiInterpreter
import com.hotelopai.assistant.application.ConversationInterpreterPromptBuilder
import com.hotelopai.assistant.application.DeterministicConversationInterpreter
import com.hotelopai.assistant.application.InterpretationResult
import com.hotelopai.assistant.application.MockAiInterpreter
import com.hotelopai.assistant.domain.Conversation
import com.hotelopai.assistant.domain.IntentType
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.math.min

class OpenAiInterpreter(
    private val properties: com.hotelopai.config.AiInterpreterProperties.OpenAiProperties,
    private val objectMapper: ObjectMapper,
    private val fallbackInterpreter: MockAiInterpreter = MockAiInterpreter()
) : AiInterpreter {
    private val logger = LoggerFactory.getLogger(OpenAiInterpreter::class.java)
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(properties.requestTimeout)
        .build()
    private val schemaNode: JsonNode = objectMapper.readTree(INTERPRETATION_SCHEMA_JSON)

    override fun interpret(conversation: Conversation, userText: String): InterpretationResult {
        if (properties.apiKey.isBlank()) {
            logger.warn("OpenAI API key is missing; falling back to deterministic interpreter.")
            return fallbackInterpreter.interpret(conversation, userText)
        }

        return runCatching {
            interpretWithOpenAi(conversation, userText)
        }.getOrElse { error ->
            logger.warn(
                "OpenAI interpretation failed; falling back to deterministic interpreter. reason={}",
                error.message
            )
            fallbackInterpreter.interpret(conversation, userText)
        }
    }

    private fun interpretWithOpenAi(
        conversation: Conversation,
        userText: String
    ): InterpretationResult {
        val request = buildRequest(conversation, userText)
        val response = executeWithRetry(request)
        val payload = parsePayload(response)
        validatePayload(payload)

        return payload.toInterpretation()
    }

    private fun buildRequest(
        conversation: Conversation,
        userText: String
    ): HttpRequest {
        val requestBody = OpenAiChatCompletionRequestDto(
            model = properties.model,
            messages = listOf(
                OpenAiMessageDto(
                    role = "system",
                    content = systemPrompt()
                ),
                OpenAiMessageDto(
                    role = "user",
                    content = ConversationInterpreterPromptBuilder.build(conversation, userText)
                )
            ),
            responseFormat = OpenAiResponseFormatDto(
                jsonSchema = OpenAiJsonSchemaDto(
                    name = "hotel_opai_conversation_interpretation",
                    strict = true,
                    schema = schemaNode
                )
            ),
            maxCompletionTokens = properties.maxCompletionTokens,
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

                if (!shouldRetry(response.statusCode(), attempt, maxAttempts)) {
                    throw IllegalStateException("OpenAI request failed with status ${response.statusCode()}: ${response.body()}")
                }
            } catch (exception: Exception) {
                lastFailure = exception
                if (!shouldRetry(exception, attempt, maxAttempts)) {
                    throw exception
                }
            }

            sleepBackoff(attempt)
            attempt += 1
        }

        throw lastFailure ?: IllegalStateException("OpenAI request failed")
    }

    private fun parsePayload(response: HttpResponse<String>): OpenAiInterpretationPayloadDto {
        val responseJson = objectMapper.readTree(response.body())
        val messageNode = responseJson.path("choices").path(0).path("message")
        val refusal = messageNode.path("refusal")
        if (!refusal.isMissingNode && !refusal.isNull && refusal.asText().isNotBlank()) {
            throw IllegalStateException("OpenAI refused to produce an interpretation")
        }

        val content = messageNode.path("content").takeIf { !it.isMissingNode && !it.isNull }?.asText()
            ?: throw IllegalStateException("OpenAI response did not include content")

        val contentJson = objectMapper.readTree(content)
        return objectMapper.treeToValue(contentJson, OpenAiInterpretationPayloadDto::class.java)
    }

    private fun validatePayload(payload: OpenAiInterpretationPayloadDto) {
        require(payload.confidence in 0.0..1.0) { "confidence must be between 0 and 1" }
        require(payload.intent.isNotBlank()) { "intent is required" }
        payload.extractedFields.forEach { (key, value) ->
            require(key.isNotBlank()) { "extracted field key must not be blank" }
            require(value.isNotBlank()) { "extracted field value must not be blank" }
        }
        payload.missingFields.forEach {
            require(it.isNotBlank()) { "missing field keys must not be blank" }
        }
        payload.followUpQuestion?.let {
            require(it.isNotBlank()) { "followUpQuestion must not be blank when present" }
        }
    }

    private fun OpenAiInterpretationPayloadDto.toInterpretation(): InterpretationResult =
        InterpretationResult(
            intent = IntentType.entries.firstOrNull { it.name == intent } ?: IntentType.UNKNOWN,
            fields = extractedFields.filterValues { it.isNotBlank() },
            confidence = confidence,
            language = language?.takeIf { it.isNotBlank() },
            followUpQuestion = followUpQuestion?.takeIf { it.isNotBlank() },
            missingFields = missingFields.filter { it.isNotBlank() }
        )

    private fun shouldRetry(
        statusCode: Int,
        attempt: Int,
        maxAttempts: Int
    ): Boolean =
        attempt < maxAttempts && (statusCode == 429 || statusCode >= 500)

    private fun shouldRetry(
        exception: Exception,
        attempt: Int,
        maxAttempts: Int
    ): Boolean =
        attempt < maxAttempts && (
            exception is IOException ||
                exception is java.net.http.HttpTimeoutException ||
                exception is InterruptedException
            )

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

    private fun systemPrompt(): String = """
        You are the Hotel OpAI conversation interpreter.
        Analyze multilingual hotel operations requests and return only JSON that matches the schema.
        Rules:
        - Detect the most likely intent from the supported hotel operations list.
        - Extract any room, description, asset, area, minibar, or other operational fields from the latest user message and current conversation context.
        - If confidence is low, provide a follow-up question instead of inventing a task.
        - Never create tasks.
        - Preserve the user language in follow-up questions when possible.
        - Return compact, valid JSON only.
    """.trimIndent()

    private companion object {
        val INTERPRETATION_SCHEMA_JSON = """
        {
          "type": "object",
          "additionalProperties": false,
          "required": ["intent", "confidence", "language", "extractedFields", "missingFields", "followUpQuestion"],
          "properties": {
            "intent": {
              "type": "string",
              "enum": [
                "GUEST_REQUEST",
                "MAINTENANCE",
                "HOUSEKEEPING",
                "DAMAGE_REPORT",
                "LOST_AND_FOUND",
                "TRAY_REMOVAL",
                "LAUNDRY",
                "MINIBAR",
                "FLASH_TASK",
                "SHIFT_HANDOVER",
                "PUBLIC_AREA",
                "INVENTORY",
                "DELIVERIES",
                "UNKNOWN"
              ]
            },
            "confidence": {
              "type": "number",
              "minimum": 0,
              "maximum": 1
            },
            "language": {
              "type": ["string", "null"]
            },
            "extractedFields": {
              "type": "object",
              "additionalProperties": {
                "type": "string"
              }
            },
            "missingFields": {
              "type": "array",
              "items": {
                "type": "string"
              }
            },
            "followUpQuestion": {
              "type": ["string", "null"]
            }
          }
        }
        """.trimIndent()
    }
}
