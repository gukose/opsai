package com.hotelopai.shared.api.compat

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.node.MissingNode
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import java.nio.file.Files
import java.nio.file.Path

enum class ApiChangeClassification {
    BREAKING,
    POTENTIALLY_BREAKING,
    BACKWARD_COMPATIBLE,
    DOCUMENTATION_ONLY
}

data class ApiChange(
    val id: String,
    val classification: ApiChangeClassification,
    val location: String,
    val summary: String,
    val oldValue: String? = null,
    val newValue: String? = null
)

data class ApiAcknowledgement(
    val id: String,
    val apiVersion: String,
    val classification: ApiChangeClassification,
    val reason: String,
    val migration: String,
    val sprint: String,
    val deprecationStatus: String,
    val contractCorrection: Boolean = false,
    val removalTarget: String? = null
)

data class CompatibilityResult(
    val changes: List<ApiChange>,
    val acknowledgements: List<ApiAcknowledgement>,
    val duplicateAcknowledgementIds: Set<String>,
    val staleAcknowledgementIds: Set<String>,
    val unacknowledgedRiskIds: Set<String>,
    val forbiddenBreakingIds: Set<String>
) {
    val failed: Boolean =
        duplicateAcknowledgementIds.isNotEmpty() ||
            staleAcknowledgementIds.isNotEmpty() ||
            unacknowledgedRiskIds.isNotEmpty() ||
            forbiddenBreakingIds.isNotEmpty()

    fun failureMessage(): String =
        buildString {
            appendLine("OpenAPI compatibility check failed.")
            appendLine(summary())
            if (duplicateAcknowledgementIds.isNotEmpty()) {
                appendLine("Duplicate acknowledgements: ${duplicateAcknowledgementIds.sorted().joinToString()}")
            }
            if (staleAcknowledgementIds.isNotEmpty()) {
                appendLine("Stale acknowledgements: ${staleAcknowledgementIds.sorted().joinToString()}")
            }
            if (unacknowledgedRiskIds.isNotEmpty()) {
                appendLine("Unacknowledged breaking/potentially-breaking changes: ${unacknowledgedRiskIds.sorted().joinToString()}")
            }
            if (forbiddenBreakingIds.isNotEmpty()) {
                appendLine("Forbidden v1 breaking changes without contractCorrection=true: ${forbiddenBreakingIds.sorted().joinToString()}")
            }
            appendLine("Run ./gradlew :backend:generateOpenApiChangelog to refresh docs/api/CHANGELOG.md after reviewing intentional changes.")
        }

    fun summary(): String {
        val counts = changes.groupingBy(ApiChange::classification).eachCount()
        return ApiChangeClassification.entries.joinToString(prefix = "Change counts: ") {
            "${it.name}=${counts[it] ?: 0}"
        }
    }
}

object OpenApiCompatibility {
    private val yamlMapper = ObjectMapper(YAMLFactory())
    private val methodNames = setOf("get", "put", "post", "delete", "patch", "head", "options", "trace")

    fun read(path: Path): JsonNode =
        normalize(yamlMapper.readTree(Files.readString(path)))

    fun normalize(node: JsonNode): JsonNode {
        val copy = node.deepCopy<ObjectNode>()
        copy.remove("servers")
        normalizeDocumentation(copy)
        return copy
    }

    fun compare(old: JsonNode, new: JsonNode): List<ApiChange> {
        val changes = mutableListOf<ApiChange>()
        comparePaths(old, new, changes)
        compareComponents(old, new, changes)
        return changes.sortedWith(compareBy<ApiChange> { it.classification.ordinal }.thenBy { it.id })
    }

