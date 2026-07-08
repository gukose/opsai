package com.hotelopai.unimock.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "ops.ai.unimock.database")
data class DatabaseProperties(
    val url: String,
    val username: String,
    val password: String,
    val driverClassName: String,
    val maximumPoolSize: Int
)
