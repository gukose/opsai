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
        val schemaCount = jdbcTemplate.queryForObject(
            """
                select count(*)
                from pg_catalog.pg_namespace
                where nspname in ('public', 'unimock')
            """.trimIndent(),
            Int::class.java
        )

        assertEquals(2, schemaCount)
    }
}
