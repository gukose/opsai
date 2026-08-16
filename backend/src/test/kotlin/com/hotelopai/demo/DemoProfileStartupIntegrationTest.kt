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
        val seededEmails = users.findByHotelId(hotelId).filter { it.status == com.hotelopai.auth.domain.UserStatus.ACTIVE }.map { it.email.value }
        assertThat(seededEmails).hasSize(35)
        assertThat(seededEmails).contains(
            "kemal.yilmaz@demo.hotelopai.app", "bekzod.abdullayev@demo.hotelopai.app",
            "ayse.yilmaz@demo.hotelopai.app", "anna.muller@demo.hotelopai.app"
        )
        assertThat(jdbc.queryForObject("select count(*) from employee where hotel_id=:hotel and status='ACTIVE'", mapOf("hotel" to hotelId), Long::class.java)).isEqualTo(35L)
        val employeeNumbers = jdbc.query("select employee_number from employee where hotel_id=:hotel and status='ACTIVE' order by employee_number", mapOf("hotel" to hotelId)) { rs, _ -> rs.getString(1) }
        assertThat(employeeNumbers).containsExactlyElementsOf((1..35).map { "EMP%04d".format(it) })
        assertThat(jdbc.queryForObject("select count(distinct email) from app_user where hotel_id=:hotel and status='ACTIVE'", mapOf("hotel" to hotelId), Long::class.java)).isEqualTo(35L)
        assertThat(jdbc.queryForObject("select count(*) from demo_bootstrap_marker where hotel_id=:hotel", mapOf("hotel" to hotelId), Long::class.java)).isEqualTo(1L)
        assertThat(jdbc.queryForObject("select count(*) from workforce_shift where hotel_id=:hotel and status='WORKING'", mapOf("hotel" to hotelId), Long::class.java)).isGreaterThanOrEqualTo(7L)
        assertThat(jdbc.queryForObject("""select count(*) from task where hotel_id=:hotel and title='HVAC maintenance'
            and status='ASSIGNED' and assignee_display_name='Bekzod Abdullayev'""", mapOf("hotel" to hotelId), Long::class.java)).isEqualTo(1L)
    }
}
