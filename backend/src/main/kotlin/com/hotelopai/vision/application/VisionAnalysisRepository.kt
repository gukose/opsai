package com.hotelopai.vision.application

import com.hotelopai.vision.domain.VisionAnalysis
import java.util.UUID

interface VisionAnalysisRepository {
    fun save(analysis: VisionAnalysis): VisionAnalysis

    fun findById(id: UUID): VisionAnalysis?

    fun findByIdempotencyScope(
        attachmentId: UUID,
        conversationId: String,
        hotelId: String,
        userId: String,
        idempotencyKey: String
    ): VisionAnalysis?
}
