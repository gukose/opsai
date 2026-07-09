package com.hotelopai.integration.openai

import com.fasterxml.jackson.databind.ObjectMapper
import com.hotelopai.assistant.application.AssistantAiVersions
import com.hotelopai.assistant.application.AssistantInterpretationRequest
import com.hotelopai.assistant.application.MockAiInterpreter
import com.hotelopai.assistant.domain.Conversation
import com.hotelopai.assistant.domain.ConversationState
import com.hotelopai.assistant.domain.IntentType
import com.hotelopai.support.MockHttpResponse
import com.hotelopai.support.MockHttpServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Duration

class OpenAiInterpreterTest {
    private val objectMapper = ObjectMapper()
    private lateinit var server: MockHttpServer

    @BeforeEach
    fun setUp() {
        server = MockHttpServer().apply { start() }
    }

    @AfterEach
    fun tearDown() {
        server.stop()
    }

    @Test
    fun `openai adapter maps a valid structured response`() {
        stubOpenAi(
            responseBody = responseBodyFor(
                contentJson = validContent(
                    confidence = 0.92,
                    promptVersion = AssistantAiVersions.PROMPT_VERSION,
                    schemaVersion = AssistantAiVersions.SCHEMA_VERSION
                )
            )
        )

        val interpreter = openAiInterpreter()
        val request = assistantRequest()

        val result = interpreter.interpret(request)

        assertEquals(IntentType.MAINTENANCE, result.intent)
        assertEquals("101", result.fields["roomNumber"])
        assertEquals("assistant-ai-v1", result.promptVersion)
        assertEquals(1, result.schemaVersion)
        assertEquals("openai", result.providerName)
        assertEquals("test-model", result.providerModel)

        val recorded = server.lastRequest("POST", "/v1/chat/completions")
        assertEquals(1, server.requests("POST", "/v1/chat/completions").size)
        assertInstanceOf(String::class.java, recorded?.body)
        assertFalse(recorded!!.body.contains("hotel-123"))
        assertFalse(recorded.body.contains("user-456"))
    }

    @Test
    fun `malformed JSON is rejected safely`() {
        stubOpenAi(
            responseBody = """{"choices":[{"message":{"content":"not-json"}}]}"""
        )

        val interpreter = openAiInterpreter(fallbackEnabled = false)

        assertThrows(OpenAiMalformedResponseException::class.java) {
            interpreter.interpret(assistantRequest())
        }
    }

    @Test
    fun `schema-invalid output is rejected safely`() {
        stubOpenAi(
            responseBody = responseBodyFor(
                contentJson = validContent(
                    confidence = 0.88,
                    schemaVersion = 2
                )
            )
        )

        val interpreter = openAiInterpreter(fallbackEnabled = false)

        assertThrows(com.hotelopai.assistant.application.InvalidStructuredInterpretationException::class.java) {
            interpreter.interpret(assistantRequest())
        }
    }

    @Test
    fun `unsupported prompt version is rejected safely`() {
        stubOpenAi(
            responseBody = responseBodyFor(
                contentJson = validContent(
                    confidence = 0.88,
                    promptVersion = "assistant-ai-v2"
                )
            )
        )

        val interpreter = openAiInterpreter(fallbackEnabled = false)

        assertThrows(com.hotelopai.assistant.application.InvalidStructuredInterpretationException::class.java) {
            interpreter.interpret(assistantRequest())
        }
    }

    @Test
    fun `timeout falls back when explicitly enabled`() {
        stubOpenAi(
            responseBody = responseBodyFor(validContent()),
            delayMs = 600
        )

        val interpreter = openAiInterpreter(
            requestTimeout = Duration.ofMillis(100),
            fallbackEnabled = true
        )

        val result = interpreter.interpret(assistantRequest())

        assertEquals(IntentType.MAINTENANCE, result.intent)
        assertEquals(1, server.requests("POST", "/v1/chat/completions").size)
    }

    @Test
    fun `rate limit retries are bounded and fallback works when enabled`() {
        stubOpenAi(
            responseBody = """{"message":"rate limited"}""",
            status = 429
        )

        val interpreter = openAiInterpreter(
            fallbackEnabled = true,
            retries = 2
        )

        val result = interpreter.interpret(assistantRequest())

        assertEquals(IntentType.MAINTENANCE, result.intent)
        assertEquals(2, server.requests("POST", "/v1/chat/completions").size)
    }

