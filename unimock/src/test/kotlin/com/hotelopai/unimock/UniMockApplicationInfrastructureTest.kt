package com.hotelopai.unimock

import com.hotelopai.unimock.support.UnimockPostgresIntegrationTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class UniMockApplicationInfrastructureTest : UnimockPostgresIntegrationTestSupport() {
    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    private val httpClient = HttpClient.newHttpClient()

    @Test
    fun `application context loads and health endpoint is up`() {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$port/actuator/health"))
            .GET()
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        assertThat(response.statusCode()).isEqualTo(200)
        assertThat(response.body()).contains("UP")
    }

    @Test
    fun `flyway runs and unimock schema exists`() {
        val schemaCount = jdbcTemplate.queryForObject(
            "select count(*) from information_schema.schemata where schema_name = 'unimock'",
            Long::class.java
        ) ?: 0L

        assertThat(schemaCount).isEqualTo(1L)

        val flywayHistoryCount = jdbcTemplate.queryForObject(
            "select count(*) from unimock.flyway_schema_history where success = true",
            Long::class.java
        ) ?: 0L

        assertThat(flywayHistoryCount).isGreaterThanOrEqualTo(1L)
    }

    @Test
    fun `pms mock verification log table exists`() {
        val tableName = jdbcTemplate.queryForObject(
            "select to_regclass('unimock.pms_mock_verification_log')",
            String::class.java
        )

        assertThat(tableName).isEqualTo("unimock.pms_mock_verification_log")
    }
}
