package com.hotelopai.shared.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.hotelopai.support.PostgresIntegrationTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Bean
import org.springframework.test.context.ActiveProfiles
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class OpenApiDeprecationMetadataIntegrationTest : PostgresIntegrationTestSupport() {
    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    private val httpClient = HttpClient.newHttpClient()

    @Test
    fun `deprecated endpoint metadata includes deprecation headers in OpenAPI`() {
        val response = get("/v3/api-docs/v1")
        val root = objectMapper.readTree(response.body())
        val endpoint = root.at("/paths/~1api~1v1~1contract-test~1deprecated/get")

        assertThat(response.statusCode()).isEqualTo(200)
        assertThat(endpoint.path("deprecated").asBoolean()).isTrue()
        assertThat(endpoint.at("/responses/200/headers/Deprecation").isMissingNode).isFalse()
        assertThat(endpoint.at("/responses/200/headers/Sunset").isMissingNode).isFalse()
        assertThat(endpoint.at("/responses/200/headers/Link").isMissingNode).isFalse()
        assertThat(endpoint.at("/responses/200/headers/X-API-Deprecation-Info").isMissingNode).isFalse()
    }

    private fun get(path: String): HttpResponse<String> {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$port$path"))
            .GET()
            .build()

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString())
    }

    @TestConfiguration
    class DeprecatedEndpointConfiguration {
        @Bean
        fun deprecatedContractTestController(): DeprecatedContractTestController = DeprecatedContractTestController()
    }

    @RestController
    @RequestMapping("/api/v1/contract-test")
    class DeprecatedContractTestController {
        @GetMapping("/deprecated")
        @DeprecatedApi(
            sunset = "2027-01-01T00:00:00Z",
            link = "https://docs.hotelopai.example/deprecations/contract-test",
            message = "Use the replacement endpoint."
        )
        fun deprecatedEndpoint(): Map<String, String> = mapOf("status" to "ok")
    }
}
