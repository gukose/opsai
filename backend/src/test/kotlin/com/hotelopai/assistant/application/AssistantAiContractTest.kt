package com.hotelopai.assistant.application

import com.hotelopai.assistant.domain.Conversation
import com.hotelopai.assistant.domain.ConversationState
import com.hotelopai.assistant.domain.IntentType
import com.hotelopai.assistant.domain.TaskPreview
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class AssistantAiContractTest {
    private val validator = StructuredInterpretationValidator()

    @Test
    fun `valid structured interpretation passes validation`() {
        val payload = validPayload()

        val interpretation = validator.validate(payload)

        assertEquals(IntentType.MAINTENANCE, interpretation.intent)
        assertEquals("assistant-ai-v1", interpretation.promptVersion)
        assertEquals(1, interpretation.schemaVersion)
        assertEquals("101", interpretation.fields["roomNumber"])
        assertNotNull(interpretation.taskPreviewCandidate)
    }

    @Test
    fun `malformed structured interpretation is rejected safely`() {
        val payload = validPayload().copy(extractedFields = mapOf(" " to "101"))

        assertThrows(InvalidStructuredInterpretationException::class.java) {
            validator.validate(payload)
        }
    }

    @Test
    fun `unsupported schema versions are rejected`() {
        val payload = validPayload().copy(schemaVersion = 2)

        assertThrows(InvalidStructuredInterpretationException::class.java) {
            validator.validate(payload)
        }
    }

    @Test
    fun `missing required contract values are rejected`() {
        val payload = validPayload().copy(missingRequiredFields = null)

        assertThrows(InvalidStructuredInterpretationException::class.java) {
            validator.validate(payload)
        }
    }

    @Test
    fun `confidence outside the valid range is rejected`() {
        val payload = validPayload().copy(confidence = 1.5)

        assertThrows(InvalidStructuredInterpretationException::class.java) {
            validator.validate(payload)
        }
    }

    @Test
    fun `request keeps authenticated tenant context authoritative`() {
        val conversation = conversation()
        val request = AssistantInterpretationRequest.of(conversation, "Room 101 AC not working")

        assertEquals("hotel-123", request.conversation.hotelId)
        assertEquals("user-456", request.conversation.userId)
        assertEquals("assistant-ai-v1", request.promptVersion)
        assertEquals(1, request.schemaVersion)
    }

    @Test
    fun `prompt catalog resources are versioned and bundled`() {
        val bundle = AssistantPromptCatalog.current

        assertEquals(AssistantAiVersions.PROMPT_VERSION, bundle.promptVersion)
        assertEquals(AssistantAiVersions.SCHEMA_VERSION, bundle.schemaVersion)
        assertFalse(bundle.systemPrompt.isBlank())
        assertFalse(bundle.schemaJson.isBlank())
    }

    private fun validPayload(): StructuredInterpretationPayload =
        StructuredInterpretationPayload(
            promptVersion = AssistantAiVersions.PROMPT_VERSION,
            schemaVersion = AssistantAiVersions.SCHEMA_VERSION,
            intentCode = IntentType.MAINTENANCE.name,
            confidence = 0.92,
            detectedLanguage = "en",
            extractedFields = mapOf(
                "roomNumber" to "101",
                "description" to "AC not working"
            ),
            missingRequiredFields = emptyList(),
            followUpQuestion = null,
            assistantMessage = "Please review the task.",
            taskPreviewCandidate = StructuredTaskPreviewCandidatePayload(
                intentCode = IntentType.MAINTENANCE.name,
                title = "Maintenance",
                description = "AC not working",
                roomNumber = "101",
                assignedTeam = "Maintenance",
                priority = "Medium",
                slaMinutes = 60,
                requiresPmsUpdate = true,
                departmentCode = "MAINTENANCE",
                pmsUpdateType = "MAINTENANCE"
            ),
            prioritySuggestion = "Medium",
            slaPolicyKey = "maintenance-default",
            requiredSkillCode = "maintenance",
            departmentCode = "maintenance",
            pmsUpdateType = "MAINTENANCE",
            requiresPmsUpdate = true,
            providerName = "deterministic",
            providerModel = "rule-based"
        )

    private fun conversation(): Conversation =
        Conversation(
            id = "conversation-1",
            hotelId = "hotel-123",
            userId = "user-456",
            state = ConversationState.IDLE
        )
}
