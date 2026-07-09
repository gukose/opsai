package com.hotelopai.assistant.application

import com.hotelopai.assistant.domain.IntentType

data class InterpretationResult(
    val intent: IntentType,
    val fields: Map<String, String>,
    val confidence: Double,
    val language: String? = null,
    val followUpQuestion: String? = null,
    val missingFields: List<String> = emptyList()
)
