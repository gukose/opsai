package com.hotelopai.knowledge.application

import com.hotelopai.knowledge.domain.KnowledgeCategory
import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration
import java.util.Locale

@ConfigurationProperties("ops.ai.knowledge")
data class KnowledgeProperties(
    val chunkSize: Int = 1_200,
    val chunkOverlap: Int = 120,
    val maxImportCharacters: Int = 1_000_000,
    val semanticSearch: KnowledgeSemanticSearchProperties = KnowledgeSemanticSearchProperties(),
    val retrievalQuality: KnowledgeRetrievalQualityProperties = KnowledgeRetrievalQualityProperties(),
    val ragContext: KnowledgeRagContextProperties = KnowledgeRagContextProperties(),
    val answers: KnowledgeAnswerProperties = KnowledgeAnswerProperties()
) {
    init {
        require(chunkSize in 200..20_000) { "knowledge chunk size must be between 200 and 20000" }
        require(chunkOverlap in 0 until chunkSize) { "knowledge chunk overlap must be non-negative and smaller than chunk size" }
        require(maxImportCharacters in 1_000..1_000_000) { "knowledge max import characters must be between 1000 and 1000000" }
    }
}

data class KnowledgeRetrievalQualityProperties(
    val gatesEnabled: Boolean = false,
    val verificationEnabled: Boolean = false,
    val datasetVersion: String = "hotel-operations-curated-v1",
    val k: Int = 5,
    val modes: Set<KnowledgeSearchMode> = setOf(KnowledgeSearchMode.KEYWORD, KnowledgeSearchMode.SEMANTIC, KnowledgeSearchMode.HYBRID),
    val defaultThresholds: KnowledgeRetrievalQualityThresholdProperties = KnowledgeRetrievalQualityThresholdProperties(),
    val keywordThresholds: KnowledgeRetrievalQualityThresholdProperties = KnowledgeRetrievalQualityThresholdProperties(minimumHitRate = 0.7),
    val semanticThresholds: KnowledgeRetrievalQualityThresholdProperties = KnowledgeRetrievalQualityThresholdProperties(minimumHitRate = 0.5),
    val hybridThresholds: KnowledgeRetrievalQualityThresholdProperties = KnowledgeRetrievalQualityThresholdProperties(minimumHitRate = 0.7)
) {
    init {
        require(datasetVersion.isNotBlank()) { "knowledge retrieval quality dataset version must not be blank" }
        require(k in 1..50) { "knowledge retrieval quality k must be between 1 and 50" }
        require(modes.isNotEmpty()) { "knowledge retrieval quality modes must not be empty" }
    }
}

data class KnowledgeRetrievalQualityThresholdProperties(
    val minimumPrecisionAtK: Double = 0.1,
    val minimumRecallAtK: Double = 0.1,
    val minimumMrr: Double = 0.1,
    val minimumNdcg: Double = 0.1,
    val minimumHitRate: Double = 0.5,
    val maximumAverageLatencyMillis: Long = 5_000,
    val minimumEvaluatedQueryCount: Int = 1
) {
    init {
        require(minimumPrecisionAtK in 0.0..1.0) { "knowledge retrieval quality precision threshold must be between 0 and 1" }
        require(minimumRecallAtK in 0.0..1.0) { "knowledge retrieval quality recall threshold must be between 0 and 1" }
        require(minimumMrr in 0.0..1.0) { "knowledge retrieval quality MRR threshold must be between 0 and 1" }
        require(minimumNdcg in 0.0..1.0) { "knowledge retrieval quality NDCG threshold must be between 0 and 1" }
        require(minimumHitRate in 0.0..1.0) { "knowledge retrieval quality hit-rate threshold must be between 0 and 1" }
        require(maximumAverageLatencyMillis > 0) { "knowledge retrieval quality latency threshold must be positive" }
        require(minimumEvaluatedQueryCount in 1..500) { "knowledge retrieval quality minimum query count must be between 1 and 500" }
    }
}