    fun evaluate(
        changes: List<ApiChange>,
        acknowledgements: List<ApiAcknowledgement>
    ): CompatibilityResult {
        val ids = changes.map(ApiChange::id).toSet()
        val acknowledgementIds = acknowledgements.map(ApiAcknowledgement::id)
        val duplicateIds = acknowledgementIds.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        val staleIds = acknowledgementIds.toSet() - ids
        val acknowledged = acknowledgements.associateBy(ApiAcknowledgement::id)
        val unacknowledgedRiskIds = changes
            .filter { it.classification == ApiChangeClassification.BREAKING || it.classification == ApiChangeClassification.POTENTIALLY_BREAKING }
            .filterNot { acknowledged.containsKey(it.id) }
            .map(ApiChange::id)
            .toSet()
        val forbiddenBreakingIds = changes
            .filter { it.classification == ApiChangeClassification.BREAKING }
            .filterNot { acknowledged[it.id]?.contractCorrection == true }
            .map(ApiChange::id)
            .toSet()
        return CompatibilityResult(
            changes = changes,
            acknowledgements = acknowledgements,
            duplicateAcknowledgementIds = duplicateIds,
            staleAcknowledgementIds = staleIds,
            unacknowledgedRiskIds = unacknowledgedRiskIds,
            forbiddenBreakingIds = forbiddenBreakingIds
        )
    }

    fun changelog(changes: List<ApiChange>, acknowledgements: List<ApiAcknowledgement>): String {
        val acknowledgementById = acknowledgements.associateBy(ApiAcknowledgement::id)
        return buildString {
            appendLine("# API Changelog")
            appendLine()
            appendLine("Generated from the reviewed OpenAPI snapshot comparison. Do not edit generated entries by hand.")
            appendLine()
            appendLine("## v1")
            appendLine()
            if (changes.isEmpty()) {
                appendLine("No contract changes detected.")
                return@buildString
            }
            ApiChangeClassification.entries.forEach { classification ->
                val section = changes.filter { it.classification == classification }
                if (section.isNotEmpty()) {
                    appendLine("### ${classification.name}")
                    appendLine()
                    section.forEach { change ->
                        val acknowledgement = acknowledgementById[change.id]
                        appendLine("- `${change.id}` ${change.summary}")
                        appendLine("  - Location: `${change.location}`")
                        acknowledgement?.let {
                            appendLine("  - Reason: ${it.reason}")
                            appendLine("  - Migration: ${it.migration}")
                            appendLine("  - Sprint: ${it.sprint}")
                            appendLine("  - Deprecation: ${it.deprecationStatus}")
                        }
                    }
                    appendLine()
                }
            }
        }
    }

    private fun normalizeDocumentation(node: JsonNode) {
        if (node.isObject) {
            val objectNode = node as ObjectNode
            listOf("description", "summary", "example", "examples", "externalDocs").forEach(objectNode::remove)
            objectNode.fields().forEachRemaining { (_, value) -> normalizeDocumentation(value) }
        } else if (node.isArray) {
            node.forEach(::normalizeDocumentation)
        }
    }

    private fun comparePaths(old: JsonNode, new: JsonNode, changes: MutableList<ApiChange>) {
        val oldPaths = old.path("paths")
        val newPaths = new.path("paths")
        val oldNames = fieldNames(oldPaths)
        val newNames = fieldNames(newPaths)

        (oldNames - newNames).forEach { path ->
            changes += change("path.removed", ApiChangeClassification.BREAKING, path, "Endpoint path removed")
        }
        (newNames - oldNames).forEach { path ->
            changes += change("path.added", ApiChangeClassification.BACKWARD_COMPATIBLE, path, "Endpoint path added")
        }
        (oldNames intersect newNames).forEach { path ->
            comparePathItem(path, oldPaths.path(path), newPaths.path(path), changes)
        }
    }

    private fun comparePathItem(path: String, old: JsonNode, new: JsonNode, changes: MutableList<ApiChange>) {
        val oldMethods = fieldNames(old).filter { it in methodNames }.toSet()
        val newMethods = fieldNames(new).filter { it in methodNames }.toSet()
        (oldMethods - newMethods).forEach { method ->
            changes += change("method.removed", ApiChangeClassification.BREAKING, "$path#$method", "HTTP method removed")
        }
        (newMethods - oldMethods).forEach { method ->
            changes += change("method.added", ApiChangeClassification.BACKWARD_COMPATIBLE, "$path#$method", "HTTP method added")
        }
        (oldMethods intersect newMethods).forEach { method ->
            compareOperation(path, method, old.path(method), new.path(method), changes)
        }
    }

