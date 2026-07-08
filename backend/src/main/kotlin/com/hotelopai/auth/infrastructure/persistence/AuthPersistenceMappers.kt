package com.hotelopai.auth.infrastructure.persistence

import com.hotelopai.auth.domain.EmailAddress
import com.hotelopai.auth.domain.Permission
import com.hotelopai.auth.domain.RefreshSession
import com.hotelopai.auth.domain.Role
import com.hotelopai.auth.domain.User

internal object PermissionPersistenceMapper {
    fun toDomain(entity: PermissionJpaEntity): Permission =
        Permission(
            id = requireNotNull(entity.id) { "permission.id must not be null" },
            code = entity.code,
            name = entity.name,
            description = entity.description,
            version = requireNotNull(entity.version) { "permission.version must not be null" },
            createdAt = requireNotNull(entity.createdAt) { "permission.createdAt must not be null" },
            createdBy = entity.createdBy,
            updatedAt = requireNotNull(entity.updatedAt) { "permission.updatedAt must not be null" },
            updatedBy = entity.updatedBy
        )

    fun toEntity(domain: Permission): PermissionJpaEntity =
        PermissionJpaEntity().apply {
            id = domain.id
            code = domain.code
            name = domain.name
            description = domain.description
            version = domain.version.takeIf { it > 0 }
            createdAt = domain.createdAt
            createdBy = domain.createdBy
            updatedAt = domain.updatedAt
            updatedBy = domain.updatedBy
        }
}

internal object RolePersistenceMapper {
    fun toDomain(entity: RoleJpaEntity): Role =
        Role(
            id = requireNotNull(entity.id) { "role.id must not be null" },
            hotelId = requireNotNull(entity.hotelId) { "role.hotelId must not be null" },
            code = entity.code,
            name = entity.name,
            description = entity.description,
            permissionIds = entity.permissionIds.toSet(),
            version = requireNotNull(entity.version) { "role.version must not be null" },
            createdAt = requireNotNull(entity.createdAt) { "role.createdAt must not be null" },
            createdBy = entity.createdBy,
            updatedAt = requireNotNull(entity.updatedAt) { "role.updatedAt must not be null" },
            updatedBy = entity.updatedBy
        )

    fun toEntity(domain: Role): RoleJpaEntity =
        RoleJpaEntity().apply {
            id = domain.id
            hotelId = domain.hotelId
            code = domain.code
            name = domain.name
            description = domain.description
            permissionIds = domain.permissionIds.toMutableSet()
            version = domain.version.takeIf { it > 0 }
            createdAt = domain.createdAt
            createdBy = domain.createdBy
            updatedAt = domain.updatedAt
            updatedBy = domain.updatedBy
        }
}

internal object UserPersistenceMapper {
    fun toDomain(entity: UserJpaEntity): User =
        User(
            id = requireNotNull(entity.id) { "user.id must not be null" },
            hotelId = requireNotNull(entity.hotelId) { "user.hotelId must not be null" },
            employeeId = entity.employeeId,
            email = EmailAddress.of(entity.email),
            displayName = entity.displayName,
            passwordHash = entity.passwordHash,
            roleIds = entity.roleIds.toSet(),
            status = entity.status,
            version = requireNotNull(entity.version) { "user.version must not be null" },
            createdAt = requireNotNull(entity.createdAt) { "user.createdAt must not be null" },
            createdBy = entity.createdBy,
            updatedAt = requireNotNull(entity.updatedAt) { "user.updatedAt must not be null" },
            updatedBy = entity.updatedBy
        )

    fun toEntity(domain: User): UserJpaEntity =
        UserJpaEntity().apply {
            id = domain.id
            hotelId = domain.hotelId
            employeeId = domain.employeeId
            email = domain.email.value
            displayName = domain.displayName
            passwordHash = domain.passwordHash
            status = domain.status
            roleIds = domain.roleIds.toMutableSet()
            version = domain.version.takeIf { it > 0 }
            createdAt = domain.createdAt
            createdBy = domain.createdBy
            updatedAt = domain.updatedAt
            updatedBy = domain.updatedBy
        }
}

internal object RefreshSessionPersistenceMapper {
    fun toDomain(entity: RefreshSessionJpaEntity): RefreshSession =
        RefreshSession(
            id = requireNotNull(entity.id) { "refreshSession.id must not be null" },
            userId = requireNotNull(entity.userId) { "refreshSession.userId must not be null" },
            hotelId = requireNotNull(entity.hotelId) { "refreshSession.hotelId must not be null" },
            refreshTokenHash = entity.refreshTokenHash,
            deviceId = entity.deviceId,
            deviceName = entity.deviceName,
            ipAddress = entity.ipAddress,
            userAgent = entity.userAgent,
            expiresAt = requireNotNull(entity.expiresAt) { "refreshSession.expiresAt must not be null" },
            revokedAt = entity.revokedAt,
            lastUsedAt = entity.lastUsedAt,
            version = requireNotNull(entity.version) { "refreshSession.version must not be null" },
            createdAt = requireNotNull(entity.createdAt) { "refreshSession.createdAt must not be null" },
            createdBy = entity.createdBy,
            updatedAt = requireNotNull(entity.updatedAt) { "refreshSession.updatedAt must not be null" },
            updatedBy = entity.updatedBy
        )

    fun toEntity(domain: RefreshSession): RefreshSessionJpaEntity =
        RefreshSessionJpaEntity().apply {
            id = domain.id
            userId = domain.userId
            hotelId = domain.hotelId
            refreshTokenHash = domain.refreshTokenHash
            deviceId = domain.deviceId
            deviceName = domain.deviceName
            ipAddress = domain.ipAddress
            userAgent = domain.userAgent
            expiresAt = domain.expiresAt
            revokedAt = domain.revokedAt
            lastUsedAt = domain.lastUsedAt
            version = domain.version.takeIf { it > 0 }
            createdAt = domain.createdAt
            createdBy = domain.createdBy
            updatedAt = domain.updatedAt
            updatedBy = domain.updatedBy
        }
}
