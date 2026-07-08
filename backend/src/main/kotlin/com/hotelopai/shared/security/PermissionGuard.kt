package com.hotelopai.shared.security

import org.springframework.stereotype.Component

@Component("permissionGuard")
class PermissionGuard(
    private val currentUserContextResolver: CurrentUserContextResolver
) {
    fun hasAnyPermission(vararg permissions: String): Boolean {
        val current = currentUserContextResolver.current()
        return permissions.any { permission -> permission in current.permissions }
    }

    fun currentHotelId(): java.util.UUID =
        currentUserContextResolver.current().hotelId
}