    private fun compareOperation(path: String, method: String, old: JsonNode, new: JsonNode, changes: MutableList<ApiChange>) {
        val location = "$path#$method"
        compareSecurity(location, old.path("security"), new.path("security"), changes)
        compareParameters(location, old.path("parameters"), new.path("parameters"), changes)
        compareRequestBody(location, old, new, changes)
        compareResponses(location, old.path("responses"), new.path("responses"), changes)
        if (old.path("deprecated").asBoolean(false) != new.path("deprecated").asBoolean(false)) {
            changes += change(
                "operation.deprecated.changed",
                ApiChangeClassification.DOCUMENTATION_ONLY,
                location,
                "Deprecation metadata changed",
                old.path("deprecated").toString(),
                new.path("deprecated").toString()
            )
        }
    }

    private fun compareSecurity(location: String, old: JsonNode, new: JsonNode, changes: MutableList<ApiChange>) {
        val oldProtected = old.isArray && old.size() > 0
        val newProtected = new.isArray && new.size() > 0
        if (!oldProtected && newProtected) {
            changes += change("security.added", ApiChangeClassification.BREAKING, location, "Authentication requirement added")
        } else if (oldProtected && !newProtected) {
            changes += change("security.removed", ApiChangeClassification.BACKWARD_COMPATIBLE, location, "Authentication requirement removed")
        } else if (old != new) {
            changes += change("security.changed", ApiChangeClassification.POTENTIALLY_BREAKING, location, "Security requirement changed", old.toString(), new.toString())
        }
    }

    private fun compareParameters(location: String, old: JsonNode, new: JsonNode, changes: MutableList<ApiChange>) {
        val oldParams = parameterMap(old)
        val newParams = parameterMap(new)
        (oldParams.keys - newParams.keys).forEach { key ->
            changes += change("parameter.removed", ApiChangeClassification.BREAKING, "$location parameter:$key", "Parameter removed")
        }
        (newParams.keys - oldParams.keys).forEach { key ->
            val classification = if (newParams.getValue(key).path("required").asBoolean(false)) {
                ApiChangeClassification.BREAKING
            } else {
                ApiChangeClassification.BACKWARD_COMPATIBLE
            }
            changes += change("parameter.added", classification, "$location parameter:$key", "Parameter added")
        }
        (oldParams.keys intersect newParams.keys).forEach { key ->
            val oldParam = oldParams.getValue(key)
            val newParam = newParams.getValue(key)
            if (oldParam.path("in").asText() != newParam.path("in").asText()) {
                changes += change("parameter.location.changed", ApiChangeClassification.BREAKING, "$location parameter:$key", "Parameter location changed")
            }
            if (!oldParam.path("required").asBoolean(false) && newParam.path("required").asBoolean(false)) {
                changes += change("parameter.required.added", ApiChangeClassification.BREAKING, "$location parameter:$key", "Parameter became required")
            }
            compareSchema("$location parameter:$key", oldParam.path("schema"), newParam.path("schema"), SchemaContext.REQUEST, changes)
        }
    }

    private fun compareRequestBody(location: String, old: JsonNode, new: JsonNode, changes: MutableList<ApiChange>) {
        val oldBody = firstContentSchema(old.path("requestBody"))
        val newBody = firstContentSchema(new.path("requestBody"))
        if (oldBody.isMissingNode && !newBody.isMissingNode) {
            changes += change("requestBody.added", ApiChangeClassification.BREAKING, location, "Request body added")
        } else if (!oldBody.isMissingNode && newBody.isMissingNode) {
            changes += change("requestBody.removed", ApiChangeClassification.BACKWARD_COMPATIBLE, location, "Request body removed")
        } else if (!oldBody.isMissingNode && !newBody.isMissingNode) {
            compareSchema("$location request", oldBody, newBody, SchemaContext.REQUEST, changes)
        }
    }

