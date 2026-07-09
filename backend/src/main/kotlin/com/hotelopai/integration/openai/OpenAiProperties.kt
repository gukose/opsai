package com.hotelopai.integration.openai

import java.time.Duration

class OpenAiProperties {
    var apiKey: String = ""
    var baseUrl: String = "https://api.openai.com"
    var model: String = ""
    var requestTimeout: Duration = Duration.ofSeconds(20)
    var maxOutputTokens: Int = 400
    var temperature: Double = 0.0
    var retries: Int = 1

    override fun toString(): String =
        "OpenAiProperties(apiKey=***redacted***, baseUrl=$baseUrl, model=${model.ifBlank { "<unset>" }}, requestTimeout=$requestTimeout, maxOutputTokens=$maxOutputTokens, temperature=$temperature, retries=$retries)"
}
