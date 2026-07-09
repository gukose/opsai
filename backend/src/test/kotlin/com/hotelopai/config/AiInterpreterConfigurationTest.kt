package com.hotelopai.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.hotelopai.assistant.application.MockAiInterpreter
import com.hotelopai.integration.openai.OpenAiInterpreter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class AiInterpreterConfigurationTest {
    private val configuration = AiInterpreterConfiguration()
    private val objectMapper = ObjectMapper()

    @Test
    fun `deterministic provider is selected by configuration`() {
        val properties = assistantProperties(provider = "deterministic")

        val interpreter = configuration.aiInterpreter(properties, objectMapper, MockAiInterpreter())

        assertInstanceOf(MockAiInterpreter::class.java, interpreter)
    }

    @Test
    fun `openai provider is selected by configuration`() {
        val properties = assistantProperties(
            provider = "openai",
            apiKey = "local-test-key",
            model = "local-test-model"
        )

        val interpreter = configuration.aiInterpreter(properties, objectMapper, MockAiInterpreter())

        assertInstanceOf(OpenAiInterpreter::class.java, interpreter)
    }

    @Test
    fun `unsupported provider fails clearly`() {
        val properties = assistantProperties(provider = "unknown")

        val exception = assertThrows(IllegalStateException::class.java) {
            configuration.aiInterpreter(properties, objectMapper, MockAiInterpreter())
        }

        assertEquals("Unsupported ASSISTANT_AI_PROVIDER value: unknown", exception.message)
    }

    @Test
    fun `deterministic provider starts without an api key`() {
        val properties = assistantProperties(provider = "deterministic", apiKey = "")

        val interpreter = configuration.aiInterpreter(properties, objectMapper, MockAiInterpreter())

        assertInstanceOf(MockAiInterpreter::class.java, interpreter)
    }

    @Test
    fun `openai provider fails clearly when api key is missing`() {
        val properties = assistantProperties(
            provider = "openai",
            apiKey = "",
            model = "local-test-model"
        )

        val exception = assertThrows(IllegalStateException::class.java) {
            configuration.aiInterpreter(properties, objectMapper, MockAiInterpreter())
        }

        assertEquals("OpenAI provider requires OPENAI_API_KEY", exception.message)
    }

    private fun assistantProperties(
        provider: String,
        apiKey: String = "",
        model: String = ""
    ): AssistantAiProperties =
        AssistantAiProperties().apply {
            this.provider = provider
            this.fallbackEnabled = true
            this.openai.apiKey = apiKey
            this.openai.model = model
        }
}
