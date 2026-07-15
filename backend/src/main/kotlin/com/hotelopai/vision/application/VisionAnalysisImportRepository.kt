package com.hotelopai.vision.application

import com.hotelopai.vision.domain.VisionAnalysisImport
import java.util.UUID

interface VisionAnalysisImportRepository {
    fun save(record: VisionAnalysisImport): VisionAnalysisImport

    fun findByConversationIdAndAnalysisId(conversationId: String, analysisId: UUID): VisionAnalysisImport?
}