data class KnowledgeRagContextProperties(
    val maximumRetrievedChunks: Int = 8,
    val maximumChunksPerDocument: Int = 3,
    val maximumCharactersPerChunk: Int = 800,
    val maximumTotalContextCharacters: Int = 4_000,
    val minimumRetrievalScore: Double = 0.0,
    val allowedCategories: Set<KnowledgeCategory> = emptySet(),
    val languagePolicy: Set<String> = setOf("en")
) {
    init {
        require(maximumRetrievedChunks in 1..50) { "knowledge RAG context maximum retrieved chunks must be between 1 and 50" }
        require(maximumChunksPerDocument in 1..20) { "knowledge RAG context per-document chunk limit must be between 1 and 20" }
        require(maximumCharactersPerChunk in 100..5_000) { "knowledge RAG context chunk characters must be between 100 and 5000" }
        require(maximumTotalContextCharacters in 500..50_000) { "knowledge RAG context total characters must be between 500 and 50000" }
        require(minimumRetrievalScore >= 0.0) { "knowledge RAG context minimum score must not be negative" }
        require(languagePolicy.all { it.matches(Regex("[a-zA-Z]{2,8}(-[a-zA-Z0-9]{2,8})?")) }) { "knowledge RAG context language policy must contain valid language tags" }
    }
}

data class KnowledgeAnswerProperties(
    val enabled: Boolean = false,
    val activeProvider: String = "internal-demo",
    val model: String = "internal-demo-knowledge-answer-v1",
    val promptTemplateId: String = "knowledge-answer-v1",
    val promptVersion: String = "knowledge-answer-prompt-v1",
    val contextSchemaVersion: String = "knowledge-context-v1",
    val maxPromptCharacters: Int = 8_000,
    val maxQueryCharacters: Int = 200,
    val maxAnswerCharacters: Int = 1_200,
    val minimumContextItems: Int = 1,
    val duplicateWindow: Duration = Duration.ofMinutes(15),
    val historyRetention: Duration = Duration.ofDays(30),
    val historyCleanupBatchSize: Int = 100,
    val hourlyRequestLimit: Int = 60,
    val dailyRequestLimit: Int = 250,
    val maximumConcurrentRequests: Int = 2,
    val inFlightAbandonedTimeout: Duration = Duration.ofMinutes(10),
    val maxRetryAttempts: Int = 2,
    val providers: KnowledgeAnswerProviderProperties = KnowledgeAnswerProviderProperties()
) {
    init {
        require(activeProvider.isNotBlank()) { "knowledge answer active provider must not be blank" }
        require(model.isNotBlank()) { "knowledge answer model must not be blank" }
        require(promptTemplateId.isNotBlank()) { "knowledge answer prompt template id must not be blank" }
        require(promptVersion.isNotBlank()) { "knowledge answer prompt version must not be blank" }
        require(contextSchemaVersion.isNotBlank()) { "knowledge answer context schema version must not be blank" }
        require(maxPromptCharacters in 1_000..50_000) { "knowledge answer prompt size must be between 1000 and 50000" }
        require(maxQueryCharacters in 20..1_000) { "knowledge answer query size must be between 20 and 1000" }
        require(maxAnswerCharacters in 100..5_000) { "knowledge answer size must be between 100 and 5000" }
        require(minimumContextItems in 1..20) { "knowledge answer minimum context items must be between 1 and 20" }
        require(duplicateWindow > Duration.ZERO) { "knowledge answer duplicate window must be positive" }
        require(historyRetention > Duration.ZERO) { "knowledge answer history retention must be positive" }
        require(historyCleanupBatchSize in 1..1_000) { "knowledge answer history cleanup batch size must be between 1 and 1000" }
        require(hourlyRequestLimit in 1..10_000) { "knowledge answer hourly request limit must be between 1 and 10000" }
        require(dailyRequestLimit in 1..100_000) { "knowledge answer daily request limit must be between 1 and 100000" }
        require(maximumConcurrentRequests in 1..100) { "knowledge answer concurrent request limit must be between 1 and 100" }
        require(inFlightAbandonedTimeout > Duration.ZERO) { "knowledge answer abandoned timeout must be positive" }
        require(maxRetryAttempts in 0..10) { "knowledge answer max retry attempts must be between 0 and 10" }
    }
}

data class KnowledgeAnswerProviderProperties(
    val internalDemo: KnowledgeInternalDemoAnswerProviderProperties = KnowledgeInternalDemoAnswerProviderProperties(),
    val openai: KnowledgeExternalAnswerProviderProperties = KnowledgeExternalAnswerProviderProperties(),
    val externalPolicy: KnowledgeAnswerExternalProviderPolicyProperties = KnowledgeAnswerExternalProviderPolicyProperties()
)

data class KnowledgeInternalDemoAnswerProviderProperties(
    val enabled: Boolean = true
)