    private fun compareResponses(location: String, old: JsonNode, new: JsonNode, changes: MutableList<ApiChange>) {
        val oldCodes = fieldNames(old)
        val newCodes = fieldNames(new)
        (oldCodes - newCodes).forEach { code ->
            changes += change("response.removed", ApiChangeClassification.BREAKING, "$location response:$code", "Response status removed")
        }
        (newCodes - oldCodes).forEach { code ->
            changes += change("response.added", ApiChangeClassification.BACKWARD_COMPATIBLE, "$location response:$code", "Response status added")
        }
        (oldCodes intersect newCodes).forEach { code ->
            val oldSchema = firstContentSchema(old.path(code))
            val newSchema = firstContentSchema(new.path(code))
            if (!oldSchema.isMissingNode && !newSchema.isMissingNode) {
                compareSchema("$location response:$code", oldSchema, newSchema, SchemaContext.RESPONSE, changes)
            }
        }
    }

    private fun compareComponents(old: JsonNode, new: JsonNode, changes: MutableList<ApiChange>) {
        val oldSchemas = old.at("/components/schemas")
        val newSchemas = new.at("/components/schemas")
        val oldNames = fieldNames(oldSchemas)
        val newNames = fieldNames(newSchemas)
        (oldNames - newNames).forEach { schema ->
            changes += change("schema.removed", ApiChangeClassification.BREAKING, "schema:$schema", "Component schema removed")
        }
        (newNames - oldNames).forEach { schema ->
            changes += change("schema.added", ApiChangeClassification.BACKWARD_COMPATIBLE, "schema:$schema", "Component schema added")
        }
        (oldNames intersect newNames).forEach { schema ->
            compareSchema("schema:$schema", oldSchemas.path(schema), newSchemas.path(schema), SchemaContext.RESPONSE, changes)
        }
    }

    private fun compareSchema(location: String, old: JsonNode, new: JsonNode, context: SchemaContext, changes: MutableList<ApiChange>) {
        if (old.isMissingNode || new.isMissingNode) return
        compareReferences(location, old, new, changes)?.let { return }
        if (hasComposition(old) || hasComposition(new)) {
            if (old != new) changes += change("schema.composition.changed", ApiChangeClassification.POTENTIALLY_BREAKING, location, "Schema composition changed")
            return
        }
        val oldType = old.path("type").asText("")
        val newType = new.path("type").asText("")
        if (oldType.isNotBlank() && newType.isNotBlank() && oldType != newType) {
            changes += change("schema.type.changed", ApiChangeClassification.BREAKING, location, "Schema type changed", oldType, newType)
        }
        val oldFormat = old.path("format").asText("")
        val newFormat = new.path("format").asText("")
        if (oldFormat != newFormat) {
            changes += change("schema.format.changed", ApiChangeClassification.POTENTIALLY_BREAKING, location, "Schema format changed", oldFormat, newFormat)
        }
        if (old.path("nullable").asBoolean(false) && !new.path("nullable").asBoolean(false)) {
            changes += change("schema.nullable.removed", ApiChangeClassification.BREAKING, location, "Nullable field became non-nullable")
        }
        compareLimits(location, old, new, changes)
        compareEnums(location, old.path("enum"), new.path("enum"), changes)
        compareObjectProperties(location, old, new, context, changes)
    }

