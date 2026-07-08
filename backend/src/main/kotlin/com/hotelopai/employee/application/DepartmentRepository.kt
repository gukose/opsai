package com.hotelopai.employee.application

import com.hotelopai.employee.domain.Department
import java.util.UUID

interface DepartmentRepository {
    fun save(department: Department): Department

    fun findById(id: UUID): Department?

    fun findByHotelId(hotelId: UUID): List<Department>

    fun findByHotelIdAndCode(hotelId: UUID, code: String): Department?
}