data class KnowledgeExternalAnswerProviderProperties(
    val enabled: Boolean = false,
    val endpoint: String = "https://api.openai.com/v1/chat/completions",
    val model: String = "gpt-5-mini",
    val credentialReference: String? = null,
    val timeout: Duration = Duration.ofSeconds(10),
    val maxAttempts: Int = 2,
    val maximumTokens: Int = 512,
    val temperature: Double = 0.0,
    val topP: Double = 1.0,
    val allowedProfiles: List<String> = emptyList(),
    val smokeTestOnly: Boolean = true,
    val smokeTestEnabled: Boolean = false,
    val fixtureModeEnabled: Boolean = false,
    val allowFallbackToInternalDemo: Boolean = false
) {
    init {
        require(endpoint.isNotBlank()) { "external knowledge answer endpoint must not be blank" }
        require(model.isNotBlank()) { "external knowledge answer model must not be blank" }
        require(!timeout.isNegative && !timeout.isZero) { "external knowledge answer timeout must be positive" }
        require(maxAttempts in 1..5) { "external knowledge answer max attempts must be between 1 and 5" }
        require(maximumTokens in 1..8_000) { "external knowledge answer maximum tokens must be between 1 and 8000" }
        require(temperature in 0.0..2.0) { "external knowledge answer temperature must be between 0 and 2" }
        require(topP in 0.0..1.0) { "external knowledge answer top_p must be between 0 and 1" }
        require(allowedProfiles.all { it.isNotBlank() }) { "external knowledge answer allowed profiles must not be blank" }
        if (enabled) {
            require(!credentialReference.isNullOrBlank()) { "external knowledge answer credential reference must be configured when enabled" }
        }
    }
}

data class KnowledgeAnswerExternalProviderPolicyProperties(
    val allowedProfiles: List<String> = listOf("local", "test"),
    val productionProhibited: Boolean = true,
    val requireHttpsOutsideLocal: Boolean = true,
    val localEndpointAllowlist: List<String> = listOf("http://localhost", "http://127.0.0.1"),
    val diagnosticsRetention: Duration = Duration.ofDays(30),
    val diagnosticsCleanupBatchSize: Int = 100
) {
    init {
        require(allowedProfiles.all { it.isNotBlank() }) { "knowledge answer external allowed profiles must not be blank" }
        require(localEndpointAllowlist.all { it.isNotBlank() }) { "knowledge answer external local endpoint allowlist must not be blank" }
        require(diagnosticsRetention > Duration.ZERO) { "knowledge answer provider diagnostics retention must be positive" }
        require(diagnosticsCleanupBatchSize in 1..1_000) { "knowledge answer provider diagnostics cleanup batch size must be between 1 and 1000" }
    }
}

data class KnowledgeSemanticSearchProperties(
    val enabled: Boolean = false,
    val activeProvider: String = "deterministic-local",
    val model: String = "deterministic-local-v1",
    val vectorDimension: Int = 16,
    val batchSize: Int = 20,
    val requestTimeout: java.time.Duration = java.time.Duration.ofSeconds(5),
    val maxAttempts: Int = 3,
    val allowedProfiles: List<String> = emptyList(),
    val credentialReference: String? = null,
    val similarityThreshold: Double = 0.15,
    val semanticResultLimit: Int = 20,
    val hybridKeywordWeight: Double = 0.55,
    val hybridSemanticWeight: Double = 0.45,
    val keywordFallbackEnabled: Boolean = false,
    val externalProviders: KnowledgeExternalEmbeddingProviderProperties = KnowledgeExternalEmbeddingProviderProperties(),
    val schedule: KnowledgeEmbeddingScheduleProperties = KnowledgeEmbeddingScheduleProperties(),
    val diagnosticsRetention: Duration = Duration.ofDays(30),
    val diagnosticsCleanupBatchSize: Int = 100
) {
    init {
        require(activeProvider.isNotBlank()) { "knowledge semantic search active provider must not be blank" }
        require(model.isNotBlank()) { "knowledge semantic search model must not be blank" }
        require(vectorDimension in 1..4096) { "knowledge semantic search vector dimension must be between 1 and 4096" }
        require(batchSize in 1..100) { "knowledge semantic search batch size must be between 1 and 100" }
        require(!requestTimeout.isNegative && !requestTimeout.isZero) { "knowledge semantic search request timeout must be positive" }
        require(maxAttempts in 1..10) { "knowledge semantic search max attempts must be between 1 and 10" }
        require(similarityThreshold in -1.0..1.0) { "knowledge semantic search similarity threshold must be between -1 and 1" }
        require(semanticResultLimit in 1..100) { "knowledge semantic result limit must be between 1 and 100" }
        require(hybridKeywordWeight >= 0.0 && hybridSemanticWeight >= 0.0 && hybridKeywordWeight + hybridSemanticWeight > 0.0) {
            "knowledge hybrid search weights must be non-negative and not both zero"
        }
        require(allowedProfiles.all { it.isNotBlank() }) { "knowledge semantic allowed profiles must not be blank" }
        require(diagnosticsRetention > Duration.ZERO) { "knowledge embedding diagnostics retention must be positive" }
        require(diagnosticsCleanupBatchSize in 1..1_000) { "knowledge embedding diagnostics cleanup batch size must be between 1 and 1000" }
        if (enabled) {
            require(allowedProfiles.isNotEmpty()) { "knowledge semantic search allowed profiles must be configured when enabled" }
        }
        if (schedule.enabled) {
            require(enabled) { "knowledge semantic search must be enabled before embedding refresh scheduling is enabled" }
        }
    }
}

