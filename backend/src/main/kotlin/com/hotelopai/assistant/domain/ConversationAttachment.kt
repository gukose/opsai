package com.hotelopai.assistant.domain

data class ConversationAttachment(
    val id: String,
    val type: AttachmentType = AttachmentType.IMAGE,
    val originalFileName: String? = null,
    val mimeType: String? = null,
    val sizeBytes: Long? = null,
    val widthPx: Int? = null,
    val heightPx: Int? = null,
    val localReference: String? = null,
    val storageStatus: AttachmentStorageStatus = AttachmentStorageStatus.LOCAL_METADATA_ONLY
)

enum class AttachmentType {
    IMAGE,
    PDF,
    DOCUMENT
}

enum class AttachmentStorageStatus {
    LOCAL_METADATA_ONLY
}

data class ImageObservation(
    val id: String = "",
    val attachmentId: String? = null,
    val text: String = "",
    val source: ImageObservationSource = ImageObservationSource.USER_PROVIDED,
    val description: String? = null,
    val confidence: Double? = null
) {
    val semanticText: String
        get() = text.ifBlank { description.orEmpty() }
}

enum class ImageObservationSource {
    USER_PROVIDED
}
