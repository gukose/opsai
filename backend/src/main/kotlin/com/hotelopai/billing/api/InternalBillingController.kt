package com.hotelopai.billing.api
import com.hotelopai.billing.application.*
import com.hotelopai.shared.security.*
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.time.LocalDate
data class BillingRequest(val businessDate:LocalDate,val occupiedRoomCount:Int,val sourceProvider:String,val sourceEvent:String,val idempotencyKey:String)
data class BillingCorrectionRequest(val correctedCount:Int,val reasonCode:String)
@RestController @RequestMapping("/api/v1/internal/billing") class InternalBillingController(private val service:BillingCounterService,private val current:CurrentUserContextResolver){
 @GetMapping @PreAuthorize(PermissionExpressions.BILLING_REPORTS) fun list()=service.list(current.current().hotelId)
 @PostMapping @PreAuthorize(PermissionExpressions.BILLING_REPORTS) fun record(@RequestBody r:BillingRequest)=service.record(current.current().hotelId,r.businessDate,r.occupiedRoomCount,r.sourceProvider,r.sourceEvent,r.idempotencyKey)
 @PostMapping("/{id}/corrections") @PreAuthorize(PermissionExpressions.BILLING_REPORTS) fun correct(@PathVariable id:java.util.UUID,@RequestBody r:BillingCorrectionRequest)=current.current().let{service.correct(it.hotelId,id,r.correctedCount,r.reasonCode,it.userId)}
 @GetMapping("/{id}/corrections") @PreAuthorize(PermissionExpressions.BILLING_REPORTS) fun corrections(@PathVariable id:java.util.UUID)=service.corrections(current.current().hotelId,id)
}
