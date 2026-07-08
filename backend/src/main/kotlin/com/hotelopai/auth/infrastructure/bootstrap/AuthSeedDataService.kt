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
import com.hotelopai.shared.kernel.UuidV7Generator
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
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
    private val passwordHasher: PasswordHasher
) {
    fun seed() {
        val hotel = ensureHotel()
        val permissions = ensurePermissions()
        val role = ensureAdminRole(hotel.id, permissions.map { it.id }.toSet())
        val department = ensureDepartment(hotel.id)
        val skill = ensureSkill(hotel.id)
        ensureAdminUser(hotel.id, role.id, department.id, skill.id)
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
                    permissionIds = permissionIds,
                    version = existing.version + 1
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
            PermissionSeed("AUTH_LOGIN", "Login to Hotel OpAI"),
            PermissionSeed("AUTH_MANAGE", "Manage authentication sessions"),
            PermissionSeed("AUTH_VIEW", "View current user session")
        )
    }

    private data class PermissionSeed(
        val code: String,
        val name: String
    )
}
