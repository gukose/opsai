package com.hotelopai

import com.hotelopai.support.PostgresIntegrationTestSupport
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@ActiveProfiles("test")
class OpsaiApplicationTests : PostgresIntegrationTestSupport() {
    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Test
    fun contextLoads() {
        val publicSchemaCount = jdbcTemplate.queryForObject(
            """
                select count(*)
                from pg_catalog.pg_namespace
                where nspname = 'public'
            """.trimIndent(),
            Int::class.java
        )

        val publicFlywayHistoryCount = jdbcTemplate.queryForObject(
            """
                select count(*)
                from public.flyway_schema_history
                where success = true
            """.trimIndent(),
            Long::class.java
        )

        assertEquals(1, publicSchemaCount)
        assertEquals(16L, publicFlywayHistoryCount)
    }
}
