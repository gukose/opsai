package com.hotelopai.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "ops.ai.cors")
data class WebCorsProperties(
    val allowedOrigins: List<String> = listOf(
        "http://localhost:8081",
        "http://localhost:19006"
    ),
    val allowedMethods: List<String> = listOf(
        "GET",
        "POST",
        "PUT",
        "PATCH",
        "DELETE",
        "OPTIONS"
    ),
    val allowedHeaders: List<String> = listOf(
        "Authorization",
        "Content-Type"
    ),
    val maxAge: Duration = Duration.ofHours(1)
)
