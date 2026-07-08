package com.hotelopai.unimock.application.pms

import com.fasterxml.jackson.databind.JsonNode
import java.util.UUID

interface PmsDocumentRepository {
    fun replaceDocument(simulationId: UUID, documentPath: String, document: JsonNode)
}
