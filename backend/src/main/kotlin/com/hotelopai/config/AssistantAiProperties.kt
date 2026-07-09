package com.hotelopai.config

import com.hotelopai.integration.openai.OpenAiProperties
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "assistant.ai")
class AssistantAiProperties {
    var provider: String = "deterministic"
    var fallbackEnabled: Boolean = true
    var confidenceThreshold: Double = 0.65
    var openai: OpenAiProperties = OpenAiProperties()

    fun normalizedProvider(): Provider =
        when (provider.trim().lowercase()) {
            "" -> throw IllegalStateException("ASSISTANT_AI_PROVIDER is required")
            "deterministic" -> Provider.DETERMINISTIC
            "openai" -> Provider.OPENAI
            else -> throw IllegalStateException("Unsupported ASSISTANT_AI_PROVIDER value: $provider")
        }

    override fun toString(): String =
        "AssistantAiProperties(provider=${provider.trim().ifBlank { "deterministic" }}, fallbackEnabled=$fallbackEnabled, confidenceThreshold=$confidenceThreshold, openai=$openai)"

    enum class Provider {
        DETERMINISTIC,
        OPENAI
    }
}
