package com.hotelopai.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "ops.ai.database")
data class DatabaseProperties(
    val url: String,
    val username: String,
    val password: String,
    val driverClassName: String = "org.postgresql.Driver",
    val maximumPoolSize: Int = 5,
    val minimumIdle: Int = 1,
    val connectionTimeout: Long = 20_000
)
