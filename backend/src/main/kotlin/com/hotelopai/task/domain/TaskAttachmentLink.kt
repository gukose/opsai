package com.hotelopai.task.domain

import java.time.Instant
import java.util.UUID

data class TaskAttachmentLink(
    val id: UUID,
    val taskId: UUID,
    val attachmentId: UUID,
    val conversationId: String,
    val hotelId: String,
    val userId: String,
    val sourceType: TaskAttachmentSourceType,
    val analysisId: UUID? = null,
    val analysisImportId: UUID? = null,
    val createdAt: Instant
) {
    init {
        require(conversationId.isNotBlank()) { "conversationId must not be blank" }
        require(hotelId.isNotBlank()) { "hotelId must not be blank" }
        require(userId.isNotBlank()) { "userId must not be blank" }
        if (sourceType == TaskAttachmentSourceType.ASSISTANT_MESSAGE) {
            require(analysisId == null && analysisImportId == null) {
                "assistant message links must not include vision provenance"
            }
        }
        if (sourceType == TaskAttachmentSourceType.VISION_ANALYSIS) {
            require(analysisId != null) { "vision analysis links require analysisId" }
        }
    }
}

enum class TaskAttachmentSourceType {
    ASSISTANT_MESSAGE,
    VISION_ANALYSIS
}

data class TaskAttachmentLinkView(
    val attachmentId: UUID,
    val conversationId: String,
    val type: String,
    val originalFileName: String,
    val declaredMimeType: String,
    val declaredSizeBytes: Long,
    val widthPx: Int?,
    val heightPx: Int?,
    val storageStatus: String,
    val sourceType: TaskAttachmentSourceType,
    val analysisId: UUID?,
    val analysisImportId: UUID?,
    val createdAt: Instant
)
