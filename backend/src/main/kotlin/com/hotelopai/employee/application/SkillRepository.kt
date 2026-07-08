package com.hotelopai.employee.application

import com.hotelopai.employee.domain.Skill
import java.util.UUID

interface SkillRepository {
    fun save(skill: Skill): Skill

    fun findById(id: UUID): Skill?

    fun findByHotelId(hotelId: UUID): List<Skill>

    fun findByHotelIdAndCode(hotelId: UUID, code: String): Skill?
}
