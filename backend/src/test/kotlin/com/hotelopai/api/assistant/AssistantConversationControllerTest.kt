package com.hotelopai.assistant.api

import com.hotelopai.support.PostgresIntegrationTestSupport
import com.hotelopai.assistant.application.ConversationRepository
import com.hotelopai.assistant.domain.Conversation
import com.hotelopai.shared.kernel.UuidV7Generator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.ActiveProfiles
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AssistantConversationControllerTest : PostgresIntegrationTestSupport() {
    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var conversationRepository: ConversationRepository

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
        bearerToken: String? = null
    ): HttpResponse<String> {
        val requestBuilder = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$port$path"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
        if (bearerToken != null) {
            requestBuilder.header("Authorization", "Bearer $bearerToken")
        }

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
        return LoginSnapshot(accessToken = accessToken, hotelId = hotelId)
    }

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

    private data class LoginSnapshot(
        val accessToken: String,
        val hotelId: String
    )
}
