package com.hotelopai.config

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import javax.sql.DataSource
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(DatabaseProperties::class)
class DatabaseConfiguration {
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
