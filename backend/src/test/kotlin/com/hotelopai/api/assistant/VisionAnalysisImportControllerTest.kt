package com.hotelopai.assistant.api

import com.hotelopai.assistant.application.AssistantAttachmentRegistrationService
import com.hotelopai.assistant.application.ConversationRepository
import com.hotelopai.assistant.application.RegisterAssistantAttachmentCommand
import com.hotelopai.assistant.domain.AttachmentType
import com.hotelopai.assistant.domain.Conversation
import com.hotelopai.shared.kernel.UuidV7Generator
import com.hotelopai.support.PostgresIntegrationTestSupport
import com.hotelopai.vision.application.VisionAnalysisRepository
import com.hotelopai.vision.domain.VisionAnalysis
import com.hotelopai.vision.domain.VisionAnalysisStatus
import com.hotelopai.vision.domain.VisionDetectedObservation
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import java.math.BigDecimal
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Instant
import java.util.UUID

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class VisionAnalysisImportControllerTest : PostgresIntegrationTestSupport() {
    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var conversationRepository: ConversationRepository

    @Autowired
    private lateinit var attachmentRegistrationService: AssistantAttachmentRegistrationService

    @Autowired
    private lateinit var visionAnalysisRepository: VisionAnalysisRepository

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    private val httpClient = HttpClient.newHttpClient()

    @Test
    fun `unauthenticated import returns 401 and authenticated same owner import succeeds`() {
        val login = login()
        val fixture = completedAnalysis(login, "Room 101 AC not working", BigDecimal("0.80"))

        val unauthenticated = post(
            path = "/api/v1/assistant/conversations/${fixture.conversationId}/vision-analyses/${fixture.analysisId}/import",
            body = ""
        )
        assertEquals(401, unauthenticated.statusCode())

        val beforeTasks = taskCount()
        val imported = post(
            path = "/api/v1/assistant/conversations/${fixture.conversationId}/vision-analyses/${fixture.analysisId}/import",
            body = "",
            bearerToken = login.accessToken
        )

        assertEquals(200, imported.statusCode(), imported.body())
        assertThat(imported.body()).contains("VISION_ANALYSIS", "WAITING_FOR_CONFIRMATION", "\"taskPreview\"")
        assertThat(taskCount()).isEqualTo(beforeTasks)
    }

    @Test
    fun `client supplied import body fields are rejected`() {
        val login = login()
        val fixture = completedAnalysis(login, "Room 101 AC not working", BigDecimal("0.80"))
        val forbiddenBodies = listOf(
            """{"hotelId":"client-hotel"}""",
            """{"userId":"client-user"}""",
            """{"attachmentId":"${fixture.attachmentId}"}""",
            """{"observationText":"client text"}""",
            """{"confidence":0.99}""",
            """{"providerMetadata":{"secret":"x"}}""",
            """{"storageReference":"blob://fake"}""",
            """{"localReference":"file://local"}""",
            """{"fileUri":"file://local"}""",
            """{"deviceUri":"ph://1"}""",
            """{"mediaUrl":"https://example.invalid/x.jpg"}""",
            """{"imageUrl":"https://example.invalid/x.jpg"}""",
            """{"base64":"abc"}""",
            """{"binary":"abc"}""",
            """{"rawBytes":"abc"}""",
            """{"unknownProviderPayload":"abc"}"""
        )

        forbiddenBodies.forEach { body ->
            val response = post(
                path = "/api/v1/assistant/conversations/${fixture.conversationId}/vision-analyses/${fixture.analysisId}/import",
                body = body,
                bearerToken = login.accessToken
            )
            assertEquals(400, response.statusCode(), "body $body should be rejected: ${response.body()}")
        }
    }

    @Test
    fun `analysis status eligibility is enforced`() {
        val login = login()
        val pending = analysisWithStatus(login, VisionAnalysisStatus.PENDING)
        val failed = analysisWithStatus(login, VisionAnalysisStatus.FAILED)
        val ineligible = analysisWithStatus(login, VisionAnalysisStatus.INELIGIBLE)

        listOf(pending, failed, ineligible).forEach { fixture ->
            val response = post(
                path = "/api/v1/assistant/conversations/${fixture.conversationId}/vision-analyses/${fixture.analysisId}/import",
                body = "",
                bearerToken = login.accessToken
            )
            assertEquals(409, response.statusCode(), response.body())
            assertThat(response.body()).contains("Only COMPLETED vision analysis can be imported")
        }
    }

    @Test
    fun `missing analysis missing attachment and non image attachment are rejected`() {
        val login = login()
        val conversationId = startConversation(login)
        val missingAnalysis = post(
            path = "/api/v1/assistant/conversations/$conversationId/vision-analyses/${UUID.randomUUID()}/import",
            body = "",
            bearerToken = login.accessToken
        )
        assertEquals(404, missingAnalysis.statusCode())

        val pdfFixture = completedAnalysis(
            login = login,
            observationText = "Report text",
            confidence = BigDecimal("0.80"),
            attachmentType = AttachmentType.PDF,
            fileName = "report.pdf",
            mimeType = "application/pdf"
        )
        val nonImage = post(
            path = "/api/v1/assistant/conversations/${pdfFixture.conversationId}/vision-analyses/${pdfFixture.analysisId}/import",
            body = "",
            bearerToken = login.accessToken
        )
        assertEquals(400, nonImage.statusCode(), nonImage.body())
    }

    @Test
    fun `cross user hotel and conversation imports use non leaking not found behavior`() {
        val login = login()
        val ownerFixture = completedAnalysis(login, "Room 101 AC not working", BigDecimal("0.80"))
        val otherConversationId = startConversation(login)

        val crossConversation = post(
            path = "/api/v1/assistant/conversations/$otherConversationId/vision-analyses/${ownerFixture.analysisId}/import",
            body = "",
            bearerToken = login.accessToken
        )
        assertEquals(404, crossConversation.statusCode(), crossConversation.body())

        val crossUserConversation = conversationRepository.save(
            Conversation(
                id = "conversation-cross-user-${UuidV7Generator.generate()}",
                hotelId = login.hotelId,
                userId = "other-user"
            )
        )
        val crossUser = post(
            path = "/api/v1/assistant/conversations/${crossUserConversation.id}/vision-analyses/${ownerFixture.analysisId}/import",
            body = "",
            bearerToken = login.accessToken
        )
        assertEquals(404, crossUser.statusCode())

        val crossHotelConversation = conversationRepository.save(
            Conversation(
                id = "conversation-cross-hotel-${UuidV7Generator.generate()}",
                hotelId = "other-hotel",
                userId = login.userId
            )
        )
        val crossHotel = post(
            path = "/api/v1/assistant/conversations/${crossHotelConversation.id}/vision-analyses/${ownerFixture.analysisId}/import",
            body = "",
            bearerToken = login.accessToken
        )
        assertEquals(404, crossHotel.statusCode())
    }

    @Test
    fun `completed analysis with no usable observations is rejected and blank observations are omitted`() {
        val login = login()
        val noObservation = completedAnalysis(login, "", BigDecimal("0.80"), includeBlankOnly = true)

        val rejected = post(
            path = "/api/v1/assistant/conversations/${noObservation.conversationId}/vision-analyses/${noObservation.analysisId}/import",
            body = "",
            bearerToken = login.accessToken
        )
        assertEquals(409, rejected.statusCode(), rejected.body())

        val mixed = completedAnalysis(login, "Room 101 AC not working", BigDecimal("0.80"), includeLeadingBlank = true)
        val imported = post(
            path = "/api/v1/assistant/conversations/${mixed.conversationId}/vision-analyses/${mixed.analysisId}/import",
            body = "",
            bearerToken = login.accessToken
        )
        assertEquals(200, imported.statusCode(), imported.body())
        assertThat(imported.body()).contains("Room 101 AC not working")
    }

    @Test
    fun `confidence boundaries follow low medium and high behavior`() {
        val login = login()
        val low = completedAnalysis(login, "Room 101 AC not working", BigDecimal("0.49"))
        val mediumStart = completedAnalysis(login, "AC not working", BigDecimal("0.50"))
        val mediumEnd = completedAnalysis(login, "AC not working", BigDecimal("0.79"))
        val high = completedAnalysis(login, "Room 101 AC not working", BigDecimal("0.80"))
        val beforeTasks = taskCount()

        val lowResponse = importFixture(login, low)
        assertEquals(200, lowResponse.statusCode(), lowResponse.body())
        assertThat(lowResponse.body()).contains("WAITING_FOR_USER_ANSWER")
        assertThat(lowResponse.body()).doesNotContain("WAITING_FOR_CONFIRMATION")

        val mediumStartResponse = importFixture(login, mediumStart)
        assertEquals(200, mediumStartResponse.statusCode(), mediumStartResponse.body())
        assertThat(mediumStartResponse.body()).contains("WAITING_FOR_USER_ANSWER")

        val mediumEndResponse = importFixture(login, mediumEnd)
        assertEquals(200, mediumEndResponse.statusCode(), mediumEndResponse.body())
        assertThat(mediumEndResponse.body()).contains("WAITING_FOR_USER_ANSWER")

        val highResponse = importFixture(login, high)
        assertEquals(200, highResponse.statusCode(), highResponse.body())
        assertThat(highResponse.body()).contains("WAITING_FOR_CONFIRMATION")
        assertThat(taskCount()).isEqualTo(beforeTasks)
    }

    @Test
    fun `duplicate import is idempotent and creates no duplicate conversation effect`() {
        val login = login()
        val fixture = completedAnalysis(login, "Room 101 AC not working", BigDecimal("0.80"))
        val beforeTasks = taskCount()

        val first = importFixture(login, fixture)
        val second = importFixture(login, fixture)

        assertEquals(200, first.statusCode(), first.body())
        assertEquals(200, second.statusCode(), second.body())
        assertThat(importCount(fixture.analysisId)).isEqualTo(1)
        assertThat(messageCount(fixture.conversationId)).isEqualTo(1)
        assertThat(taskCount()).isEqualTo(beforeTasks)
    }

    @Test
    fun `import persistence round trips source fields without media payloads or provider secrets`() {
        val login = login()
        val fixture = completedAnalysis(
            login = login,
            observationText = "  Room 101 AC not working  ",
            confidence = BigDecimal("0.80"),
            providerId = "deterministic/local:secret"
        )

        val response = importFixture(login, fixture)
        assertEquals(200, response.statusCode(), response.body())

        val conversation = conversationRepository.findById(fixture.conversationId) ?: error("conversation missing")
        val importedObservation = conversation.messages.last().imageObservations.single()
        assertThat(importedObservation.source.name).isEqualTo("VISION_ANALYSIS")
        assertThat(importedObservation.analysisId).isEqualTo(fixture.analysisId.toString())
        assertThat(importedObservation.attachmentId).isEqualTo(fixture.attachmentId.toString())
        assertThat(importedObservation.confidence).isEqualTo(0.80)
        assertThat(importedObservation.providerId).isEqualTo("deterministiclocalsecret")
        assertThat(importedObservation.advisory).isTrue()
        assertThat(importedObservation.order).isEqualTo(0)
        assertThat(importedObservation.text).contains("Advisory provider analysis: Room 101 AC not working")

        val messagesJson = jdbcTemplate.queryForObject(
            "select messages_json::text from assistant_conversation where id = '${fixture.conversationId}'",
            String::class.java
        ) ?: ""
        assertThat(messagesJson).contains("VISION_ANALYSIS", fixture.analysisId.toString(), fixture.attachmentId.toString())
        assertThat(messagesJson).doesNotContain("base64", "file://", "storageReference", "providerMetadata", "secret-key")

        val importRow = jdbcTemplate.queryForMap("select * from vision_analysis_import where analysis_id = '${fixture.analysisId}'::uuid")
        assertThat(importRow["message_id"]).isNotNull()
        assertThat(importRow["status"]).isEqualTo("COMPLETED")
    }

    private fun importFixture(login: LoginSnapshot, fixture: AnalysisFixture): HttpResponse<String> =
        post(
            path = "/api/v1/assistant/conversations/${fixture.conversationId}/vision-analyses/${fixture.analysisId}/import",
            body = "",
            bearerToken = login.accessToken
        )

    private fun completedAnalysis(
        login: LoginSnapshot,
        observationText: String,
        confidence: BigDecimal,
        attachmentType: AttachmentType = AttachmentType.IMAGE,
        fileName: String = "vision.jpg",
        mimeType: String = "image/jpeg",
        providerId: String = "deterministic-local",
        includeBlankOnly: Boolean = false,
        includeLeadingBlank: Boolean = false
    ): AnalysisFixture {
        val conversationId = startConversation(login)
        val attachment = attachmentRegistrationService.register(
            conversationId = conversationId,
            hotelId = login.hotelId,
            userId = login.userId,
            command = RegisterAssistantAttachmentCommand(
                type = attachmentType,
                originalFileName = fileName,
                mimeType = mimeType,
                sizeBytes = 100,
                widthPx = if (attachmentType == AttachmentType.IMAGE) 100 else null,
                heightPx = if (attachmentType == AttachmentType.IMAGE) 100 else null
            )
        )
        val now = Instant.now()
        val observations = when {
            includeBlankOnly -> listOf(VisionDetectedObservation(0, "blank", "   ", confidence))
            includeLeadingBlank -> listOf(
                VisionDetectedObservation(0, "blank", "   ", confidence),
                VisionDetectedObservation(1, "issue", observationText, confidence)
            )
            else -> listOf(VisionDetectedObservation(0, "issue", observationText, confidence))
        }
        val analysis = visionAnalysisRepository.save(
            VisionAnalysis(
                id = UuidV7Generator.generate(now),
                attachmentId = attachment.id,
                conversationId = conversationId,
                hotelId = login.hotelId,
                userId = login.userId,
                status = VisionAnalysisStatus.COMPLETED,
                providerId = providerId,
                providerModel = "deterministic-fixture",
                providerVersion = "test",
                confidence = confidence,
                observations = observations,
                detectedIssueCategory = "MAINTENANCE",
                detectedLocationHint = "101",
                providerMetadata = mapOf("fixtureKey" to "test", "safe" to "true"),
                idempotencyKey = "analysis-${UuidV7Generator.generate()}",
                attemptCount = 1,
                requestedAt = now,
                completedAt = now,
                createdAt = now,
                updatedAt = now
            )
        )
        return AnalysisFixture(conversationId, attachment.id, analysis.id)
    }

    private fun analysisWithStatus(login: LoginSnapshot, status: VisionAnalysisStatus): AnalysisFixture {
        val fixture = completedAnalysis(login, "Room 101 AC not working", BigDecimal("0.80"))
        val now = Instant.now()
        visionAnalysisRepository.save(
            VisionAnalysis(
                id = fixture.analysisId,
                attachmentId = fixture.attachmentId,
                conversationId = fixture.conversationId,
                hotelId = login.hotelId,
                userId = login.userId,
                status = status,
                providerId = "deterministic-local",
                confidence = if (status == VisionAnalysisStatus.COMPLETED) BigDecimal("0.80") else null,
                observations = if (status == VisionAnalysisStatus.COMPLETED) {
                    listOf(VisionDetectedObservation(0, "issue", "Room 101 AC not working", BigDecimal("0.80")))
                } else {
                    emptyList()
                },
                failureCode = if (status == VisionAnalysisStatus.FAILED || status == VisionAnalysisStatus.INELIGIBLE) "TEST" else null,
                failureMessage = if (status == VisionAnalysisStatus.FAILED || status == VisionAnalysisStatus.INELIGIBLE) "test failure" else null,
                idempotencyKey = "analysis-status-${UuidV7Generator.generate()}",
                attemptCount = 1,
                requestedAt = now,
                completedAt = if (status == VisionAnalysisStatus.COMPLETED) now else null,
                failedAt = if (status == VisionAnalysisStatus.FAILED || status == VisionAnalysisStatus.INELIGIBLE) now else null,
                createdAt = now,
                updatedAt = now
            )
        )
        return fixture
    }

    private fun startConversation(login: LoginSnapshot): String =
        extractConversationId(
            post(
                path = "/api/v1/assistant/conversations",
                body = """{"hotelId":"ignored","userId":"ignored"}""",
                bearerToken = login.accessToken
            ).body()
        )

    private fun login(): LoginSnapshot {
        val response = post(
            "/api/v1/auth/login",
            """{"hotelCode":"hotel-opai-demo","email":"admin@hotelopai.local","password":"admin123"}"""
        )
        assertEquals(200, response.statusCode())
        return LoginSnapshot(
            accessToken = extract(response.body(), "accessToken"),
            hotelId = extract(response.body(), "hotelId"),
            userId = extract(response.body(), "userId")
        )
    }

    private fun post(path: String, body: String, bearerToken: String? = null): HttpResponse<String> {
        val requestBuilder = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$port$path"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
        if (bearerToken != null) {
            requestBuilder.header("Authorization", "Bearer $bearerToken")
        }
        return httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString())
    }

    private fun extractConversationId(body: String): String =
        extract(body, "conversationId")

    private fun extract(body: String, field: String): String =
        Regex(""""$field":"([^"]+)"""")
            .find(body)
            ?.groupValues
            ?.get(1)
            ?: error("$field not found in response: $body")

    private fun taskCount(): Int =
        jdbcTemplate.queryForObject("select count(*) from task", Int::class.java) ?: 0

    private fun importCount(analysisId: UUID): Int =
        jdbcTemplate.queryForObject(
            "select count(*) from vision_analysis_import where analysis_id = '$analysisId'::uuid",
            Int::class.java
        ) ?: 0

    private fun messageCount(conversationId: String): Int =
        jdbcTemplate.queryForObject(
            "select jsonb_array_length(messages_json) from assistant_conversation where id = '$conversationId'",
            Int::class.java
        ) ?: 0

    private data class LoginSnapshot(
        val accessToken: String,
        val hotelId: String,
        val userId: String
    )

    private data class AnalysisFixture(
        val conversationId: String,
        val attachmentId: UUID,
        val analysisId: UUID
    )
}
