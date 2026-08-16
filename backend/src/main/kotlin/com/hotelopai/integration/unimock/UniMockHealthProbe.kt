package com.hotelopai.integration.unimock

data class UniMockHealthProbe(
    val reachable: Boolean,
    val status: Int?,
    val elapsedMs: Long,
    val failureCategory: String?
)
