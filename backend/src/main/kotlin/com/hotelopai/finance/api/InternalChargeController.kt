package com.hotelopai.finance.api
import com.hotelopai.finance.application.*
import com.hotelopai.shared.security.*
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal
import java.util.UUID
data class ChargeProposalRequest(val type:ChargeType,val sourceId:UUID,val roomNumber:String,val amount:BigDecimal,val currency:String,val idempotencyKey:String)
data class RejectChargeRequest(val reason:String)
@RestController @RequestMapping("/api/v1/internal/charges") class InternalChargeController(private val service:FinancialChargeService,private val current:CurrentUserContextResolver){
 @GetMapping @PreAuthorize("${PermissionExpressions.MINIBAR_OPERATIONS} or ${PermissionExpressions.DAMAGE_REVIEW}") fun list()=service.list(current.current().hotelId)
 @PostMapping @PreAuthorize("${PermissionExpressions.MINIBAR_OPERATIONS} or ${PermissionExpressions.DAMAGE_REVIEW}") fun propose(@RequestBody r:ChargeProposalRequest)=service.propose(CreateChargeProposal(current.current().hotelId,r.type,r.sourceId,r.roomNumber,r.amount,r.currency,r.idempotencyKey))
 @PostMapping("/{id}/approve") @PreAuthorize("${PermissionExpressions.MINIBAR_OPERATIONS} or ${PermissionExpressions.DAMAGE_REVIEW}") fun approve(@PathVariable id:UUID)=service.approveAndPost(id,current.current().hotelId,current.current().userId)
 @PostMapping("/{id}/reject") @PreAuthorize("${PermissionExpressions.MINIBAR_OPERATIONS} or ${PermissionExpressions.DAMAGE_REVIEW}") fun reject(@PathVariable id:UUID,@RequestBody r:RejectChargeRequest)=service.reject(id,current.current().hotelId,current.current().userId,r.reason)
}
