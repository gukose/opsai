package com.hotelopai.shared.api

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator
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
import java.nio.file.Files
import java.nio.file.Path

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class OpenApiContractSnapshotTest : PostgresIntegrationTestSupport() {
    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    private val httpClient = HttpClient.newHttpClient()
    private val yamlMapper = ObjectMapper(
        YAMLFactory()
            .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
            .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES)
    )

    @Test
    fun `runtime v1 contract matches committed snapshot`() {
        val runtime = normalizedRuntimeContract()
        val snapshotPath = Path.of("..", "docs", "api", "openapi-v1.yaml").normalize()
        val mode = System.getProperty("hotelopai.openapi.contract.mode", "verify")

        if (mode == "refresh") {
            Files.writeString(snapshotPath, yamlMapper.writeValueAsString(runtime))
            return
        }

        val snapshot = normalize(yamlMapper.readTree(Files.readString(snapshotPath)))
        val firstDifference = firstDifference(snapshot, runtime)
        assertThat(snapshot)
            .withFailMessage(
                "Runtime OpenAPI differs from docs/api/openapi-v1.yaml. " +
                    "First difference: $firstDifference. " +
                    "Run ./gradlew :backend:refreshOpenApiContract after reviewing intentional API changes."
            )
            .isEqualTo(runtime)
    }

    private fun normalizedRuntimeContract(): JsonNode {
        val response = get("/v3/api-docs/v1")
        assertThat(response.statusCode()).isEqualTo(200)
        return normalize(objectMapper.readTree(response.body()))
    }

    private fun normalize(node: JsonNode): JsonNode {
        val copy = node.deepCopy<ObjectNode>()
        copy.remove("servers")
        return copy
    }

    private fun get(path: String): HttpResponse<String> {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$port$path"))
            .GET()
            .build()

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString())
    }

    private fun firstDifference(left: JsonNode, right: JsonNode, path: String = ""): String {
        if (left == right) {
            return "none"
        }
        if (left.nodeType != right.nodeType) {
            return "$path type ${left.nodeType} != ${right.nodeType}; snapshot=$left runtime=$right"
        }
        if (left.isObject) {
            val leftNames = left.fieldNames().asSequence().toSet()
            val rightNames = right.fieldNames().asSequence().toSet()
            val missing = rightNames - leftNames
            if (missing.isNotEmpty()) {
                return "$path missing in snapshot: ${missing.first()}"
            }
            val extra = leftNames - rightNames
            if (extra.isNotEmpty()) {
                return "$path extra in snapshot: ${extra.first()}"
            }
            return leftNames.sorted().firstNotNullOfOrNull { name ->
                firstDifference(left.get(name), right.get(name), "$path/$name").takeIf { it != "none" }
            } ?: "none"
        }
        if (left.isArray) {
            if (left.size() != right.size()) {
                return "$path array size ${left.size()} != ${right.size()}"
            }
            for (index in 0 until left.size()) {
                val difference = firstDifference(left.get(index), right.get(index), "$path/$index")
                if (difference != "none") {
                    return difference
                }
            }
            return "none"
        }
        return "$path snapshot=$left runtime=$right"
    }
}
