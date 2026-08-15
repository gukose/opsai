package com.hotelopai.workforce.api
import com.hotelopai.workforce.application.*
import com.hotelopai.shared.security.*
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.util.UUID
data class HandoverRequest(val targetDepartmentId:UUID,val roomNumber:String?=null,val tags:List<String> = emptyList(),val note:String,val importance:HandoverImportance=HandoverImportance.NORMAL,val requiredRead:Boolean=false,val taskId:UUID?=null)
@RestController @RequestMapping("/api/v1/internal/shift-handovers") class InternalShiftHandoverController(private val service:ShiftHandoverService,private val current:CurrentUserContextResolver){
 @PostMapping @PreAuthorize(PermissionExpressions.SHIFT_OPERATIONS) fun create(@RequestBody r:HandoverRequest)=service.create(CreateShiftHandover(current.current().hotelId,current.current().userId,r.targetDepartmentId,r.roomNumber,r.tags,r.note,r.importance,r.requiredRead,r.taskId))
 @GetMapping("/unread") @PreAuthorize(PermissionExpressions.SHIFT_OPERATIONS) fun unread(@RequestParam departmentId:UUID)=service.unread(current.current().hotelId,current.current().userId,departmentId)
 @PostMapping("/{id}/acknowledge") @PreAuthorize(PermissionExpressions.SHIFT_OPERATIONS) fun acknowledge(@PathVariable id:UUID)=service.acknowledge(id,current.current().hotelId,current.current().userId)
}
