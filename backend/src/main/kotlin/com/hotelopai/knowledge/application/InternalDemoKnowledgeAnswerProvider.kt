package com.hotelopai.knowledge.application

import org.springframework.stereotype.Component

@Component
class InternalDemoKnowledgeAnswerProvider(
    private val properties: KnowledgeProperties
) : KnowledgeAnswerProvider {
    override val providerId: String = "internal-demo"
    override val modelId: String = properties.answers.model

    override fun readiness(): KnowledgeEmbeddingProviderReadiness =
        if (properties.answers.providers.internalDemo.enabled) KnowledgeEmbeddingProviderReadiness.READY else KnowledgeEmbeddingProviderReadiness.DISABLED

    override fun generate(prompt: KnowledgePrompt): KnowledgeAnswerProviderResponse {
        if (readiness() != KnowledgeEmbeddingProviderReadiness.READY) {
            return KnowledgeAnswerProviderResponse(KnowledgeAnswerStatus.PROVIDER_DISABLED, null, null, emptyList(), KnowledgeAnswerFailureCategory.PROVIDER_DISABLED)
        }
        if (prompt.contextItems.isEmpty()) {
            return KnowledgeAnswerProviderResponse(KnowledgeAnswerStatus.INSUFFICIENT_CONTEXT, null, KnowledgeAnswerConfidence.LOW, emptyList(), KnowledgeAnswerFailureCategory.INSUFFICIENT_CONTEXT)
        }
        val first = prompt.contextItems.first()
        val answer = "Based on ${first.citationId}, ${first.text.take(260).trim()}"
        return KnowledgeAnswerProviderResponse(
            status = KnowledgeAnswerStatus.ANSWERED,
            answerText = answer,
            confidence = KnowledgeAnswerConfidence.MEDIUM,
            citationIds = listOf(first.citationId)
        )
    }
}
