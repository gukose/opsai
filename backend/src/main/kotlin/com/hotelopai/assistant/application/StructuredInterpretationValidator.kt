package com.hotelopai.assistant.application

import com.hotelopai.assistant.domain.IntentType
import com.hotelopai.assistant.domain.TaskPreview

class InvalidStructuredInterpretationException(message: String) : RuntimeException(message)

class StructuredInterpretationValidator(
    private val supportedPromptVersion: String = AssistantAiVersions.PROMPT_VERSION,
    private val supportedSchemaVersion: Int = AssistantAiVersions.SCHEMA_VERSION
) {
    fun validate(payload: StructuredInterpretationPayload): InterpretationResult {
        val promptVersion = payload.promptVersion?.trim().takeUnless { it.isNullOrBlank() }
            ?: throw InvalidStructuredInterpretationException("promptVersion is required")
        if (promptVersion != supportedPromptVersion) {
            throw InvalidStructuredInterpretationException("Unsupported promptVersion: $promptVersion")
        }

        val schemaVersion = payload.schemaVersion
            ?: throw InvalidStructuredInterpretationException("schemaVersion is required")
        if (schemaVersion != supportedSchemaVersion) {
            throw InvalidStructuredInterpretationException("Unsupported schemaVersion: $schemaVersion")
        }

        val intentCode = payload.intentCode?.trim().takeUnless { it.isNullOrBlank() }
            ?: throw InvalidStructuredInterpretationException("intentCode is required")
        val intent = resolveIntent(intentCode)

        val confidence = payload.confidence
            ?: throw InvalidStructuredInterpretationException("confidence is required")
        if (confidence !in 0.0..1.0) {
            throw InvalidStructuredInterpretationException("confidence must be between 0 and 1")
        }

        val extractedFields = validateMap(payload.extractedFields)
        val missingRequiredFields = validateList(payload.missingRequiredFields)

        val followUpQuestion = payload.followUpQuestion?.trim()?.takeIf { it.isNotBlank() }
        val assistantMessage = payload.assistantMessage?.trim()?.takeIf { it.isNotBlank() }

        val taskPreviewCandidate = payload.taskPreviewCandidate?.let { candidate ->
            validateTaskPreviewCandidate(candidate)
        }

        val requiresPmsUpdate = payload.requiresPmsUpdate ?: false

        return InterpretationResult(
            intent = intent,
            fields = extractedFields,
            confidence = confidence,
            language = payload.detectedLanguage?.trim()?.takeIf { it.isNotBlank() },
            followUpQuestion = followUpQuestion,
            missingFields = missingRequiredFields,
            assistantMessage = assistantMessage,
            taskPreviewCandidate = taskPreviewCandidate,
            prioritySuggestion = payload.prioritySuggestion?.trim()?.takeIf { it.isNotBlank() },
            slaPolicyKey = payload.slaPolicyKey?.trim()?.takeIf { it.isNotBlank() },
            requiredSkillCode = payload.requiredSkillCode?.trim()?.takeIf { it.isNotBlank() },
            departmentCode = payload.departmentCode?.trim()?.takeIf { it.isNotBlank() },
            pmsUpdateType = payload.pmsUpdateType?.trim()?.takeIf { it.isNotBlank() },
            requiresPmsUpdate = requiresPmsUpdate,
            promptVersion = promptVersion,
            schemaVersion = schemaVersion,
            providerName = payload.providerName?.trim()?.takeIf { it.isNotBlank() } ?: "unknown",
            providerModel = payload.providerModel?.trim()?.takeIf { it.isNotBlank() }
        )
    }

    private fun validateMap(values: Map<String, String?>?): Map<String, String> {
        val entries = values ?: throw InvalidStructuredInterpretationException("extractedFields is required")
        return entries
            .mapNotNull { (key, value) ->
                val normalizedKey = key.trim()
                if (normalizedKey.isBlank()) {
                    throw InvalidStructuredInterpretationException("extractedFields contains a blank key")
                }

                val normalizedValue = value?.trim()?.takeIf { it.isNotBlank() }
                normalizedValue?.let { normalizedKey to it }
            }
            .toMap()
    }

    private fun validateList(values: List<String?>?): List<String> {
        val entries = values ?: throw InvalidStructuredInterpretationException("missingRequiredFields is required")
        return entries.map {
            val value = it?.trim()
            if (value.isNullOrBlank()) {
                throw InvalidStructuredInterpretationException("missingRequiredFields contains a blank value")
            }
            value
        }.distinct()
    }

    private fun validateTaskPreviewCandidate(candidate: StructuredTaskPreviewCandidatePayload): TaskPreview {
        val intentCode = candidate.intentCode?.trim().takeUnless { it.isNullOrBlank() }
            ?: throw InvalidStructuredInterpretationException("taskPreviewCandidate.intentCode is required")
        resolveIntent(intentCode)

        val title = candidate.title?.trim().takeUnless { it.isNullOrBlank() }
            ?: throw InvalidStructuredInterpretationException("taskPreviewCandidate.title is required")
        val description = candidate.description?.trim().takeUnless { it.isNullOrBlank() }
            ?: throw InvalidStructuredInterpretationException("taskPreviewCandidate.description is required")

        if (candidate.slaMinutes != null && candidate.slaMinutes < 0) {
            throw InvalidStructuredInterpretationException("taskPreviewCandidate.slaMinutes cannot be negative")
        }

        return TaskPreview(
            type = resolveIntent(intentCode),
            title = title,
            description = description,
            roomNumber = candidate.roomNumber?.trim()?.takeIf { it.isNotBlank() },
            publicAreaId = candidate.publicAreaId?.trim()?.takeIf { it.isNotBlank() },
            assetId = candidate.assetId?.trim()?.takeIf { it.isNotBlank() },
            assignedTeam = candidate.assignedTeam?.trim()?.takeIf { it.isNotBlank() },
            priority = candidate.priority?.trim()?.takeIf { it.isNotBlank() },
            slaMinutes = candidate.slaMinutes,
            requiresPmsUpdate = candidate.requiresPmsUpdate ?: false
        )
    }

    private fun resolveIntent(intentCode: String): IntentType =
        IntentType.entries.firstOrNull { it.name == intentCode }
            ?: throw InvalidStructuredInterpretationException("Unsupported intentCode: $intentCode")
}
