package com.hotelopai.task.api

import com.hotelopai.assistant.application.AssistantAttachmentRegistrationService
import com.hotelopai.assistant.application.AssistantConversationService
import com.hotelopai.assistant.application.AssistantAttachmentRepository
import com.hotelopai.assistant.application.ConversationRepository
import com.hotelopai.assistant.application.RegisterAssistantAttachmentCommand
import com.hotelopai.assistant.application.TaskConfirmationRepository
import com.hotelopai.assistant.domain.AttachmentStorageStatus
import com.hotelopai.assistant.domain.AttachmentType
import com.hotelopai.assistant.domain.Conversation
import com.hotelopai.assistant.domain.ConversationAttachment
import com.hotelopai.assistant.domain.ConversationMessage
import com.hotelopai.assistant.domain.ConversationState
import com.hotelopai.assistant.domain.InputType
import com.hotelopai.assistant.domain.IntentType
import com.hotelopai.assistant.domain.MessageRole
import com.hotelopai.assistant.domain.RegisteredConversationAttachment
import com.hotelopai.assistant.domain.TaskPreview
import com.hotelopai.shared.kernel.UuidV7Generator
import com.hotelopai.support.PostgresIntegrationTestSupport
import com.hotelopai.vision.application.VisionAnalysisRepository
import com.hotelopai.vision.domain.VisionAnalysis
import com.hotelopai.vision.domain.VisionAnalysisStatus
import com.hotelopai.vision.domain.VisionDetectedObservation
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
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
class TaskAttachmentLinkIntegrationTest : PostgresIntegrationTestSupport() {
    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var assistantConversationService: AssistantConversationService

    @Autowired
    private lateinit var conversationRepository: ConversationRepository

    @Autowired
    private lateinit var attachmentRegistrationService: AssistantAttachmentRegistrationService

    @Autowired
    private lateinit var assistantAttachmentRepository: AssistantAttachmentRepository

    @Autowired
    private lateinit var visionAnalysisRepository: VisionAnalysisRepository

    @Autowired
    private lateinit var taskConfirmationRepository: TaskConfirmationRepository

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    private val httpClient = HttpClient.newHttpClient()

    @Test
    fun `confirmation links only relevant registered attachments and read API exposes safe metadata`() {
        val login = login()
        val conversationId = startConversation(login)
        val oldAttachment = registerAttachment(login, conversationId, "old.jpg")
        sendMessage(login, conversationId, text = "Keep this unrelated image for later", attachmentIds = listOf(oldAttachment))
        post("/api/v1/assistant/conversations/$conversationId/reset", "", login.accessToken)

        val firstRelevant = registerAttachment(login, conversationId, "sink.jpg")
        val secondRelevant = registerAttachment(login, conversationId, "floor.jpg")
        val preview = sendMessage(
            login = login,
            conversationId = conversationId,
            text = "Room 101 sink is leaking",
            attachmentIds = listOf(firstRelevant, secondRelevant)
        )
        assertThat(preview.body()).contains("WAITING_FOR_CONFIRMATION")

        val confirmed = confirm(login, conversationId, "confirm-links-1")
        assertEquals(200, confirmed.statusCode(), confirmed.body())
        val taskId = extract(confirmed.body(), "createdTaskId")

        val links = get("/api/v1/tasks/$taskId/attachments", login.accessToken)
        assertEquals(200, links.statusCode(), links.body())
        assertThat(links.body()).contains(firstRelevant.toString(), secondRelevant.toString(), "ASSISTANT_MESSAGE")
        assertThat(links.body()).doesNotContain(oldAttachment.toString(), "storageReference", "downloadUrl", "localReference", "base64")
        assertThat(linkCount(taskId)).isEqualTo(2)
        val linkCreatedAtNanos = jdbcTemplate.queryForList(
            "select created_at from task_attachment_link where task_id = ?::uuid",
            java.sql.Timestamp::class.java,
            taskId
        ).map { requireNotNull(it).toInstant().nano }
        assertThat(linkCreatedAtNanos).allMatch { it % 1_000 == 0 }

        val duplicate = confirm(login, conversationId, "confirm-links-1")
        assertEquals(200, duplicate.statusCode(), duplicate.body())
        assertThat(extract(duplicate.body(), "createdTaskId")).isEqualTo(taskId)
        assertThat(linkCount(taskId)).isEqualTo(2)
        assertThat(taskCountForConversationTask(taskId)).isEqualTo(1)
    }

