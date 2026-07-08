package com.hotelopai.auth.infrastructure.persistence

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface PermissionJpaRepository : JpaRepository<PermissionJpaEntity, UUID> {
    fun findByCode(code: String): PermissionJpaEntity?

    fun findAllByOrderByCodeAsc(): List<PermissionJpaEntity>
}

interface RoleJpaRepository : JpaRepository<RoleJpaEntity, UUID> {
    fun findByHotelId(hotelId: UUID): List<RoleJpaEntity>

    fun findByHotelIdAndCode(hotelId: UUID, code: String): RoleJpaEntity?

    fun findAllByHotelIdOrderByCodeAsc(hotelId: UUID): List<RoleJpaEntity>
}

interface UserJpaRepository : JpaRepository<UserJpaEntity, UUID> {
    fun findByHotelId(hotelId: UUID): List<UserJpaEntity>

    fun findByHotelIdAndEmail(hotelId: UUID, email: String): UserJpaEntity?

    fun findAllByHotelIdOrderByEmailAsc(hotelId: UUID): List<UserJpaEntity>
}

interface RefreshSessionJpaRepository : JpaRepository<RefreshSessionJpaEntity, UUID> {
    fun findByUserId(userId: UUID): List<RefreshSessionJpaEntity>

    fun findByRefreshTokenHash(refreshTokenHash: String): RefreshSessionJpaEntity?
}
