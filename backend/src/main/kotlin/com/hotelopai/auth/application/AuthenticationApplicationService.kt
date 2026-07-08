package com.hotelopai.auth.application

import com.hotelopai.auth.domain.EmailAddress
import com.hotelopai.auth.domain.Permission
import com.hotelopai.auth.domain.RefreshSession
import com.hotelopai.auth.domain.User
import com.hotelopai.auth.domain.UserStatus
import com.hotelopai.employee.application.EmployeeRepository
import com.hotelopai.hotel.application.HotelRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.UUID

@Service
@Transactional
class AuthenticationApplicationService(
    private val hotelRepository: HotelRepository,
    private val userRepository: UserRepository,
    private val roleRepository: RoleRepository,
    private val permissionRepository: PermissionRepository,
    private val employeeRepository: EmployeeRepository,
    private val refreshSessionRepository: RefreshSessionRepository,
    private val passwordHasher: PasswordHasher,
    private val refreshTokenCodec: RefreshTokenCodec,
    private val accessTokenService: AccessTokenService,
    private val authSessionPolicy: AuthSessionPolicy,
    private val clock: Clock
) {
    fun login(command: LoginCommand): AuthSessionResult {
        val now = clock.instant()
        val hotel = hotelRepository.findByCode(command.hotelCode.trim())
            ?: throw InvalidCredentialsException()
        val user = userRepository.findByHotelIdAndEmail(hotel.id, EmailAddress.of(command.email).value)
            ?: throw InvalidCredentialsException()

        validateLoginEligibility(user, hotel)
        if (!passwordHasher.matches(command.password, user.passwordHash)) {
            throw InvalidCredentialsException()
        }

        return createSession(command, hotel, user, now)
    }

    fun refresh(command: RefreshCommand): AuthSessionResult {
        val now = clock.instant()
        val tokenHash = refreshTokenCodec.hash(command.refreshToken)
        val session = refreshSessionRepository.findByRefreshTokenHash(tokenHash)
            ?: throw InvalidRefreshTokenException()

        return when (session.status(now)) {
            com.hotelopai.auth.domain.RefreshSessionStatus.REVOKED -> throw RevokedRefreshTokenException()
            com.hotelopai.auth.domain.RefreshSessionStatus.EXPIRED -> throw ExpiredRefreshTokenException()
            com.hotelopai.auth.domain.RefreshSessionStatus.ACTIVE -> rotateSession(session, command, now)
        }
    }

    fun logout(command: LogoutCommand) {
        val now = clock.instant()
        val session = refreshSessionRepository.findById(command.sessionId)
            ?: throw InvalidAccessSessionException()
        if (session.revokedAt == null) {
            refreshSessionRepository.save(session.revoke(now))
        }
    }

    fun currentUser(query: CurrentUserQuery): CurrentUserResult {
        val user = userRepository.findById(query.userId) ?: throw InvalidAccessSessionException()
        val hotel = hotelRepository.findById(user.hotelId) ?: throw InvalidAccessSessionException()
        validateActiveUserAndHotel(user, hotel)
        return buildCurrentUser(user, hotel)
    }

    private fun createSession(
        command: LoginCommand,
        hotel: com.hotelopai.hotel.domain.Hotel,
        user: User,
        now: java.time.Instant
    ): AuthSessionResult {
        val refreshToken = refreshTokenCodec.generate()
        val session = RefreshSession(
            userId = user.id,
            hotelId = hotel.id,
            refreshTokenHash = refreshTokenCodec.hash(refreshToken),
            deviceId = command.deviceId?.takeIf { it.isNotBlank() } ?: "default",
            deviceName = command.deviceName,
            ipAddress = command.ipAddress,
            userAgent = command.userAgent,
            createdAt = now,
            expiresAt = now.plus(authSessionPolicy.refreshTokenTtl())
        )
        val savedSession = refreshSessionRepository.save(session)
        val currentUser = buildCurrentUser(user, hotel)
        val accessToken = accessTokenService.issueToken(
            context = toAccessTokenContext(savedSession.id, currentUser, user),
            now = now
        )

        return AuthSessionResult(
            accessToken = accessToken.token,
            accessTokenExpiresAt = accessToken.expiresAt,
            refreshToken = refreshToken,
            refreshTokenExpiresAt = savedSession.expiresAt,
            currentUser = currentUser
        )
    }

    private fun rotateSession(
        session: RefreshSession,
        command: RefreshCommand,
        now: java.time.Instant
    ): AuthSessionResult {
        val user = userRepository.findById(session.userId) ?: throw InvalidRefreshTokenException()
        val hotel = hotelRepository.findById(session.hotelId) ?: throw InvalidRefreshTokenException()
        validateActiveUserAndHotel(user, hotel)

        val nextRefreshToken = refreshTokenCodec.generate()
        val rotated = session.rotate(
            nextRefreshTokenHash = refreshTokenCodec.hash(nextRefreshToken),
            nextExpiresAt = now.plus(authSessionPolicy.refreshTokenTtl()),
            now = now
        ).copy(
            deviceId = command.deviceId?.takeIf { it.isNotBlank() } ?: session.deviceId,
            deviceName = command.deviceName ?: session.deviceName,
            ipAddress = command.ipAddress ?: session.ipAddress,
            userAgent = command.userAgent ?: session.userAgent
        )
        val savedSession = refreshSessionRepository.save(rotated)
        val currentUser = buildCurrentUser(user, hotel)
        val accessToken = accessTokenService.issueToken(
            context = toAccessTokenContext(savedSession.id, currentUser, user),
            now = now
        )

        return AuthSessionResult(
            accessToken = accessToken.token,
            accessTokenExpiresAt = accessToken.expiresAt,
            refreshToken = nextRefreshToken,
            refreshTokenExpiresAt = savedSession.expiresAt,
            currentUser = currentUser
        )
    }

    private fun buildCurrentUser(
        user: User,
        hotel: com.hotelopai.hotel.domain.Hotel
    ): CurrentUserResult {
        val roles = user.roleIds.map { roleId ->
            requireNotNull(roleRepository.findById(roleId)) { "Role not found: $roleId" }
        }.filter { it.hotelId == user.hotelId }
            .sortedBy { it.code }
        val permissions = roles.asSequence()
            .flatMap { it.permissionIds.asSequence() }
            .distinct()
            .map { permissionId ->
                requireNotNull(permissionRepository.findById(permissionId)) { "Permission not found: $permissionId" }
            }
            .sortedBy { it.code }

        val employee = user.employeeId?.let(employeeRepository::findById)
            ?.takeIf { it.hotelId == user.hotelId }

        return CurrentUserResult(
            userId = user.id,
            hotelId = user.hotelId,
            employeeId = employee?.id ?: user.employeeId,
            email = user.email.value,
            displayName = user.displayName,
            hotelName = hotel.name,
            roles = roles.map { role ->
                RoleSummaryResult(
                    roleId = role.id,
                    code = role.code,
                    name = role.name
                )
            },
            permissions = permissions.map { permission ->
                PermissionSummaryResult(
                    permissionId = permission.id,
                    code = permission.code,
                    name = permission.name
                )
            }.toList()
        )
    }

    private fun validateLoginEligibility(user: User, hotel: com.hotelopai.hotel.domain.Hotel) {
        validateActiveUserAndHotel(user, hotel)
    }

    private fun validateActiveUserAndHotel(user: User, hotel: com.hotelopai.hotel.domain.Hotel) {
        if (user.status != UserStatus.ACTIVE) {
            throw UserInactiveException()
        }
        if (hotel.status != com.hotelopai.hotel.domain.HotelStatus.ACTIVE) {
            throw InvalidCredentialsException()
        }
    }

    private fun toAccessTokenContext(
        sessionId: UUID,
        currentUser: CurrentUserResult,
        user: User
    ): AccessTokenContext =
        AccessTokenContext(
            userId = currentUser.userId,
            hotelId = currentUser.hotelId,
            employeeId = currentUser.employeeId,
            email = currentUser.email,
            displayName = currentUser.displayName,
            hotelName = currentUser.hotelName,
            sessionId = sessionId,
            roleIds = user.roleIds,
            roleCodes = currentUser.roles.map { it.code }.toSet(),
            permissionIds = currentUser.permissions.map { it.permissionId }.toSet(),
            permissionCodes = currentUser.permissions.map { it.code }.toSet()
        )
}