    @Test
    fun `authentication failure is not retried`() {
        stubOpenAi(
            responseBody = """{"message":"unauthorized"}""",
            status = 401
        )

        val interpreter = openAiInterpreter(
            fallbackEnabled = true,
            retries = 3
        )

        assertThrows(OpenAiAuthenticationException::class.java) {
            interpreter.interpret(assistantRequest())
        }

        assertEquals(1, server.requests("POST", "/v1/chat/completions").size)
    }

    @Test
    fun `fallback does not create duplicate messages or tasks`() {
        stubOpenAi(
            responseBody = """{"choices":[{"message":{"content":"not-json"}}]}"""
        )

        val interpreter = openAiInterpreter(
            fallbackEnabled = true,
            fallbackInterpreter = MockAiInterpreter()
        )

        val result = interpreter.interpret(assistantRequest())

        assertEquals(IntentType.MAINTENANCE, result.intent)
        assertEquals("deterministic", result.providerName)
    }

    private fun openAiInterpreter(
        requestTimeout: Duration = Duration.ofSeconds(1),
        retries: Int = 1,
        fallbackEnabled: Boolean = false,
        fallbackInterpreter: MockAiInterpreter? = MockAiInterpreter()
    ): OpenAiInterpreter {
        val properties = OpenAiProperties().apply {
            apiKey = "test-api-key"
            model = "test-model"
            baseUrl = server.baseUrl
            this.requestTimeout = requestTimeout
            this.retries = retries
        }

        return OpenAiInterpreter(
            properties = properties,
            objectMapper = objectMapper,
            fallbackInterpreter = fallbackInterpreter,
            fallbackEnabled = fallbackEnabled
        )
    }

    private fun assistantRequest(): AssistantInterpretationRequest =
        AssistantInterpretationRequest.of(
            conversation = Conversation(
                id = "conversation-1",
                hotelId = "hotel-123",
                userId = "user-456",
                state = ConversationState.IDLE
            ),
            userText = "Room 101 AC not working"
        )

    private fun stubOpenAi(
        responseBody: String,
        status: Int = 200,
        delayMs: Long = 0
    ) {
        server.stub(
            method = "POST",
            path = "/v1/chat/completions",
            response = MockHttpResponse(
                status = status,
                body = responseBody,
                delayMs = delayMs
            )
        )
    }

    private fun responseBodyFor(contentJson: String): String =
        """{"choices":[{"message":{"content":${objectMapper.writeValueAsString(contentJson)}}}]}"""

    private fun validContent(
        confidence: Double = 0.91,
        promptVersion: String = AssistantAiVersions.PROMPT_VERSION,
        schemaVersion: Int = AssistantAiVersions.SCHEMA_VERSION
    ): String =
        objectMapper.writeValueAsString(
            mapOf(
                "promptVersion" to promptVersion,
                "schemaVersion" to schemaVersion,
                "intentCode" to IntentType.MAINTENANCE.name,
                "confidence" to confidence,
                "detectedLanguage" to "en",
                "extractedFields" to mapOf(
                    "roomNumber" to "101",
                    "description" to "AC not working"
                ),
                "missingRequiredFields" to emptyList<String>(),
                "followUpQuestion" to null,
                "assistantMessage" to "Understood.",
                "taskPreviewCandidate" to mapOf(
                    "intentCode" to IntentType.MAINTENANCE.name,
                    "title" to "Maintenance",
                    "description" to "AC not working",
                    "roomNumber" to "101",
                    "assignedTeam" to "Maintenance",
                    "priority" to "Medium",
                    "slaMinutes" to 60,
                    "requiresPmsUpdate" to true,
                    "departmentCode" to "MAINTENANCE",
                    "pmsUpdateType" to "MAINTENANCE"
                ),
                "prioritySuggestion" to "Medium",
                "slaPolicyKey" to "maintenance-default",
                "requiredSkillCode" to "maintenance",
                "departmentCode" to "maintenance",
                "pmsUpdateType" to "MAINTENANCE",
                "requiresPmsUpdate" to true,
                "providerName" to "openai",
                "providerModel" to "test-model"
            )
        )
}
