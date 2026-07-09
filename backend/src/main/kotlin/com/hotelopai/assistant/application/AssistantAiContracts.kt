package com.hotelopai.assistant.application

import com.hotelopai.assistant.domain.Conversation

object AssistantAiVersions {
    const val PROMPT_VERSION: String = "assistant-ai-v1"
    const val SCHEMA_VERSION: Int = 1
}

data class AssistantInterpretationRequest(
    val conversation: Conversation,
    val userText: String,
    val promptVersion: String = AssistantAiVersions.PROMPT_VERSION,
    val schemaVersion: Int = AssistantAiVersions.SCHEMA_VERSION
) {
    companion object {
        fun of(conversation: Conversation, userText: String): AssistantInterpretationRequest =
            AssistantInterpretationRequest(
                conversation = conversation,
                userText = userText.trim()
            )
    }
}

data class StructuredInterpretationPayload(
    val promptVersion: String? = null,
    val schemaVersion: Int? = null,
    val intentCode: String? = null,
    val confidence: Double? = null,
    val detectedLanguage: String? = null,
    val extractedFields: Map<String, String?>? = null,
    val missingRequiredFields: List<String?>? = null,
    val followUpQuestion: String? = null,
    val assistantMessage: String? = null,
    val taskPreviewCandidate: StructuredTaskPreviewCandidatePayload? = null,
    val prioritySuggestion: String? = null,
    val slaPolicyKey: String? = null,
    val requiredSkillCode: String? = null,
    val departmentCode: String? = null,
    val pmsUpdateType: String? = null,
    val requiresPmsUpdate: Boolean? = null,
    val providerName: String? = null,
    val providerModel: String? = null
)

data class StructuredTaskPreviewCandidatePayload(
    val intentCode: String? = null,
    val title: String? = null,
    val description: String? = null,
    val roomNumber: String? = null,
    val publicAreaId: String? = null,
    val assetId: String? = null,
    val assignedTeam: String? = null,
    val priority: String? = null,
    val slaMinutes: Int? = null,
    val requiresPmsUpdate: Boolean? = null,
    val requiredSkillCode: String? = null,
    val departmentCode: String? = null,
    val pmsUpdateType: String? = null
)

fun com.hotelopai.assistant.domain.TaskPreview.toStructuredPayload(
    intentCode: String
): StructuredTaskPreviewCandidatePayload =
    StructuredTaskPreviewCandidatePayload(
        intentCode = intentCode,
        title = title,
        description = description,
        roomNumber = roomNumber,
        publicAreaId = publicAreaId,
        assetId = assetId,
        assignedTeam = assignedTeam,
        priority = priority,
        slaMinutes = slaMinutes,
        requiresPmsUpdate = requiresPmsUpdate,
        requiredSkillCode = null,
        departmentCode = assignedTeam?.uppercase()?.replace(' ', '_'),
        pmsUpdateType = if (requiresPmsUpdate) intentCode else null
    )
