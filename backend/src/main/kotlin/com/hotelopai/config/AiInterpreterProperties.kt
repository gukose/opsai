package com.hotelopai.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "ops.ai.interpreter")
data class AiInterpreterProperties(
    val mode: Mode = Mode.MOCK,
    val confidenceThreshold: Double = 0.65,
    val openai: OpenAiProperties = OpenAiProperties()
) {
    enum class Mode {
        MOCK,
        OPENAI
    }

    data class OpenAiProperties(
        val apiKey: String = "",
        val baseUrl: String = "https://api.openai.com",
        val model: String = "gpt-4.1-mini",
        val requestTimeout: Duration = Duration.ofSeconds(20),
        val maxCompletionTokens: Int = 400,
        val temperature: Double = 0.0,
        val retries: Int = 1
    )
}
