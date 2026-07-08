package com.hotelopai.auth.infrastructure.persistence

import com.hotelopai.auth.application.PermissionRepository
import com.hotelopai.auth.application.RefreshSessionRepository
import com.hotelopai.auth.application.RoleRepository
import com.hotelopai.auth.application.UserRepository
import com.hotelopai.auth.domain.Permission
import com.hotelopai.auth.domain.RefreshSession
import com.hotelopai.auth.domain.Role
import com.hotelopai.auth.domain.User
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Repository
@Transactional
class JpaPermissionRepositoryAdapter(
    private val permissionJpaRepository: PermissionJpaRepository
) : PermissionRepository {
    override fun save(permission: Permission): Permission =
        PermissionPersistenceMapper.toDomain(
            permissionJpaRepository.saveAndFlush(PermissionPersistenceMapper.toEntity(permission))
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
            roleJpaRepository.saveAndFlush(RolePersistenceMapper.toEntity(role))
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
    override fun save(user: User): User =
        UserPersistenceMapper.toDomain(
            userJpaRepository.saveAndFlush(UserPersistenceMapper.toEntity(user))
        )

    override fun findById(id: UUID): User? =
        userJpaRepository.findById(id).orElse(null)?.let(UserPersistenceMapper::toDomain)

    override fun findByHotelId(hotelId: UUID): List<User> =
        userJpaRepository.findAllByHotelIdOrderByEmailAsc(hotelId).map(UserPersistenceMapper::toDomain)

    override fun findByHotelIdAndEmail(hotelId: UUID, email: String): User? =
        userJpaRepository.findByHotelIdAndEmail(hotelId, email)?.let(UserPersistenceMapper::toDomain)
}

@Repository
@Transactional
class JpaRefreshSessionRepositoryAdapter(
    private val refreshSessionJpaRepository: RefreshSessionJpaRepository
) : RefreshSessionRepository {
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
                    expiresAt = refreshSession.expiresAt
                    revokedAt = refreshSession.revokedAt
                    lastUsedAt = refreshSession.lastUsedAt
                    createdAt = refreshSession.createdAt
                    createdBy = refreshSession.createdBy
                    updatedAt = refreshSession.updatedAt
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
