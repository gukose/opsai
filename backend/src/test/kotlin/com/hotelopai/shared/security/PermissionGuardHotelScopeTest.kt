package com.hotelopai.shared.security

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyMap
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import java.util.UUID

class PermissionGuardHotelScopeTest {
    private val resolver=mock(CurrentUserContextResolver::class.java)
    private val jdbc=mock(NamedParameterJdbcTemplate::class.java)
    private val guard=PermissionGuard(resolver,jdbc)
    private val user=UUID.randomUUID();private val currentHotel=UUID.randomUUID();private val otherHotel=UUID.randomUUID()

    @Test fun `current hotel permission is accepted without trusting another hotel`() {
        `when`(resolver.current()).thenReturn(context(setOf(PermissionCodes.ROOM_VIEW)))
        assertTrue(guard.hasHotelPermission(currentHotel,PermissionCodes.ROOM_VIEW))
        `when`(jdbc.queryForObject(anyString(),anyMap<String,Any>(),org.mockito.ArgumentMatchers.eq(Long::class.java))).thenReturn(0)
        assertFalse(guard.hasHotelPermission(otherHotel,PermissionCodes.ROOM_VIEW))
    }

    @Test fun `platform permission explicitly crosses hotel scope`() {
        `when`(resolver.current()).thenReturn(context(setOf(PermissionCodes.PLATFORM_HOTEL_MANAGE)))
        assertTrue(guard.hasHotelPermission(otherHotel,PermissionCodes.ROOM_UPDATE))
    }

    private fun context(permissions:Set<String>)=CurrentUserContext(user,currentHotel,UUID.randomUUID(),permissions,emptySet())
}
