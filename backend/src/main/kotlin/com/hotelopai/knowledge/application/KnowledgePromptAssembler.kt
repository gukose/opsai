package com.hotelopai.knowledge.application

import org.springframework.stereotype.Component

@Component
class KnowledgePromptAssembler(
    private val properties: KnowledgeProperties
) {
    fun assemble(query: String, context: KnowledgeContextAssemblyResult): KnowledgePrompt {
        val citations = context.items.mapIndexed { index, item ->
            val citationId = "K${index + 1}"
            KnowledgePromptContextItem(citationId, item.selectedText, item.citation.toAnswerCitation(citationId, item.selectedText))
        }
        val prompt = KnowledgePrompt(
            templateId = properties.answers.promptTemplateId,
            version = properties.answers.promptVersion,
            systemInstructions = SYSTEM_INSTRUCTIONS,
            operatorQuery = query.trim(),
            contextItems = citations.sortedBy { it.citationId },
            outputSchema = OUTPUT_SCHEMA
        )
        require(prompt.size <= properties.answers.maxPromptCharacters) { "knowledge answer prompt exceeds configured size limit" }
        return prompt
    }

    private fun KnowledgeSourceCitation.toAnswerCitation(citationId: String, excerpt: String): KnowledgeCitation =
        KnowledgeCitation(citationId, documentReference, chunkReference, title, category, chunkPosition, retrievalScore, contentFingerprint, excerpt.take(800))

    companion object {
        private const val SYSTEM_INSTRUCTIONS =
            "Answer only from supplied Hotel OpAI knowledge context. Return insufficient context when evidence is weak. Do not create tasks or direct operational actions."
        private const val OUTPUT_SCHEMA =
            "Structured output: status, concise answer, confidence LOW|MEDIUM|HIGH, citationIds."
    }
}
