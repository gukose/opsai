package com.hotelopai.hotel.infrastructure.persistence

import com.hotelopai.hotel.domain.Hotel
import com.hotelopai.shared.kernel.PersistenceInstant

internal object HotelPersistenceMapper {
    fun toDomain(entity: HotelJpaEntity): Hotel =
        Hotel(
            id = requireNotNull(entity.id) { "hotel.id must not be null" },
            code = entity.code,
            name = entity.name,
            status = entity.status,
            version = requireNotNull(entity.version) { "hotel.version must not be null" },
            createdAt = requireNotNull(entity.createdAt) { "hotel.createdAt must not be null" },
            createdBy = entity.createdBy,
            updatedAt = requireNotNull(entity.updatedAt) { "hotel.updatedAt must not be null" },
            updatedBy = entity.updatedBy
        )

    fun toEntity(domain: Hotel): HotelJpaEntity =
        HotelJpaEntity().apply {
            id = domain.id
            code = domain.code
            name = domain.name
            status = domain.status
            version = domain.version.takeIf { it > 0 }
            createdAt = PersistenceInstant.toPersistencePrecision(domain.createdAt)
            createdBy = domain.createdBy
            updatedAt = PersistenceInstant.toPersistencePrecision(domain.updatedAt)
            updatedBy = domain.updatedBy
        }
}
