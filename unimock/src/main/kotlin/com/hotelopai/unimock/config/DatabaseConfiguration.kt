package com.hotelopai.unimock.config

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import javax.sql.DataSource
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

@Configuration
@EnableConfigurationProperties(DatabaseProperties::class, UniMockProperties::class)
class DatabaseConfiguration {
    @Bean
    fun clock(): Clock = Clock.systemUTC()

    @Bean
    fun objectMapper(): ObjectMapper = jacksonObjectMapper()

    @Bean
    fun dataSource(properties: DatabaseProperties): DataSource {
        val config = HikariConfig().apply {
            jdbcUrl = properties.url
            username = properties.username
            password = properties.password
            driverClassName = properties.driverClassName
            maximumPoolSize = properties.maximumPoolSize
        }

        return HikariDataSource(config)
    }
}
