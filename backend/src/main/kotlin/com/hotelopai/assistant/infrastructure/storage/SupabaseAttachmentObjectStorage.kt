package com.hotelopai.assistant.infrastructure.storage

import com.hotelopai.assistant.application.AttachmentObjectStorage
import com.hotelopai.assistant.application.StoredObject
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.MediaType
import org.springframework.web.client.RestClient
import java.io.InputStream

@ConfigurationProperties("ops.ai.attachment-storage")
data class AttachmentStorageProperties(
    val enabled: Boolean = false,
    val supabaseUrl: String = "",
    val serviceRoleKey: String = "",
    val bucket: String = "task-attachments"
)

@Configuration
class AttachmentStorageConfiguration {
    @Bean fun attachmentObjectStorage(properties: AttachmentStorageProperties): AttachmentObjectStorage =
        SupabaseAttachmentObjectStorage(properties)
}

class SupabaseAttachmentObjectStorage(private val properties: AttachmentStorageProperties) : AttachmentObjectStorage {
    private val client = RestClient.builder().baseUrl(properties.supabaseUrl.trimEnd('/')).build()

    override fun store(reference: String, bytes: ByteArray, contentType: String): StoredObject {
        require(properties.enabled && properties.supabaseUrl.isNotBlank() && properties.serviceRoleKey.isNotBlank()) {
            "Attachment storage is not configured"
        }
        client.post().uri("/storage/v1/object/${properties.bucket}/$reference")
            .header("Authorization", "Bearer ${properties.serviceRoleKey}")
            .header("apikey", properties.serviceRoleKey)
            .contentType(MediaType.parseMediaType(contentType)).body(bytes).retrieve().toBodilessEntity()
        return StoredObject(reference, contentType, bytes.size.toLong())
    }

    override fun read(reference: String): InputStream {
        require(properties.enabled && properties.supabaseUrl.isNotBlank() && properties.serviceRoleKey.isNotBlank()) {
            "Attachment storage is not configured"
        }
        return client.get().uri("/storage/v1/object/${properties.bucket}/$reference")
            .header("Authorization", "Bearer ${properties.serviceRoleKey}")
            .header("apikey", properties.serviceRoleKey).retrieve().body(InputStream::class.java)
            ?: error("Stored attachment is empty")
    }
}
