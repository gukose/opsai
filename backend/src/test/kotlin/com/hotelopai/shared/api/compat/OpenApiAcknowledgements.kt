package com.hotelopai.shared.api.compat

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import java.nio.file.Files
import java.nio.file.Path

object OpenApiAcknowledgements {
    private val mapper = ObjectMapper(YAMLFactory())

    fun read(path: Path): List<ApiAcknowledgement> {
        if (!Files.exists(path)) {
            return emptyList()
        }
        val root = mapper.readTree(Files.readString(path))
        val entries = root.path("acknowledgements")
        if (!entries.isArray) {
            return emptyList()
        }
        return entries.map(::parse)
    }

    private fun parse(node: JsonNode): ApiAcknowledgement =
        ApiAcknowledgement(
            id = requiredText(node, "id"),
            apiVersion = requiredText(node, "apiVersion"),
            classification = ApiChangeClassification.valueOf(requiredText(node, "classification")),
            reason = requiredText(node, "reason"),
            migration = requiredText(node, "migration"),
            sprint = requiredText(node, "sprint"),
            deprecationStatus = requiredText(node, "deprecationStatus"),
            contractCorrection = node.path("contractCorrection").asBoolean(false),
            removalTarget = node.path("removalTarget").takeIf { it.isTextual }?.asText()
        )

    private fun requiredText(node: JsonNode, field: String): String =
        node.path(field).asText().takeIf(String::isNotBlank)
            ?: throw IllegalArgumentException("API acknowledgement is missing required field: $field")
}
