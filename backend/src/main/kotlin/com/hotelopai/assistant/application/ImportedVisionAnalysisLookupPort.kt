package com.hotelopai.assistant.application

import java.util.UUID

interface ImportedVisionAnalysisLookupPort {
    fun findCompletedImport(conversationId: String, analysisId: UUID): ImportedVisionAnalysisReference?
}

data class ImportedVisionAnalysisReference(
    val id: UUID,
    val analysisId: UUID,
    val conversationId: String,
    val attachmentId: UUID,
    val hotelId: String,
    val userId: String
)

object NoOpImportedVisionAnalysisLookupPort : ImportedVisionAnalysisLookupPort {
    override fun findCompletedImport(conversationId: String, analysisId: UUID): ImportedVisionAnalysisReference? = null
}
