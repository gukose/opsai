package com.hotelopai.vision.application

import com.hotelopai.vision.domain.VisionAnalysisProviderMode
import com.hotelopai.vision.domain.VisionAnalysisResult
import java.time.Instant
import java.util.UUID

interface VisionAnalysisPort {
    fun analyze(request: VisionAnalysisRequest): VisionAnalysisResult
}

data class VisionAnalysisRequest(
    val analysisId: UUID,
    val attachmentId: UUID,
    val conversationId: String,
    val hotelId: String,
    val userId: String,
    val providerMode: VisionAnalysisProviderMode,
    val idempotencyKey: String,
    val requestedAt: Instant,
    val storageReference: String? = null,
    val fixtureKey: String? = null
) {
    init {
        require(conversationId.isNotBlank()) { "conversationId must not be blank" }
        require(hotelId.isNotBlank()) { "hotelId must not be blank" }
        require(userId.isNotBlank()) { "userId must not be blank" }
        require(idempotencyKey.isNotBlank()) { "idempotencyKey must not be blank" }
        require(storageReference == null || storageReference.isNotBlank()) { "storageReference must not be blank" }
        require(fixtureKey == null || fixtureKey.isNotBlank()) { "fixtureKey must not be blank" }
    }
}
