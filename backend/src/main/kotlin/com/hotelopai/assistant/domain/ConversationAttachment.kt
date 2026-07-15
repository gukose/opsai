package com.hotelopai.assistant.domain

import java.time.Instant
import java.util.UUID

data class ConversationAttachment(
    val id: String,
    val type: AttachmentType = AttachmentType.IMAGE,
    val originalFileName: String? = null,
    val mimeType: String? = null,
    val sizeBytes: Long? = null,
    val widthPx: Int? = null,
    val heightPx: Int? = null,
    val localReference: String? = null,
    val storageStatus: AttachmentStorageStatus = AttachmentStorageStatus.LOCAL_METADATA_ONLY,
    val storageReference: String? = null
)

data class RegisteredConversationAttachment(
    val id: UUID,
    val conversationId: String,
    val hotelId: String,
    val userId: String,
    val type: AttachmentType,
    val originalFileName: String,
    val declaredMimeType: String,
    val declaredSizeBytes: Long,
    val widthPx: Int? = null,
    val heightPx: Int? = null,
    val storageStatus: AttachmentStorageStatus = AttachmentStorageStatus.REGISTERED,
    val storageReference: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant = createdAt
) {
    fun toMessageAttachment(): ConversationAttachment =
        ConversationAttachment(
            id = id.toString(),
            type = type,
            originalFileName = originalFileName,
            mimeType = declaredMimeType,
            sizeBytes = declaredSizeBytes,
            widthPx = widthPx,
            heightPx = heightPx,
            storageStatus = storageStatus,
            storageReference = storageReference
        )
}

enum class AttachmentType {
    IMAGE,
    PDF,
    DOCUMENT
}

enum class AttachmentStorageStatus {
    LOCAL_METADATA_ONLY,
    REGISTERED
}

data class ImageObservation(
    val id: String = "",
    val attachmentId: String? = null,
    val analysisId: String? = null,
    val text: String = "",
    val source: ImageObservationSource = ImageObservationSource.USER_PROVIDED,
    val confidence: Double? = null,
    val providerId: String? = null,
    val advisory: Boolean = false,
    val order: Int? = null,
    val description: String? = null,
) {
    val semanticText: String
        get() = text.ifBlank { description.orEmpty() }
}

enum class ImageObservationSource {
    USER_PROVIDED,
    VISION_ANALYSIS
}
