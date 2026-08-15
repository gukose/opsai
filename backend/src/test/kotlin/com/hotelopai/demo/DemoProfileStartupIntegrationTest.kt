package com.hotelopai.demo

import com.hotelopai.auth.application.UserRepository
import com.hotelopai.support.PostgresIntegrationTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.test.context.ActiveProfiles

@SpringBootTest(properties = [
    "ops.ai.demo.bootstrap.enabled=true",
    "ops.ai.demo.bootstrap.gm-password=demo-password-gm",
    "ops.ai.demo.bootstrap.housekeeping-supervisor-password=demo-password-hks",
    "ops.ai.demo.bootstrap.housekeeper-password=demo-password-hk",
    "ops.ai.demo.bootstrap.technician-password=demo-password-tech",
    "ops.ai.demo.bootstrap.reception-password=demo-password-reception",
    "ops.ai.demo.bootstrap.guest-relations-password=demo-password-guest-relations",
    "ops.ai.demo.bootstrap.admin-password=demo-password-admin",
    "ops.ai.auth.jwt.secret=demo-test-jwt-secret-demo-test-jwt-secret",
    "ops.ai.voice.active-provider=internal-demo",
    "ops.ai.voice.external.enabled=false",
    "ops.ai.knowledge.answers.enabled=false",
    "ops.ai.knowledge.semantic-search.enabled=false",
    "ops.ai.scheduler.enabled=false",
    "assistant.ai.provider=deterministic"
])
@ActiveProfiles("demo")
class DemoProfileStartupIntegrationTest : PostgresIntegrationTestSupport() {
    @Autowired lateinit var bootstrap: DemoBootstrapService
    @Autowired lateinit var users: UserRepository
    @Autowired lateinit var jdbc: NamedParameterJdbcTemplate

    @Test
    fun `demo profile starts and bootstrap is idempotent with role workforce and assignment data`() {
        bootstrap.bootstrap()
        bootstrap.bootstrap()

        val hotelId = jdbc.queryForObject("select id from hotel where code=:code", mapOf("code" to DemoBootstrapService.HOTEL_CODE), java.util.UUID::class.java)!!
        assertThat(users.findByHotelId(hotelId).map { it.email.value }).contains(
            "gm@demo.hotelopai.app", "housekeeping.supervisor@demo.hotelopai.app", "housekeeper@demo.hotelopai.app",
            "technician@demo.hotelopai.app", "reception@demo.hotelopai.app", "guest.relations@demo.hotelopai.app",
            "reviewer.admin@demo.hotelopai.app"
        )
        assertThat(jdbc.queryForObject("select count(*) from demo_bootstrap_marker where hotel_id=:hotel", mapOf("hotel" to hotelId), Long::class.java)).isEqualTo(1L)
        assertThat(jdbc.queryForObject("select count(*) from workforce_shift where hotel_id=:hotel and status='WORKING'", mapOf("hotel" to hotelId), Long::class.java)).isGreaterThanOrEqualTo(7L)
        assertThat(jdbc.queryForObject("""select count(*) from task where hotel_id=:hotel and title='HVAC maintenance'
            and status='ASSIGNED' and assignee_display_name='HVAC Technician'""", mapOf("hotel" to hotelId), Long::class.java)).isEqualTo(1L)
    }
}
