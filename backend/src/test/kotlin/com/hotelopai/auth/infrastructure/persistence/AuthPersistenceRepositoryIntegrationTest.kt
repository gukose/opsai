package com.hotelopai.auth.infrastructure.persistence

import com.hotelopai.auth.application.PermissionRepository
import com.hotelopai.auth.application.RefreshSessionRepository
import com.hotelopai.auth.application.RoleRepository
import com.hotelopai.auth.application.UserRepository
import com.hotelopai.auth.domain.EmailAddress
import com.hotelopai.auth.domain.Permission
import com.hotelopai.auth.domain.RefreshSession
import com.hotelopai.auth.domain.Role
import com.hotelopai.auth.domain.User
import com.hotelopai.auth.domain.UserStatus
import com.hotelopai.hotel.application.HotelRepository
import com.hotelopai.hotel.domain.Hotel
import com.hotelopai.support.PostgresIntegrationTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AuthPersistenceRepositoryIntegrationTest : PostgresIntegrationTestSupport() {
    @Autowired
    private lateinit var hotelRepository: HotelRepository

    @Autowired
    private lateinit var permissionRepository: PermissionRepository

    @Autowired
    private lateinit var roleRepository: RoleRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var refreshSessionRepository: RefreshSessionRepository

    @Test
    fun `persists permission and role permission references`() {
        val permission = permissionRepository.save(
            Permission(
                code = "TASK_VIEW",
                name = "View tasks"
            )
        )
        val hotel = hotelRepository.save(Hotel(code = "alpha-hotel", name = "Alpha Hotel"))

        val role = Role(
            hotelId = hotel.id,
            code = "manager",
            name = "Manager",
            permissionIds = setOf(permission.id)
        )

        val saved = roleRepository.save(role)

        assertThat(saved).isEqualTo(role)
        assertThat(roleRepository.findByHotelId(hotel.id)).containsExactly(saved)
        assertThat(roleRepository.findByHotelIdAndCode(hotel.id, "manager")).isEqualTo(saved)
    }

    @Test
    fun `persists user and refresh session`() {
        val hotel = hotelRepository.save(Hotel(code = "beta-hotel", name = "Beta Hotel"))
        val role = roleRepository.save(
            Role(
                hotelId = hotel.id,
                code = "front-desk",
                name = "Front Desk"
            )
        )

        val user = User(
            hotelId = hotel.id,
            email = EmailAddress.of("guest@example.com"),
            displayName = "Guest User",
            passwordHash = "password-hash",
            roleIds = setOf(role.id),
            status = UserStatus.ACTIVE
        )

        val savedUser = userRepository.save(user)
        val sessionCreatedAt = Instant.parse("2026-01-01T00:00:00Z")
        val session = RefreshSession(
            userId = savedUser.id,
            hotelId = hotel.id,
            refreshTokenHash = "hash-123",
            deviceId = "device-1",
            deviceName = "iPhone",
            ipAddress = "127.0.0.1",
            userAgent = "JUnit",
            createdAt = sessionCreatedAt,
            expiresAt = sessionCreatedAt.plusSeconds(3600)
        )

        val savedSession = refreshSessionRepository.save(session)

        assertThat(savedUser).isEqualTo(user)
        assertThat(userRepository.findByHotelId(hotel.id)).containsExactly(savedUser)
        assertThat(userRepository.findByHotelIdAndEmail(hotel.id, "guest@example.com")).isEqualTo(savedUser)
        assertThat(refreshSessionRepository.findByRefreshTokenHash("hash-123")).isEqualTo(savedSession)
        assertThat(refreshSessionRepository.findByUserId(savedUser.id)).containsExactly(savedSession)
    }

    @Test
    fun `allows same user email in different hotels but scopes queries by hotel`() {
        val firstHotel = hotelRepository.save(Hotel(code = "hotel-a", name = "Hotel A"))
        val secondHotel = hotelRepository.save(Hotel(code = "hotel-b", name = "Hotel B"))

        val firstUser = userRepository.save(
            User(
                hotelId = firstHotel.id,
                email = EmailAddress.of("shared@example.com"),
                displayName = "First User",
                passwordHash = "password-hash-1"
            )
        )
        val secondUser = userRepository.save(
            User(
                hotelId = secondHotel.id,
                email = EmailAddress.of("shared@example.com"),
                displayName = "Second User",
                passwordHash = "password-hash-2"
            )
        )

        assertThat(userRepository.findByHotelId(firstHotel.id)).containsExactly(firstUser)
        assertThat(userRepository.findByHotelId(secondHotel.id)).containsExactly(secondUser)
        assertThat(userRepository.findByHotelIdAndEmail(firstHotel.id, "shared@example.com")).isEqualTo(firstUser)
        assertThat(userRepository.findByHotelIdAndEmail(secondHotel.id, "shared@example.com")).isEqualTo(secondUser)
    }

    @Test
    fun `rejects duplicate refresh token hash`() {
        val hotel = hotelRepository.save(Hotel(code = "gamma-hotel", name = "Gamma Hotel"))
        val user = userRepository.save(
            User(
                hotelId = hotel.id,
                email = EmailAddress.of("token@example.com"),
                displayName = "Token User",
                passwordHash = "password-hash"
            )
        )

        refreshSessionRepository.save(
            RefreshSession(
                userId = user.id,
                hotelId = hotel.id,
                refreshTokenHash = "duplicate-token",
                deviceId = "device-1",
                createdAt = Instant.parse("2026-01-01T00:00:00Z"),
                expiresAt = Instant.parse("2026-01-01T01:00:00Z")
            )
        )

        assertThrows<DataIntegrityViolationException> {
            refreshSessionRepository.save(
                RefreshSession(
                    userId = user.id,
                    hotelId = hotel.id,
                    refreshTokenHash = "duplicate-token",
                    deviceId = "device-2",
                    createdAt = Instant.parse("2026-01-01T02:00:00Z"),
                    expiresAt = Instant.parse("2026-01-01T03:00:00Z")
                )
            )
        }
    }
}
