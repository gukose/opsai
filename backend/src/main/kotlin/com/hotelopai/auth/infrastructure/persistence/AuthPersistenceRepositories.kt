package com.hotelopai.auth.infrastructure.persistence

import com.hotelopai.auth.application.PermissionRepository
import com.hotelopai.auth.application.RefreshSessionRepository
import com.hotelopai.auth.application.RoleRepository
import com.hotelopai.auth.application.UserRepository
import com.hotelopai.auth.domain.Permission
import com.hotelopai.auth.domain.RefreshSession
import com.hotelopai.auth.domain.Role
import com.hotelopai.auth.domain.User
import com.hotelopai.shared.kernel.PersistenceInstant
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.util.UUID
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import java.sql.Timestamp

@Repository
@Transactional
class JpaPermissionRepositoryAdapter(
    private val permissionJpaRepository: PermissionJpaRepository
) : PermissionRepository {
    override fun save(permission: Permission): Permission =
        PermissionPersistenceMapper.toDomain(
            permissionJpaRepository.save(PermissionPersistenceMapper.toEntity(permission))
        )

    override fun findById(id: UUID): Permission? =
        permissionJpaRepository.findById(id).orElse(null)?.let(PermissionPersistenceMapper::toDomain)

    override fun findByCode(code: String): Permission? =
        permissionJpaRepository.findByCode(code)?.let(PermissionPersistenceMapper::toDomain)

    override fun findAll(): List<Permission> =
        permissionJpaRepository.findAllByOrderByCodeAsc().map(PermissionPersistenceMapper::toDomain)
}

@Repository
@Transactional
class JpaRoleRepositoryAdapter(
    private val roleJpaRepository: RoleJpaRepository
) : RoleRepository {
    override fun save(role: Role): Role =
        RolePersistenceMapper.toDomain(
            roleJpaRepository.save(
                roleJpaRepository.findById(role.id).orElse(null)?.apply {
                    hotelId = role.hotelId
                    code = role.code
                    name = role.name
                    description = role.description
                    permissionIds.clear()
                    permissionIds.addAll(role.permissionIds)
                    updatedAt = PersistenceInstant.toPersistencePrecision(role.updatedAt)
                    updatedBy = role.updatedBy
                } ?: RolePersistenceMapper.toEntity(role)
            )
        )

    override fun findById(id: UUID): Role? =
        roleJpaRepository.findById(id).orElse(null)?.let(RolePersistenceMapper::toDomain)

    override fun findByHotelId(hotelId: UUID): List<Role> =
        roleJpaRepository.findAllByHotelIdOrderByCodeAsc(hotelId).map(RolePersistenceMapper::toDomain)

    override fun findByHotelIdAndCode(hotelId: UUID, code: String): Role? =
        roleJpaRepository.findByHotelIdAndCode(hotelId, code)?.let(RolePersistenceMapper::toDomain)
}

@Repository
@Transactional
class JpaUserRepositoryAdapter(
    private val userJpaRepository: UserJpaRepository
) : UserRepository {
    override fun save(user: User): User {
        val entity = userJpaRepository.findById(user.id).orElse(null)?.also {
            require(it.hotelId == user.hotelId) {
                "Cannot update user ${user.id} outside hotel ${user.hotelId}"
            }
            UserPersistenceMapper.updateEntity(user, it)
        } ?: UserPersistenceMapper.toEntity(user)
        return UserPersistenceMapper.toDomain(userJpaRepository.save(entity))
    }

    override fun findById(id: UUID): User? =
        userJpaRepository.findById(id).orElse(null)?.let(UserPersistenceMapper::toDomain)

    override fun findByHotelId(hotelId: UUID): List<User> =
        userJpaRepository.findAllByHotelIdOrderByEmailAsc(hotelId).map(UserPersistenceMapper::toDomain)

    override fun findByHotelIdAndEmail(hotelId: UUID, email: String): User? =
        userJpaRepository.findByHotelIdAndEmail(hotelId, email)?.let(UserPersistenceMapper::toDomain)

    override fun findByMembershipHotelIdAndEmail(hotelId:UUID,email:String):User? =
        userJpaRepository.findMembershipUser(hotelId,email)?.let(UserPersistenceMapper::toDomain)
}