    @Test
    fun `confirmation links registered attachment sent by mobile shaped request`() {
        val login = login()
        val conversationId = startConversation(login)
        val attachmentId = registerAttachment(login, conversationId, "mobile-sink.jpg")

        val preview = post(
            "/api/v1/assistant/conversations/$conversationId/messages",
            """
                {
                  "text":"Room 101 sink is leaking",
                  "inputType":"MIXED",
                  "attachments":[],
                  "attachmentIds":["$attachmentId"]
                }
            """.trimIndent(),
            login.accessToken
        )
        assertEquals(200, preview.statusCode(), preview.body())
        assertThat(preview.body()).contains(
            "WAITING_FOR_CONFIRMATION",
            attachmentId.toString(),
            "mobile-sink.jpg",
            """"storageStatus":"REGISTERED""""
        )

        val confirmed = confirm(login, conversationId, "confirm-mobile-shaped-link")
        assertEquals(200, confirmed.statusCode(), confirmed.body())
        val taskId = extract(confirmed.body(), "createdTaskId")

        val links = get("/api/v1/tasks/$taskId/attachments", login.accessToken)
        assertEquals(200, links.statusCode(), links.body())
        assertThat(links.body()).contains(
            attachmentId.toString(),
            "mobile-sink.jpg",
            "ASSISTANT_MESSAGE",
            """"storageStatus":"REGISTERED""""
        )
        assertThat(links.body()).doesNotContain("storageReference", "downloadUrl", "localReference", "base64")
        assertThat(linkCount(taskId)).isEqualTo(1)
    }

    @Test
    fun `text only and local metadata only confirmation create no durable links`() {
        val login = login()
        val textOnlyConversationId = startConversation(login)
        val preview = sendMessage(
            login = login,
            conversationId = textOnlyConversationId,
            text = "Room 102 needs towels",
            attachmentIds = emptyList()
        )
        assertThat(preview.body()).contains("WAITING_FOR_CONFIRMATION")

        val confirmed = confirm(login, textOnlyConversationId, "confirm-no-links")
        assertEquals(200, confirmed.statusCode(), confirmed.body())
        val taskId = extract(confirmed.body(), "createdTaskId")

        val links = get("/api/v1/tasks/$taskId/attachments", login.accessToken)
        assertEquals(200, links.statusCode(), links.body())
        assertEquals("[]", links.body())

        val localMetadataConversationId = startConversation(login)
        val localMetadataPreview = post(
            "/api/v1/assistant/conversations/$localMetadataConversationId/messages",
            """
            {
              "text":"Room 103 sink is leaking",
              "inputType":"TEXT",
              "attachments":[{
                "id":"local-attachment-1",
                "type":"IMAGE",
                "originalFileName":"local.jpg",
                "mimeType":"image/jpeg",
                "sizeBytes":100,
                "widthPx":100,
                "heightPx":100,
                "storageStatus":"LOCAL_METADATA_ONLY"
              }]
            }
            """.trimIndent(),
            login.accessToken
        )
        assertEquals(200, localMetadataPreview.statusCode(), localMetadataPreview.body())
        assertThat(localMetadataPreview.body()).contains("WAITING_FOR_CONFIRMATION")

        val localMetadataConfirmed = confirm(login, localMetadataConversationId, "confirm-local-metadata-only")
        assertEquals(200, localMetadataConfirmed.statusCode(), localMetadataConfirmed.body())
        val localMetadataTaskId = extract(localMetadataConfirmed.body(), "createdTaskId")
        assertThat(linkCount(localMetadataTaskId)).isZero()
    }

