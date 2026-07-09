package com.hotelopai.assistant.domain

data class ConversationAttachment(
    val id: String,
    val originalFileName: String? = null,
    val mimeType: String? = null,
    val sizeBytes: Long? = null,
    val widthPx: Int? = null,
    val heightPx: Int? = null
)

data class ImageObservation(
    val attachmentId: String? = null,
    val description: String,
    val confidence: Double? = null
)
