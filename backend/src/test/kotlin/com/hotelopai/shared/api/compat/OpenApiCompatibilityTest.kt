package com.hotelopai.shared.api.compat

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class OpenApiCompatibilityTest {
    private val mapper = ObjectMapper(YAMLFactory())

    @Test
    fun `removed endpoint method response field enum value required request field and auth addition are breaking`() {
        val old = contract(
            paths = """
              /tasks:
                get:
                  responses:
                    "200":
                      content:
                        application/json:
                          schema:
                            ${ref("TaskResponse")}
                    "202":
                      description: Accepted
                post:
                  security: []
                  requestBody:
                    content:
                      application/json:
                        schema:
                          ${ref("CreateTaskRequest")}
                  responses:
                    "200":
                      description: OK
                    "202":
                      description: Accepted
            """,
            schemas = """
              TaskResponse:
                type: object
                properties:
                  id: { type: string }
                  status:
                    type: string
                    enum: [CREATED, STARTED, COMPLETED]
                  readAt:
                    type: string
                    nullable: true
                  legacy: { type: string }
              CreateTaskRequest:
                type: object
                properties:
                  title: { type: string }
                  priority: { type: string }
                required: [title]
            """
        )
        val new = contract(
            paths = """
              /tasks:
                post:
                  security:
                    - bearerAuth: []
                  requestBody:
                    content:
                      application/json:
                        schema:
                          ${ref("CreateTaskRequest")}
                  responses:
                    "200":
                      description: OK
            """,
            schemas = """
              TaskResponse:
                type: object
                properties:
                  id: { type: string }
                  status:
                    type: string
                    enum: [CREATED, COMPLETED]
                  readAt:
                    type: string
                    nullable: false
              CreateTaskRequest:
                type: object
                properties:
                  title: { type: string }
                  priority: { type: string }
                required: [title, priority]
            """
        )

        val changes = OpenApiCompatibility.compare(read(old), read(new))

        assertThat(changes).anySatisfy { assertBreaking(it, "method.removed", "/tasks#get") }
        assertThat(changes).anySatisfy { assertBreaking(it, "response.removed", "/tasks#post response:202") }
        assertThat(changes).anySatisfy { assertBreaking(it, "property.removed", "schema:TaskResponse.legacy") }
        assertThat(changes).anySatisfy { assertBreaking(it, "enum.removed", "schema:TaskResponse.status enum:STARTED") }
        assertThat(changes).anySatisfy { assertBreaking(it, "schema.nullable.removed", "schema:TaskResponse.readAt") }
        assertThat(changes).anySatisfy { assertBreaking(it, "property.required.added", "schema:CreateTaskRequest.priority") }
        assertThat(changes).anySatisfy { assertBreaking(it, "security.added", "/tasks#post") }
    }

    @Test
    fun `narrowed limits format changes and composition changes are potentially breaking`() {
        val old = schemaOnly(
            """
            Sample:
              type: object
              properties:
                name:
                  type: string
                  maxLength: 100
                happenedAt:
                  type: string
                  format: date-time
                shape:
                  oneOf:
                    - type: string
            """
        )
        val new = schemaOnly(
            """
            Sample:
              type: object
              properties:
                name:
                  type: string
                  maxLength: 50
                happenedAt:
                  type: string
                  format: date
                shape:
                  oneOf:
                    - type: integer
            """
        )

        val changes = OpenApiCompatibility.compare(read(old), read(new))

        assertThat(changes).anySatisfy { assertPotential(it, "schema.limit.narrowed", "schema:Sample.name maxLength") }
        assertThat(changes).anySatisfy { assertPotential(it, "schema.format.changed", "schema:Sample.happenedAt") }
        assertThat(changes).anySatisfy { assertPotential(it, "schema.composition.changed", "schema:Sample.shape") }
    }

    @Test
    fun `schema reference changes are potentially breaking`() {
        val old = contract(
            paths = """
              /tasks:
                get:
                  responses:
                    "200":
                      content:
                        application/json:
                          schema:
                            ${ref("TaskResponse")}
            """,
            schemas = """
              TaskResponse:
                type: object
              OtherResponse:
                type: object
            """
        )
        val new = contract(
            paths = """
              /tasks:
                get:
                  responses:
                    "200":
                      content:
                        application/json:
                          schema:
                            ${ref("OtherResponse")}
            """,
            schemas = """
              TaskResponse:
                type: object
              OtherResponse:
                type: object
            """
        )

        val changes = OpenApiCompatibility.compare(read(old), read(new))

        assertThat(changes).anySatisfy { assertPotential(it, "schema.ref.changed", "/tasks#get response:200") }
    }

    @Test
    fun `added endpoint optional request field response field enum value and response status are backward compatible`() {
        val old = contract(
            paths = """
              /tasks:
                post:
                  requestBody:
                    content:
                      application/json:
                        schema:
                          ${ref("CreateTaskRequest")}
                  responses:
                    "200":
                      content:
                        application/json:
                          schema:
                            ${ref("TaskResponse")}
            """,
            schemas = """
              CreateTaskRequest:
                type: object
                properties:
                  title: { type: string }
                required: [title]
              TaskResponse:
                type: object
                properties:
                  id: { type: string }
                  status:
                    type: string
                    enum: [CREATED]
            """
        )
        val new = contract(
            paths = """
              /tasks:
                post:
                  requestBody:
                    content:
                      application/json:
                        schema:
                          ${ref("CreateTaskRequest")}
                  responses:
                    "200":
                      content:
                        application/json:
                          schema:
                            ${ref("TaskResponse")}
                    "202":
                      description: Accepted
              /notifications:
                get:
                  responses:
                    "200": { description: OK }
            """,
            schemas = """
              CreateTaskRequest:
                type: object
                properties:
                  title: { type: string }
                  note: { type: string }
                required: [title]
              TaskResponse:
                type: object
                properties:
                  id: { type: string }
                  status:
                    type: string
                    enum: [CREATED, STARTED]
                  extra: { type: string }
            """
        )

        val changes = OpenApiCompatibility.compare(read(old), read(new))

        assertThat(changes).anySatisfy { assertCompatible(it, "path.added", "/notifications") }
        assertThat(changes).anySatisfy { assertCompatible(it, "property.added", "schema:CreateTaskRequest.note") }
        assertThat(changes).anySatisfy { assertCompatible(it, "property.added", "schema:TaskResponse.extra") }
        assertThat(changes).anySatisfy { assertCompatible(it, "enum.added", "schema:TaskResponse.status enum:STARTED") }
        assertThat(changes).anySatisfy { assertCompatible(it, "response.added", "/tasks#post response:202") }
    }

    @Test
    fun `description examples formatting and key order changes are documentation only or normalized away`() {
        val old = contract(
            paths = """
              /tasks:
                get:
                  summary: Old summary
                  responses:
                    "200":
                      description: Old description
                      content:
                        application/json:
                          schema:
                            ${ref("TaskResponse")}
            """,
            schemas = """
              TaskResponse:
                description: Old schema description
                type: object
                properties:
                  id:
                    example: old
                    type: string
            """
        )
        val new = contract(
            paths = """
              /tasks:
                get:
                  summary: New summary
                  responses:
                    "200":
                      description: New description
                      content:
                        application/json:
                          schema:
                            ${ref("TaskResponse")}
            """,
            schemas = """
              TaskResponse:
                type: object
                description: New schema description
                properties:
                  id:
                    type: string
                    example: new
            """
        )

        assertThat(OpenApiCompatibility.compare(read(old), read(new))).isEmpty()
    }

    @Test
    fun `governance fails unacknowledged risks stale duplicates and forbidden v1 breaking changes`() {
        val breaking = ApiChange(
            id = "breaking:path.removed:/tasks",
            classification = ApiChangeClassification.BREAKING,
            location = "/tasks",
            summary = "Endpoint path removed"
        )
        val acknowledged = ApiAcknowledgement(
            id = breaking.id,
            apiVersion = "v1",
            classification = ApiChangeClassification.BREAKING,
            reason = "Intentional",
            migration = "Use v2",
            sprint = "Sprint 10C",
            deprecationStatus = "not_deprecated"
        )

        val result = OpenApiCompatibility.evaluate(listOf(breaking), listOf(acknowledged, acknowledged, acknowledged.copy(id = "stale")))

        assertThat(result.failed).isTrue()
        assertThat(result.duplicateAcknowledgementIds).contains(breaking.id)
        assertThat(result.staleAcknowledgementIds).contains("stale")
        assertThat(result.forbiddenBreakingIds).contains(breaking.id)
    }

    @Test
    fun `unacknowledged risky changes fail and acknowledged potentially breaking change passes`() {
        val potentiallyBreaking = ApiChange(
            id = "potentially_breaking:schema.format.changed:schema:Sample.happenedAt",
            classification = ApiChangeClassification.POTENTIALLY_BREAKING,
            location = "schema:Sample.happenedAt",
            summary = "Schema format changed"
        )

        val unacknowledged = OpenApiCompatibility.evaluate(listOf(potentiallyBreaking), emptyList())
        assertThat(unacknowledged.failed).isTrue()
        assertThat(unacknowledged.unacknowledgedRiskIds).contains(potentiallyBreaking.id)

        val acknowledged = OpenApiCompatibility.evaluate(
            listOf(potentiallyBreaking),
            listOf(
                ApiAcknowledgement(
                    id = potentiallyBreaking.id,
                    apiVersion = "v1",
                    classification = ApiChangeClassification.POTENTIALLY_BREAKING,
                    reason = "Format metadata corrected",
                    migration = "No payload change",
                    sprint = "Sprint 10C",
                    deprecationStatus = "not_applicable"
                )
            )
        )
        assertThat(acknowledged.failed).isFalse()
    }

    @Test
    fun `contract correction acknowledgement can allow documented breaking correction`() {
        val breaking = ApiChange(
            id = "breaking:response.removed:/tasks#get response:418",
            classification = ApiChangeClassification.BREAKING,
            location = "/tasks#get response:418",
            summary = "Incorrect response removed"
        )
        val acknowledgement = ApiAcknowledgement(
            id = breaking.id,
            apiVersion = "v1",
            classification = ApiChangeClassification.BREAKING,
            reason = "Contract documented a response never returned by runtime",
            migration = "No client migration required",
            sprint = "Sprint 10C",
            deprecationStatus = "not_applicable",
            contractCorrection = true
        )

        assertThat(OpenApiCompatibility.evaluate(listOf(breaking), listOf(acknowledgement)).failed).isFalse()
    }

    @Test
    fun `changelog output is deterministic and includes acknowledgement guidance`() {
        val change = ApiChange(
            id = "potentially_breaking:schema.format.changed:schema:Sample.happenedAt",
            classification = ApiChangeClassification.POTENTIALLY_BREAKING,
            location = "schema:Sample.happenedAt",
            summary = "Schema format changed"
        )
        val acknowledgement = ApiAcknowledgement(
            id = change.id,
            apiVersion = "v1",
            classification = ApiChangeClassification.POTENTIALLY_BREAKING,
            reason = "Format metadata corrected",
            migration = "No payload change",
            sprint = "Sprint 10C",
            deprecationStatus = "not_applicable"
        )

        val first = OpenApiCompatibility.changelog(listOf(change), listOf(acknowledgement))
        val second = OpenApiCompatibility.changelog(listOf(change), listOf(acknowledgement))

        assertThat(first).isEqualTo(second)
        assertThat(first).contains("POTENTIALLY_BREAKING")
        assertThat(first).contains("Format metadata corrected")
        assertThat(OpenApiCompatibility.changelog(emptyList(), emptyList())).contains("No contract changes detected.")
    }

    @Test
    fun `newly deprecated operation is documentation only and appears in changelog`() {
        val old = contract(
            paths = """
              /tasks:
                get:
                  deprecated: false
                  responses:
                    "200": { description: OK }
            """,
            schemas = "{}"
        )
        val new = contract(
            paths = """
              /tasks:
                get:
                  deprecated: true
                  responses:
                    "200": { description: OK }
            """,
            schemas = "{}"
        )

        val changes = OpenApiCompatibility.compare(read(old), read(new))
        assertThat(changes).anySatisfy {
            assertThat(it.classification).isEqualTo(ApiChangeClassification.DOCUMENTATION_ONLY)
            assertThat(it.id).contains("operation.deprecated.changed")
            assertThat(it.location).isEqualTo("/tasks#get")
        }
        assertThat(OpenApiCompatibility.changelog(changes, emptyList())).contains("DOCUMENTATION_ONLY")
    }

    @Test
    fun `baseline resolver falls back to working tree bootstrap when git baseline is unavailable`(@TempDir tempDir: Path) {
        val docs = tempDir.resolve("docs/api")
        Files.createDirectories(docs)
        Files.writeString(docs.resolve("openapi-v1.yaml"), schemaOnly("Sample:\n  type: object"))

        val baseline = OpenApiBaselineResolver(tempDir).resolve()

        assertThat(baseline.bootstrap).isTrue()
        assertThat(baseline.source).isEqualTo("working-tree-bootstrap")
        assertThat(baseline.content).contains("Sample")
    }

    private fun assertBreaking(change: ApiChange, type: String, location: String) {
        assertThat(change.classification).isEqualTo(ApiChangeClassification.BREAKING)
        assertThat(change.id).contains(type)
        assertThat(change.location).isEqualTo(location)
    }

    private fun assertPotential(change: ApiChange, type: String, location: String) {
        assertThat(change.classification).isEqualTo(ApiChangeClassification.POTENTIALLY_BREAKING)
        assertThat(change.id).contains(type)
        assertThat(change.location).isEqualTo(location)
    }

    private fun assertCompatible(change: ApiChange, type: String, location: String) {
        assertThat(change.classification).isEqualTo(ApiChangeClassification.BACKWARD_COMPATIBLE)
        assertThat(change.id).contains(type)
        assertThat(change.location).isEqualTo(location)
    }

    private fun read(value: String) = OpenApiCompatibility.normalize(mapper.readTree(value))

    private fun schemaOnly(schemas: String): String =
        contract(paths = "{}", schemas = schemas)

    private fun contract(paths: String, schemas: String): String =
        buildString {
            appendLine("openapi: 3.1.0")
            appendLine("info:")
            appendLine("  title: Test")
            appendLine("  version: v1")
            appendLine("paths:")
            appendLine(indent(paths, 2))
            appendLine("components:")
            appendLine("  schemas:")
            appendLine(indent(schemas, 4))
        }.trimEnd()

    private fun ref(schema: String): String =
        """${'$'}ref: '#/components/schemas/$schema'"""

    private fun indent(value: String, spaces: Int): String {
        val prefix = " ".repeat(spaces)
        return value.trimIndent().lines().joinToString("\n") { "$prefix$it" }
    }
}