    @Test
    fun `vision analysis import confirmation links analysis attachment with provenance`() {
        val login = login()
        val fixture = completedAnalysis(login)
        val imported = post(
            "/api/v1/assistant/conversations/${fixture.conversationId}/vision-analyses/${fixture.analysisId}/import",
            "",
            login.accessToken
        )
        assertEquals(200, imported.statusCode(), imported.body())
        assertThat(imported.body()).contains("WAITING_FOR_CONFIRMATION")

        val confirmed = confirm(login, fixture.conversationId, "confirm-vision-link")
        assertEquals(200, confirmed.statusCode(), confirmed.body())
        val taskId = extract(confirmed.body(), "createdTaskId")
        val importId = jdbcTemplate.queryForObject(
            "select id::text from vision_analysis_import where analysis_id = '${fixture.analysisId}'::uuid",
            String::class.java
        )

        val links = get("/api/v1/tasks/$taskId/attachments", login.accessToken)
        assertEquals(200, links.statusCode(), links.body())
        assertThat(links.body()).contains(
            fixture.attachmentId.toString(),
            fixture.analysisId.toString(),
            importId,
            "VISION_ANALYSIS",
            "REGISTERED"
        )
        assertThat(links.body()).doesNotContain("providerMetadata", "storageReference", "downloadUrl", "base64")
        assertThat(taskCountForConversationTask(taskId)).isEqualTo(1)
    }

    @Test
    fun `scope inconsistent registered attachment rolls back task confirmation and conversation mutation`() {
        val login = login()
        val conversation = conversationRepository.save(
            Conversation(
                id = "conversation-rollback-${UuidV7Generator.generate()}",
                hotelId = login.hotelId,
                userId = login.userId
            )
        )
        val foreignConversation = conversationRepository.save(
            Conversation(
                id = "conversation-foreign-${UuidV7Generator.generate()}",
                hotelId = login.hotelId,
                userId = login.userId
            )
        )
        val foreignAttachment = assistantAttachmentRepository.save(
            RegisteredConversationAttachment(
                id = UuidV7Generator.generate(),
                conversationId = foreignConversation.id,
                hotelId = login.hotelId,
                userId = login.userId,
                type = AttachmentType.IMAGE,
                originalFileName = "foreign.jpg",
                declaredMimeType = "image/jpeg",
                declaredSizeBytes = 100,
                widthPx = 100,
                heightPx = 100,
                createdAt = Instant.now()
            )
        )
        val messageId = "message-${UuidV7Generator.generate()}"
        conversationRepository.save(
            conversation.copy(
                state = ConversationState.WAITING_FOR_CONFIRMATION,
                messages = listOf(
                    ConversationMessage(
                        id = messageId,
                        role = MessageRole.USER,
                        inputType = InputType.IMAGE,
                        text = "Room 101 sink is leaking",
                        attachments = listOf(
                            ConversationAttachment(
                                id = foreignAttachment.id.toString(),
                                type = AttachmentType.IMAGE,
                                storageStatus = AttachmentStorageStatus.REGISTERED
                            )
                        )
                    )
                ),
                intent = IntentType.MAINTENANCE,
                collectedFields = mapOf("roomNumber" to "101", "issue" to "sink is leaking"),
                taskPreview = TaskPreview(
                    type = IntentType.MAINTENANCE,
                    title = "Sink leak",
                    description = "Room 101 sink is leaking",
                    roomNumber = "101",
                    priority = "HIGH",
                    slaMinutes = 60
                ),
                activeDraftId = "draft-rollback",
                activeDraftSourceMessageIds = listOf(messageId),
                draftVersion = 1
            )
        )

        val beforeTasks = taskCount()
        val beforeLinks = linkCount()
        assertThrows(IllegalStateException::class.java) {
            assistantConversationService.confirmTask(
                conversationId = conversation.id,
                hotelId = login.hotelId,
                userId = login.userId,
                idempotencyKey = "confirm-rollback"
            )
        }

        assertThat(taskCount()).isEqualTo(beforeTasks)
        assertThat(linkCount()).isEqualTo(beforeLinks)
        assertThat(
            taskConfirmationRepository.findByConversationIdAndIdempotencyKey(conversation.id, "confirm-rollback")
        ).isNull()
        val reloaded = conversationRepository.findById(conversation.id) ?: error("conversation missing")
        assertThat(reloaded.state).isEqualTo(ConversationState.WAITING_FOR_CONFIRMATION)
        assertThat(reloaded.createdTaskId).isNull()
    }

