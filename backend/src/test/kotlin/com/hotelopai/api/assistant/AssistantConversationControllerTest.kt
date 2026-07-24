package com.hotelopai.assistant.api

import com.hotelopai.support.PostgresIntegrationTestSupport
import com.hotelopai.assistant.application.ConversationRepository
import com.hotelopai.assistant.domain.Conversation
import com.hotelopai.shared.kernel.UuidV7Generator
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Instant
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AssistantConversationControllerTest : PostgresIntegrationTestSupport() {
    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var conversationRepository: ConversationRepository

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    private val httpClient = HttpClient.newHttpClient()

    @Test
    fun `assistant conversation endpoints support start message confirm and reset`() {
        val login = login()

        val startResponse = post(
            path = "/api/v1/assistant/conversations",
            body = """{"hotelId":"${login.hotelId}","userId":"user-1"}""",
            bearerToken = login.accessToken
        )

        assertEquals(200, startResponse.statusCode())
        assertContains(startResponse.body(), """"state":"IDLE"""")
        val conversationId = extractConversationId(startResponse.body())

        val messageResponse = post(
            path = "/api/v1/assistant/conversations/$conversationId/messages",
            body = """{"text":"Room 101 AC not working","inputType":"TEXT"}""",
            bearerToken = login.accessToken
        )

        assertEquals(200, messageResponse.statusCode())
        assertContains(messageResponse.body(), """"state":"WAITING_FOR_CONFIRMATION"""")
        assertContains(messageResponse.body(), """"intent":"MAINTENANCE"""")
        assertContains(messageResponse.body(), """"type":"MAINTENANCE"""")
        assertContains(messageResponse.body(), """"roomNumber":"101"""")

        val confirmResponse = post(
            path = "/api/v1/assistant/conversations/$conversationId/confirm",
            body = """{"idempotencyKey":"confirm-101"}""",
            bearerToken = login.accessToken
        )

        assertEquals(200, confirmResponse.statusCode())
        assertContains(confirmResponse.body(), """"state":"TASK_CREATED"""")
        assertContains(confirmResponse.body(), """"idempotencyKey":"confirm-101"""")
        val createdTaskId = extractCreatedTaskId(confirmResponse.body())
        assertContains(confirmResponse.body(), """"createdTaskId":"$createdTaskId"""")
        assertContains(confirmResponse.body(), "Task ID: $createdTaskId")

        val duplicateConfirmResponse = post(
            path = "/api/v1/assistant/conversations/$conversationId/confirm",
            body = """{"idempotencyKey":"confirm-101"}""",
            bearerToken = login.accessToken
        )

        assertEquals(200, duplicateConfirmResponse.statusCode())
        assertContains(duplicateConfirmResponse.body(), """"createdTaskId":"$createdTaskId"""")

        val taskResponse = get("/api/v1/tasks/$createdTaskId", login.accessToken)
        assertEquals(200, taskResponse.statusCode())
        assertContains(taskResponse.body(), """"id":"$createdTaskId"""")
        assertContains(taskResponse.body(), """"intentType":"MAINTENANCE"""")

        val resetResponse = post(
            path = "/api/v1/assistant/conversations/$conversationId/reset",
            body = "",
            bearerToken = login.accessToken
        )

        assertEquals(200, resetResponse.statusCode())
        assertContains(resetResponse.body(), """"state":"IDLE"""")
        assertContains(resetResponse.body(), """"taskPreview":null""")
    }

    @Test
    fun `concurrent confirmation of same draft creates one task`() {
        val login = login()
        val conversationId = startConversation(login)
        val message = post(
            path = "/api/v1/assistant/conversations/$conversationId/messages",
            body = """{"text":"Room 101 AC not working","inputType":"TEXT"}""",
            bearerToken = login.accessToken
        )
        assertEquals(200, message.statusCode(), message.body())

        val executor = Executors.newFixedThreadPool(2)
        val futures = listOf("confirm-concurrent-1", "confirm-concurrent-2").map { key ->
            executor.submit<HttpResponse<String>> {
                post(
                    path = "/api/v1/assistant/conversations/$conversationId/confirm",
                    body = """{"idempotencyKey":"$key"}""",
                    bearerToken = login.accessToken
                )
            }
        }
        executor.shutdown()
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS))

        val responses = futures.map { it.get() }

        responses.forEach { response ->
            assertEquals(200, response.statusCode(), response.body())
            assertContains(response.body(), """"state":"TASK_CREATED"""")
        }
        val createdTaskIds = responses.map { extractCreatedTaskId(it.body()) }.toSet()
        assertThat(createdTaskIds).hasSize(1)
        assertEquals(
            1,
            jdbcTemplate.queryForObject(
                "select count(*) from assistant_task_confirmation where conversation_id = ?",
                Int::class.java,
                conversationId
            )
        )
        assertEquals(
            0,
            jdbcTemplate.queryForObject(
                "select count(*) from task_attachment_link where conversation_id = ?",
                Int::class.java,
                conversationId
            )
        )
    }

    @Test
    fun `assistant conversation endpoints accept image attachments`() {
        val login = login()

        val conversationId = extractConversationId(
            post(
                path = "/api/v1/assistant/conversations",
                body = """{"hotelId":"ignored","userId":"ignored"}""",
                bearerToken = login.accessToken
            ).body()
        )

        val imageResponse = post(
            path = "/api/v1/assistant/conversations/$conversationId/messages",
            body = """
                {
                  "text":"",
                  "inputType":"IMAGE",
                  "attachments":[
                    {
                      "id":"att-1",
                      "type":"IMAGE",
                      "originalFileName":"broken-door.jpg",
                      "mimeType":"image/jpeg",
                      "sizeBytes":123456,
                      "widthPx":1200,
                      "heightPx":900,
                      "storageStatus":"LOCAL_METADATA_ONLY"
                    }
                  ]
                }
            """.trimIndent(),
            bearerToken = login.accessToken
        )

        assertEquals(200, imageResponse.statusCode())
        assertContains(imageResponse.body(), """"inputType":"IMAGE"""")
        assertContains(imageResponse.body(), """"attachments":[{"id":"att-1"""")
        assertContains(imageResponse.body(), """"state":"WAITING_FOR_USER_ANSWER"""")
    }

    @Test
    fun `conversation start derives ownership from authenticated context`() {
        val login = login()

        val response = post(
            path = "/api/v1/assistant/conversations",
            body = """{"hotelId":"client-hotel","userId":"client-user"}""",
            bearerToken = login.accessToken
        )

        assertEquals(200, response.statusCode())
        assertContains(response.body(), """"conversationId":""")

        val conversationId = extractConversationId(response.body())
        val messageResponse = post(
            path = "/api/v1/assistant/conversations/$conversationId/messages",
            body = """{"text":"Room 101 AC not working","inputType":"TEXT"}""",
            bearerToken = login.accessToken
        )
        assertEquals(200, messageResponse.statusCode())
    }

    @Test
    fun `assistant conversation access is scoped to authenticated owner`() {
        val owner = login()
        val inaccessible = conversationRepository.save(
            Conversation(
                id = "conversation-inaccessible-${UuidV7Generator.generate()}",
                hotelId = owner.hotelId,
                userId = "other-user"
            )
        )

        val messageResponse = post(
            path = "/api/v1/assistant/conversations/${inaccessible.id}/messages",
            body = attachmentMessageBody("att-owner", "IMAGE", "owner.jpg", "image/jpeg", 100),
            bearerToken = owner.accessToken
        )
        val confirmResponse = post(
            path = "/api/v1/assistant/conversations/${inaccessible.id}/confirm",
            body = """{"idempotencyKey":"confirm-owner"}""",
            bearerToken = owner.accessToken
        )
        val resetResponse = post(
            path = "/api/v1/assistant/conversations/${inaccessible.id}/reset",
            body = "",
            bearerToken = owner.accessToken
        )

        assertEquals(404, messageResponse.statusCode())
        assertEquals(404, confirmResponse.statusCode())
        assertEquals(404, resetResponse.statusCode())
    }

    @Test
    fun `assistant accepts supported attachment mime types and metadata only storage`() {
        val login = login()
        listOf(
            Triple("IMAGE", "photo.jpg", "image/jpeg"),
            Triple("IMAGE", "photo.png", "image/png"),
            Triple("IMAGE", "photo.webp", "image/webp"),
            Triple("PDF", "report.pdf", "application/pdf"),
            Triple("DOCUMENT", "notes.txt", "text/plain")
        ).forEachIndexed { index, (type, filename, mimeType) ->
            val conversationId = startConversation(login)
            val response = post(
                path = "/api/v1/assistant/conversations/$conversationId/messages",
                body = attachmentMessageBody(
                    "att-$index",
                    type,
                    filename,
                    mimeType,
                    10_000_000,
                    widthPx = if (type == "IMAGE") 100 else null,
                    heightPx = if (type == "IMAGE") 100 else null
                ),
                bearerToken = login.accessToken
            )

            assertEquals(200, response.statusCode(), response.body())
            assertContains(response.body(), """"storageStatus":"LOCAL_METADATA_ONLY"""")
            assertContains(response.body(), """"originalFileName":"$filename"""")
        }
    }

    @Test
    fun `attachment metadata registration requires authentication`() {
        val response = post(
            path = "/api/v1/assistant/conversations/conversation-any/attachments",
            body = registrationBody("IMAGE", "photo.jpg", "image/jpeg", 123, widthPx = 100, heightPx = 100)
        )

        assertEquals(401, response.statusCode())
    }

    @Test
    fun `attachment registration persists server owned metadata only identity`() {
        val login = login()
        val conversationId = startConversation(login)
        val beforeTasks = taskCount()

        val response = post(
            path = "/api/v1/assistant/conversations/$conversationId/attachments",
            body = registrationBody("IMAGE", "broken-door.jpg", "image/jpeg", 123456, widthPx = 1200, heightPx = 900),
            bearerToken = login.accessToken
        )

        assertEquals(200, response.statusCode(), response.body())
        val attachmentId = extractAttachmentId(response.body())
        assertThat(attachmentId).isNotEqualTo("client-id")
        assertContains(response.body(), """"conversationId":"$conversationId"""")
        assertContains(response.body(), """"type":"IMAGE"""")
        assertContains(response.body(), """"originalFileName":"broken-door.jpg"""")
        assertContains(response.body(), """"mimeType":"image/jpeg"""")
        assertContains(response.body(), """"sizeBytes":123456""")
        assertContains(response.body(), """"storageStatus":"REGISTERED"""")
        assertContains(response.body(), """"storageReference":null""")
        assertEquals(beforeTasks, taskCount(), "registration must not create a task")

        val row = jdbcTemplate.queryForMap("select * from assistant_attachment where id = '$attachmentId'::uuid")
        assertEquals(conversationId, row["conversation_id"])
        assertEquals(login.hotelId, row["hotel_id"])
        assertEquals(login.userId, row["user_id"])
        assertEquals("REGISTERED", row["storage_status"])
        assertEquals(null, row["storage_reference"])
    }

    @Test
    fun `attachment registration without idempotency header creates distinct registrations`() {
        val login = login()
        val conversationId = startConversation(login)
        val body = registrationBody("IMAGE", "retry-free.jpg", "image/jpeg", 100, widthPx = 100, heightPx = 100)

        val first = post(
            path = "/api/v1/assistant/conversations/$conversationId/attachments",
            body = body,
            bearerToken = login.accessToken
        )
        val second = post(
            path = "/api/v1/assistant/conversations/$conversationId/attachments",
            body = body,
            bearerToken = login.accessToken
        )

        assertEquals(200, first.statusCode(), first.body())
        assertEquals(200, second.statusCode(), second.body())
        assertThat(extractAttachmentId(first.body())).isNotEqualTo(extractAttachmentId(second.body()))
    }

    @Test
    fun `attachment registration idempotency key returns existing attachment for identical metadata`() {
        val login = login()
        val conversationId = startConversation(login)
        val body = registrationBody("IMAGE", "idempotent.jpg", "image/jpeg", 100, widthPx = 100, heightPx = 100)
        val headers = mapOf("Idempotency-Key" to "attachment-key-1")

        val first = post(
            path = "/api/v1/assistant/conversations/$conversationId/attachments",
            body = body,
            bearerToken = login.accessToken,
            headers = headers
        )
        val second = post(
            path = "/api/v1/assistant/conversations/$conversationId/attachments",
            body = body,
            bearerToken = login.accessToken,
            headers = headers
        )

        assertEquals(200, first.statusCode(), first.body())
        assertEquals(200, second.statusCode(), second.body())
        val attachmentId = extractAttachmentId(first.body())
        assertEquals(attachmentId, extractAttachmentId(second.body()))
        val firstCreatedAt = Instant.parse(extractAttachmentCreatedAt(first.body()))
        val secondCreatedAt = Instant.parse(extractAttachmentCreatedAt(second.body()))
        assertThat(secondCreatedAt).isEqualTo(firstCreatedAt)
        assertThat(
            jdbcTemplate.queryForObject(
                "select created_at from assistant_attachment where id = ?::uuid",
                java.sql.Timestamp::class.java,
                attachmentId
            )?.toInstant()
        ).isEqualTo(firstCreatedAt)
        assertEquals(
            1,
            jdbcTemplate.queryForObject(
                "select count(*) from assistant_attachment where conversation_id = ? and registration_idempotency_key = ?",
                Int::class.java,
                conversationId,
                "attachment-key-1"
            )
        )
    }

    @Test
    fun `concurrent attachment registration with same idempotency key creates one row`() {
        val login = login()
        val conversationId = startConversation(login)
        val body = registrationBody("IMAGE", "concurrent.jpg", "image/jpeg", 100, widthPx = 100, heightPx = 100)
        val executor = Executors.newFixedThreadPool(2)
        val futures = (1..2).map {
            executor.submit<HttpResponse<String>> {
                post(
                    path = "/api/v1/assistant/conversations/$conversationId/attachments",
                    body = body,
                    bearerToken = login.accessToken,
                    headers = mapOf("Idempotency-Key" to "attachment-key-concurrent")
                )
            }
        }
        executor.shutdown()
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS))

        val responses = futures.map { it.get() }

        responses.forEach { assertEquals(200, it.statusCode(), it.body()) }
        assertThat(responses.map { extractAttachmentId(it.body()) }.toSet()).hasSize(1)
        assertEquals(
            1,
            jdbcTemplate.queryForObject(
                "select count(*) from assistant_attachment where conversation_id = ? and registration_idempotency_key = ?",
                Int::class.java,
                conversationId,
                "attachment-key-concurrent"
            )
        )
    }

    @Test
    fun `attachment registration idempotency key rejects different metadata`() {
        val login = login()
        val conversationId = startConversation(login)
        val headers = mapOf("Idempotency-Key" to "attachment-key-conflict")
        val first = post(
            path = "/api/v1/assistant/conversations/$conversationId/attachments",
            body = registrationBody("IMAGE", "same-key.jpg", "image/jpeg", 100, widthPx = 100, heightPx = 100),
            bearerToken = login.accessToken,
            headers = headers
        )
        assertEquals(200, first.statusCode(), first.body())

        listOf(
            registrationBody("IMAGE", "different-name.jpg", "image/jpeg", 100, widthPx = 100, heightPx = 100),
            registrationBody("IMAGE", "same-key.jpg", "image/png", 100, widthPx = 100, heightPx = 100),
            registrationBody("IMAGE", "same-key.jpg", "image/jpeg", 101, widthPx = 100, heightPx = 100)
        ).forEach { changedBody ->
            val conflict = post(
                path = "/api/v1/assistant/conversations/$conversationId/attachments",
                body = changedBody,
                bearerToken = login.accessToken,
                headers = headers
            )

            assertEquals(409, conflict.statusCode(), conflict.body())
            assertContains(conflict.body(), """"title":"Attachment registration conflict"""")
        }
    }

    @Test
    fun `attachment registration idempotency key is scoped by conversation user and hotel`() {
        val login = login()
        val conversationId = startConversation(login)
        val otherConversationId = startConversation(login)
        val crossUser = conversationRepository.save(
            Conversation(
                id = "conversation-idem-cross-user-${UuidV7Generator.generate()}",
                hotelId = login.hotelId,
                userId = "other-user"
            )
        )
        val crossHotel = conversationRepository.save(
            Conversation(
                id = "conversation-idem-cross-hotel-${UuidV7Generator.generate()}",
                hotelId = UuidV7Generator.generate().toString(),
                userId = login.userId
            )
        )
        val key = "attachment-key-scope"
        val body = registrationBody("IMAGE", "scoped.jpg", "image/jpeg", 100, widthPx = 100, heightPx = 100)

        val first = post(
            path = "/api/v1/assistant/conversations/$conversationId/attachments",
            body = body,
            bearerToken = login.accessToken,
            headers = mapOf("Idempotency-Key" to key)
        )
        val sameUserOtherConversation = post(
            path = "/api/v1/assistant/conversations/$otherConversationId/attachments",
            body = body,
            bearerToken = login.accessToken,
            headers = mapOf("Idempotency-Key" to key)
        )

        assertEquals(200, first.statusCode(), first.body())
        assertEquals(200, sameUserOtherConversation.statusCode(), sameUserOtherConversation.body())
        assertThat(extractAttachmentId(first.body())).isNotEqualTo(extractAttachmentId(sameUserOtherConversation.body()))

        listOf(crossUser.id, crossHotel.id).forEach { inaccessibleConversationId ->
            val inaccessible = post(
                path = "/api/v1/assistant/conversations/$inaccessibleConversationId/attachments",
                body = body,
                bearerToken = login.accessToken,
                headers = mapOf("Idempotency-Key" to key)
            )
            assertEquals(404, inaccessible.statusCode(), inaccessible.body())
        }
    }

    @Test
    fun `attachment registration accepts supported durable metadata types`() {
        val login = login()
        listOf(
            registrationBody("IMAGE", "photo.webp", "image/webp", 100, widthPx = 100, heightPx = 100),
            registrationBody("PDF", "report.pdf", "application/pdf", 100, widthPx = null, heightPx = null),
            registrationBody("DOCUMENT", "notes.txt", "text/plain", 100, widthPx = null, heightPx = null)
        ).forEach { body ->
            val response = post(
                path = "/api/v1/assistant/conversations/${startConversation(login)}/attachments",
                body = body,
                bearerToken = login.accessToken
            )

            assertEquals(200, response.statusCode(), response.body())
            assertContains(response.body(), """"storageStatus":"REGISTERED"""")
            assertContains(response.body(), """"storageReference":null""")
        }
    }

    @Test
    fun `attachment registration rejects client ownership storage binary and media fields`() {
        val login = login()
        val forbiddenFragments = listOf(
            """"id":"client-id"""",
            """"hotelId":"client-hotel"""",
            """"userId":"client-user"""",
            """"ownerId":"client-owner"""",
            """"storageReference":"blob://fake"""",
            """"storageStatus":"STORED"""",
            """"imageBase64":"abc"""",
            """"imageBytes":"abc"""",
            """"base64":"abc"""",
            """"binary":"abc"""",
            """"rawBinary":"abc"""",
            """"rawBytes":"abc"""",
            """"localReference":"file://photo.jpg"""",
            """"localUri":"file://photo.jpg"""",
            """"fileUri":"file://photo.jpg"""",
            """"deviceUri":"ph://photo"""",
            """"providerUrl":"https://example.invalid/photo.jpg"""",
            """"mediaUrl":"https://example.invalid/photo.jpg"""",
            """"imageUrl":"https://example.invalid/photo.jpg"""",
            """"fileUrl":"https://example.invalid/photo.jpg""""
        )

        forbiddenFragments.forEach { fragment ->
            val response = post(
                path = "/api/v1/assistant/conversations/${startConversation(login)}/attachments",
                body = registrationBodyWithExtra(fragment),
                bearerToken = login.accessToken
            )

            assertEquals(400, response.statusCode(), "fragment $fragment should be rejected: ${response.body()}")
            assertContains(response.body(), """"title":"Invalid assistant request"""")
        }
    }

    @Test
    fun `attachment registration validates declared metadata`() {
        val login = login()
        val invalidBodies = listOf(
            registrationBody("IMAGE", "", "image/jpeg", 100, widthPx = 100, heightPx = 100),
            registrationBody("IMAGE", "a".repeat(181), "image/jpeg", 100, widthPx = 100, heightPx = 100),
            registrationBody("IMAGE", "photo.jpg", "application/octet-stream", 100, widthPx = 100, heightPx = 100),
            registrationBody("IMAGE", "photo.jpg", "application/pdf", 100, widthPx = 100, heightPx = 100),
            registrationBody("PDF", "report.pdf", "image/jpeg", 100, widthPx = null, heightPx = null),
            registrationBody("DOCUMENT", "notes.txt", "application/pdf", 100, widthPx = null, heightPx = null),
            registrationBody("IMAGE", "photo.jpg", "image/jpeg", 0, widthPx = 100, heightPx = 100),
            registrationBody("IMAGE", "photo.jpg", "image/jpeg", 10_000_001, widthPx = 100, heightPx = 100),
            registrationBody("IMAGE", "photo.jpg", "image/jpeg", 100, widthPx = 0, heightPx = 100),
            registrationBody("IMAGE", "photo.jpg", "image/jpeg", 100, widthPx = 100, heightPx = 10_001),
            registrationBody("PDF", "report.pdf", "application/pdf", 100, widthPx = 10, heightPx = null)
        )

        invalidBodies.forEach { body ->
            val response = post(
                path = "/api/v1/assistant/conversations/${startConversation(login)}/attachments",
                body = body,
                bearerToken = login.accessToken
            )

            assertEquals(400, response.statusCode(), response.body())
            assertContains(response.body(), """"title":"Invalid assistant request"""")
        }
    }

    @Test
    fun `attachment registration is scoped to authenticated conversation owner`() {
        val owner = login()
        val crossUser = conversationRepository.save(
            Conversation(
                id = "conversation-cross-user-${UuidV7Generator.generate()}",
                hotelId = owner.hotelId,
                userId = "other-user"
            )
        )
        val crossHotel = conversationRepository.save(
            Conversation(
                id = "conversation-cross-hotel-${UuidV7Generator.generate()}",
                hotelId = UuidV7Generator.generate().toString(),
                userId = owner.userId
            )
        )

        listOf(crossUser.id, crossHotel.id).forEach { conversationId ->
            val response = post(
                path = "/api/v1/assistant/conversations/$conversationId/attachments",
                body = registrationBody("IMAGE", "photo.jpg", "image/jpeg", 100, widthPx = 100, heightPx = 100),
                bearerToken = owner.accessToken
            )

            assertEquals(404, response.statusCode(), response.body())
        }
    }

    @Test
    fun `registered attachment references are limited to their owning conversation`() {
        val login = login()
        val sourceConversationId = startConversation(login)
        val otherConversationId = startConversation(login)
        val registrationResponse = post(
            path = "/api/v1/assistant/conversations/$sourceConversationId/attachments",
            body = registrationBody("IMAGE", "room.jpg", "image/jpeg", 100, widthPx = 100, heightPx = 100),
            bearerToken = login.accessToken
        )
        assertEquals(200, registrationResponse.statusCode(), registrationResponse.body())
        val attachmentId = extractAttachmentId(registrationResponse.body())

        val accepted = post(
            path = "/api/v1/assistant/conversations/$sourceConversationId/messages",
            body = """{"text":"","inputType":"IMAGE","attachmentIds":["$attachmentId"]}""",
            bearerToken = login.accessToken
        )
        assertEquals(200, accepted.statusCode(), accepted.body())
        assertContains(accepted.body(), """"id":"$attachmentId"""")
        assertContains(accepted.body(), """"storageStatus":"REGISTERED"""")
        assertContains(accepted.body(), """"storageReference":null""")

        val rejected = post(
            path = "/api/v1/assistant/conversations/$otherConversationId/messages",
            body = """{"text":"","inputType":"IMAGE","attachmentIds":["$attachmentId"]}""",
            bearerToken = login.accessToken
        )
        assertEquals(400, rejected.statusCode(), rejected.body())

        val crossUser = conversationRepository.save(
            Conversation(
                id = "conversation-attachment-cross-user-${UuidV7Generator.generate()}",
                hotelId = login.hotelId,
                userId = "other-user"
            )
        )
        val crossHotel = conversationRepository.save(
            Conversation(
                id = "conversation-attachment-cross-hotel-${UuidV7Generator.generate()}",
                hotelId = UuidV7Generator.generate().toString(),
                userId = login.userId
            )
        )

        listOf(crossUser.id, crossHotel.id).forEach { inaccessibleConversationId ->
            val inaccessible = post(
                path = "/api/v1/assistant/conversations/$inaccessibleConversationId/messages",
                body = """{"text":"","inputType":"IMAGE","attachmentIds":["$attachmentId"]}""",
                bearerToken = login.accessToken
            )
            assertEquals(404, inaccessible.statusCode(), inaccessible.body())
        }
    }

    @Test
    fun `mobile shaped message request preserves registered attachment response and persistence`() {
        val login = login()
        val conversationId = startConversation(login)
        val registrationResponse = post(
            path = "/api/v1/assistant/conversations/$conversationId/attachments",
            body = registrationBody("IMAGE", "registered-sink.jpg", "image/jpeg", 100, widthPx = 100, heightPx = 100),
            bearerToken = login.accessToken
        )
        assertEquals(200, registrationResponse.statusCode(), registrationResponse.body())
        val attachmentId = extractAttachmentId(registrationResponse.body())

        val sent = post(
            path = "/api/v1/assistant/conversations/$conversationId/messages",
            body = """
                {
                  "text":"Room 101 sink is leaking",
                  "inputType":"MIXED",
                  "attachments":[],
                  "attachmentIds":["$attachmentId"]
                }
            """.trimIndent(),
            bearerToken = login.accessToken
        )

        assertEquals(200, sent.statusCode(), sent.body())
        assertContains(sent.body(), """"id":"$attachmentId"""")
        assertContains(sent.body(), """"originalFileName":"registered-sink.jpg"""")
        assertContains(sent.body(), """"storageStatus":"REGISTERED"""")
        assertContains(sent.body(), """"storageReference":null""")

        val persistedJson = jdbcTemplate.queryForObject(
            "select messages_json::text from assistant_conversation where id = ?",
            String::class.java,
            conversationId
        ) ?: ""
        assertContains(persistedJson, attachmentId)
        assertContains(persistedJson, "registered-sink.jpg")
        assertContains(persistedJson, "REGISTERED")

        val reloaded = conversationRepository.findByIdAndHotelIdAndUserId(
            conversationId,
            login.hotelId,
            login.userId
        )
        assertEquals(attachmentId, reloaded?.messages?.last()?.attachments?.single()?.id)
        assertEquals(com.hotelopai.assistant.domain.AttachmentStorageStatus.REGISTERED, reloaded?.messages?.last()?.attachments?.single()?.storageStatus)
    }

    @Test
    fun `legacy local metadata image observations remain supported`() {
        val login = login()
        val conversationId = startConversation(login)

        val response = post(
            path = "/api/v1/assistant/conversations/$conversationId/messages",
            body = """
                {
                  "text":"",
                  "inputType":"MIXED",
                  "attachments":[{
                    "id":"legacy-image",
                    "type":"IMAGE",
                    "originalFileName":"legacy.jpg",
                    "mimeType":"image/jpeg",
                    "sizeBytes":100,
                    "widthPx":100,
                    "heightPx":100,
                    "localReference":"local://legacy-image",
                    "storageStatus":"LOCAL_METADATA_ONLY"
                  }],
                  "imageObservations":[{
                    "id":"note-1",
                    "attachmentId":"legacy-image",
                    "text":"User says the sink is leaking",
                    "source":"USER_PROVIDED"
                  }]
                }
            """.trimIndent(),
            bearerToken = login.accessToken
        )

        assertEquals(200, response.statusCode(), response.body())
        assertContains(response.body(), """"storageStatus":"LOCAL_METADATA_ONLY"""")
        assertContains(response.body(), """"source":"USER_PROVIDED"""")
    }

    @Test
    fun `assistant validates attachment metadata`() {
        val login = login()
        val invalidBodies = listOf(
            attachmentMessageBody("", "IMAGE", "photo.jpg", "image/jpeg", 100),
            attachmentMessageBody("   ", "IMAGE", "photo.jpg", "image/jpeg", 100),
            duplicateAttachmentMessageBody(),
            attachmentMessageBody("att", "IMAGE", "", "image/jpeg", 100),
            attachmentMessageBody("att", "IMAGE", "a".repeat(181), "image/jpeg", 100),
            attachmentMessageBody("att", "IMAGE", "photo.jpg", "application/octet-stream", 100),
            attachmentMessageBody("att", "IMAGE", "photo.jpg", "application/pdf", 100),
            attachmentMessageBody("att", "PDF", "report.pdf", "image/jpeg", 100),
            attachmentMessageBody("att", "DOCUMENT", "notes.txt", "application/pdf", 100),
            attachmentMessageBody("att", "IMAGE", "photo.jpg", "image/jpeg", 0),
            attachmentMessageBody("att", "IMAGE", "photo.jpg", "image/jpeg", -1),
            attachmentMessageBody("att", "IMAGE", "photo.jpg", "image/jpeg", 10_000_001),
            attachmentMessageBody("att", "IMAGE", "photo.jpg", "image/jpeg", 100, widthPx = 0),
            attachmentMessageBody("att", "IMAGE", "photo.jpg", "image/jpeg", 100, widthPx = 10_001),
            attachmentMessageBody("att", "IMAGE", "photo.jpg", "image/jpeg", 100, heightPx = 0),
            attachmentMessageBody("att", "IMAGE", "photo.jpg", "image/jpeg", 100, heightPx = 10_001),
            attachmentMessageBody("att", "PDF", "report.pdf", "application/pdf", 100, widthPx = 10),
            fourAttachmentMessageBody(),
            ownershipAttachmentMessageBody()
        )

        invalidBodies.forEach { body ->
            val response = post(
                path = "/api/v1/assistant/conversations/${startConversation(login)}/messages",
                body = body,
                bearerToken = login.accessToken
            )
            assertEquals(400, response.statusCode(), response.body())
            assertContains(response.body(), """"title":"Invalid assistant request"""")
        }
    }

    @Test
    fun `attachment only messages stay in assistant validation flow without creating tasks`() {
        val login = login()
        val conversationId = startConversation(login)

        val response = post(
            path = "/api/v1/assistant/conversations/$conversationId/messages",
            body = attachmentMessageBody("att-only", "IMAGE", "unknown-room.jpg", "image/jpeg", 1234),
            bearerToken = login.accessToken
        )

        assertEquals(200, response.statusCode())
        assertContains(response.body(), """"state":"WAITING_FOR_USER_ANSWER"""")
        assertContains(response.body(), """"createdTaskId":null""")
    }

    @Test
    fun `blank text without attachments remains invalid`() {
        val login = login()

        val response = post(
            path = "/api/v1/assistant/conversations/${startConversation(login)}/messages",
            body = """{"text":"   ","inputType":"TEXT","attachments":[]}""",
            bearerToken = login.accessToken
        )

        assertEquals(400, response.statusCode())
    }

    @Test
    fun `assistant asks follow-up when required fields are missing`() {
        val login = login()

        val conversationId = extractConversationId(
            post(
                path = "/api/v1/assistant/conversations",
                body = """{"hotelId":"ignored","userId":"ignored"}""",
                bearerToken = login.accessToken
            ).body()
        )

        val messageResponse = post(
            path = "/api/v1/assistant/conversations/$conversationId/messages",
            body = """{"text":"Guest needs towels","inputType":"TEXT"}""",
            bearerToken = login.accessToken
        )

        assertEquals(200, messageResponse.statusCode())
        assertContains(messageResponse.body(), """"state":"WAITING_FOR_USER_ANSWER"""")
        assertContains(messageResponse.body(), """"key":"roomNumber"""")
        assertContains(messageResponse.body(), """"prompt":"Which room is this request for?"""")
    }

    private fun post(
        path: String,
        body: String,
        bearerToken: String? = null,
        headers: Map<String, String> = emptyMap()
    ): HttpResponse<String> {
        val requestBuilder = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$port$path"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
        if (bearerToken != null) {
            requestBuilder.header("Authorization", "Bearer $bearerToken")
        }
        headers.forEach { (name, value) -> requestBuilder.header(name, value) }

        return httpClient.send(
            requestBuilder.build(),
            HttpResponse.BodyHandlers.ofString()
        )
    }

    private fun get(path: String, bearerToken: String? = null): HttpResponse<String> {
        val requestBuilder = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$port$path"))
            .GET()
        if (bearerToken != null) {
            requestBuilder.header("Authorization", "Bearer $bearerToken")
        }

        return httpClient.send(
            requestBuilder.build(),
            HttpResponse.BodyHandlers.ofString()
        )
    }

    private fun loginAccessToken(): String {
        return login().accessToken
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
        return loginAs(
            hotelCode = "hotel-opai-demo",
            email = "admin@hotelopai.local",
            password = "admin123"
        )
    }

    private fun loginAs(hotelCode: String, email: String, password: String): LoginSnapshot {
        val response = post(
            "/api/v1/auth/login",
            """{
              "hotelCode":"$hotelCode",
              "email":"$email",
              "password":"$password"
            }"""
        )
        assertEquals(200, response.statusCode())
        val accessToken = Regex(""""accessToken":"([^"]+)"""")
            .find(response.body())
            ?.groupValues
            ?.get(1)
            ?: error("accessToken not found in response: ${response.body()}")
        val hotelId = Regex(""""hotelId":"([^"]+)"""")
            .find(response.body())
            ?.groupValues
            ?.get(1)
            ?: error("hotelId not found in response: ${response.body()}")
        val userId = Regex(""""userId":"([^"]+)"""")
            .find(response.body())
            ?.groupValues
            ?.get(1)
            ?: error("userId not found in response: ${response.body()}")
        return LoginSnapshot(accessToken = accessToken, hotelId = hotelId, userId = userId)
    }

    private fun registrationBody(
        type: String,
        filename: String,
        mimeType: String,
        sizeBytes: Long,
        widthPx: Int?,
        heightPx: Int?
    ): String {
        val dimensions = buildString {
            if (widthPx != null) append(""","widthPx":$widthPx""")
            if (heightPx != null) append(""","heightPx":$heightPx""")
        }
        return """
            {
              "type":"$type",
              "originalFileName":"$filename",
              "mimeType":"$mimeType",
              "sizeBytes":$sizeBytes
              $dimensions
            }
        """.trimIndent()
    }

    private fun registrationBodyWithExtra(extraField: String): String =
        """
            {
              "type":"IMAGE",
              "originalFileName":"photo.jpg",
              "mimeType":"image/jpeg",
              "sizeBytes":100,
              "widthPx":100,
              "heightPx":100,
              $extraField
            }
        """.trimIndent()

    private fun attachmentMessageBody(
        id: String,
        type: String,
        filename: String,
        mimeType: String,
        sizeBytes: Long,
        widthPx: Int? = 100,
        heightPx: Int? = 100
    ): String {
        val dimensions = if (widthPx != null || heightPx != null) {
            ""","widthPx":${widthPx ?: 100},"heightPx":${heightPx ?: 100}"""
        } else {
            ""
        }
        return """
            {
              "text":"",
              "inputType":"MIXED",
              "attachments":[{
                "id":"$id",
                "type":"$type",
                "originalFileName":"$filename",
                "mimeType":"$mimeType",
                "sizeBytes":$sizeBytes,
                "localReference":"local://$id",
                "storageStatus":"LOCAL_METADATA_ONLY"
                $dimensions
              }]
            }
        """.trimIndent()
    }

    private fun duplicateAttachmentMessageBody(): String =
        """
            {
              "text":"",
              "inputType":"MIXED",
              "attachments":[
                {"id":"dup","type":"IMAGE","originalFileName":"a.jpg","mimeType":"image/jpeg","sizeBytes":1,"storageStatus":"LOCAL_METADATA_ONLY"},
                {"id":"dup","type":"IMAGE","originalFileName":"b.jpg","mimeType":"image/jpeg","sizeBytes":1,"storageStatus":"LOCAL_METADATA_ONLY"}
              ]
            }
        """.trimIndent()

    private fun fourAttachmentMessageBody(): String =
        """
            {
              "text":"",
              "inputType":"MIXED",
              "attachments":[
                {"id":"a1","type":"IMAGE","originalFileName":"a.jpg","mimeType":"image/jpeg","sizeBytes":1,"storageStatus":"LOCAL_METADATA_ONLY"},
                {"id":"a2","type":"IMAGE","originalFileName":"b.jpg","mimeType":"image/jpeg","sizeBytes":1,"storageStatus":"LOCAL_METADATA_ONLY"},
                {"id":"a3","type":"IMAGE","originalFileName":"c.jpg","mimeType":"image/jpeg","sizeBytes":1,"storageStatus":"LOCAL_METADATA_ONLY"},
                {"id":"a4","type":"IMAGE","originalFileName":"d.jpg","mimeType":"image/jpeg","sizeBytes":1,"storageStatus":"LOCAL_METADATA_ONLY"}
              ]
            }
        """.trimIndent()

    private fun ownershipAttachmentMessageBody(): String =
        """
            {
              "text":"",
              "inputType":"MIXED",
              "attachments":[{
                "id":"owner",
                "type":"IMAGE",
                "originalFileName":"owner.jpg",
                "mimeType":"image/jpeg",
                "sizeBytes":1,
                "hotelId":"client-hotel",
                "storageStatus":"LOCAL_METADATA_ONLY"
              }]
            }
        """.trimIndent()

    private fun extractConversationId(body: String): String =
        Regex(""""conversationId":"([^"]+)"""")
            .find(body)
            ?.groupValues
            ?.get(1)
            ?: error("conversationId not found in response: $body")

    private fun extractAttachmentId(body: String): String =
        Regex(""""attachmentId":"([^"]+)"""")
            .find(body)
            ?.groupValues
            ?.get(1)
            ?: error("attachmentId not found in response: $body")

    private fun extractAttachmentCreatedAt(body: String): String =
        Regex(""""createdAt":"([^"]+)"""")
            .find(body)
            ?.groupValues
            ?.get(1)
            ?: error("attachment createdAt not found in response: $body")

    private fun extractCreatedTaskId(body: String): String =
        Regex(""""createdTaskId":"([^"]+)"""")
            .find(body)
            ?.groupValues
            ?.get(1)
            ?: error("createdTaskId not found in response: $body")

    private fun assertContains(value: String, expected: String) {
        assertTrue(
            value.contains(expected),
            "Expected response to contain $expected but was $value"
        )
    }

    private fun taskCount(): Int =
        jdbcTemplate.queryForObject("select count(*) from task", Int::class.java) ?: 0

    private data class LoginSnapshot(
        val accessToken: String,
        val hotelId: String,
        val userId: String
    )
}
