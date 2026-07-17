package com.hotelopai.performance

import com.hotelopai.support.PostgresIntegrationTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@ActiveProfiles("test")
class DatabasePerformanceIndexIntegrationTest : PostgresIntegrationTestSupport() {
    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Test
    fun `Sprint 8F performance indexes exist with expected definitions`() {
        val indexes = jdbcTemplate.query(
            """
            select indexname, indexdef
            from pg_indexes
            where schemaname = 'public'
              and indexname in (
                'idx_task_hotel_created_at',
                'idx_task_hotel_assignee_updated_at_desc',
                'idx_task_hotel_active_sla_deadline',
                'idx_operational_outbox_pending_created_due'
              )
            """.trimIndent()
        ) { rs, _ -> rs.getString("indexname") to rs.getString("indexdef") }.toMap()

        assertThat(indexes.keys).containsExactlyInAnyOrder(
            "idx_task_hotel_created_at",
            "idx_task_hotel_assignee_updated_at_desc",
            "idx_task_hotel_active_sla_deadline",
            "idx_operational_outbox_pending_created_due"
        )
        assertThat(indexes["idx_task_hotel_created_at"])
            .contains("task", "hotel_id", "created_at")
        assertThat(indexes["idx_task_hotel_assignee_updated_at_desc"])
            .contains("task", "hotel_id", "assignee_type", "assignee_id", "updated_at DESC")
        assertThat(indexes["idx_task_hotel_active_sla_deadline"])
            .contains("task", "hotel_id", "sla_deadline", "WHERE", "COMPLETED", "CANCELLED")
        assertThat(indexes["idx_operational_outbox_pending_created_due"])
            .contains("operational_outbox", "created_at", "next_attempt_at", "WHERE", "PENDING")
    }

    @Test
    fun `Sprint 8F performance index names are unique`() {
        val duplicateNames = jdbcTemplate.queryForList(
            """
            select indexname
            from pg_indexes
            where schemaname = 'public'
            group by indexname
            having count(*) > 1
            """.trimIndent(),
            String::class.java
        )

        assertThat(duplicateNames).isEmpty()
    }
}