data class KnowledgeExternalEmbeddingProviderProperties(
    val openai: KnowledgeOpenAiEmbeddingProviderProperties = KnowledgeOpenAiEmbeddingProviderProperties(),
    val productionProhibited: Boolean = true,
    val allowedProfiles: List<String> = listOf("local", "test"),
    val requireHttpsOutsideLocal: Boolean = true,
    val localEndpointAllowlist: List<String> = listOf("http://localhost", "http://127.0.0.1")
) {
    init {
        require(allowedProfiles.all { it.isNotBlank() }) { "knowledge external embedding allowed profiles must not be blank" }
        require(localEndpointAllowlist.all { it.isNotBlank() }) { "knowledge external embedding local endpoint allowlist must not be blank" }
    }
}

data class KnowledgeOpenAiEmbeddingProviderProperties(
    val enabled: Boolean = false,
    val endpoint: String = "https://api.openai.com/v1/embeddings",
    val model: String = "text-embedding-3-small",
    val dimensions: Int = 1536,
    val timeout: Duration = Duration.ofSeconds(10),
    val maxAttempts: Int = 2,
    val allowedProfiles: List<String> = emptyList(),
    val credentialReference: String? = null,
    val smokeTestEnabled: Boolean = false
) {
    init {
        require(endpoint.isNotBlank()) { "OpenAI knowledge embedding endpoint must not be blank" }
        require(model.isNotBlank()) { "OpenAI knowledge embedding model must not be blank" }
        require(dimensions in 1..4096) { "OpenAI knowledge embedding dimensions must be between 1 and 4096" }
        require(!timeout.isNegative && !timeout.isZero) { "OpenAI knowledge embedding timeout must be positive" }
        require(maxAttempts in 1..5) { "OpenAI knowledge embedding max attempts must be between 1 and 5" }
        require(allowedProfiles.all { it.isNotBlank() }) { "OpenAI knowledge embedding allowed profiles must not be blank" }
        if (enabled) {
            require(!credentialReference.isNullOrBlank()) { "OpenAI knowledge embedding credential reference must be configured when enabled" }
        }
    }
}

data class KnowledgeEmbeddingScheduleProperties(
    val enabled: Boolean = false,
    val executionInterval: Duration = Duration.ofMinutes(10),
    val startupDelay: Duration = Duration.ofMinutes(2),
    val batchSize: Int = 20,
    val lockTimeout: Duration = Duration.ofMinutes(5),
    val allowedProfiles: List<String> = listOf("local", "test"),
    val cleanupEnabled: Boolean = false,
    val cleanupExecutionInterval: Duration = Duration.ofHours(24)
) {
    init {
        require(!executionInterval.isNegative && !executionInterval.isZero) { "knowledge embedding schedule interval must be positive" }
        require(!startupDelay.isNegative) { "knowledge embedding schedule startup delay must not be negative" }
        require(batchSize in 1..100) { "knowledge embedding schedule batch size must be between 1 and 100" }
        require(!lockTimeout.isNegative && !lockTimeout.isZero) { "knowledge embedding schedule lock timeout must be positive" }
        require(allowedProfiles.all { it.isNotBlank() }) { "knowledge embedding schedule allowed profiles must not be blank" }
        require(!cleanupExecutionInterval.isNegative && !cleanupExecutionInterval.isZero) { "knowledge embedding cleanup interval must be positive" }
    }
}

