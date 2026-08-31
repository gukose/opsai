package com.hotelopai.task.api

import com.hotelopai.task.application.*
import com.hotelopai.task.domain.Task
import org.springframework.stereotype.Component
import java.time.Instant
import com.hotelopai.housekeeping.application.HousekeepingRepository
import com.hotelopai.housekeeping.domain.HousekeepingStatus

@Component
class TaskResponseMapper(private val history: TaskStateHistoryRepository, private val housekeeping: HousekeepingRepository? = null) {
    fun toResponse(task: Task): TaskResponse {
        val timing = TaskTimingCalculator.calculate(history.findByTaskId(task.id), Instant.now())
        val workflow = housekeeping?.findByTaskIdAndHotelId(task.id, task.hotelId)
        val latestReason = workflow?.takeIf { it.status.name == "REWORK" }?.let { housekeeping.inspections(it.id, task.hotelId).asReversed().firstOrNull { inspection -> inspection.result.name == "REJECT" }?.rejectionReason }
        return TaskResponse.from(task, timing).copy(awaitingInspection = workflow?.let { it.inspectionRequired && it.status == HousekeepingStatus.INSPECTION } == true, latestInspectionRejectionReason = latestReason)
    }

    fun toResponses(tasks: List<Task>): List<TaskResponse> {
        if (tasks.isEmpty()) return emptyList()
        val grouped = history.findByTaskIds(tasks.map { it.id })
        val now = Instant.now()
        val inspectionIds = housekeeping?.list(tasks.first().hotelId).orEmpty().filter { it.status == HousekeepingStatus.INSPECTION && it.inspectionRequired }.map { it.taskId }.toSet()
        return tasks.map { TaskResponse.from(it, TaskTimingCalculator.calculate(grouped[it.id].orEmpty(), now)).copy(awaitingInspection = it.id in inspectionIds) }
    }

    fun toPageResponse(page: TaskPage<Task>): TaskPageResponse =
        TaskPageResponse.from(page, toResponses(page.items))

    private fun isAwaitingInspection(task: Task): Boolean = housekeeping?.findByTaskIdAndHotelId(task.id, task.hotelId)?.let { it.inspectionRequired && it.status == HousekeepingStatus.INSPECTION } == true
}
