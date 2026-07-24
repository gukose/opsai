package com.hotelopai.shared.api

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.hotelopai.support.PostgresIntegrationTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.ActiveProfiles
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class OpenApiRuntimeIntegrationTest : PostgresIntegrationTestSupport() {
    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    private val httpClient = HttpClient.newHttpClient()

    @Test
    fun `runtime v1 OpenAPI is published for public API surface`() {
        val response = get("/v3/api-docs/v1")
        val root = json(response)
        val paths = root.path("paths")

        assertThat(response.statusCode()).isEqualTo(200)
        assertThat(paths.has("/api/v1/auth/login")).isTrue()
        assertThat(paths.has("/api/v1/auth/refresh")).isTrue()
        assertThat(paths.has("/api/v1/tasks")).isTrue()
        assertThat(paths.has("/api/v1/assistant/conversations")).isTrue()
        assertThat(paths.has("/api/v1/notifications")).isTrue()
        assertThat(paths.has("/api/v1/dashboard/summary")).isTrue()
        assertThat(paths.has("/api/v1/dashboard/reports/tasks")).isTrue()
        assertThat(paths.has("/api/v1/dev/pms/rooms")).isFalse()
        assertThat(paths.has("/api/v1/auth/context")).isFalse()
        assertThat(paths.has("/actuator/health")).isFalse()
    }

    @Test
    fun `development PMS OpenAPI is separated from public v1 group`() {
        val response = get("/v3/api-docs/dev-pms")
        val paths = json(response).path("paths")

        assertThat(response.statusCode()).isEqualTo(200)
        assertThat(paths.has("/api/v1/dev/pms/rooms")).isTrue()
        assertThat(paths.has("/api/v1/tasks")).isFalse()
    }

    @Test
    fun `security and shared error schemas are documented`() {
        val root = json(get("/v3/api-docs/v1"))

        assertThat(root.at("/components/securitySchemes/bearerAuth/type").asText()).isEqualTo("http")
        assertThat(root.at("/components/securitySchemes/bearerAuth/scheme").asText()).isEqualTo("bearer")
        assertThat(root.at("/components/schemas/ProblemDetail").isMissingNode).isFalse()

        val login = root.at("/paths/~1api~1v1~1auth~1login/post")
        val tasks = root.at("/paths/~1api~1v1~1tasks/get")

        assertThat(login.has("security")).isTrue()
        assertThat(login.path("security")).isEmpty()
        assertThat(tasks.path("security").toString()).contains("bearerAuth")
        assertThat(tasks.at("/responses/401/content/application~1problem+json/schema/\$ref").asText())
            .isEqualTo("#/components/schemas/ProblemDetail")
    }

    @Test
    fun `common response headers and pagination schema are documented`() {
        val root = json(get("/v3/api-docs/v1"))
        val taskList = root.at("/paths/~1api~1v1~1tasks/get")

        assertThat(taskList.at("/responses/200/headers/X-API-Version/schema/default").asText()).isEqualTo("v1")
        assertThat(root.at("/components/schemas/TaskPageResponse/properties/items").isMissingNode).isFalse()
    }

    @Test
    fun `operation ids are present unique and component references resolve`() {
        val root = json(get("/v3/api-docs/v1"))
        val operationIds = mutableListOf<String>()

        root.path("paths").fields().forEach { (_, pathItem) ->
            pathItem.fields().forEach { (_, operation) ->
                operationIds += operation.path("operationId").asText()
            }
        }

        assertThat(operationIds).allSatisfy { assertThat(it).isNotBlank() }
        assertThat(operationIds).doesNotHaveDuplicates()
        unresolvedRefs(root).let { unresolved ->
            assertThat(unresolved).isEmpty()
        }
    }

    @Test
    fun `swagger UI follows test environment policy`() {
        val response = get("/swagger-ui/index.html")

        assertThat(response.statusCode()).isIn(401, 404)
    }

    private fun get(path: String): HttpResponse<String> {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$port$path"))
            .GET()
            .build()

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString())
    }

    private fun json(response: HttpResponse<String>): JsonNode {
        assertThat(response.body()).isNotBlank()
        return objectMapper.readTree(response.body())
    }

    private fun unresolvedRefs(root: JsonNode): List<String> {
        val refs = mutableListOf<String>()
        collectRefs(root, refs)
        return refs.filterNot { ref ->
            ref.startsWith("#/") && root.at(ref.removePrefix("#")).isMissingNode.not()
        }
    }

    private fun collectRefs(node: JsonNode, refs: MutableList<String>) {
        when {
            node.isObject -> node.fields().forEach { (field, value) ->
                if (field == "\$ref" && value.isTextual) {
                    refs += value.asText()
                }
                collectRefs(value, refs)
            }
            node.isArray -> node.forEach { collectRefs(it, refs) }
        }
    }
}
