package com.hotelopai.damage.api
import com.hotelopai.damage.application.*
import com.hotelopai.shared.security.*
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal
import java.util.UUID
data class DamageRequest(val roomNumber:String?,val location:String,val category:String,val description:String,val suggestedAmount:BigDecimal?=null,val currency:String?=null,val visionAnalysisId:UUID?=null,val aiProvider:String?=null,val aiConfidence:BigDecimal?=null,val attachmentIds:List<UUID> = emptyList(),val idempotencyKey:String)
data class DamageApprovalRequest(val amount:BigDecimal,val currency:String="EUR",val postToPms:Boolean=false)
@RestController @RequestMapping("/api/v1/internal/damage") class InternalDamageController(private val service:DamageService,private val current:CurrentUserContextResolver){
 @GetMapping @PreAuthorize(PermissionExpressions.DAMAGE_REVIEW) fun list()=service.list(current.current().hotelId)
 @PostMapping @PreAuthorize(PermissionExpressions.HOUSEKEEPING_OPERATIONS) fun create(@RequestBody r:DamageRequest)=service.create(CreateDamageReport(current.current().hotelId,r.roomNumber,r.location,r.category,r.description,r.suggestedAmount,r.currency,r.visionAnalysisId,r.aiProvider,r.aiConfidence,r.attachmentIds,current.current().userId,r.idempotencyKey))
 @PostMapping("/{id}/approve") @PreAuthorize(PermissionExpressions.DAMAGE_REVIEW) fun approve(@PathVariable id:UUID,@RequestBody r:DamageApprovalRequest)=service.approve(id,current.current().hotelId,current.current().userId,r.amount,r.currency,r.postToPms)
 @PostMapping("/{id}/reject") @PreAuthorize(PermissionExpressions.DAMAGE_REVIEW) fun reject(@PathVariable id:UUID)=service.reject(id,current.current().hotelId,current.current().userId)
}
