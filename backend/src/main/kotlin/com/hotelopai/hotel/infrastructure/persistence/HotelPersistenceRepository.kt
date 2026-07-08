package com.hotelopai.hotel.infrastructure.persistence

import com.hotelopai.hotel.application.HotelRepository
import com.hotelopai.hotel.domain.Hotel
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Repository
@Transactional
class HotelPersistenceRepository(
    private val hotelJpaRepository: HotelJpaRepository
) : HotelRepository {
    override fun save(hotel: Hotel): Hotel =
        HotelPersistenceMapper.toDomain(
            hotelJpaRepository.saveAndFlush(HotelPersistenceMapper.toEntity(hotel))
        )

    override fun findById(id: UUID): Hotel? =
        hotelJpaRepository.findById(id).orElse(null)?.let(HotelPersistenceMapper::toDomain)

    override fun findByCode(code: String): Hotel? =
        hotelJpaRepository.findByCode(code)?.let(HotelPersistenceMapper::toDomain)

    override fun findAll(): List<Hotel> =
        hotelJpaRepository.findAllByOrderByCodeAsc().map(HotelPersistenceMapper::toDomain)
}
