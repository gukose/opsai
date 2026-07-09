package com.hotelopai.assistant.application

import org.springframework.core.io.ClassPathResource

data class AssistantPromptBundle(
    val promptVersion: String,
    val schemaVersion: Int,
    val systemPrompt: String,
    val schemaJson: String
)

object AssistantPromptCatalog {
    val current: AssistantPromptBundle by lazy {
        AssistantPromptBundle(
            promptVersion = AssistantAiVersions.PROMPT_VERSION,
            schemaVersion = AssistantAiVersions.SCHEMA_VERSION,
            systemPrompt = readResource("assistant-ai/v1/system-prompt.md"),
            schemaJson = readResource("assistant-ai/v1/interpretation-schema.json")
        )
    }

    private fun readResource(path: String): String {
        val resource = ClassPathResource(path)
        if (!resource.exists()) {
            throw IllegalStateException("Missing assistant AI resource: $path")
        }

        resource.inputStream.use { stream ->
            return String(stream.readBytes(), Charsets.UTF_8)
        }
    }
}
