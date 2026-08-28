package com.hotelopai.integration.unimock.demo

import com.hotelopai.housekeeping.application.CreateHousekeepingCommand
import com.hotelopai.housekeeping.application.HousekeepingService
import com.hotelopai.housekeeping.application.MinibarReadinessService
import com.hotelopai.housekeeping.domain.HousekeepingWorkflow
import com.hotelopai.housekeeping.domain.HousekeepingWorkflowType
import com.hotelopai.task.application.CreateTaskCommand
import com.hotelopai.task.application.TaskLifecycleService
import com.hotelopai.task.domain.Task
import com.hotelopai.task.domain.TaskIntentType
import com.hotelopai.task.domain.TaskPriority
import com.hotelopai.task.domain.TaskSource
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.util.UUID

data class PmsCheckoutResult(val workflow:HousekeepingWorkflow,val minibarTask:Task)

@Service
class PmsCheckoutOrchestrator(
    private val housekeeping:HousekeepingService,
    private val tasks:TaskLifecycleService,
    private val minibarReadiness:MinibarReadinessService
) {
    fun checkout(hotelId:UUID,roomNumber:String,providerEventId:String,now:Instant):PmsCheckoutResult {
        val workflow=housekeeping.create(
            CreateHousekeepingCommand(hotelId,roomNumber,HousekeepingWorkflowType.DEPARTURE_CLEANING,true,"pms:$providerEventId")
        )
        val minibarTask=tasks.createTask(
            CreateTaskCommand(
                hotelId=hotelId,
                intentType=TaskIntentType.MINIBAR,
                source=TaskSource.PMS,
                title="Minibar Check",
                description="PMS CHECK_OUT minibar inspection for room $roomNumber; providerEventId=$providerEventId",
                roomNumber=roomNumber,
                priority=TaskPriority.HIGH,
                slaDeadline=now.plus(Duration.ofHours(2))
            )
        )
        minibarReadiness.markPending(hotelId,roomNumber)
        return PmsCheckoutResult(workflow,minibarTask)
    }
}
