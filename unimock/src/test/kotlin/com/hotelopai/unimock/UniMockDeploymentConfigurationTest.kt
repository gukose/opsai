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
    fun `Railway profile binds IPv6 wildcard and injected port with health exposed`() {
        val environment = StandardEnvironment()
        environment.propertySources.addFirst(MapPropertySource("railway", mapOf(
            "spring.profiles.active" to "prod",
            "PORT" to "17432",
            "SERVER_PORT" to "8090"
        )))
        YamlPropertySourceLoader().load("application", ClassPathResource("application.yml"))
            .reversed().forEach(environment.propertySources::addLast)
        YamlPropertySourceLoader().load("application-prod", ClassPathResource("application-prod.yml"))
            .reversed().forEach(environment.propertySources::addLast)

        assertThat(environment.getProperty("server.address")).isEqualTo("::")
        assertThat(environment.getProperty("server.port")).isEqualTo("17432")
        assertThat(environment.getProperty("management.endpoints.web.exposure.include"))
            .contains("health")
        assertThat(environment.getProperty("management.endpoint.health.probes.enabled"))
            .isEqualTo("true")
    }

    @Test
    fun `UniMock retains its local default port`() {
        val environment = StandardEnvironment()
        YamlPropertySourceLoader().load("application", ClassPathResource("application.yml"))
            .reversed().forEach(environment.propertySources::addLast)

        assertThat(environment.getProperty("server.port")).isEqualTo("8090")
        assertThat(environment.getProperty("server.address")).isEqualTo("::")
    }

    @Test
    fun `console history migration is packaged in the UniMock migration location`() {
        val original = ClassPathResource("db/migration/V5__create_pms_demo_console_history.sql")
        val history = ClassPathResource("db/migration/V6__create_pms_demo_console_history_in_unimock_schema.sql")
        assertThat(original.exists()).isTrue()
        assertThat(history.exists()).isTrue()
        assertThat(original.inputStream.bufferedReader().readText())
            .contains("create table pms_demo_console_event")
        assertThat(history.inputStream.bufferedReader().readText())
            .contains("unimock.pms_demo_console_event")
    }
}
