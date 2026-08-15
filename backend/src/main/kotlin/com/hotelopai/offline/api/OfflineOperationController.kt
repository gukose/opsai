package com.hotelopai.offline.api
import com.hotelopai.offline.application.*
import com.hotelopai.shared.security.*
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
data class OfflineOperationRequest(val clientOperationId:String,val type:OfflineOperationType,val resourceId:String)
@RestController @RequestMapping("/api/v1/internal/offline-operations") class OfflineOperationController(private val service:OfflineOperationService,private val current:CurrentUserContextResolver){
 @PostMapping @PreAuthorize("${PermissionExpressions.TASK_START} or ${PermissionExpressions.TASK_PAUSE} or ${PermissionExpressions.TASK_RESUME} or ${PermissionExpressions.TASK_COMPLETE}") fun submit(@RequestBody r:OfflineOperationRequest)=service.submit(current.current().hotelId,current.current().userId,OfflineSubmission(r.clientOperationId,r.type,r.resourceId))
}