@Repository
@Transactional
class JpaRefreshSessionRepositoryAdapter(
    private val refreshSessionJpaRepository: RefreshSessionJpaRepository,
    private val jdbc: NamedParameterJdbcTemplate
) : RefreshSessionRepository {
    override fun create(refreshSession: RefreshSession): RefreshSession {
        jdbc.update("insert into refresh_session(id,user_id,hotel_id,version,created_at,created_by,updated_at,updated_by,refresh_token_hash,device_id,device_name,ip_address,user_agent,expires_at,revoked_at,last_used_at) values(:id,:userId,:hotelId,0,:createdAt,:createdBy,:updatedAt,:updatedBy,:hash,:deviceId,:deviceName,:ipAddress,:userAgent,:expiresAt,:revokedAt,:lastUsedAt)",
            mapOf("id" to refreshSession.id, "userId" to refreshSession.userId, "hotelId" to refreshSession.hotelId, "createdAt" to Timestamp.from(refreshSession.createdAt), "createdBy" to refreshSession.createdBy, "updatedAt" to Timestamp.from(refreshSession.updatedAt), "updatedBy" to refreshSession.updatedBy, "hash" to refreshSession.refreshTokenHash, "deviceId" to refreshSession.deviceId, "deviceName" to refreshSession.deviceName, "ipAddress" to refreshSession.ipAddress, "userAgent" to refreshSession.userAgent, "expiresAt" to Timestamp.from(refreshSession.expiresAt), "revokedAt" to refreshSession.revokedAt?.let(Timestamp::from), "lastUsedAt" to refreshSession.lastUsedAt?.let(Timestamp::from)))
        return refreshSession.copy(version = 0)
    }
    override fun rotate(current: RefreshSession, replacement: RefreshSession): RefreshSession {
        require(current.id != replacement.id) { "Replacement refresh session must have a fresh id" }
        val updated = jdbc.update(
            "update refresh_session set revoked_at=:now,last_used_at=:now,updated_at=:now,version=version+1 where id=:id and version=:version and revoked_at is null and expires_at>:now",
            mapOf("id" to current.id, "version" to current.version, "now" to Timestamp.from(replacement.lastUsedAt ?: replacement.updatedAt))
        )
        if (updated != 1) throw com.hotelopai.auth.application.InvalidRefreshTokenException()
        val persistedReplacement = replacement.copy(version = 0)
        jdbc.update(
            "insert into refresh_session(id,user_id,hotel_id,version,created_at,created_by,updated_at,updated_by,refresh_token_hash,device_id,device_name,ip_address,user_agent,expires_at,revoked_at,last_used_at) values(:id,:userId,:hotelId,:version,:createdAt,:createdBy,:updatedAt,:updatedBy,:hash,:deviceId,:deviceName,:ipAddress,:userAgent,:expiresAt,:revokedAt,:lastUsedAt)",
            mapOf("id" to persistedReplacement.id, "userId" to persistedReplacement.userId, "hotelId" to persistedReplacement.hotelId, "version" to persistedReplacement.version,
                "createdAt" to Timestamp.from(persistedReplacement.createdAt), "createdBy" to persistedReplacement.createdBy, "updatedAt" to Timestamp.from(persistedReplacement.updatedAt), "updatedBy" to persistedReplacement.updatedBy,
                "hash" to persistedReplacement.refreshTokenHash, "deviceId" to persistedReplacement.deviceId, "deviceName" to persistedReplacement.deviceName, "ipAddress" to persistedReplacement.ipAddress, "userAgent" to persistedReplacement.userAgent,
                "expiresAt" to Timestamp.from(persistedReplacement.expiresAt), "revokedAt" to persistedReplacement.revokedAt?.let(Timestamp::from), "lastUsedAt" to persistedReplacement.lastUsedAt?.let(Timestamp::from)))
        return persistedReplacement
    }
    override fun save(refreshSession: RefreshSession): RefreshSession =
        RefreshSessionPersistenceMapper.toDomain(
            refreshSessionJpaRepository.saveAndFlush(
                refreshSessionJpaRepository.findById(refreshSession.id).orElse(null)?.apply {
                    userId = refreshSession.userId
                    hotelId = refreshSession.hotelId
                    refreshTokenHash = refreshSession.refreshTokenHash
                    deviceId = refreshSession.deviceId
                    deviceName = refreshSession.deviceName
                    ipAddress = refreshSession.ipAddress
                    userAgent = refreshSession.userAgent
                    expiresAt = PersistenceInstant.toPersistencePrecision(refreshSession.expiresAt)
                    revokedAt = PersistenceInstant.toPersistencePrecisionOrNull(refreshSession.revokedAt)
                    lastUsedAt = PersistenceInstant.toPersistencePrecisionOrNull(refreshSession.lastUsedAt)
                    createdAt = PersistenceInstant.toPersistencePrecision(refreshSession.createdAt)
                    createdBy = refreshSession.createdBy
                    updatedAt = PersistenceInstant.toPersistencePrecision(refreshSession.updatedAt)
                    updatedBy = refreshSession.updatedBy
                } ?: RefreshSessionPersistenceMapper.toEntity(refreshSession)
            )
        )

    override fun findById(id: UUID): RefreshSession? =
        refreshSessionJpaRepository.findById(id).orElse(null)?.let(RefreshSessionPersistenceMapper::toDomain)

    override fun findByUserId(userId: UUID): List<RefreshSession> =
        refreshSessionJpaRepository.findByUserId(userId).map(RefreshSessionPersistenceMapper::toDomain)

    override fun findByRefreshTokenHash(refreshTokenHash: String): RefreshSession? =
        refreshSessionJpaRepository.findByRefreshTokenHash(refreshTokenHash)?.let(RefreshSessionPersistenceMapper::toDomain)
}
