package com.hotelopai.auth.application

import com.hotelopai.auth.domain.User
import java.util.UUID

interface UserRepository {
    fun save(user: User): User

    fun findById(id: UUID): User?

    fun findByHotelId(hotelId: UUID): List<User>

    fun findByHotelIdAndEmail(hotelId: UUID, email: String): User?
}
