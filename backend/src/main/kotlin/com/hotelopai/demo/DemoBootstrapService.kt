package com.hotelopai.demo

import com.hotelopai.auth.application.PasswordHasher
import com.hotelopai.auth.application.PermissionRepository
import com.hotelopai.auth.application.RoleRepository
import com.hotelopai.auth.application.UserRepository
import com.hotelopai.auth.domain.EmailAddress
import com.hotelopai.auth.domain.Permission
import com.hotelopai.auth.domain.Role
import com.hotelopai.auth.domain.User
import com.hotelopai.auth.domain.UserStatus
import com.hotelopai.employee.application.DepartmentRepository
import com.hotelopai.employee.application.EmployeeRepository
import com.hotelopai.employee.application.SkillRepository
import com.hotelopai.employee.domain.Department
import com.hotelopai.employee.domain.Employee
import com.hotelopai.employee.domain.EmployeeOperationalStatus
import com.hotelopai.employee.domain.Skill
import com.hotelopai.hotel.application.HotelRepository
import com.hotelopai.hotel.domain.Hotel
import com.hotelopai.shared.kernel.UuidV7Generator
import com.hotelopai.shared.security.PermissionCodes
import com.hotelopai.task.application.CreateTaskCommand
import com.hotelopai.task.application.TaskLifecycleService
import com.hotelopai.task.domain.TaskIntentType
import com.hotelopai.task.domain.TaskPriority
import com.hotelopai.task.domain.TaskSource
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.sql.Timestamp
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@ConfigurationProperties("ops.ai.demo.bootstrap")
data class DemoBootstrapProperties(
    val enabled: Boolean = false,
    val gmPassword: String = "",
    val housekeepingSupervisorPassword: String = "",
    val housekeeperPassword: String = "",
    val technicianPassword: String = "",
    val receptionPassword: String = "",
    val guestRelationsPassword: String = "",
    val adminPassword: String = ""
) {
    fun passwordFor(code: String): String = when (code) {
        "GM" -> gmPassword
        "HOUSEKEEPING_SUPERVISOR" -> housekeepingSupervisorPassword
        "HOUSEKEEPER" -> housekeeperPassword
        "TECHNICIAN" -> technicianPassword
        "RECEPTION" -> receptionPassword
        "GUEST_RELATIONS" -> guestRelationsPassword
        "ADMIN" -> adminPassword
        else -> error("Unsupported demo role")
    }.also { require(it.length >= 12) { "Every DEMO user password must be supplied through an environment variable and contain at least 12 characters" } }
}

@Configuration
@Profile("demo")
@EnableConfigurationProperties(DemoBootstrapProperties::class)
class DemoBootstrapConfiguration {
    @Bean
    @ConditionalOnProperty(prefix = "ops.ai.demo.bootstrap", name = ["enabled"], havingValue = "true")
    fun demoBootstrapRunner(service: DemoBootstrapService): ApplicationRunner = ApplicationRunner { service.bootstrap() }
}

