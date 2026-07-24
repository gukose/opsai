package com.hotelopai.shared.api.compat

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import java.nio.file.Files
import java.nio.file.Path

@EnabledIfSystemProperty(named = "hotelopai.openapi.compatibility.task", matches = "true")
class OpenApiCompatibilityTaskTest {
    private val mapper = ObjectMapper(YAMLFactory())

    @Test
    fun `current OpenAPI snapshot is compatible with baseline and changelog is current`() {
        val mode = System.getProperty("hotelopai.openapi.compatibility.mode", "check")
        val docsRoot = Path.of("..", "docs", "api").normalize()
        val currentPath = docsRoot.resolve("openapi-v1.yaml")
        val acknowledgementPath = docsRoot.resolve("api-change-acknowledgements.yaml")
        val changelogPath = docsRoot.resolve("CHANGELOG.md")
        val baseline = OpenApiBaselineResolver().resolve()
        val current = OpenApiCompatibility.read(currentPath)
        val baselineNode = baseline.content?.let { OpenApiCompatibility.normalize(mapper.readTree(it)) } ?: current
        val changes = if (baseline.bootstrap && baseline.content == null) {
            emptyList()
        } else {
            OpenApiCompatibility.compare(baselineNode, current)
        }
        val acknowledgements = OpenApiAcknowledgements.read(acknowledgementPath)
        val result = OpenApiCompatibility.evaluate(changes, acknowledgements)
        val changelog = OpenApiCompatibility.changelog(changes, acknowledgements)

        when (mode) {
            "changelog" -> {
                Files.writeString(changelogPath, changelog)
            }
            else -> {
                assertThat(result.failed)
                    .withFailMessage(result.failureMessage())
                    .isFalse()
                assertThat(Files.readString(changelogPath))
                    .withFailMessage("API changelog is stale. Run ./gradlew :backend:generateOpenApiChangelog.")
                    .isEqualTo(changelog)
            }
        }
    }
}
