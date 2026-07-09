package com.hotelopai.integration.unimock

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.hotelopai.integration.unimock.UniMockClient
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(UniMockClientProperties::class)
class UniMockClientConfiguration {
    @Bean
    fun objectMapper(): ObjectMapper =
        jacksonObjectMapper().findAndRegisterModules()

    @Bean
    fun uniMockClient(
        properties: UniMockClientProperties,
        objectMapper: ObjectMapper
    ): RestUniMockClient =
        RestUniMockClient(
            properties = properties,
            objectMapper = objectMapper
        )
}
