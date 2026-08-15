package com.hotelopai.knowledge.application

import com.hotelopai.knowledge.domain.KnowledgeCategory
import com.hotelopai.observability.OperationalObservability
import com.hotelopai.shared.kernel.PersistenceInstant
import org.springframework.stereotype.Service
import java.time.Clock
import java.util.Locale

@Service
class KnowledgeContextAssembler(
    private val knowledgeBaseService: KnowledgeBaseService,
    private val properties: KnowledgeProperties,
    private val clock: Clock,
    private val auditSink: KnowledgeOperationsAuditSink = NoOpKnowledgeOperationsAuditSink,
    private val observability: OperationalObservability = OperationalObservability.noop()
) {
    fun assemble(request: KnowledgeContextAssemblyRequest): KnowledgeContextAssemblyResult {
        require(request.query.trim().length in 2..200) { "knowledge context query length must be between 2 and 200" }
        val limits = properties.ragContext
        val categories = if (request.categories.isEmpty()) limits.allowedCategories else request.categories
        val maxChunks = (request.limit ?: limits.maximumRetrievedChunks).coerceIn(1, limits.maximumRetrievedChunks)
        val candidateLimit = (maxChunks * 4).coerceAtMost(100)
        val pages = if (categories.isEmpty()) {
            listOf(search(request, null, candidateLimit))
        } else {
            categories.sortedBy { it.name }.map { search(request, it, candidateLimit) }
        }
        val selected = mutableListOf<KnowledgeContextItem>()
        val perDocument = mutableMapOf<java.util.UUID, Int>()
        val exactTextSeen = linkedSetOf<String>()
        var duplicates = 0
        var totalChars = 0
        val allowedLanguages = limits.languagePolicy.map { it.lowercase(Locale.ROOT) }.toSet()
        val requestedLanguage = request.language.lowercase(Locale.ROOT)
        pages.flatMap { it.content }
            .filter { it.language.lowercase(Locale.ROOT) == requestedLanguage && (allowedLanguages.isEmpty() || requestedLanguage in allowedLanguages) }
            .filter { scoreOf(it) >= limits.minimumRetrievalScore }
            .sortedWith(compareByDescending<KnowledgeSearchResult> { scoreOf(it) }.thenBy { it.title }.thenBy { it.chunkOrder }.thenBy { it.chunkId.value })
            .forEach { result ->
                if (selected.size >= maxChunks) return@forEach
                val normalizedText = result.snippet.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")
                if (!exactTextSeen.add(normalizedText)) {
                    duplicates += 1
                    return@forEach
                }
                val documentCount = perDocument[result.documentId.value] ?: 0
                if (documentCount >= limits.maximumChunksPerDocument) return@forEach
                val text = result.snippet.take(limits.maximumCharactersPerChunk)
                if (totalChars + text.length > limits.maximumTotalContextCharacters) return@forEach
                perDocument[result.documentId.value] = documentCount + 1
                totalChars += text.length
                selected += result.toContextItem(request.mode, text)
            }
        observability.incrementCounter("knowledge_context_assembly_total", "retrieval_mode" to request.mode.name.lowercase(), "outcome" to "assembled", "readiness_state" to "not_evaluated")
        observability.incrementCounter("knowledge_context_selected_chunks_total", selected.size.toDouble(), "retrieval_mode" to request.mode.name.lowercase(), "outcome" to sizeBand(selected.size))
        observability.incrementCounter("knowledge_context_deduplicated_chunks_total", duplicates.toDouble(), "retrieval_mode" to request.mode.name.lowercase(), "outcome" to "deduplicated")
        auditSink.record(KnowledgeOperationsAuditEvent("rag_context_assembled", "assembled", PersistenceInstant.now(clock), request.mode.name.lowercase()))
        return KnowledgeContextAssemblyResult(
            mode = request.mode,
            itemCount = selected.size,
            totalCharacters = totalChars,
            duplicateCount = duplicates,
            items = selected
        )
    }

    private fun search(request: KnowledgeContextAssemblyRequest, category: KnowledgeCategory?, limit: Int): KnowledgeSearchPage =
        knowledgeBaseService.search(
            KnowledgeSearchQuery(
                query = request.query,
                hotelId = request.hotelId,
                category = category,
                mode = request.mode,
                page = 0,
                size = limit
            )
        )

    private fun KnowledgeSearchResult.toContextItem(mode: KnowledgeSearchMode, selectedText: String): KnowledgeContextItem {
        val score = score ?: KnowledgeSearchScore(rank.toDouble(), 0.0, rank.toDouble())
        return KnowledgeContextItem(
            citation = KnowledgeSourceCitation(
                documentReference = documentId.value,
                chunkReference = chunkId.value,
                title = title,
                category = category,
                chunkPosition = chunkOrder,
                retrievalScore = score.combinedScore,
                contentFingerprint = KnowledgeEmbeddingService.fingerprint(selectedText)
            ),
            selectedText = selectedText,
            retrievalMode = mode,
            score = KnowledgeContextScore(score.keywordScore, score.semanticScore, score.combinedScore, score.fallbackUsed)
        )
    }

    private fun scoreOf(result: KnowledgeSearchResult): Double =
        result.score?.combinedScore ?: result.rank.toDouble()

    private fun sizeBand(value: Int): String =
        when {
            value == 0 -> "zero"
            value <= 3 -> "one_three"
            value <= 8 -> "four_eight"
            else -> "over_eight"
        }
}
