package com.hotelopai.vision.application

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "assistant.vision")
class VisionAnalysisProperties {
    var enabled: Boolean = true
    var provider: String = "unavailable"
    var deterministicFixturesEnabled: Boolean = false

    fun normalizedProvider(): Provider =
        when (provider.trim().lowercase()) {
            "unavailable", "" -> Provider.UNAVAILABLE
            "deterministic" -> Provider.DETERMINISTIC
            else -> throw IllegalStateException("Unsupported ASSISTANT_VISION_PROVIDER value: $provider")
        }

    enum class Provider {
        UNAVAILABLE,
        DETERMINISTIC
    }
}

class VisionAnalysisProviderUnavailableException(message: String) : RuntimeException(message)
