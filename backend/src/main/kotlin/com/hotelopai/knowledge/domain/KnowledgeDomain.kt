package com.hotelopai.knowledge.domain

import java.time.Instant
import java.util.Locale
import java.util.UUID

@JvmInline
value class KnowledgeDocumentId(val value: UUID)

@JvmInline
value class KnowledgeChunkId(val value: UUID)

enum class KnowledgeSource {
    INTERNAL,
    SOP,
    MAINTENANCE_MANUAL,
    OPERATIONS_GUIDE,
    TRAINING,
    IMPORTED_TEXT,
    IMPORTED_MARKDOWN
}

enum class KnowledgeCategory {
    GENERAL,
    MAINTENANCE,
    HOUSEKEEPING,
    FRONT_DESK,
    SAFETY,
    PMS,
    AI_OPERATIONS
}

data class KnowledgeMetadata(
    val tags: Set<String> = emptySet(),
    val attributes: Map<String, String> = emptyMap()
) {
    init {
        require(tags.size <= 50) { "knowledge metadata tags must be bounded" }
        require(attributes.size <= 50) { "knowledge metadata attributes must be bounded" }
        require(tags.all { normalizeTag(it).isNotBlank() }) { "knowledge metadata tags must not be blank" }
        require(attributes.keys.all { it.isNotBlank() }) { "knowledge metadata attribute keys must not be blank" }
        require(attributes.values.all { it.length <= 500 }) { "knowledge metadata attribute values must be bounded" }
    }

    fun normalized(): KnowledgeMetadata =
        copy(
            tags = tags.map(::normalizeTag).filter { it.isNotBlank() }.toSortedSet(),
            attributes = attributes
                .mapKeys { it.key.trim().lowercase(Locale.ROOT).take(80) }
                .mapValues { it.value.trim().take(500) }
                .toSortedMap()
        )

    companion object {
        fun normalizeTag(value: String): String =
            value.trim().lowercase(Locale.ROOT).replace(Regex("[^a-z0-9_-]+"), "-").trim('-').take(80)
    }
}

data class KnowledgeChunk(
    val id: KnowledgeChunkId = KnowledgeChunkId(UUID.randomUUID()),
    val documentId: KnowledgeDocumentId,
    val order: Int,
    val heading: String?,
    val text: String,
    val metadata: KnowledgeMetadata = KnowledgeMetadata(),
    val createdAt: Instant,
    val updatedAt: Instant,
    val version: Long = 0
) {
    init {
        require(order >= 0) { "knowledge chunk order must be non-negative" }
        require(text.isNotBlank()) { "knowledge chunk text must not be blank" }
        require(text.length <= 20_000) { "knowledge chunk text must be bounded" }
        require(heading == null || heading.isNotBlank()) { "knowledge chunk heading must not be blank" }
    }

    override fun toString(): String =
        "KnowledgeChunk(id=$id, documentId=$documentId, order=$order, headingPresent=${heading != null}, textLength=${text.length})"
}

data class KnowledgeDocument(
    val id: KnowledgeDocumentId = KnowledgeDocumentId(UUID.randomUUID()),
    val hotelId: UUID? = null,
    val title: String,
    val category: KnowledgeCategory,
    val source: KnowledgeSource,
    val language: String,
    val originalContent: String,
    val metadata: KnowledgeMetadata = KnowledgeMetadata(),
    val chunks: List<KnowledgeChunk> = emptyList(),
    val createdAt: Instant,
    val updatedAt: Instant,
    val version: Long = 0
) {
    init {
        require(title.isNotBlank()) { "knowledge document title must not be blank" }
        require(title.length <= 240) { "knowledge document title must be bounded" }
        require(hotelId == null || hotelId != UUID(0L, 0L)) { "knowledge document hotel id must not be empty" }
        require(language.matches(Regex("[a-zA-Z]{2,8}(-[a-zA-Z0-9]{2,8})?"))) { "knowledge document language must be a valid tag" }
        require(originalContent.isNotBlank()) { "knowledge document content must not be blank" }
        require(originalContent.length <= 1_000_000) { "knowledge document content must be bounded" }
        require(chunks.map { it.order }.distinct().size == chunks.size) { "knowledge document chunks must have unique order" }
        require(chunks.all { it.documentId == id }) { "knowledge document chunks must belong to the document" }
    }

    override fun toString(): String =
        "KnowledgeDocument(id=$id, hotelScoped=${hotelId != null}, title=$title, category=$category, source=$source, language=$language, chunkCount=${chunks.size})"
}
