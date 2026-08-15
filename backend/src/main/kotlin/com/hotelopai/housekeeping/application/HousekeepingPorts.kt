package com.hotelopai.housekeeping.application

import com.hotelopai.housekeeping.domain.HousekeepingInspection
import com.hotelopai.housekeeping.domain.HousekeepingWorkflow
import java.util.UUID

interface HousekeepingRepository {
    fun insert(workflow: HousekeepingWorkflow): HousekeepingWorkflow
    fun findByIdAndHotelId(id: UUID, hotelId: UUID): HousekeepingWorkflow?
    fun findByIdempotencyKey(hotelId: UUID, key: String): HousekeepingWorkflow?
    fun save(workflow: HousekeepingWorkflow): HousekeepingWorkflow
    fun list(hotelId: UUID): List<HousekeepingWorkflow>
    fun appendInspection(hotelId: UUID, inspection: HousekeepingInspection)
    fun inspections(workflowId: UUID, hotelId: UUID): List<HousekeepingInspection>
}

class HousekeepingNotFoundException(id: UUID) : RuntimeException("Housekeeping workflow '$id' was not found")
