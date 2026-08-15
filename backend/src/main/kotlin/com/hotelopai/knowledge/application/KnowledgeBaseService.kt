package com.hotelopai.knowledge.application

import com.hotelopai.knowledge.domain.KnowledgeDocument
import com.hotelopai.knowledge.domain.KnowledgeDocumentId
import com.hotelopai.knowledge.domain.KnowledgeMetadata
import com.hotelopai.observability.OperationalObservability
import com.hotelopai.shared.kernel.PersistenceInstant
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.UUID

@Service
@EnableConfigurationProperties(KnowledgeProperties::class)
class KnowledgeBaseService(
    private val documentRepository: KnowledgeDocumentRepository,
    private val chunkRepository: KnowledgeChunkRepository,
    private val metadataRepository: KnowledgeMetadataRepository,
    private val embeddingService: KnowledgeEmbeddingService,
    private val properties: KnowledgeProperties,
    private val clock: Clock,
    private val observability: OperationalObservability = OperationalObservability.noop()
) {
    private val chunker = DeterministicKnowledgeChunker(properties)

    @Transactional
    fun importDocument(command: KnowledgeImportCommand): KnowledgeDocument {
        val now = PersistenceInstant.now(clock)
        require(command.content.length <= properties.maxImportCharacters) { "knowledge import content is too large" }
        val metadata = command.metadata.normalized()
        val document = KnowledgeDocument(
            hotelId = command.hotelId,
            title = command.title.trim(),
            category = command.category,
            source = command.source,
            language = command.language.trim().lowercase(),
            originalContent = command.content,
            metadata = metadata,
            createdAt = now,
            updatedAt = now
        )
        val saved = documentRepository.save(document)
        metadataRepository.save(saved.id, metadata, now)
        val chunks = chunkRepository.replace(saved.id, chunker.chunk(command.content, command.contentType).map { it.toChunk(saved.id, now) })
        observability.incrementCounter("knowledge_imports_total", "source" to command.source.name.lowercase(), "category" to command.category.name.lowercase(), "outcome" to "imported")
        observability.incrementCounter("knowledge_chunks_total", chunks.size.toDouble(), "source" to command.source.name.lowercase(), "category" to command.category.name.lowercase(), "outcome" to "generated")
        return saved.copy(chunks = chunks, metadata = metadata)
    }

    fun documents(filter: KnowledgeDocumentFilter): KnowledgeDocumentPage =
        documentRepository.find(filter.copy(page = filter.page.coerceAtLeast(0), size = filter.size.coerceIn(1, 100)))

    fun detail(id: KnowledgeDocumentId): KnowledgeDocument =
        documentRepository.find(id) ?: throw KnowledgeDocumentNotFoundException(id)

    fun detail(id: KnowledgeDocumentId, hotelId: UUID): KnowledgeDocument =
        documentRepository.find(id, hotelId) ?: throw KnowledgeDocumentNotFoundException(id)

    @Transactional
    fun delete(id: KnowledgeDocumentId): Boolean {
        val deleted = documentRepository.delete(id)
        observability.incrementCounter("knowledge_deletions_total", "outcome" to if (deleted) "deleted" else "not_found")
        return deleted
    }

    @Transactional
    fun delete(id: KnowledgeDocumentId, hotelId: UUID): Boolean {
        val deleted = documentRepository.delete(id, hotelId)
        observability.incrementCounter("knowledge_deletions_total", "outcome" to if (deleted) "deleted" else "not_found")
        return deleted
    }

    @Transactional
    fun rechunk(id: KnowledgeDocumentId): KnowledgeDocument {
        val current = detail(id)
        val now = PersistenceInstant.now(clock)
        val chunks = chunkRepository.replace(id, chunker.chunk(current.originalContent, inferredContentType(current.source)).map { it.toChunk(id, now) })
        embeddingService.markDocumentStale(id)
        observability.incrementCounter("knowledge_chunks_total", chunks.size.toDouble(), "source" to current.source.name.lowercase(), "category" to current.category.name.lowercase(), "outcome" to "regenerated")
        return current.copy(chunks = chunks, updatedAt = now)
    }

    @Transactional
    fun rechunk(id: KnowledgeDocumentId, hotelId: UUID): KnowledgeDocument {
        detail(id, hotelId)
        return rechunk(id)
    }

    fun search(query: KnowledgeSearchQuery): KnowledgeSearchPage {
        val normalized = query.copy(page = query.page.coerceAtLeast(0), size = query.size.coerceIn(1, 100))
        require(normalized.query.trim().length in 2..200) { "knowledge search query length must be between 2 and 200" }
        val result = when (normalized.mode) {
            KnowledgeSearchMode.KEYWORD -> chunkRepository.search(normalized)
            KnowledgeSearchMode.SEMANTIC -> embeddingService.semanticSearch(normalized, strict = true)
            KnowledgeSearchMode.HYBRID -> embeddingService.hybridSearch(normalized)
        }
        observability.incrementCounter("knowledge_searches_total", "search_mode" to normalized.mode.name.lowercase(), "outcome" to "searched", "category" to (normalized.category?.name?.lowercase() ?: "all"))
        return result
    }

    private fun inferredContentType(source: com.hotelopai.knowledge.domain.KnowledgeSource): KnowledgeImportContentType =
        if (source == com.hotelopai.knowledge.domain.KnowledgeSource.IMPORTED_MARKDOWN) {
            KnowledgeImportContentType.MARKDOWN
        } else {
            KnowledgeImportContentType.PLAIN_TEXT
        }
}