    private fun completedAnalysis(login: LoginSnapshot): AnalysisFixture {
        val conversationId = startConversation(login)
        val attachmentId = registerAttachment(login, conversationId, "vision.jpg")
        val now = Instant.now()
        val analysis = visionAnalysisRepository.save(
            VisionAnalysis(
                id = UuidV7Generator.generate(now),
                attachmentId = attachmentId,
                conversationId = conversationId,
                hotelId = login.hotelId,
                userId = login.userId,
                status = VisionAnalysisStatus.COMPLETED,
                providerId = "deterministic-local",
                providerModel = "deterministic-fixture",
                providerVersion = "test",
                confidence = BigDecimal("0.80"),
                observations = listOf(VisionDetectedObservation(0, "issue", "Room 101 sink is leaking", BigDecimal("0.80"))),
                idempotencyKey = "analysis-${UuidV7Generator.generate()}",
                attemptCount = 1,
                requestedAt = now,
                completedAt = now,
                createdAt = now,
                updatedAt = now
            )
        )
        return AnalysisFixture(conversationId, attachmentId, analysis.id)
    }

    private fun registerAttachment(login: LoginSnapshot, conversationId: String, fileName: String): UUID =
        attachmentRegistrationService.register(
            conversationId = conversationId,
            hotelId = login.hotelId,
            userId = login.userId,
            command = RegisterAssistantAttachmentCommand(
                type = AttachmentType.IMAGE,
                originalFileName = fileName,
                mimeType = "image/jpeg",
                sizeBytes = 100,
                widthPx = 100,
                heightPx = 100
            )
        ).id

    private fun sendMessage(
        login: LoginSnapshot,
        conversationId: String,
        text: String,
        attachmentIds: List<UUID>
    ): HttpResponse<String> =
        post(
            "/api/v1/assistant/conversations/$conversationId/messages",
            """{"text":"$text","inputType":"TEXT","attachmentIds":[${attachmentIds.joinToString { "\"$it\"" }}]}""",
            login.accessToken
        )

    private fun confirm(login: LoginSnapshot, conversationId: String, idempotencyKey: String): HttpResponse<String> =
        post(
            "/api/v1/assistant/conversations/$conversationId/confirm",
            """{"idempotencyKey":"$idempotencyKey"}""",
            login.accessToken
        )

    private fun startConversation(login: LoginSnapshot): String =
        extract(
            post(
                "/api/v1/assistant/conversations",
                """{"hotelId":"ignored","userId":"ignored"}""",
                login.accessToken
            ).body(),
            "conversationId"
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
        val builder = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$port$path"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
        if (bearerToken != null) {
            builder.header("Authorization", "Bearer $bearerToken")
        }
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString())
    }

    private fun get(path: String, bearerToken: String? = null): HttpResponse<String> {
        val builder = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$port$path"))
            .GET()
        if (bearerToken != null) {
            builder.header("Authorization", "Bearer $bearerToken")
        }
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString())
    }

    private fun extract(body: String, field: String): String =
        Regex(""""$field":"([^"]+)"""")
            .find(body)
            ?.groupValues
            ?.get(1)
            ?: error("$field not found in response: $body")

    private fun taskCount(): Int =
        jdbcTemplate.queryForObject("select count(*) from task", Int::class.java) ?: 0

    private fun taskCountForConversationTask(taskId: String): Int =
        jdbcTemplate.queryForObject("select count(*) from task where id = '$taskId'::uuid", Int::class.java) ?: 0

    private fun linkCount(taskId: String? = null): Int =
        jdbcTemplate.queryForObject(
            taskId?.let { "select count(*) from task_attachment_link where task_id = '$it'::uuid" }
                ?: "select count(*) from task_attachment_link",
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
