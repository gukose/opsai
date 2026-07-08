package com.hotelopai.hotel.application

import com.hotelopai.hotel.domain.Hotel
import java.util.UUID

interface HotelRepository {
    fun save(hotel: Hotel): Hotel

    fun findById(id: UUID): Hotel?

    fun findByCode(code: String): Hotel?

    fun findAll(): List<Hotel>
}
