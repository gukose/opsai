package com.hotelopai.demo

import com.hotelopai.auth.application.UserRepository
import com.hotelopai.support.PostgresIntegrationTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.test.context.ActiveProfiles
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

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
    fun `empty previous and repeated bootstrap safely upsert canonical workforce`() {
        val hotelId = jdbc.queryForObject("select id from hotel where code=:code", mapOf("code" to DemoBootstrapService.HOTEL_CODE), UUID::class.java)!!

        // Application startup bootstrapped the initially empty Testcontainers database.
        assertThat(count("employee", hotelId, "status='ACTIVE'")).isEqualTo(35L)

        // Simulate canonical rows left by an older deployment, including version zero.
        jdbc.update("update employee set display_name='Old Name',status='INACTIVE',operational_status='OFFLINE',version=0 where hotel_id=:hotel and employee_number='EMP0014'", mapOf("hotel" to hotelId))
        jdbc.update("update app_user set status='DISABLED',version=0 where hotel_id=:hotel and email='zeynep.celik@demo.hotelopai.app'", mapOf("hotel" to hotelId))

        val otherHotelId = UUID.randomUUID()
        val otherEmployeeId = UUID.randomUUID()
        val now = Timestamp.from(Instant.now())
        jdbc.update("insert into hotel(id,version,created_at,updated_at,code,name,status) values(:id,0,:now,:now,'other-hotel','Other Hotel','ACTIVE')",
            mapOf("id" to otherHotelId, "now" to now))
        jdbc.update("""insert into employee(id,hotel_id,version,created_at,updated_at,employee_number,display_name,status,operational_status,languages)
            values(:id,:hotel,0,:now,:now,'EMP0001','Other Hotel Employee','INACTIVE','OFFLINE','{}')""",
            mapOf("id" to otherEmployeeId, "hotel" to otherHotelId, "now" to now))

        bootstrap.bootstrap()
        val roleMappings = count("employee_role", hotelId)
        val skillMappings = count("employee_skill", hotelId)
        val shifts = count("workforce_shift", hotelId)
        bootstrap.bootstrap()
        bootstrap.bootstrap()

        val seededEmails = users.findByHotelId(hotelId).filter { it.status == com.hotelopai.auth.domain.UserStatus.ACTIVE }.map { it.email.value }
        assertThat(seededEmails).hasSize(42)
        assertThat(seededEmails).contains(
            "kemal.yilmaz@demo.hotelopai.app", "bekzod.abdullayev@demo.hotelopai.app",
            "ayse.yilmaz@demo.hotelopai.app", "anna.muller@demo.hotelopai.app"
        )
        assertThat(jdbc.queryForObject("select count(*) from employee where hotel_id=:hotel and status='ACTIVE'", mapOf("hotel" to hotelId), Long::class.java)).isEqualTo(35L)
        val employeeNumbers = jdbc.query("select employee_number from employee where hotel_id=:hotel and status='ACTIVE' order by employee_number", mapOf("hotel" to hotelId)) { rs, _ -> rs.getString(1) }
        assertThat(employeeNumbers).containsExactlyElementsOf((1..35).map { "EMP%04d".format(it) })
        assertThat(jdbc.queryForObject("select count(distinct email) from app_user where hotel_id=:hotel and status='ACTIVE'", mapOf("hotel" to hotelId), Long::class.java)).isEqualTo(35L)
        val aliases = mapOf(
            "reviewer.admin@demo.hotelopai.app" to "EMP0001",
            "gm@demo.hotelopai.app" to "EMP0001",
            "technician@demo.hotelopai.app" to "EMP0024",
            "housekeeper@demo.hotelopai.app" to "EMP0014",
            "housekeeping.supervisor@demo.hotelopai.app" to "EMP0013",
            "reception@demo.hotelopai.app" to "EMP0006",
            "guest.relations@demo.hotelopai.app" to "EMP0011"
        )
        aliases.forEach { (email, employeeNumber) ->
            assertThat(jdbc.queryForObject("""select count(*) from app_user u join employee e on e.id=u.employee_id
                where u.hotel_id=:hotel and u.email=:email and u.status='ACTIVE' and e.employee_number=:employee""",
                mapOf("hotel" to hotelId, "email" to email, "employee" to employeeNumber), Long::class.java)).isEqualTo(1L)
        }
        assertThat(jdbc.queryForObject("select count(*) from employee where hotel_id=:hotel and status='ACTIVE'", mapOf("hotel" to hotelId), Long::class.java)).isEqualTo(35L)
        assertThat(jdbc.queryForObject("""select count(*) from app_user u join user_role ur on ur.user_id=u.id
            join role_permission rp on rp.role_id=ur.role_id join permission p on p.id=rp.permission_id
            where u.hotel_id=:hotel and u.email in ('housekeeping.supervisor@demo.hotelopai.app','mustafa.tekin@demo.hotelopai.app') and p.code='TASK_ASSIGN'""",
            mapOf("hotel" to hotelId), Long::class.java)).isEqualTo(2L)
        assertThat(jdbc.queryForObject("""select count(*) from app_user u join user_role ur on ur.user_id=u.id
            join role_permission rp on rp.role_id=ur.role_id join permission p on p.id=rp.permission_id
            where u.hotel_id=:hotel and u.email='housekeeper@demo.hotelopai.app' and p.code='TASK_ASSIGN'""",
            mapOf("hotel" to hotelId), Long::class.java)).isZero()
        assertThat(count("employee_role", hotelId)).isEqualTo(roleMappings)
        assertThat(count("employee_skill", hotelId)).isEqualTo(skillMappings)
        assertThat(count("workforce_shift", hotelId)).isEqualTo(shifts)
        assertThat(jdbc.queryForObject("select count(*) from employee_role er join employee e on e.id=er.employee_id where e.hotel_id=:hotel", mapOf("hotel" to hotelId), Long::class.java)).isEqualTo(roleMappings)
        assertThat(jdbc.queryForObject("select count(*) from employee_skill es join employee e on e.id=es.employee_id where e.hotel_id=:hotel", mapOf("hotel" to hotelId), Long::class.java)).isEqualTo(skillMappings)
        assertThat(jdbc.queryForObject("select display_name from employee where hotel_id=:hotel and employee_number='EMP0014'", mapOf("hotel" to hotelId), String::class.java)).isEqualTo("Zeynep Çelik")
        assertThat(jdbc.queryForObject("select status from app_user where hotel_id=:hotel and email='zeynep.celik@demo.hotelopai.app'", mapOf("hotel" to hotelId), String::class.java)).isEqualTo("ACTIVE")
        assertThat(jdbc.queryForObject("select display_name from employee where id=:id and hotel_id=:hotel", mapOf("id" to otherEmployeeId, "hotel" to otherHotelId), String::class.java)).isEqualTo("Other Hotel Employee")
        assertThat(jdbc.queryForObject("select status from employee where id=:id", mapOf("id" to otherEmployeeId), String::class.java)).isEqualTo("INACTIVE")
        assertThat(jdbc.queryForObject("select count(*) from demo_bootstrap_marker where hotel_id=:hotel", mapOf("hotel" to hotelId), Long::class.java)).isEqualTo(1L)
        assertThat(jdbc.queryForObject("select count(*) from workforce_shift where hotel_id=:hotel and status='WORKING'", mapOf("hotel" to hotelId), Long::class.java)).isGreaterThanOrEqualTo(7L)
        assertThat(jdbc.queryForObject("""select count(*) from task where hotel_id=:hotel and title='HVAC maintenance'
            and status='ASSIGNED' and assignee_display_name='Bekzod Abdullayev'""", mapOf("hotel" to hotelId), Long::class.java)).isEqualTo(1L)
    }

    private fun count(table: String, hotelId: UUID, extraPredicate: String = "true"): Long {
        val hotelColumn = if (table == "employee_role" || table == "employee_skill")
            "employee_id in (select id from employee where hotel_id=:hotel)" else "hotel_id=:hotel"
        return jdbc.queryForObject("select count(*) from $table where $hotelColumn and $extraPredicate", mapOf("hotel" to hotelId), Long::class.java)!!
    }
}
