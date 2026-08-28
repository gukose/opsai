package com.hotelopai.auth.infrastructure.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
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

    @Query(value="""select u.* from app_user u join user_hotel_membership m on m.user_id=u.id where m.hotel_id=:hotelId and m.active=true and lower(u.email)=lower(:email) and (m.start_date is null or m.start_date<=current_date) and (m.end_date is null or m.end_date>=current_date) order by u.created_at limit 1""",nativeQuery=true)
    fun findMembershipUser(@Param("hotelId") hotelId:UUID,@Param("email") email:String):UserJpaEntity?
}

interface RefreshSessionJpaRepository : JpaRepository<RefreshSessionJpaEntity, UUID> {
    fun findByUserId(userId: UUID): List<RefreshSessionJpaEntity>

    fun findByRefreshTokenHash(refreshTokenHash: String): RefreshSessionJpaEntity?
}
