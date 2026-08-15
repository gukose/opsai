package com.hotelopai.recovery.api
import com.hotelopai.recovery.application.*
import com.hotelopai.shared.security.*
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.util.UUID
data class RiskRequest(val guestSessionId:UUID,val signals:RiskSignals)
data class RecoveryCompleteRequest(val outcome:String,val followUpStatus:String)
@RestController @RequestMapping("/api/v1/internal/recovery") class InternalRiskRecoveryController(private val service:RiskRecoveryService,private val current:CurrentUserContextResolver){
 @PostMapping("/risk") @PreAuthorize(PermissionExpressions.SERVICE_RECOVERY_OPERATIONS) fun assess(@RequestBody r:RiskRequest)=service.assess(current.current().hotelId,r.guestSessionId,r.signals)
 @PostMapping("/{id}/complete") @PreAuthorize(PermissionExpressions.SERVICE_RECOVERY_OPERATIONS) fun complete(@PathVariable id:UUID,@RequestBody r:RecoveryCompleteRequest)=service.complete(id,current.current().hotelId,r.outcome,r.followUpStatus)
}
