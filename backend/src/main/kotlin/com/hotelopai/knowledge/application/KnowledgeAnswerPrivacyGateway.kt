package com.hotelopai.knowledge.application

import org.springframework.stereotype.Component

@Component
class KnowledgeAnswerPrivacyGateway {
    private val blockedPatterns = listOf(
        "email" to Regex("\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b", RegexOption.IGNORE_CASE),
        "phone" to Regex("\\b(?:\\+?\\d[\\d .-]{7,}\\d)\\b"),
        "payment" to Regex("\\b(?:card|cvv|iban|payment|billing card)\\b", RegexOption.IGNORE_CASE),
        "credential" to Regex("\\b(?:api[_-]?key|password|secret|token|bearer|authorization)\\b", RegexOption.IGNORE_CASE),
        "webhook" to Regex("\\b(?:webhook payload|x-signature|signature=|event payload)\\b", RegexOption.IGNORE_CASE),
        "raw_identifier" to Regex("\\b(?:reservation|property|guest|pms)[_-]?[a-f0-9]{8,}\\b", RegexOption.IGNORE_CASE)
    )

    fun validateQuery(query: String) {
        val failure = blockedPatterns.firstOrNull { (_, pattern) -> pattern.containsMatchIn(query) }
        require(failure == null) { "knowledge answer query contains unsupported sensitive data: ${failure?.first}" }
    }

    fun validateOutput(answer: String?) {
        if (answer.isNullOrBlank()) return
        val failure = blockedPatterns.firstOrNull { (_, pattern) -> pattern.containsMatchIn(answer) }
        require(failure == null) { "knowledge answer output contains unsupported sensitive data: ${failure?.first}" }
    }
}
