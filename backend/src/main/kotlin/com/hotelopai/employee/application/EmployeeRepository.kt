package com.hotelopai.employee.application

import com.hotelopai.employee.domain.Employee
import java.util.UUID

interface EmployeeRepository {
    fun save(employee: Employee): Employee

    fun findById(id: UUID): Employee?

    fun findByHotelId(hotelId: UUID): List<Employee>

    fun findByHotelIdAndEmployeeNumber(hotelId: UUID, employeeNumber: String): Employee?

    fun findByUserId(userId: UUID): Employee?
}
