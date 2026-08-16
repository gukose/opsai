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
    val defaultPassword: String = "",
    val gmPassword: String = "",
    val housekeepingSupervisorPassword: String = "",
    val housekeeperPassword: String = "",
    val technicianPassword: String = "",
    val receptionPassword: String = "",
    val guestRelationsPassword: String = "",
    val adminPassword: String = ""
) {
    fun passwordFor(code: String): String = (defaultPassword.takeIf { it.isNotBlank() } ?: when (code) {
        "GM" -> gmPassword
        "HOUSEKEEPING_MANAGER", "HOUSEKEEPING_SUPERVISOR", "TECHNICAL_MANAGER" -> housekeepingSupervisorPassword
        "HOUSEKEEPER", "PUBLIC_AREA_ATTENDANT", "LAUNDRY_COORDINATOR" -> housekeeperPassword
        "CHIEF_ENGINEER", "TECHNICIAN" -> technicianPassword
        "RECEPTIONIST", "FRONT_OFFICE_MANAGER", "FRONT_OFFICE_SUPERVISOR", "NIGHT_AUDITOR", "BELL_SERVICE" -> receptionPassword
        "GUEST_RELATIONS" -> guestRelationsPassword
        else -> adminPassword
    }).also { require(it.length >= 12) { "Every DEMO user password must be supplied through an environment variable and contain at least 12 characters" } }
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

        val departmentByCode = listOf("MANAGEMENT", "SALES_MARKETING", "FINANCE", "HOUSEKEEPING", "MAINTENANCE", "FRONT_OFFICE", "GUEST_RELATIONS", "FOOD_BEVERAGE", "SECURITY", "IT_SYSTEMS")
            .associateWith { ensureDepartment(hotel.id, it) }
        val skillByCode = listOf("ROOM_CLEANING", "HOUSEKEEPING_INSPECTION", "MINIBAR", "HVAC_REPAIR", "ELECTRICAL", "PLUMBING", "CARPENTRY", "GUEST_RECOVERY", "DEEP_CLEANING", "AMENITIES", "IT_NETWORK", "QUALITY_INSPECTION")
            .associateWith { ensureSkill(hotel.id, it) }

        val staff = CANONICAL_EMPLOYEES.map { definition ->
            val role = ensureRole(hotel.id, definition, allPermissionIds)
            ensureUserAndEmployee(hotel.id, definition, role.id, departmentByCode.getValue(definition.department),
                definition.skills.map(skillByCode::getValue).map(Skill::id).toSet())
        }
        reconcileLegacyDemoAccounts(hotel.id)
        ensureLegacyAliases(hotel.id, staff)
        val employeeByNumber = staff.associateBy { it.employeeNumber }
        CANONICAL_EMPLOYEES.forEach { definition ->
            val supervisor = definition.supervisorEmployeeNumber?.let(employeeByNumber::getValue)?.id
            val current = employeeByNumber.getValue(definition.employeeNumber)
            if (current.supervisorEmployeeId != supervisor) employees.save(current.copy(supervisorEmployeeId = supervisor))
        }
        staff.forEach { ensureActiveShift(hotel.id, it.id) }
        // Master operational data is idempotent and is also repaired for an
        // already bootstrapped demo (the marker only governs sample tasks).
        seedInventory(hotel.id, Instant.now())
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
        val existing = roles.findByHotelIdAndCode(hotelId, definition.appRole)
        return when {
            existing == null -> roles.save(Role(hotelId = hotelId, code = definition.appRole, name = definition.sourceRole, permissionIds = desiredIds))
            existing.permissionIds != desiredIds -> roles.save(existing.copy(permissionIds = desiredIds))
            else -> existing
        }
    }

    private fun ensureUserAndEmployee(hotelId: UUID, definition: DemoUser, roleId: UUID, department: Department, skillIds: Set<UUID>): Employee {
        val existingEmployee = employees.findByHotelIdAndEmployeeNumber(hotelId, definition.employeeNumber)
        val employeeId = existingEmployee?.id ?: UuidV7Generator.generate()
        val password = properties.passwordFor(definition.appRole)
        val email = emailFor(definition.displayName)
        val existingUser = users.findByHotelIdAndEmail(hotelId, email)
        val user = if (existingUser == null) users.save(User(
            hotelId = hotelId, employeeId = employeeId, email = EmailAddress.of(email),
            displayName = definition.displayName, passwordHash = passwordHasher.hash(password), roleIds = setOf(roleId), status = UserStatus.ACTIVE
        )) else users.save(existingUser.copy(
            employeeId = employeeId, displayName = definition.displayName, roleIds = setOf(roleId), status = UserStatus.ACTIVE
        ))
        return employees.save((existingEmployee ?: Employee(
            id = employeeId, hotelId = hotelId, employeeNumber = definition.employeeNumber, displayName = definition.displayName
        )).copy(
            userId = user.id, displayName = definition.displayName, departmentId = department.id,
            roleIds = setOf(roleId), skillIds = skillIds, primaryRoleCode = definition.appRole,
            homeArea = definition.homeArea, languages = definition.languages,
            status = com.hotelopai.employee.domain.EmployeeStatus.ACTIVE,
            operationalStatus = EmployeeOperationalStatus.AVAILABLE
        ))
    }

    private fun emailFor(displayName: String): String = displayName
        .lowercase()
        .replace("ğ", "g").replace("ü", "u").replace("ş", "s").replace("ı", "i")
        .replace("ö", "o").replace("ç", "c").replace("ä", "a").replace("é", "e")
        .replace("[^a-z0-9]+".toRegex(), ".")
        .trim('.') + "@demo.hotelopai.app"

    private fun reconcileLegacyDemoAccounts(hotelId: UUID) {
        val legacyEmails = listOf(
            "murat.technician@demo.hotelopai.app", "burak.technician@demo.hotelopai.app",
            "ayse.housekeeper@demo.hotelopai.app", "burcu.housekeeper@demo.hotelopai.app",
            "admin@hotelopai.local", "technician@hotelopai.local", "housekeeper@hotelopai.local"
        )
        jdbc.update("update app_user set status='DISABLED' where hotel_id=:hotel and email in (:emails)", mapOf("hotel" to hotelId, "emails" to legacyEmails))
        jdbc.update("update employee set status='INACTIVE' where hotel_id=:hotel and employee_number like 'DEMO-%'", mapOf("hotel" to hotelId))
        jdbc.update("update employee set status='INACTIVE' where hotel_id=:hotel and employee_number in ('EMP-ADMIN','EMP-TECH-001','EMP-HK-001')", mapOf("hotel" to hotelId))
    }

    private fun ensureLegacyAliases(hotelId: UUID, staff: List<Employee>) {
        val employeeByNumber = staff.associateBy { it.employeeNumber }
        val aliases = listOf(
            "reviewer.admin@demo.hotelopai.app" to "EMP0001",
            "gm@demo.hotelopai.app" to "EMP0001",
            "technician@demo.hotelopai.app" to "EMP0024",
            "housekeeper@demo.hotelopai.app" to "EMP0014",
            "housekeeping.supervisor@demo.hotelopai.app" to "EMP0013",
            "reception@demo.hotelopai.app" to "EMP0006",
            "guest.relations@demo.hotelopai.app" to "EMP0011"
        )
        aliases.forEach { (email, employeeNumber) ->
            val employee = employeeByNumber.getValue(employeeNumber)
            val roleId = employee.roleIds.firstOrNull()
                ?: error("Canonical employee $employeeNumber has no role")
            val existing = users.findByHotelIdAndEmail(hotelId, email)
            val password = properties.passwordFor(employee.primaryRoleCode ?: "")
            val alias = existing?.copy(
                employeeId = employee.id,
                displayName = employee.displayName,
                passwordHash = passwordHasher.hash(password),
                roleIds = setOf(roleId),
                status = UserStatus.ACTIVE
            ) ?: User(
                hotelId = hotelId,
                employeeId = employee.id,
                email = EmailAddress.of(email),
                displayName = employee.displayName,
                passwordHash = passwordHasher.hash(password),
                roleIds = setOf(roleId),
                status = UserStatus.ACTIVE
            )
            users.save(alias)
        }
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
        jdbc.update("insert into demo_bootstrap_marker(hotel_id,dataset_version,applied_at) values(:hotel,:version,:now)",
            mapOf("hotel" to hotelId, "version" to DATASET_VERSION, "now" to Timestamp.from(now)))
    }

    private fun seedInventory(hotelId: UUID, now: Instant) {
        val categories = listOf(
            "MINIBAR" to "Minibar",
            "HOUSEKEEPING" to "Housekeeping Consumables",
            "LINEN" to "Linen & Laundry",
            "TECHNICAL" to "Technical Maintenance Stock"
        )
        categories.forEach { (code, name) ->
            jdbc.update("""insert into inventory_category(id,hotel_id,code,name) values(:id,:hotel,:code,:name)
                on conflict(hotel_id,code) do update set name=excluded.name""",
                mapOf("id" to UuidV7Generator.generate(now), "hotel" to hotelId, "code" to code, "name" to name))
        }
        val locations = listOf(
            "MAIN" to ("Main Store" to "WAREHOUSE"),
            "HOUSEKEEPING_STORE" to ("Housekeeping Store" to "HOUSEKEEPING"),
            "LAUNDRY" to ("Laundry / Linen Store" to "LAUNDRY"),
            "TECHNICAL_STORE" to ("Technical Store" to "TECHNICAL")
        )
        locations.forEach { (code, descriptor) ->
            jdbc.update("""insert into inventory_location(id,hotel_id,code,name,location_type) values(:id,:hotel,:code,:name,:type)
                on conflict(hotel_id,code) do update set name=excluded.name,location_type=excluded.location_type""",
                mapOf("id" to UuidV7Generator.generate(now), "hotel" to hotelId, "code" to code, "name" to descriptor.first, "type" to descriptor.second))
        }
        val categoryIds = jdbc.query("select code,id from inventory_category where hotel_id=:hotel", mapOf("hotel" to hotelId)) { rs, _ -> rs.getString(1) to rs.getObject(2, UUID::class.java) }.toMap()
        val mainLocation = jdbc.queryForObject("select id from inventory_location where hotel_id=:hotel and code='MAIN'", mapOf("hotel" to hotelId), UUID::class.java)!!
        data class Stock(val category: String, val code: String, val name: String, val unit: String, val minimum: String, val quantity: String, val price: String)
        val stock = listOf(
            Stock("MINIBAR", "WATER", "Still Water", "BOTTLE", "20", "120", "3.50"),
            Stock("MINIBAR", "SPARKLING_WATER", "Sparkling Water", "BOTTLE", "10", "60", "4.00"),
            Stock("MINIBAR", "COLA", "Cola", "BOTTLE", "10", "60", "5.00"),
            Stock("MINIBAR", "JUICE", "Fruit Juice", "BOTTLE", "10", "60", "5.00"),
            Stock("MINIBAR", "SNACK_CHOCOLATE", "Chocolate Snack", "EACH", "10", "60", "6.00"),
            Stock("MINIBAR", "SNACK_NUTS", "Mixed Nuts", "PACK", "10", "60", "7.00"),
            Stock("HOUSEKEEPING", "BATH_TOWEL", "Bath Towel", "EACH", "40", "240", "12.00"),
            Stock("HOUSEKEEPING", "HAND_TOWEL", "Hand Towel", "EACH", "40", "240", "8.00"),
            Stock("HOUSEKEEPING", "FACE_TOWEL", "Face Towel", "EACH", "40", "240", "6.00"),
            Stock("HOUSEKEEPING", "BATH_MAT", "Bath Mat", "EACH", "20", "120", "10.00"),
            Stock("HOUSEKEEPING", "TOILET_PAPER", "Toilet Paper", "ROLL", "100", "600", "1.50"),
            Stock("HOUSEKEEPING", "TISSUES", "Tissues", "PACK", "50", "300", "2.00"),
            Stock("HOUSEKEEPING", "SOAP", "Soap", "EACH", "100", "600", "1.00"),
            Stock("HOUSEKEEPING", "SHAMPOO", "Shampoo", "BOTTLE", "100", "600", "2.00"),
            Stock("HOUSEKEEPING", "SHOWER_GEL", "Shower Gel", "BOTTLE", "100", "600", "2.00"),
            Stock("HOUSEKEEPING", "TRASH_BAGS", "Trash Bags", "PACK", "20", "100", "5.00"),
            Stock("HOUSEKEEPING", "CLEANING_CLOTHS", "Cleaning Cloths", "PACK", "10", "60", "8.00"),
            Stock("HOUSEKEEPING", "CLEANING_CHEMICAL", "Cleaning Chemical", "LITER", "20", "100", "9.00"),
            Stock("LINEN", "BED_SHEET", "Bed Sheet", "EACH", "100", "600", "15.00"),
            Stock("LINEN", "PILLOWCASE", "Pillowcase", "EACH", "100", "600", "5.00"),
            Stock("LINEN", "DUVET_COVER", "Duvet Cover", "EACH", "50", "300", "20.00"),
            Stock("LINEN", "PILLOW", "Pillow", "EACH", "30", "160", "18.00"),
            Stock("TECHNICAL", "LIGHT_BULB", "LED Light Bulb", "EACH", "20", "100", "4.00"),
            Stock("TECHNICAL", "BATTERY", "AA Battery", "PACK", "10", "50", "6.00"),
            Stock("TECHNICAL", "PLUMBING_SEAL", "Plumbing Seal", "EACH", "20", "100", "2.00"),
            Stock("TECHNICAL", "FAUCET_COMPONENT", "Faucet Component", "SET", "5", "25", "12.00"),
            Stock("TECHNICAL", "SHOWER_COMPONENT", "Shower Component", "SET", "5", "25", "12.00"),
            Stock("TECHNICAL", "HVAC_FILTER", "HVAC Filter", "EACH", "20", "100", "14.00"),
            Stock("TECHNICAL", "DOOR_LOCK_COMPONENT", "Door / Lock Component", "SET", "5", "25", "20.00"),
            Stock("TECHNICAL", "SCREWS_FITTINGS", "Screws and Fittings", "PACK", "10", "50", "8.00")
        )
        stock.forEach { item ->
            val categoryId = categoryIds.getValue(item.category)
            jdbc.update("""insert into inventory_item(id,hotel_id,category_id,code,name,unit,unit_price,minimum_stock,created_at,updated_at)
                values(:id,:hotel,:category,:code,:name,:unit,:price,:minimum,:now,:now)
                on conflict(hotel_id,code) do update set category_id=excluded.category_id,name=excluded.name,unit=excluded.unit,unit_price=excluded.unit_price,minimum_stock=excluded.minimum_stock,updated_at=excluded.updated_at""",
                mapOf("id" to UuidV7Generator.generate(now), "hotel" to hotelId, "category" to categoryId, "code" to item.code, "name" to item.name, "unit" to item.unit, "price" to item.price.toBigDecimal(), "minimum" to item.minimum.toBigDecimal(), "now" to Timestamp.from(now)))
            val itemId = jdbc.queryForObject("select id from inventory_item where hotel_id=:hotel and code=:code", mapOf("hotel" to hotelId, "code" to item.code), UUID::class.java)!!
            jdbc.update("""insert into inventory_balance(hotel_id,item_id,location_id,quantity,version,updated_at)
                values(:hotel,:item,:location,:quantity,0,:now)
                on conflict(hotel_id,item_id,location_id) do update set quantity=excluded.quantity,updated_at=excluded.updated_at""",
                mapOf("hotel" to hotelId, "item" to itemId, "location" to mainLocation, "quantity" to item.quantity.toBigDecimal(), "now" to Timestamp.from(now)))
        }
    }

    private data class DemoUser(
        val employeeNumber: String, val displayName: String, val sourceRole: String, val appRole: String,
        val department: String, val skills: Set<String>, val homeArea: String?, val languages: Set<String>,
        val shiftCode: String, val permissions: Set<String>, val supervisorEmployeeNumber: String? = null
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
        private val SUPERVISOR = FIELD + setOf(PermissionCodes.TASK_ASSIGN, PermissionCodes.SHIFT_OPERATIONS, PermissionCodes.DASHBOARD_READ)
        private val HOUSEKEEPING = FIELD + setOf(PermissionCodes.HOUSEKEEPING_OPERATIONS, PermissionCodes.SHIFT_OPERATIONS)
        private val CANONICAL_EMPLOYEES = listOf(
            DemoUser("EMP0001", "Kemal Yılmaz", "General Manager", "GM", "MANAGEMENT", emptySet(), null, setOf("Turkish", "English"), "N", REQUIRED_PERMISSIONS - PermissionCodes.DEV_PMS_ACCESS),
            DemoUser("EMP0002", "Selin Kaya", "Sales & Marketing Manager", "SALES_MANAGER", "SALES_MARKETING", setOf("GUEST_RECOVERY"), null, setOf("Turkish", "English"), "N", FIELD + setOf(PermissionCodes.DASHBOARD_READ, PermissionCodes.REPORT_READ), "EMP0001"),
            DemoUser("EMP0003", "Ahmet Demir", "Finance Manager", "FINANCE_MANAGER", "FINANCE", emptySet(), null, setOf("Turkish", "English"), "N", FIELD + setOf(PermissionCodes.DASHBOARD_READ, PermissionCodes.REPORT_READ), "EMP0001"),
            DemoUser("EMP0004", "Murat Yıldız", "Front Office Manager", "FRONT_OFFICE_MANAGER", "FRONT_OFFICE", setOf("GUEST_RECOVERY"), null, setOf("Turkish", "English"), "N", SUPERVISOR, "EMP0001"),
            DemoUser("EMP0005", "Burak Çetin", "Front Office Supervisor", "FRONT_OFFICE_SUPERVISOR", "FRONT_OFFICE", setOf("GUEST_RECOVERY"), null, setOf("Turkish", "English"), "V1", SUPERVISOR, "EMP0004"),
            DemoUser("EMP0006", "Eser Aydın", "Receptionist", "RECEPTIONIST", "FRONT_OFFICE", emptySet(), null, setOf("Turkish", "English"), "V1", FIELD, "EMP0005"),
            DemoUser("EMP0007", "Canan Öz", "Receptionist", "RECEPTIONIST", "FRONT_OFFICE", emptySet(), null, setOf("Turkish", "English"), "V2", FIELD, "EMP0005"),
            DemoUser("EMP0008", "Aibek Tursunov", "Night Auditor", "NIGHT_AUDITOR", "FRONT_OFFICE", emptySet(), null, setOf("Kyrgyz", "Russian", "English"), "V3", FIELD, "EMP0004"),
            DemoUser("EMP0009", "Mert Can", "Bell Service", "BELL_SERVICE", "FRONT_OFFICE", emptySet(), null, setOf("Turkish", "English"), "V1", FIELD, "EMP0004"),
            DemoUser("EMP0010", "Azamat Nurpeisov", "Bell Service", "BELL_SERVICE", "FRONT_OFFICE", emptySet(), null, setOf("Kazakh", "Russian", "English"), "V2", FIELD, "EMP0004"),
            DemoUser("EMP0011", "Anna Müller", "Guest Relations Officer", "GUEST_RELATIONS", "GUEST_RELATIONS", setOf("GUEST_RECOVERY"), null, setOf("German", "English", "Turkish"), "N", FIELD + setOf(PermissionCodes.SERVICE_RECOVERY_OPERATIONS, PermissionCodes.GUEST_MESSAGING_OPERATIONS), "EMP0001"),
            DemoUser("EMP0012", "Fatma Şahin", "Executive Housekeeper", "HOUSEKEEPING_MANAGER", "HOUSEKEEPING", setOf("ROOM_CLEANING", "HOUSEKEEPING_INSPECTION", "MINIBAR", "DEEP_CLEANING"), null, setOf("Turkish", "English"), "N", SUPERVISOR + setOf(PermissionCodes.HOUSEKEEPING_OPERATIONS, PermissionCodes.HOUSEKEEPING_INSPECTION), "EMP0001"),
            DemoUser("EMP0013", "Ayşe Yılmaz", "Floor Supervisor", "HOUSEKEEPING_SUPERVISOR", "HOUSEKEEPING", setOf("ROOM_CLEANING", "HOUSEKEEPING_INSPECTION", "QUALITY_INSPECTION"), null, setOf("Turkish", "English"), "V1", SUPERVISOR + setOf(PermissionCodes.HOUSEKEEPING_OPERATIONS, PermissionCodes.HOUSEKEEPING_INSPECTION), "EMP0012"),
            DemoUser("EMP0014", "Zeynep Çelik", "Housekeeper", "HOUSEKEEPER", "HOUSEKEEPING", setOf("ROOM_CLEANING", "AMENITIES"), "1", setOf("Turkish"), "V1", HOUSEKEEPING, "EMP0013"),
            DemoUser("EMP0015", "Dilnoza Karimova", "Housekeeper", "HOUSEKEEPER", "HOUSEKEEPING", setOf("ROOM_CLEANING", "AMENITIES"), "2", setOf("Uzbek", "Russian", "English"), "V1", HOUSEKEEPING, "EMP0013"),
            DemoUser("EMP0016", "Aizada Ismailova", "Housekeeper", "HOUSEKEEPER", "HOUSEKEEPING", setOf("ROOM_CLEANING", "MINIBAR"), "3", setOf("Kyrgyz", "Russian", "English"), "V1", HOUSEKEEPING, "EMP0013"),
            DemoUser("EMP0017", "Aigerim Sarsenova", "Housekeeper", "HOUSEKEEPER", "HOUSEKEEPING", setOf("ROOM_CLEANING", "AMENITIES"), "4", setOf("Kazakh", "Russian", "English"), "V1", HOUSEKEEPING, "EMP0013"),
            DemoUser("EMP0018", "Olena Kovalenko", "Housekeeper", "HOUSEKEEPER", "HOUSEKEEPING", setOf("ROOM_CLEANING", "MINIBAR"), "5", setOf("Ukrainian", "Russian", "English"), "V1", HOUSEKEEPING, "EMP0013"),
            DemoUser("EMP0019", "Yasemin Ak", "Evening Housekeeper", "HOUSEKEEPER", "HOUSEKEEPING", setOf("ROOM_CLEANING", "DEEP_CLEANING"), null, setOf("Turkish"), "V2", HOUSEKEEPING, "EMP0013"),
            DemoUser("EMP0020", "Hülya Avcı", "Public Area Attendant", "PUBLIC_AREA_ATTENDANT", "HOUSEKEEPING", setOf("ROOM_CLEANING"), null, setOf("Turkish"), "V1", HOUSEKEEPING, "EMP0012"),
            DemoUser("EMP0021", "Nigora Rakhimova", "Laundry & Linen Coordinator", "LAUNDRY_COORDINATOR", "HOUSEKEEPING", setOf("AMENITIES"), null, setOf("Uzbek", "Russian", "English"), "V1", HOUSEKEEPING, "EMP0012"),
            DemoUser("EMP0022", "Mustafa Tekin", "Chief Engineer", "TECHNICAL_MANAGER", "MAINTENANCE", setOf("ELECTRICAL", "PLUMBING", "HVAC_REPAIR"), null, setOf("Turkish", "English"), "N", SUPERVISOR, "EMP0001"),
            DemoUser("EMP0023", "Ali Yılmaz", "Electrical Technician", "TECHNICIAN", "MAINTENANCE", setOf("ELECTRICAL"), null, setOf("Turkish", "English"), "V1", FIELD, "EMP0022"),
            DemoUser("EMP0024", "Bekzod Abdullayev", "HVAC & Plumbing Technician", "TECHNICIAN", "MAINTENANCE", setOf("HVAC_REPAIR", "PLUMBING"), null, setOf("Uzbek", "Russian", "English"), "V2", FIELD, "EMP0022"),
            DemoUser("EMP0025", "James Wilson", "IT & Network Technician", "IT_TECHNICIAN", "IT_SYSTEMS", setOf("IT_NETWORK"), null, setOf("English"), "N", FIELD, "EMP0035"),
            DemoUser("EMP0026", "Serkan Arslan", "F&B Manager", "FB_MANAGER", "FOOD_BEVERAGE", emptySet(), null, setOf("Turkish", "English"), "N", SUPERVISOR, "EMP0001"),
            DemoUser("EMP0027", "Okan Köse", "Breakfast Chef", "CHEF", "FOOD_BEVERAGE", emptySet(), null, setOf("Turkish"), "V1", FIELD, "EMP0026"),
            DemoUser("EMP0028", "Gamze Güneş", "Restaurant Waiter", "WAITER", "FOOD_BEVERAGE", emptySet(), null, setOf("Turkish", "English"), "V1", FIELD, "EMP0026"),
            DemoUser("EMP0029", "Engin Demir", "Barista", "BARISTA", "FOOD_BEVERAGE", emptySet(), null, setOf("Turkish", "English"), "V2", FIELD, "EMP0026"),
            DemoUser("EMP0030", "Madina Ergasheva", "Room Service Attendant", "ROOM_SERVICE", "FOOD_BEVERAGE", emptySet(), null, setOf("Uzbek", "Russian", "English"), "V2", FIELD, "EMP0026"),
            DemoUser("EMP0031", "Ivan Petrov", "Steward", "STEWARD", "FOOD_BEVERAGE", emptySet(), null, setOf("Russian", "English"), "V1", FIELD, "EMP0026"),
            DemoUser("EMP0032", "Hasan Karaca", "Security Supervisor", "SECURITY_SUPERVISOR", "SECURITY", emptySet(), null, setOf("Turkish", "English"), "N", SUPERVISOR, "EMP0001"),
            DemoUser("EMP0033", "Rustam Sodikov", "Security Officer", "SECURITY_OFFICER", "SECURITY", emptySet(), null, setOf("Tajik", "Russian", "English"), "V1", FIELD, "EMP0032"),
            DemoUser("EMP0034", "Sergey Ivanov", "Security Officer", "SECURITY_OFFICER", "SECURITY", emptySet(), null, setOf("Russian", "English"), "V2", FIELD, "EMP0032"),
            DemoUser("EMP0035", "Elif Korkmaz", "Digital Systems Coordinator", "IT_SYSTEM_ADMIN", "IT_SYSTEMS", setOf("IT_NETWORK"), null, setOf("Turkish", "English"), "N", FIELD + setOf(PermissionCodes.DASHBOARD_READ), "EMP0001")
        )
    }
}
