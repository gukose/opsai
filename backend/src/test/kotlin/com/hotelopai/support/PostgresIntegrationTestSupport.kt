package com.hotelopai.support

import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer

abstract class PostgresIntegrationTestSupport {
    companion object {
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16-alpine").apply {
            withDatabaseName("hotelopai_test")
            withUsername("hotelopai")
            withPassword("hotelopai")
            start()
        }

        @JvmStatic
        @DynamicPropertySource
        fun registerDatabaseProperties(registry: DynamicPropertyRegistry) {
            registry.add("ops.ai.database.url", postgres::getJdbcUrl)
            registry.add("ops.ai.database.username", postgres::getUsername)
            registry.add("ops.ai.database.password", postgres::getPassword)
            registry.add("ops.ai.database.driver-class-name", postgres::getDriverClassName)
            registry.add("ops.ai.database.maximum-pool-size") { 4 }
        }
    }
}
