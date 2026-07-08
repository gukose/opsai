package com.hotelopai.hotel.infrastructure.persistence

import com.hotelopai.infrastructure.persistence.AuditedJpaEntity
import com.hotelopai.hotel.domain.HotelStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table

@Entity
@Table(name = "hotel")
class HotelJpaEntity : AuditedJpaEntity() {
    @Column(name = "code", nullable = false, unique = true)
    var code: String = ""

    @Column(name = "name", nullable = false)
    var name: String = ""

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: HotelStatus = HotelStatus.ACTIVE
}