@Service
@Profile("demo")
class DemoBootstrapService(
    private val properties: DemoBootstrapProperties,
    private val hotels: HotelRepository,
    private val permissions: PermissionRepository,
    private val roles: RoleRepository,
    private val users: UserRepository,
    private val employees: EmployeeRepository,
    private val departments: DepartmentRepository,
    private val skills: SkillRepository,
    private val passwordHasher: PasswordHasher,
    private val tasks: TaskLifecycleService,
    private val jdbc: NamedParameterJdbcTemplate
) {
    @Transactional
    fun bootstrap() {
        if (!properties.enabled) return
        val hotel = hotels.findByCode(HOTEL_CODE) ?: hotels.save(Hotel(code = HOTEL_CODE, name = "Hotel OpAI Demo"))
        REQUIRED_PERMISSIONS.forEach { code ->
            if (permissions.findByCode(code) == null) permissions.save(Permission(code = code, name = code.replace('_', ' ').lowercase().replaceFirstChar(Char::uppercase)))
        }
        val allPermissionIds = permissions.findAll().associate { it.code to it.id }
        val missingPermissions = REQUIRED_PERMISSIONS - allPermissionIds.keys
        require(missingPermissions.isEmpty()) { "MVP permission migrations must run before DEMO bootstrap; missing permission codes: ${missingPermissions.sorted().joinToString()}" }

        val departmentByCode = listOf("MANAGEMENT", "HOUSEKEEPING", "MAINTENANCE", "FRONT_OFFICE", "GUEST_RELATIONS")
            .associateWith { ensureDepartment(hotel.id, it) }
        val skillByCode = listOf("ROOM_CLEANING", "HOUSEKEEPING_INSPECTION", "MINIBAR", "HVAC_REPAIR", "ELECTRICAL", "PLUMBING", "GUEST_RECOVERY")
            .associateWith { ensureSkill(hotel.id, it) }

        val staff = DEMO_USERS.map { definition ->
            val role = ensureRole(hotel.id, definition, allPermissionIds)
            ensureUserAndEmployee(hotel.id, definition, role.id, departmentByCode.getValue(definition.department),
                definition.skills.map(skillByCode::getValue).map(Skill::id).toSet())
        }
        staff.forEach { ensureActiveShift(hotel.id, it.id) }
        seedDatasetOnce(hotel.id)
    }

    private fun ensureDepartment(hotelId: UUID, code: String) =
        departments.findByHotelIdAndCode(hotelId, code)
            ?: departments.save(Department(hotelId = hotelId, code = code, name = code.replace('_', ' ').lowercase().replaceFirstChar(Char::uppercase)))

    private fun ensureSkill(hotelId: UUID, code: String) =
        skills.findByHotelIdAndCode(hotelId, code)
            ?: skills.save(Skill(hotelId = hotelId, code = code, name = code.replace('_', ' ').lowercase().replaceFirstChar(Char::uppercase)))

    private fun ensureRole(hotelId: UUID, definition: DemoUser, permissionIds: Map<String, UUID>): Role {
        val desiredIds = definition.permissions.map(permissionIds::getValue).toSet()
        val existing = roles.findByHotelIdAndCode(hotelId, definition.roleCode)
        return when {
            existing == null -> roles.save(Role(hotelId = hotelId, code = definition.roleCode, name = definition.displayName, permissionIds = desiredIds))
            existing.permissionIds != desiredIds -> roles.save(existing.copy(permissionIds = desiredIds))
            else -> existing
        }
    }

    private fun ensureUserAndEmployee(hotelId: UUID, definition: DemoUser, roleId: UUID, department: Department, skillIds: Set<UUID>): Employee {
        employees.findByHotelIdAndEmployeeNumber(hotelId, definition.employeeNumber)?.let { return it }
        val password = properties.passwordFor(definition.roleCode)
        val employeeId = UuidV7Generator.generate()
        val user = users.findByHotelIdAndEmail(hotelId, definition.email) ?: users.save(User(
            hotelId = hotelId, employeeId = employeeId, email = EmailAddress.of(definition.email),
            displayName = definition.displayName, passwordHash = passwordHasher.hash(password), roleIds = setOf(roleId), status = UserStatus.ACTIVE
        ))
        return employees.save(Employee(
            id = employeeId, hotelId = hotelId, userId = user.id, employeeNumber = definition.employeeNumber,
            displayName = definition.displayName, departmentId = department.id, roleIds = setOf(roleId), skillIds = skillIds,
            primaryRoleCode = definition.roleCode, homeArea = definition.homeArea, languages = setOf("en", "tr"),
            operationalStatus = EmployeeOperationalStatus.AVAILABLE
        ))
    }

    private fun ensureActiveShift(hotelId: UUID, employeeId: UUID) {
        val now = Instant.now()
        jdbc.update("""insert into workforce_shift(id,hotel_id,employee_id,planned_start,planned_end,actual_start,status,created_at,updated_at)
            select :id,:hotel,:employee,:start,:end,:start,'WORKING',:now,:now where not exists(
              select 1 from workforce_shift where hotel_id=:hotel and employee_id=:employee and status in ('STARTED','WORKING')
                and coalesce(actual_end,planned_end)>:now)""",
            mapOf("id" to UuidV7Generator.generate(now), "hotel" to hotelId, "employee" to employeeId,
                "start" to Timestamp.from(now.minus(1, ChronoUnit.HOURS)), "end" to Timestamp.from(now.plus(12, ChronoUnit.HOURS)), "now" to Timestamp.from(now)))
    }

    private fun seedDatasetOnce(hotelId: UUID) {
        val exists = jdbc.queryForObject("select count(*) from demo_bootstrap_marker where hotel_id=:hotel and dataset_version=:version",
            mapOf("hotel" to hotelId, "version" to DATASET_VERSION), Long::class.java) ?: 0
        if (exists > 0) return
        val now = Instant.now()
        listOf(
            CreateTaskCommand(hotelId, TaskIntentType.HOUSEKEEPING, TaskSource.IMPORT, "Departure cleaning", "Checkout room 302 requires departure cleaning", "302", TaskPriority.HIGH, now.plus(2, ChronoUnit.HOURS)),
            CreateTaskCommand(hotelId, TaskIntentType.HOUSEKEEPING, TaskSource.IMPORT, "Stayover cleaning", "Stayover room 204 requires service", "204", TaskPriority.MEDIUM, now.plus(4, ChronoUnit.HOURS)),
            CreateTaskCommand(hotelId, TaskIntentType.MAINTENANCE, TaskSource.IMPORT, "HVAC maintenance", "Room 302 air conditioning is not working", "302", TaskPriority.HIGH, now.plus(1, ChronoUnit.HOURS)),
            CreateTaskCommand(hotelId, TaskIntentType.GUEST_REQUEST, TaskSource.IMPORT, "Guest requests towels", "Room 101 requests two towels", "101", TaskPriority.MEDIUM, now.plus(30, ChronoUnit.MINUTES))
        ).forEach(tasks::createTask)
        seedInventory(hotelId, now)
        jdbc.update("insert into demo_bootstrap_marker(hotel_id,dataset_version,applied_at) values(:hotel,:version,:now)",
            mapOf("hotel" to hotelId, "version" to DATASET_VERSION, "now" to Timestamp.from(now)))
    }

    private fun seedInventory(hotelId: UUID, now: Instant) {
        val categoryId = UuidV7Generator.generate(now)
        val locationId = UuidV7Generator.generate(now)
        jdbc.update("""insert into inventory_category(id,hotel_id,code,name) values(:id,:hotel,'MINIBAR','Minibar')
            on conflict(hotel_id,code) do nothing""", mapOf("id" to categoryId, "hotel" to hotelId))
        jdbc.update("""insert into inventory_location(id,hotel_id,code,name,location_type) values(:id,:hotel,'MAIN','Main Store','WAREHOUSE')
            on conflict(hotel_id,code) do nothing""", mapOf("id" to locationId, "hotel" to hotelId))
        val actualCategory = jdbc.queryForObject("select id from inventory_category where hotel_id=:hotel and code='MINIBAR'", mapOf("hotel" to hotelId), UUID::class.java)!!
        listOf("WATER" to "Water", "COLA" to "Cola", "CHOCOLATE" to "Chocolate").forEach { (code, name) ->
            jdbc.update("""insert into inventory_item(id,hotel_id,category_id,code,name,unit,unit_price,created_at,updated_at)
                values(:id,:hotel,:category,:code,:name,'EACH',3.50,:now,:now) on conflict(hotel_id,code) do nothing""",
                mapOf("id" to UuidV7Generator.generate(), "hotel" to hotelId, "category" to actualCategory, "code" to code, "name" to name, "now" to Timestamp.from(now)))
        }
    }

    private data class DemoUser(
        val roleCode: String, val email: String, val employeeNumber: String, val displayName: String,
        val department: String, val skills: Set<String>, val homeArea: String?, val permissions: Set<String>
    )

    companion object {
        const val HOTEL_CODE = "hotel-opai-demo"
        const val DATASET_VERSION = "mvp-demo-v1"
        private val FIELD = setOf(PermissionCodes.AUTH_LOGIN, PermissionCodes.AUTH_VIEW, PermissionCodes.TASK_READ,
            PermissionCodes.TASK_START, PermissionCodes.TASK_PAUSE, PermissionCodes.TASK_RESUME, PermissionCodes.TASK_COMPLETE,
            PermissionCodes.TASK_ATTACHMENT_READ, PermissionCodes.ASSISTANT_USE, PermissionCodes.ASSISTANT_CONFIRM_TASK,
            PermissionCodes.ASSISTANT_ATTACHMENT_REGISTER, PermissionCodes.ASSISTANT_VISION_IMPORT,
            PermissionCodes.NOTIFICATION_READ, PermissionCodes.NOTIFICATION_MARK_READ)
        private val REQUIRED_PERMISSIONS = PermissionCodes::class.java.declaredFields.filter { it.type == String::class.java }.map { it.get(null) as String }.toSet()
        private val DEMO_USERS = listOf(
            DemoUser("GM", "gm@demo.hotelopai.app", "DEMO-GM", "General Manager", "MANAGEMENT", emptySet(), null,
                REQUIRED_PERMISSIONS - PermissionCodes.DEV_PMS_ACCESS),
            DemoUser("HOUSEKEEPING_SUPERVISOR", "housekeeping.supervisor@demo.hotelopai.app", "DEMO-HKS", "Housekeeping Supervisor", "HOUSEKEEPING", setOf("ROOM_CLEANING", "HOUSEKEEPING_INSPECTION"), "3",
                FIELD + setOf(PermissionCodes.TASK_ASSIGN, PermissionCodes.HOUSEKEEPING_OPERATIONS, PermissionCodes.HOUSEKEEPING_INSPECTION, PermissionCodes.SHIFT_OPERATIONS)),
            DemoUser("HOUSEKEEPER", "housekeeper@demo.hotelopai.app", "DEMO-HK", "Housekeeper", "HOUSEKEEPING", setOf("ROOM_CLEANING", "MINIBAR"), "3",
                FIELD + setOf(PermissionCodes.HOUSEKEEPING_OPERATIONS, PermissionCodes.MINIBAR_OPERATIONS, PermissionCodes.SHIFT_OPERATIONS, PermissionCodes.GAMIFICATION_VIEW)),
            DemoUser("TECHNICIAN", "technician@demo.hotelopai.app", "DEMO-TECH", "HVAC Technician", "MAINTENANCE", setOf("HVAC_REPAIR", "ELECTRICAL", "PLUMBING"), "3", FIELD),
            DemoUser("RECEPTION", "reception@demo.hotelopai.app", "DEMO-FO", "Front Office", "FRONT_OFFICE", emptySet(), null,
                FIELD + setOf(PermissionCodes.MINIBAR_OPERATIONS, PermissionCodes.DAMAGE_REVIEW, PermissionCodes.SERVICE_RECOVERY_OPERATIONS, PermissionCodes.GUEST_MESSAGING_OPERATIONS)),
            DemoUser("GUEST_RELATIONS", "guest.relations@demo.hotelopai.app", "DEMO-GR", "Guest Relations", "GUEST_RELATIONS", setOf("GUEST_RECOVERY"), null,
                FIELD + setOf(PermissionCodes.SERVICE_RECOVERY_OPERATIONS, PermissionCodes.GUEST_MESSAGING_OPERATIONS, PermissionCodes.DASHBOARD_READ)),
            DemoUser("ADMIN", "reviewer.admin@demo.hotelopai.app", "DEMO-ADMIN", "Admin Reviewer", "MANAGEMENT", emptySet(), null, REQUIRED_PERMISSIONS)
        )
    }
}
