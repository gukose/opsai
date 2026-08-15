package com.hotelopai.unimock

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.env.YamlPropertySourceLoader
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.StandardEnvironment
import org.springframework.core.io.ClassPathResource
import java.nio.file.Path

class UniMockDeploymentConfigurationTest {
    @Test
    fun `Railway probes the exposed aggregate health endpoint`() {
        val workingDirectory = Path.of(System.getProperty("user.dir"))
        val railwayConfig = sequenceOf(workingDirectory, workingDirectory.parent)
            .map { it.resolve("railway.unimock.json") }
            .first { it.toFile().isFile }
            .toFile()
        val root = ObjectMapper().readTree(railwayConfig)

        assertThat(root.path("deploy").path("healthcheckPath").asText())
            .isEqualTo("/actuator/health")
    }

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
