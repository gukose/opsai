package com.hotelopai.hotel.infrastructure.persistence

import com.hotelopai.hotel.application.HotelRepository
import com.hotelopai.hotel.domain.Hotel
import com.hotelopai.hotel.domain.HotelStatus
import com.hotelopai.support.PostgresIntegrationTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class HotelPersistenceRepositoryIntegrationTest : PostgresIntegrationTestSupport() {
    @Autowired
    private lateinit var hotelRepository: HotelRepository

    @Test
    fun `saves and loads hotel`() {
        val hotel = Hotel(
            code = "grand-hotel",
            name = "Grand Hotel",
            status = HotelStatus.ACTIVE
        )

        val saved = hotelRepository.save(hotel)

        assertThat(saved).isEqualTo(hotel)
        assertThat(hotelRepository.findById(saved.id)).isEqualTo(saved)
        assertThat(hotelRepository.findByCode("grand-hotel")).isEqualTo(saved)
        assertThat(hotelRepository.findAll()).contains(saved)
    }

    @Test
    fun `rejects duplicate hotel code`() {
        hotelRepository.save(
            Hotel(
                code = "shared-code",
                name = "Hotel A"
            )
        )

        assertThrows<DataIntegrityViolationException> {
            hotelRepository.save(
                Hotel(
                    code = "shared-code",
                    name = "Hotel B"
                )
            )
        }
    }
}
