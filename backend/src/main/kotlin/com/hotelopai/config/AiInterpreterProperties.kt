package com.hotelopai.config

import com.hotelopai.integration.openai.OpenAiProperties
import org.springframework.boot.context.properties.ConfigurationProperties

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
}
