package com.hotelopai.shared.security

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import java.util.UUID

@Component("permissionGuard")
class PermissionGuard(
    private val currentUserContextResolver: CurrentUserContextResolver,
    private val jdbc: NamedParameterJdbcTemplate
) {
    fun hasAnyPermission(vararg permissions: String): Boolean {
        val current = currentUserContextResolver.current()
        return permissions.any { permission -> permission in current.permissions }
    }

    fun hasRole(roleCode: String): Boolean =
        roleCode in currentUserContextResolver.current().roles

    fun currentHotelId(): java.util.UUID =
        currentUserContextResolver.current().hotelId

    /** Resolves current database assignments so a user may hold different permissions per hotel. */
    fun hasHotelPermission(hotelId: UUID, permission: String): Boolean {
        val current = currentUserContextResolver.current()
        if (PermissionCodes.PLATFORM_HOTEL_MANAGE in current.permissions) return true
        if (hotelId == current.hotelId && permission in current.permissions) return true
        val count = jdbc.queryForObject(
            """select count(*) from user_hotel_membership m
               join user_hotel_role uhr on uhr.membership_id=m.id and uhr.hotel_id=m.hotel_id
               join role_permission rp on rp.role_id=uhr.role_id
               join permission p on p.id=rp.permission_id
               join hotel h on h.id=m.hotel_id
               where m.user_id=:user and m.hotel_id=:hotel and m.active=true
                 and (m.start_date is null or m.start_date<=current_date)
                 and (m.end_date is null or m.end_date>=current_date)
                 and h.status='ACTIVE' and p.code=:permission""",
            mapOf("user" to current.userId, "hotel" to hotelId, "permission" to permission),
            Long::class.java
        ) ?: 0
        return count > 0
    }
}
