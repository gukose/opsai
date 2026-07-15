package com.hotelopai.vision.domain

import java.time.Instant
import java.util.UUID

data class VisionAnalysisImport(
    val id: UUID,
    val analysisId: UUID,
    val conversationId: String,
    val attachmentId: UUID,
    val hotelId: String,
    val userId: String,
    val messageId: String? = null,
    val status: VisionAnalysisImportStatus = VisionAnalysisImportStatus.PENDING,
    val failureCode: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant = createdAt
) {
    fun complete(messageId: String, now: Instant = Instant.now()): VisionAnalysisImport {
        require(status == VisionAnalysisImportStatus.PENDING) { "Only PENDING imports can complete" }
        require(messageId.isNotBlank()) { "messageId must not be blank" }
        return copy(
            messageId = messageId,
            status = VisionAnalysisImportStatus.COMPLETED,
            failureCode = null,
            updatedAt = now
        )
    }

    fun fail(code: String, now: Instant = Instant.now()): VisionAnalysisImport {
        require(status == VisionAnalysisImportStatus.PENDING) { "Only PENDING imports can fail" }
        require(code.isNotBlank()) { "failureCode must not be blank" }
        return copy(
            status = VisionAnalysisImportStatus.FAILED,
            failureCode = code,
            updatedAt = now
        )
    }
}

enum class VisionAnalysisImportStatus {
    PENDING,
    COMPLETED,
    FAILED
}
