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
@EnableConfigurationProperties(AiInterpreterProperties::class)
class AiInterpreterConfiguration {
    @Bean
    fun mockAiInterpreter(): MockAiInterpreter = MockAiInterpreter()

    @Bean
    @Primary
    fun aiInterpreter(
        properties: AiInterpreterProperties,
        objectMapper: ObjectMapper,
        mockAiInterpreter: MockAiInterpreter
    ): AiInterpreter =
        when (properties.mode) {
            AiInterpreterProperties.Mode.MOCK -> mockAiInterpreter
            AiInterpreterProperties.Mode.OPENAI -> OpenAiInterpreter(
                properties = properties.openai,
                objectMapper = objectMapper,
                fallbackInterpreter = mockAiInterpreter
            )
        }

    @Bean
    fun conversationStateMachine(aiInterpreter: AiInterpreter): ConversationStateMachine =
        ConversationStateMachine(interpreter = aiInterpreter)
}