    private fun compareObjectProperties(location: String, old: JsonNode, new: JsonNode, context: SchemaContext, changes: MutableList<ApiChange>) {
        val oldProperties = old.path("properties")
        val newProperties = new.path("properties")
        val oldNames = fieldNames(oldProperties)
        val newNames = fieldNames(newProperties)
        val oldRequired = textSet(old.path("required"))
        val newRequired = textSet(new.path("required"))
        (oldNames - newNames).forEach { property ->
            val classification = if (context == SchemaContext.RESPONSE) ApiChangeClassification.BREAKING else ApiChangeClassification.POTENTIALLY_BREAKING
            changes += change("property.removed", classification, "$location.$property", "Schema property removed")
        }
        (newNames - oldNames).forEach { property ->
            val classification = if (context == SchemaContext.REQUEST && property in newRequired) {
                ApiChangeClassification.BREAKING
            } else {
                ApiChangeClassification.BACKWARD_COMPATIBLE
            }
            changes += change("property.added", classification, "$location.$property", "Schema property added")
        }
        (newRequired - oldRequired).forEach { property ->
            changes += change("property.required.added", ApiChangeClassification.BREAKING, "$location.$property", "Schema property became required")
        }
        (oldNames intersect newNames).forEach { property ->
            compareSchema("$location.$property", oldProperties.path(property), newProperties.path(property), context, changes)
        }
    }

    private fun compareReferences(location: String, old: JsonNode, new: JsonNode, changes: MutableList<ApiChange>): Unit? {
        val oldRef = old.path("\$ref").asText("")
        val newRef = new.path("\$ref").asText("")
        if (oldRef.isBlank() && newRef.isBlank()) return null
        if (oldRef != newRef) {
            changes += change(
                "schema.ref.changed",
                ApiChangeClassification.POTENTIALLY_BREAKING,
                location,
                "Schema reference changed",
                oldRef.ifBlank { null },
                newRef.ifBlank { null }
            )
        }
        return Unit
    }

    private fun compareLimits(location: String, old: JsonNode, new: JsonNode, changes: MutableList<ApiChange>) {
        listOf("maxLength", "maximum", "maxItems").forEach { field ->
            if (old.has(field) && new.has(field) && new.path(field).asDouble() < old.path(field).asDouble()) {
                changes += change("schema.limit.narrowed", ApiChangeClassification.POTENTIALLY_BREAKING, "$location $field", "Validation limit narrowed")
            }
        }
    }

    private fun compareEnums(location: String, old: JsonNode, new: JsonNode, changes: MutableList<ApiChange>) {
        val oldValues = textSet(old)
        val newValues = textSet(new)
        (oldValues - newValues).forEach { value ->
            changes += change("enum.removed", ApiChangeClassification.BREAKING, "$location enum:$value", "Enum value removed")
        }
        (newValues - oldValues).forEach { value ->
            changes += change("enum.added", ApiChangeClassification.BACKWARD_COMPATIBLE, "$location enum:$value", "Enum value added")
        }
    }

    private fun hasComposition(node: JsonNode): Boolean =
        node.has("oneOf") || node.has("anyOf") || node.has("allOf")

    private fun parameterMap(parameters: JsonNode): Map<String, JsonNode> =
        if (!parameters.isArray) {
            emptyMap()
        } else {
            parameters.associateBy { "${it.path("in").asText()}:${it.path("name").asText()}" }
        }

    private fun firstContentSchema(node: JsonNode): JsonNode {
        val content = node.path("content")
        if (!content.isObject) return MissingNode.getInstance()
        val first = content.fields().asSequence().firstOrNull()?.value ?: return MissingNode.getInstance()
        return first.path("schema")
    }

    private fun change(
        type: String,
        classification: ApiChangeClassification,
        location: String,
        summary: String,
        oldValue: String? = null,
        newValue: String? = null
    ): ApiChange =
        ApiChange(
            id = "${classification.name.lowercase()}:$type:$location",
            classification = classification,
            location = location,
            summary = summary,
            oldValue = oldValue,
            newValue = newValue
        )

    private fun fieldNames(node: JsonNode): Set<String> =
        if (node.isObject) node.fieldNames().asSequence().toSet() else emptySet()

    private fun textSet(node: JsonNode): Set<String> =
        if (node.isArray) node.map(JsonNode::asText).toSet() else emptySet()

    private enum class SchemaContext {
        REQUEST,
        RESPONSE
    }
}
