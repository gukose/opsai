package com.hotelopai.knowledge.application

import com.hotelopai.knowledge.domain.KnowledgeCategory
import com.hotelopai.knowledge.domain.KnowledgeChunk
import com.hotelopai.knowledge.domain.KnowledgeChunkId
import com.hotelopai.knowledge.domain.KnowledgeDocument
import com.hotelopai.knowledge.domain.KnowledgeDocumentId
import com.hotelopai.knowledge.domain.KnowledgeMetadata
import com.hotelopai.knowledge.domain.KnowledgeSource
import java.time.Instant
import java.util.UUID

data class KnowledgeImportCommand(
    val hotelId: UUID? = null,
    val title: String,
    val category: KnowledgeCategory,
    val source: KnowledgeSource,
    val language: String = "en",
    val content: String,
    val contentType: KnowledgeImportContentType,
    val metadata: KnowledgeMetadata = KnowledgeMetadata()
)

enum class KnowledgeImportContentType {
    MARKDOWN,
    PLAIN_TEXT
}

data class KnowledgeDocumentFilter(
    val hotelId: UUID? = null,
    val category: KnowledgeCategory? = null,
    val source: KnowledgeSource? = null,
    val tag: String? = null,
    val page: Int = 0,
    val size: Int = 20
)

data class KnowledgeDocumentPage(
    val content: List<KnowledgeDocument>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int
)

data class KnowledgeSearchQuery(
    val query: String,
    val hotelId: UUID? = null,
    val category: KnowledgeCategory? = null,
    val tag: String? = null,
    val mode: KnowledgeSearchMode = KnowledgeSearchMode.KEYWORD,
    val page: Int = 0,
    val size: Int = 20
)

data class KnowledgeSearchResult(
    val documentId: KnowledgeDocumentId,
    val chunkId: KnowledgeChunkId,
    val title: String,
    val category: KnowledgeCategory,
    val source: KnowledgeSource,
    val language: String,
    val heading: String?,
    val snippet: String,
    val chunkOrder: Int,
    val rank: Int,
    val tags: Set<String>,
    val updatedAt: Instant,
    val score: KnowledgeSearchScore? = null
)

data class KnowledgeSearchPage(
    val content: List<KnowledgeSearchResult>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int
)

data class KnowledgeChunkDraft(
    val order: Int,
    val heading: String?,
    val text: String
) {
    fun toChunk(documentId: KnowledgeDocumentId, now: Instant): KnowledgeChunk =
        KnowledgeChunk(documentId = documentId, order = order, heading = heading, text = text, createdAt = now, updatedAt = now)
}

interface KnowledgeDocumentRepository {
    fun save(document: KnowledgeDocument): KnowledgeDocument
    fun find(id: KnowledgeDocumentId): KnowledgeDocument?
    fun find(id: KnowledgeDocumentId, hotelId: UUID?): KnowledgeDocument? =
        find(id)?.takeIf { hotelId == null || it.hotelId == hotelId }
    fun find(filter: KnowledgeDocumentFilter): KnowledgeDocumentPage
    fun delete(id: KnowledgeDocumentId): Boolean
    fun delete(id: KnowledgeDocumentId, hotelId: UUID?): Boolean =
        if (hotelId == null) {
            delete(id)
        } else if (find(id, hotelId) != null) {
            delete(id)
        } else {
            false
        }
}

interface KnowledgeChunkRepository {
    fun replace(documentId: KnowledgeDocumentId, chunks: List<KnowledgeChunk>): List<KnowledgeChunk>
    fun findByDocument(documentId: KnowledgeDocumentId): List<KnowledgeChunk>
    fun search(query: KnowledgeSearchQuery): KnowledgeSearchPage
}

interface KnowledgeMetadataRepository {
    fun save(documentId: KnowledgeDocumentId, metadata: KnowledgeMetadata, now: Instant)
    fun findMetadata(documentId: KnowledgeDocumentId): KnowledgeMetadata?
}

class KnowledgeDocumentNotFoundException(id: KnowledgeDocumentId) : RuntimeException("Knowledge document was not found: ${id.value}")
