package com.hotelopai.knowledge.application

import org.springframework.stereotype.Component
import java.security.MessageDigest
import kotlin.math.sqrt

@Component
class DeterministicKnowledgeEmbeddingProvider(
    private val properties: KnowledgeProperties
) : KnowledgeEmbeddingProvider {
    override val providerId: KnowledgeEmbeddingProviderId = KnowledgeEmbeddingProviderId("deterministic-local")
    override val providerType: KnowledgeEmbeddingProviderType = KnowledgeEmbeddingProviderType.INTERNAL
    override val modelIdentifier: String = properties.semanticSearch.model
    override val embeddingDimension: Int = properties.semanticSearch.vectorDimension

    override fun readiness(): KnowledgeEmbeddingProviderReadiness =
        KnowledgeEmbeddingProviderReadiness.READY

    override fun embed(requests: List<KnowledgeEmbeddingRequest>): List<KnowledgeEmbeddingResponse> {
        require(requests.size <= properties.semanticSearch.batchSize) { "knowledge embedding batch is too large" }
        return requests.map {
            KnowledgeEmbeddingResponse(it.chunkId, KnowledgeEmbeddingVector(vector(it.text)), it.contentFingerprint)
        }
    }

    private fun vector(text: String): List<Double> {
        val buckets = DoubleArray(embeddingDimension)
        text.lowercase().split(Regex("[^a-z0-9]+")).filter { it.isNotBlank() }.forEach { token ->
            val digest = MessageDigest.getInstance("SHA-256").digest(token.toByteArray(Charsets.UTF_8))
            val index = (digest[0].toInt() and 0xff) % embeddingDimension
            buckets[index] += 1.0
        }
        val magnitude = sqrt(buckets.sumOf { it * it }).takeIf { it > 0.0 } ?: 1.0
        return buckets.map { it / magnitude }
    }
}
