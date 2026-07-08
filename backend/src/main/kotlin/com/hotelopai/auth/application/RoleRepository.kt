package com.hotelopai.auth.application

import com.hotelopai.auth.domain.Role
import java.util.UUID

interface RoleRepository {
    fun save(role: Role): Role

    fun findById(id: UUID): Role?

    fun findByHotelId(hotelId: UUID): List<Role>

    fun findByHotelIdAndCode(hotelId: UUID, code: String): Role?
}
