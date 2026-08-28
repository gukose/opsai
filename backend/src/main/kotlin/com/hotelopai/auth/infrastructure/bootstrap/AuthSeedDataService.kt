package com.hotelopai.auth.infrastructure.bootstrap

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
import com.hotelopai.employee.domain.Skill
import com.hotelopai.hotel.application.HotelRepository
import com.hotelopai.hotel.domain.Hotel
import com.hotelopai.shared.security.PermissionCodes
import com.hotelopai.shared.kernel.UuidV7Generator
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import java.sql.Timestamp
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@Configuration
@Profile("local", "test")
class AuthSeedDataConfiguration {
    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
        prefix = "ops.ai.auth.seed",
        name = ["enabled"],
        havingValue = "true"
    )
    fun authSeedRunner(seedDataService: AuthSeedDataService): ApplicationRunner =
        ApplicationRunner { seedDataService.seed() }
}

@Service
@Profile("local", "test")
@Transactional
class AuthSeedDataService(
    private val hotelRepository: HotelRepository,
    private val permissionRepository: PermissionRepository,
    private val roleRepository: RoleRepository,
    private val userRepository: UserRepository,
    private val employeeRepository: EmployeeRepository,
    private val departmentRepository: DepartmentRepository,
    private val skillRepository: SkillRepository,
    private val passwordHasher: PasswordHasher,
    private val jdbc: NamedParameterJdbcTemplate
) {
    fun seed() {
        val hotel = ensureHotel()
        val permissions = ensurePermissions()
        val role = ensureAdminRole(hotel.id, permissions.map { it.id }.toSet())
        val department = ensureDepartment(hotel.id)
        val skill = ensureSkill(hotel.id)
        ensureAdminUser(hotel.id, role.id, department.id, skill.id)
        ensureOperationalDemoWorkforce(hotel.id, role.id)
        ensureHotelMemberships(hotel.id)
    }

    private fun ensureHotelMemberships(hotelId:UUID){jdbc.update("""insert into user_hotel_membership(id,user_id,hotel_id,department_id,active,created_at,updated_at)
        select gen_random_uuid(),u.id,u.hotel_id,e.department_id,true,now(),now() from app_user u left join employee e on e.id=u.employee_id where u.hotel_id=:hotel
        on conflict(user_id,hotel_id) do update set active=true""",mapOf("hotel" to hotelId));jdbc.update("""insert into user_hotel_role(id,membership_id,role_id,hotel_id,created_at)
        select gen_random_uuid(),m.id,ur.role_id,m.hotel_id,now() from user_hotel_membership m join user_role ur on ur.user_id=m.user_id join role r on r.id=ur.role_id and r.hotel_id=m.hotel_id where m.hotel_id=:hotel on conflict(membership_id,role_id) do nothing""",mapOf("hotel" to hotelId))}

    private fun ensureOperationalDemoWorkforce(hotelId: UUID, roleId: UUID) {
        val technicalDepartment = ensureOperationalDepartment(hotelId, "MAINTENANCE", "Technical")
        val housekeepingDepartment = ensureOperationalDepartment(hotelId, "HOUSEKEEPING", "Housekeeping")
        val hvacSkill = ensureOperationalSkill(hotelId, "HVAC_REPAIR", "HVAC Repair")
        val cleaningSkill = ensureOperationalSkill(hotelId, "ROOM_CLEANING", "Room Cleaning")
        val minibarSkill = ensureOperationalSkill(hotelId, "MINIBAR", "Minibar Operations")
        val technician = ensureOperationalEmployee(hotelId, roleId, technicalDepartment.id, setOf(hvacSkill.id),
            "EMP-TECH-001", "Demo HVAC Technician", "technician@hotelopai.local")
        val housekeeper = ensureOperationalEmployee(hotelId, roleId, housekeepingDepartment.id, setOf(cleaningSkill.id, minibarSkill.id),
            "EMP-HK-001", "Demo Housekeeper", "housekeeper@hotelopai.local")
        ensureActiveShift(hotelId, technician.id)
        ensureActiveShift(hotelId, housekeeper.id)
    }

    private fun ensureOperationalDepartment(hotelId: UUID, code: String, name: String): Department =
        departmentRepository.findByHotelIdAndCode(hotelId, code)
            ?: departmentRepository.save(Department(hotelId = hotelId, code = code, name = name))

    private fun ensureOperationalSkill(hotelId: UUID, code: String, name: String): Skill =
        skillRepository.findByHotelIdAndCode(hotelId, code)
            ?: skillRepository.save(Skill(hotelId = hotelId, code = code, name = name))

    private fun ensureOperationalEmployee(
        hotelId: UUID, roleId: UUID, departmentId: UUID, skillIds: Set<UUID>,
        employeeNumber: String, displayName: String, email: String
    ): Employee {
        employeeRepository.findByHotelIdAndEmployeeNumber(hotelId, employeeNumber)?.let { existing ->
            return if (existing.operationalStatus == com.hotelopai.employee.domain.EmployeeOperationalStatus.AVAILABLE) existing
            else employeeRepository.save(existing.updateOperationalStatus(com.hotelopai.employee.domain.EmployeeOperationalStatus.AVAILABLE))
        }
        val userId = UuidV7Generator.generate()
        val employeeId = UuidV7Generator.generate()
        val user = userRepository.save(User(id = userId, hotelId = hotelId, employeeId = employeeId,
            email = EmailAddress.of(email), displayName = displayName, passwordHash = passwordHasher.hash(ADMIN_PASSWORD),
            roleIds = setOf(roleId), status = UserStatus.ACTIVE))
        return employeeRepository.save(Employee(id = employeeId, hotelId = hotelId, userId = user.id,
            employeeNumber = employeeNumber, displayName = displayName, departmentId = departmentId,
            roleIds = setOf(roleId), skillIds = skillIds,
            operationalStatus = com.hotelopai.employee.domain.EmployeeOperationalStatus.AVAILABLE))
    }

    private fun ensureActiveShift(hotelId: UUID, employeeId: UUID) {
        val now = Instant.now()
        jdbc.update(
            """insert into workforce_shift(id,hotel_id,employee_id,planned_start,planned_end,actual_start,status,created_at,updated_at)
               select :id,:hotel,:employee,:start,:end,:start,'WORKING',:now,:now
               where not exists(select 1 from workforce_shift where hotel_id=:hotel and employee_id=:employee
                 and status in ('STARTED','WORKING') and coalesce(actual_end,planned_end)>:now)""",
            mapOf("id" to UuidV7Generator.generate(now), "hotel" to hotelId, "employee" to employeeId,
                "start" to Timestamp.from(now.minus(1, ChronoUnit.HOURS)), "end" to Timestamp.from(now.plus(12, ChronoUnit.HOURS)),
                "now" to Timestamp.from(now))
        )
    }

    private fun ensureHotel(): Hotel =
        hotelRepository.findByCode(HOTEL_CODE) ?: hotelRepository.save(
            Hotel(
                code = HOTEL_CODE,
                name = HOTEL_NAME
            )
        )

    private fun ensurePermissions(): List<Permission> =
        AUTH_PERMISSION_SEEDS.map { seed ->
            permissionRepository.findByCode(seed.code) ?: permissionRepository.save(
                Permission(
                    code = seed.code,
                    name = seed.name
                )
            )
        }

    private fun ensureAdminRole(hotelId: UUID, permissionIds: Set<UUID>): Role {
        val existing = roleRepository.findByHotelIdAndCode(hotelId, ADMIN_ROLE_CODE)
        val desired = Role(
            hotelId = hotelId,
            code = ADMIN_ROLE_CODE,
            name = ADMIN_ROLE_NAME,
            permissionIds = permissionIds
        )
        return if (existing == null) {
            roleRepository.save(desired)
        } else if (existing.permissionIds != permissionIds) {
            roleRepository.save(
                existing.copy(
                    permissionIds = permissionIds
                )
            )
        } else {
            existing
        }
    }

    private fun ensureDepartment(hotelId: UUID): Department {
        val existing = departmentRepository.findByHotelIdAndCode(hotelId, ADMIN_DEPARTMENT_CODE)
        return existing ?: departmentRepository.save(
            Department(
                hotelId = hotelId,
                code = ADMIN_DEPARTMENT_CODE,
                name = ADMIN_DEPARTMENT_NAME
            )
        )
    }

    private fun ensureSkill(hotelId: UUID): Skill {
        val existing = skillRepository.findByHotelIdAndCode(hotelId, ADMIN_SKILL_CODE)
        return existing ?: skillRepository.save(
            Skill(
                hotelId = hotelId,
                code = ADMIN_SKILL_CODE,
                name = ADMIN_SKILL_NAME
            )
        )
    }

    private fun ensureAdminUser(
        hotelId: UUID,
        roleId: UUID,
        departmentId: UUID,
        skillId: UUID
    ) {
        val existing = userRepository.findByHotelIdAndEmail(hotelId, ADMIN_EMAIL)
        val baseEmployee = employeeRepository.findByHotelIdAndEmployeeNumber(hotelId, ADMIN_EMPLOYEE_NUMBER)

        if (existing == null) {
            val userId = UuidV7Generator.generate()
            val employeeId = UuidV7Generator.generate()
            val passwordHash = passwordHasher.hash(ADMIN_PASSWORD)
            val user = userRepository.save(
                User(
                    id = userId,
                    hotelId = hotelId,
                    employeeId = employeeId,
                    email = EmailAddress.of(ADMIN_EMAIL),
                    displayName = ADMIN_DISPLAY_NAME,
                    passwordHash = passwordHash,
                    roleIds = setOf(roleId),
                    status = UserStatus.ACTIVE
                )
            )
            employeeRepository.save(
                Employee(
                    id = employeeId,
                    hotelId = hotelId,
                    userId = user.id,
                    employeeNumber = ADMIN_EMPLOYEE_NUMBER,
                    displayName = ADMIN_DISPLAY_NAME,
                    departmentId = departmentId,
                    roleIds = setOf(roleId),
                    skillIds = setOf(skillId)
                )
            )
            return
        }

        if (baseEmployee == null) {
            employeeRepository.save(
                Employee(
                    hotelId = hotelId,
                    userId = existing.id,
                    employeeNumber = ADMIN_EMPLOYEE_NUMBER,
                    displayName = ADMIN_DISPLAY_NAME,
                    departmentId = departmentId,
                    roleIds = setOf(roleId),
                    skillIds = setOf(skillId)
                )
            )
        }
    }

    companion object {
        private const val HOTEL_CODE = "hotel-opai-demo"
        private const val HOTEL_NAME = "Hotel OpAI Demo"
        private const val ADMIN_ROLE_CODE = "ADMIN"
        private const val ADMIN_ROLE_NAME = "Administrator"
        private const val ADMIN_DEPARTMENT_CODE = "operations"
        private const val ADMIN_DEPARTMENT_NAME = "Operations"
        private const val ADMIN_SKILL_CODE = "hotel-admin"
        private const val ADMIN_SKILL_NAME = "Hotel Administration"
        private const val ADMIN_EMAIL = "admin@hotelopai.local"
        private const val ADMIN_PASSWORD = "admin123"
        private const val ADMIN_DISPLAY_NAME = "Hotel OpAI Admin"
        private const val ADMIN_EMPLOYEE_NUMBER = "EMP-ADMIN"

        private val AUTH_PERMISSION_SEEDS = listOf(
            PermissionSeed(PermissionCodes.PLATFORM_HOTEL_MANAGE, "Manage platform hotels"),
            PermissionSeed(PermissionCodes.HOTEL_VIEW, "View hotels"),
            PermissionSeed(PermissionCodes.HOTEL_MANAGE, "Manage hotel settings"),
            PermissionSeed(PermissionCodes.BUILDING_VIEW, "View buildings"),
            PermissionSeed(PermissionCodes.BUILDING_MANAGE, "Manage buildings"),
            PermissionSeed(PermissionCodes.FLOOR_VIEW, "View floors"),
            PermissionSeed(PermissionCodes.FLOOR_MANAGE, "Manage floors"),
            PermissionSeed(PermissionCodes.ROOM_VIEW, "View rooms"),
            PermissionSeed(PermissionCodes.ROOM_CREATE, "Create rooms"),
            PermissionSeed(PermissionCodes.ROOM_UPDATE, "Update rooms"),
            PermissionSeed(PermissionCodes.ROOM_DELETE, "Deactivate rooms"),
            PermissionSeed(PermissionCodes.DEPARTMENT_VIEW, "View departments"),
            PermissionSeed(PermissionCodes.DEPARTMENT_MANAGE, "Manage departments"),
            PermissionSeed(PermissionCodes.USER_VIEW, "View users"),
            PermissionSeed(PermissionCodes.USER_CREATE, "Create users"),
            PermissionSeed(PermissionCodes.USER_UPDATE, "Update users"),
            PermissionSeed(PermissionCodes.USER_ASSIGN, "Assign users"),
            PermissionSeed(PermissionCodes.ROLE_VIEW, "View roles"),
            PermissionSeed(PermissionCodes.ROLE_MANAGE, "Manage roles"),
            PermissionSeed(PermissionCodes.SKILL_VIEW, "View skills"),
            PermissionSeed(PermissionCodes.SKILL_MANAGE, "Manage skills"),
            PermissionSeed(PermissionCodes.SHIFT_VIEW, "View shifts"),
            PermissionSeed(PermissionCodes.SHIFT_MANAGE, "Manage shifts"),
            PermissionSeed(PermissionCodes.AUTH_LOGIN, "Login to Hotel OpAI"),
            PermissionSeed(PermissionCodes.AUTH_MANAGE, "Manage authentication sessions"),
            PermissionSeed(PermissionCodes.AUTH_VIEW, "View current user session"),
            PermissionSeed(PermissionCodes.TASK_READ, "Read hotel tasks"),
            PermissionSeed(PermissionCodes.TASK_CREATE, "Create hotel tasks"),
            PermissionSeed(PermissionCodes.TASK_ASSIGN, "Assign hotel tasks"),
            PermissionSeed(PermissionCodes.TASK_START, "Start hotel tasks"),
            PermissionSeed(PermissionCodes.TASK_PAUSE, "Pause hotel tasks"),
            PermissionSeed(PermissionCodes.TASK_RESUME, "Resume hotel tasks"),
            PermissionSeed(PermissionCodes.TASK_COMPLETE, "Complete hotel tasks"),
            PermissionSeed(PermissionCodes.TASK_CANCEL, "Cancel hotel tasks"),
            PermissionSeed(PermissionCodes.TASK_MARK_OVERDUE, "Mark hotel tasks overdue"),
            PermissionSeed(PermissionCodes.TASK_ATTACHMENT_READ, "Read task attachments"),
            PermissionSeed(PermissionCodes.ASSISTANT_USE, "Use assistant"),
            PermissionSeed(PermissionCodes.ASSISTANT_CONFIRM_TASK, "Confirm assistant task"),
            PermissionSeed(PermissionCodes.ASSISTANT_ATTACHMENT_REGISTER, "Register assistant attachment metadata"),
            PermissionSeed(PermissionCodes.ASSISTANT_VISION_IMPORT, "Import assistant vision analysis"),
            PermissionSeed(PermissionCodes.NOTIFICATION_READ, "Read notifications"),
            PermissionSeed(PermissionCodes.NOTIFICATION_MARK_READ, "Mark notifications read"),
            PermissionSeed(PermissionCodes.DASHBOARD_READ, "Read dashboard summary"),
            PermissionSeed(PermissionCodes.REPORT_READ, "Read task reports"),
            PermissionSeed(PermissionCodes.DEV_PMS_ACCESS, "Access local Dev PMS proxy"),
            PermissionSeed(PermissionCodes.PMS_OPERATIONS_ACCESS, "Access PMS operations"),
            PermissionSeed(PermissionCodes.RESERVATION_SYNC_OPERATIONS, "Operate reservation synchronization"),
            PermissionSeed(PermissionCodes.AI_RECOMMENDATION_REVIEW_OPERATIONS, "Review AI pilot recommendations"),
            PermissionSeed(PermissionCodes.KNOWLEDGE_OPERATIONS, "Operate internal knowledge base")
        )
    }

    private data class PermissionSeed(
        val code: String,
        val name: String
    )
}
