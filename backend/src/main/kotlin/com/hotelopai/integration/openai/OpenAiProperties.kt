package com.hotelopai.integration.openai

import java.time.Duration

data class OpenAiProperties(
    val apiKey: String = "",
    val baseUrl: String = "https://api.openai.com",
    val model: String = "gpt-4.1-mini",
    val requestTimeout: Duration = Duration.ofSeconds(20),
    val maxCompletionTokens: Int = 400,
    val temperature: Double = 0.0,
    val retries: Int = 1
)
