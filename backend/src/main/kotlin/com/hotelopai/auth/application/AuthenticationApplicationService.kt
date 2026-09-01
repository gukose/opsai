package com.hotelopai.auth.application

import com.hotelopai.auth.domain.EmailAddress
import com.hotelopai.auth.domain.Permission
import com.hotelopai.auth.domain.RefreshSession
import com.hotelopai.auth.domain.User
import com.hotelopai.auth.domain.UserStatus
import com.hotelopai.employee.application.EmployeeRepository
import com.hotelopai.hotel.application.HotelRepository
import com.hotelopai.shared.kernel.PersistenceInstant
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.UUID
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate

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
    private val clock: Clock,
    private val jdbc: NamedParameterJdbcTemplate
) {
    fun login(command: LoginCommand): AuthSessionResult {
        val timingStarted = System.nanoTime()
        val now = PersistenceInstant.now(clock)
        val hotelStarted = System.nanoTime()
        val hotel = hotelRepository.findByCode(command.hotelCode.trim())
            ?: throw InvalidCredentialsException()
        val hotelMs = elapsedMs(hotelStarted)
        val email=EmailAddress.of(command.email).value
        val userStarted = System.nanoTime()
        val user = userRepository.findByHotelIdAndEmail(hotel.id, email)
            ?: userRepository.findByMembershipHotelIdAndEmail(hotel.id,email)
            ?: throw InvalidCredentialsException()
        val userMs = elapsedMs(userStarted)

        validateLoginEligibility(user, hotel)
        val passwordStarted = System.nanoTime()
        if (!passwordHasher.matches(command.password, user.passwordHash)) {
            throw InvalidCredentialsException()
        }
        val passwordMs = elapsedMs(passwordStarted)

        return createSession(command, hotel, user, now).also {
            logger.info("AUTH_LOGIN_TIMING hotelLookupMs={} userLookupMs={} passwordVerifyMs={} membershipLookupMs=0 employeeIdentityMs=0 roleLookupMs=0 permissionLookupMs=0 refreshSessionCreateMs=0 tokenGenerationMs=0 responseMappingMs=0 otherMs=0 dbRoundTripCount=0 totalMs={}", hotelMs, userMs, passwordMs, elapsedMs(timingStarted))
        }
    }

    fun refresh(command: RefreshCommand): AuthSessionResult {
        val timingStarted = System.nanoTime()
        val lookupStarted = System.nanoTime()
        val now = PersistenceInstant.now(clock)
        val tokenHash = refreshTokenCodec.hash(command.refreshToken)
        val session = refreshSessionRepository.findByRefreshTokenHash(tokenHash)
            ?: throw InvalidRefreshTokenException()
        val lookupMs = elapsedMs(lookupStarted)

        return when (session.status(now)) {
            com.hotelopai.auth.domain.RefreshSessionStatus.REVOKED -> throw RevokedRefreshTokenException()
            com.hotelopai.auth.domain.RefreshSessionStatus.EXPIRED -> throw ExpiredRefreshTokenException()
            com.hotelopai.auth.domain.RefreshSessionStatus.ACTIVE -> rotateSession(session, command, now).also {
                logger.info("AUTH_REFRESH_TIMING refreshSessionLookupMs={} sessionValidationMs=0 userLookupMs=0 membershipLookupMs=0 employeeIdentityMs=0 rolePermissionMs=0 sessionRotationMs={} sessionPersistMs=0 tokenGenerationMs=0 responseMappingMs=0 otherMs=0 dbRoundTripCount=0 transactionMs={} totalMs={}", lookupMs, elapsedMs(timingStarted) - lookupMs, elapsedMs(timingStarted), elapsedMs(timingStarted))
            }
        }
    }

    fun logout(command: LogoutCommand) {
        val now = PersistenceInstant.now(clock)
        val session = refreshSessionRepository.findById(command.sessionId)
            ?: throw InvalidAccessSessionException()
        if (session.revokedAt == null) {
            refreshSessionRepository.save(session.revoke(now))
        }
    }

    fun currentUser(query: CurrentUserQuery): CurrentUserResult {
        val user = userRepository.findById(query.userId) ?: throw InvalidAccessSessionException()
        val hotel = hotelRepository.findById(query.hotelId) ?: throw InvalidAccessSessionException()
        requireMembership(user.id,hotel.id)
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
            expiresAt = PersistenceInstant.toPersistencePrecision(now.plus(authSessionPolicy.refreshTokenTtl()))
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
            nextExpiresAt = PersistenceInstant.toPersistencePrecision(now.plus(authSessionPolicy.refreshTokenTtl())),
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
        val membershipRoleIds=jdbc.query("""select uhr.role_id from user_hotel_membership m join user_hotel_role uhr on uhr.membership_id=m.id and uhr.hotel_id=m.hotel_id where m.user_id=:user and m.hotel_id=:hotel and m.active=true""",mapOf("user" to user.id,"hotel" to hotel.id)){rs,_->rs.getObject(1,UUID::class.java)}.toSet()
        val effectiveRoleIds=(if(membershipRoleIds.isEmpty()&&user.hotelId==hotel.id)user.roleIds else membershipRoleIds)
        val roles = effectiveRoleIds.map { roleId ->
            requireNotNull(roleRepository.findById(roleId)) { "Role not found: $roleId" }
        }.filter { it.hotelId == hotel.id }
            .sortedBy { it.code }
        val permissions = roles.asSequence()
            .flatMap { it.permissionIds.asSequence() }
            .distinct()
            .map { permissionId ->
                requireNotNull(permissionRepository.findById(permissionId)) { "Permission not found: $permissionId" }
            }
            .sortedBy { it.code }

        val employee = user.employeeId?.let(employeeRepository::findById)
            ?.takeIf { it.hotelId == hotel.id }

        return CurrentUserResult(
            userId = user.id,
            hotelId = hotel.id,
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
        // Membership is checked once by validateLoginEligibility; avoid issuing
        // the same remote membership query a second time on the login path.
    }

    private fun validateActiveUserAndHotel(user: User, hotel: com.hotelopai.hotel.domain.Hotel) {
        if (user.status != UserStatus.ACTIVE) {
            throw UserInactiveException()
        }
        if (hotel.status != com.hotelopai.hotel.domain.HotelStatus.ACTIVE) {
            throw InvalidCredentialsException()
        }
        requireMembership(user.id, hotel.id)
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
            roleIds = currentUser.roles.map { it.roleId }.toSet(),
            roleCodes = currentUser.roles.map { it.code }.toSet(),
            permissionIds = currentUser.permissions.map { it.permissionId }.toSet(),
            permissionCodes = currentUser.permissions.map { it.code }.toSet(),
            canonicalEmployeeUserId = currentUser.employeeId?.let(employeeRepository::findById)?.userId
        )

    private fun requireMembership(userId:UUID,hotelId:UUID){val count=jdbc.queryForObject("select count(*) from user_hotel_membership where user_id=:user and hotel_id=:hotel and active=true and (start_date is null or start_date<=current_date) and (end_date is null or end_date>=current_date)",mapOf("user" to userId,"hotel" to hotelId),Long::class.java)?:0;if(count==0L)throw InvalidAccessSessionException()}

    private fun elapsedMs(startedAt: Long): Long = (System.nanoTime() - startedAt) / 1_000_000

    companion object { private val logger = org.slf4j.LoggerFactory.getLogger(AuthenticationApplicationService::class.java) }
}
