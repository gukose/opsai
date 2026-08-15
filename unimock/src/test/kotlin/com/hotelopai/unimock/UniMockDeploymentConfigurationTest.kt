package com.hotelopai.unimock

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.env.YamlPropertySourceLoader
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.StandardEnvironment
import org.springframework.core.io.ClassPathResource

class UniMockDeploymentConfigurationTest {
    @Test
    fun `Railway port takes precedence over compatibility server port`() {
        val environment = StandardEnvironment()
        environment.propertySources.addFirst(MapPropertySource("railway", mapOf(
            "PORT" to "17432",
            "SERVER_PORT" to "8090"
        )))
        YamlPropertySourceLoader().load("application", ClassPathResource("application.yml"))
            .reversed().forEach(environment.propertySources::addLast)

        assertThat(environment.getProperty("server.address")).isEqualTo("0.0.0.0")
        assertThat(environment.getProperty("server.port")).isEqualTo("17432")
    }

    @Test
    fun `UniMock retains its local default port`() {
        val environment = StandardEnvironment()
        YamlPropertySourceLoader().load("application", ClassPathResource("application.yml"))
            .reversed().forEach(environment.propertySources::addLast)

        assertThat(environment.getProperty("server.port")).isEqualTo("8090")
    }
}
