package com.hotelopai.integration.openai

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.JsonNode

data class OpenAiChatCompletionResponseDto(
    val choices: List<OpenAiChatCompletionChoiceDto> = emptyList()
)

data class OpenAiChatCompletionChoiceDto(
    val message: OpenAiChatCompletionMessageDto
)

data class OpenAiChatCompletionMessageDto(
    val content: String? = null,
    val refusal: String? = null
)

data class OpenAiChatCompletionRequestDto(
    val model: String,
    val messages: List<OpenAiMessageDto>,
    @JsonProperty("response_format")
    val responseFormat: OpenAiResponseFormatDto,
    @JsonProperty("max_completion_tokens")
    val maxCompletionTokens: Int,
    val temperature: Double = 0.0,
    val store: Boolean = false
)

data class OpenAiMessageDto(
    val role: String,
    val content: String
)

data class OpenAiResponseFormatDto(
    val type: String = "json_schema",
    @JsonProperty("json_schema")
    val jsonSchema: OpenAiJsonSchemaDto
)

data class OpenAiJsonSchemaDto(
    val name: String,
    val strict: Boolean = true,
    val schema: JsonNode
)
