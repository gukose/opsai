package com.hotelopai.vision.infrastructure.persistence

import com.hotelopai.assistant.application.ImportedVisionAnalysisLookupPort
import com.hotelopai.assistant.application.ImportedVisionAnalysisReference
import com.hotelopai.vision.application.VisionAnalysisImportRepository
import com.hotelopai.vision.domain.VisionAnalysisImportStatus
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class ImportedVisionAnalysisLookupAdapter(
    private val visionAnalysisImportRepository: VisionAnalysisImportRepository
) : ImportedVisionAnalysisLookupPort {
    override fun findCompletedImport(conversationId: String, analysisId: UUID): ImportedVisionAnalysisReference? =
        visionAnalysisImportRepository.findByConversationIdAndAnalysisId(conversationId, analysisId)
            ?.takeIf { it.status == VisionAnalysisImportStatus.COMPLETED }
            ?.let {
                ImportedVisionAnalysisReference(
                    id = it.id,
                    analysisId = it.analysisId,
                    conversationId = it.conversationId,
                    attachmentId = it.attachmentId,
                    hotelId = it.hotelId,
                    userId = it.userId
                )
            }
}
