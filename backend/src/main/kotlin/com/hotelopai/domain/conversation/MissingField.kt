package com.hotelopai.assistant.domain

data class MissingField(
    val key: String,
    val label: String,
    val required: Boolean = true
)
