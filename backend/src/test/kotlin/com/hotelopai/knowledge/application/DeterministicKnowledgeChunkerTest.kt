package com.hotelopai.knowledge.application

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class DeterministicKnowledgeChunkerTest {
    @Test
    fun `markdown chunking preserves headings and is deterministic`() {
        val chunker = DeterministicKnowledgeChunker(KnowledgeProperties(chunkSize = 200, chunkOverlap = 20))
        val content = """
            # Maintenance

            Inspect the valve before reopening the service panel.

            Confirm the pump is isolated before cleaning.

            ## Reset

            Restart the controller only after the room is clear.
        """.trimIndent()

        val first = chunker.chunk(content, KnowledgeImportContentType.MARKDOWN)
        val second = chunker.chunk(content, KnowledgeImportContentType.MARKDOWN)

        assertThat(first).isEqualTo(second)
        assertThat(first.map { it.order }).containsExactlyElementsOf(first.indices.toList())
        assertThat(first.mapNotNull { it.heading }).contains("Maintenance", "Reset")
        assertThat(first.joinToString("\n") { it.text }).contains("Inspect the valve")
    }

    @Test
    fun `plain text chunking splits long paragraphs within configured bounds`() {
        val chunker = DeterministicKnowledgeChunker(KnowledgeProperties(chunkSize = 200, chunkOverlap = 20))
        val content = (1..120).joinToString(" ") { "word$it" }

        val chunks = chunker.chunk(content, KnowledgeImportContentType.PLAIN_TEXT)

        assertThat(chunks).hasSizeGreaterThan(1)
        assertThat(chunks).allMatch { it.text.length <= 240 }
        assertThat(chunks.map { it.heading }).containsOnlyNulls()
    }

    @Test
    fun `semantic search is disabled by default and validates enabled configuration`() {
        assertThat(KnowledgeProperties().semanticSearch.enabled).isFalse()

        assertThrows(IllegalArgumentException::class.java) {
            KnowledgeProperties(semanticSearch = KnowledgeSemanticSearchProperties(enabled = true, allowedProfiles = emptyList()))
        }
        assertThrows(IllegalArgumentException::class.java) {
            KnowledgeProperties(semanticSearch = KnowledgeSemanticSearchProperties(vectorDimension = 0))
        }
    }
}
