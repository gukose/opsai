package com.hotelopai.task.api
import com.hotelopai.task.application.*
import com.hotelopai.shared.security.*
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.util.UUID
data class InterruptRequest(val employeeId:UUID,val employeeDisplayName:String,val activeTaskId:UUID,val interruptingTaskId:UUID,val reason:String,val idempotencyKey:String,val autoStart:Boolean=true)
@RestController @RequestMapping("/api/v1/internal/task-interruptions") class InternalInterruptionController(private val service:SmartInterruptionService,private val current:CurrentUserContextResolver){
 @PostMapping @PreAuthorize(PermissionExpressions.TASK_ASSIGN) fun interrupt(@RequestBody r:InterruptRequest)=service.interrupt(InterruptTaskCommand(current.current().hotelId,r.employeeId,r.employeeDisplayName,r.activeTaskId,r.interruptingTaskId,r.reason,r.idempotencyKey,r.autoStart))
 @PostMapping("/{id}/complete") @PreAuthorize(PermissionExpressions.TASK_COMPLETE) fun complete(@PathVariable id:UUID)=service.completeAndResume(id,current.current().hotelId)
}
