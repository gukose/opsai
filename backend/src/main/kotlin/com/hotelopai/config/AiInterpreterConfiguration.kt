package com.hotelopai.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.hotelopai.assistant.application.AiInterpreter
import com.hotelopai.assistant.application.ConversationStateMachine
import com.hotelopai.assistant.application.MockAiInterpreter
import com.hotelopai.integration.openai.OpenAiInterpreter
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary

@Configuration
@EnableConfigurationProperties(AssistantAiProperties::class)
class AiInterpreterConfiguration {
    @Bean
    fun mockAiInterpreter(): MockAiInterpreter = MockAiInterpreter()

    @Bean
    @Primary
    fun aiInterpreter(
        properties: AssistantAiProperties,
        objectMapper: ObjectMapper,
        mockAiInterpreter: MockAiInterpreter
    ): AiInterpreter =
        when (properties.normalizedProvider()) {
            AssistantAiProperties.Provider.DETERMINISTIC -> mockAiInterpreter
            AssistantAiProperties.Provider.OPENAI -> {
                val openAiProperties = properties.openai
                if (openAiProperties.apiKey.isBlank()) {
                    throw IllegalStateException("OpenAI provider requires OPENAI_API_KEY")
                }
                if (openAiProperties.model.isBlank()) {
                    throw IllegalStateException("OpenAI provider requires OPENAI_MODEL")
                }

                OpenAiInterpreter(
                    properties = openAiProperties,
                    objectMapper = objectMapper,
                    fallbackInterpreter = if (properties.fallbackEnabled) mockAiInterpreter else null,
                    fallbackEnabled = properties.fallbackEnabled
                )
            }
        }

    @Bean
    fun conversationStateMachine(aiInterpreter: AiInterpreter): ConversationStateMachine =
        ConversationStateMachine(interpreter = aiInterpreter)
}
