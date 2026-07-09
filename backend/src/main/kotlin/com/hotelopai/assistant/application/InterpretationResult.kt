package com.hotelopai.assistant.application

import com.hotelopai.assistant.domain.IntentType
import com.hotelopai.assistant.domain.TaskPreview

data class InterpretationResult(
    val intent: IntentType,
    val fields: Map<String, String>,
    val confidence: Double,
    val language: String? = null,
    val followUpQuestion: String? = null,
    val missingFields: List<String> = emptyList(),
    val assistantMessage: String? = null,
    val taskPreviewCandidate: TaskPreview? = null,
    val prioritySuggestion: String? = null,
    val slaPolicyKey: String? = null,
    val requiredSkillCode: String? = null,
    val departmentCode: String? = null,
    val pmsUpdateType: String? = null,
    val requiresPmsUpdate: Boolean = false,
    val promptVersion: String = AssistantAiVersions.PROMPT_VERSION,
    val schemaVersion: Int = AssistantAiVersions.SCHEMA_VERSION,
    val providerName: String = "deterministic",
    val providerModel: String? = null
)
