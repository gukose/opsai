package com.hotelopai.hotel.infrastructure.persistence

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface HotelJpaRepository : JpaRepository<HotelJpaEntity, UUID> {
    fun findByCode(code: String): HotelJpaEntity?

    fun findAllByOrderByCodeAsc(): List<HotelJpaEntity>
}
