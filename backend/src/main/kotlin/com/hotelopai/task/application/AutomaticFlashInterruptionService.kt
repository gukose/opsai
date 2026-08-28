package com.hotelopai.task.application

import com.hotelopai.task.domain.Task
import com.hotelopai.task.domain.TaskAssigneeType
import com.hotelopai.task.domain.TaskPriority
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

@Service
class AutomaticFlashInterruptionService(
    private val tasks: TaskRepository,
    @Lazy private val interruptions: SmartInterruptionService
) : AutomaticFlashInterruptionHandler, TaskCompletionObserver {
    private val log=LoggerFactory.getLogger(javaClass)

    override fun assigned(task: Task, selectedEmployeeId: UUID?, now: Instant) {
        val assignment=task.assignment
        log.info("FLASH_EVALUATION_START flashTaskId={} flashTaskType={} flashPriority={} assignedEmployeeId={}",task.id,task.intentType,task.priority,assignment?.assigneeId)
        val priorityEligible=task.priority in setOf(TaskPriority.HIGH,TaskPriority.URGENT)
        if(!priorityEligible || assignment?.assigneeType!=TaskAssigneeType.USER || selectedEmployeeId==null) {
            log.info("FLASH_INTERRUPTION_DECISION flashTaskId={} activeTaskId={} sameEmployee={} eligibleState={} priorityEligible={} decision=SKIP reason={}",task.id,null,false,false,priorityEligible,if(!priorityEligible) "PRIORITY_NOT_FLASH" else "FLASH_TASK_NOT_ASSIGNED")
            return
        }
        val active=tasks.findActiveAssignedTask(task.hotelId,setOf(selectedEmployeeId.toString(),assignment.assigneeId),task.id)
        log.info("FLASH_ACTIVE_TASK_LOOKUP employeeId={} foundTaskId={} foundTaskState={}",selectedEmployeeId,active?.id,active?.status)
        if(active==null) {
            log.info("FLASH_ACTIVE_TASK_NOT_FOUND employeeId={} searchedStates=STARTED,IN_PROGRESS reason=NO_ACTIVE_TASK",selectedEmployeeId)
            log.info("FLASH_INTERRUPTION_DECISION flashTaskId={} activeTaskId={} sameEmployee={} eligibleState={} priorityEligible={} decision=SKIP reason=NO_ACTIVE_TASK",task.id,null,false,false,true)
            return
        }
        val sameEmployee=active.assignment?.assigneeId in setOf(selectedEmployeeId.toString(),assignment.assigneeId)
        log.info("FLASH_INTERRUPTION_DECISION flashTaskId={} activeTaskId={} sameEmployee={} eligibleState={} priorityEligible={} decision={} reason={}",task.id,active.id,sameEmployee,true,true,if(sameEmployee) "INTERRUPT" else "SKIP",if(sameEmployee) "ELIGIBLE" else "DIFFERENT_EMPLOYEE")
        if(!sameEmployee) return
        val result=interruptions.interrupt(InterruptTaskCommand(task.hotelId,selectedEmployeeId,assignment.displayName,active.id,task.id,"Automatic high-priority interruption","automatic-flash:${task.id}",employeeAssigneeId=active.assignment!!.assigneeId))
        log.info("FLASH_INTERRUPTION_CREATED flashTaskId={} interruptedTaskId={} employeeId={} source={}",task.id,result.pausedTaskId,selectedEmployeeId,result.source)
        log.info("FLASH_TASK_PAUSED taskId={} previousState={} newState=WAITING",active.id,active.status)
    }

    override fun completed(task: Task) = interruptions.interruptingTaskCompleted(task)
}