class DeterministicKnowledgeChunker(
    private val properties: KnowledgeProperties
) {
    fun chunk(content: String, contentType: KnowledgeImportContentType): List<KnowledgeChunkDraft> {
        val normalized = normalize(content)
        require(normalized.isNotBlank()) { "knowledge content must not be blank" }
        require(normalized.length <= properties.maxImportCharacters) { "knowledge import content is too large" }
        val sections = sections(normalized, contentType)
        val chunks = mutableListOf<KnowledgeChunkDraft>()
        var carry = ""
        sections.forEach { section ->
            val paragraphs = section.body.split(Regex("\\n{2,}")).map { it.trim() }.filter { it.isNotBlank() }
            paragraphs.forEach { paragraph ->
                val candidate = listOf(carry, paragraph).filter { it.isNotBlank() }.joinToString("\n\n")
                if (candidate.length <= properties.chunkSize) {
                    carry = candidate
                } else {
                    if (carry.isNotBlank()) {
                        chunks += KnowledgeChunkDraft(chunks.size, section.heading, carry)
                        carry = overlap(carry)
                    }
                    splitLongParagraph(paragraph).forEach { piece ->
                        val merged = listOf(carry, piece).filter { it.isNotBlank() }.joinToString("\n\n")
                        if (merged.length > properties.chunkSize && carry.isNotBlank()) {
                            chunks += KnowledgeChunkDraft(chunks.size, section.heading, carry)
                            carry = overlap(carry)
                        }
                        carry = listOf(carry, piece).filter { it.isNotBlank() }.joinToString("\n\n")
                    }
                }
            }
            if (carry.isNotBlank()) {
                chunks += KnowledgeChunkDraft(chunks.size, section.heading, carry)
                carry = overlap(carry)
            }
        }
        return chunks.filter { it.text.isNotBlank() }
            .mapIndexed { index, chunk -> chunk.copy(order = index) }
    }

    private fun normalize(content: String): String =
        content.replace("\r\n", "\n").replace("\r", "\n").lines().joinToString("\n") { it.trimEnd() }.trim()

    private fun sections(content: String, contentType: KnowledgeImportContentType): List<Section> {
        if (contentType == KnowledgeImportContentType.PLAIN_TEXT) return listOf(Section(null, content))
        val sections = mutableListOf<Section>()
        var currentHeading: String? = null
        val current = mutableListOf<String>()
        content.lines().forEach { line ->
            val heading = markdownHeading(line)
            if (heading != null) {
                if (current.isNotEmpty()) {
                    sections += Section(currentHeading, current.joinToString("\n").trim())
                    current.clear()
                }
                currentHeading = heading
            } else {
                current += line
            }
        }
        if (current.isNotEmpty()) sections += Section(currentHeading, current.joinToString("\n").trim())
        return sections.ifEmpty { listOf(Section(currentHeading, content)) }.filter { it.body.isNotBlank() }
    }

    private fun markdownHeading(line: String): String? {
        val trimmed = line.trim()
        if (!trimmed.startsWith("#")) return null
        val level = trimmed.takeWhile { it == '#' }.length
        if (level !in 1..6 || trimmed.getOrNull(level) != ' ') return null
        return trimmed.drop(level).trim().take(240).ifBlank { null }
    }

    private fun splitLongParagraph(paragraph: String): List<String> {
        if (paragraph.length <= properties.chunkSize) return listOf(paragraph)
        val words = paragraph.split(Regex("\\s+")).filter { it.isNotBlank() }
        val pieces = mutableListOf<String>()
        var current = ""
        words.forEach { word ->
            val candidate = listOf(current, word).filter { it.isNotBlank() }.joinToString(" ")
            if (candidate.length > properties.chunkSize && current.isNotBlank()) {
                pieces += current
                current = word
            } else {
                current = candidate
            }
        }
        if (current.isNotBlank()) pieces += current
        return pieces
    }

    private fun overlap(text: String): String {
        if (properties.chunkOverlap == 0) return ""
        val words = text.split(Regex("\\s+")).filter { it.isNotBlank() }
        var acc = ""
        words.asReversed().forEach { word ->
            val candidate = listOf(word, acc).filter { it.isNotBlank() }.joinToString(" ")
            if (candidate.length <= properties.chunkOverlap) acc = candidate else return@forEach
        }
        return acc.lowercase(Locale.ROOT)
    }

    private data class Section(val heading: String?, val body: String)
}
