package com.hotelopai.guest.api
import com.hotelopai.guest.application.*
import com.hotelopai.shared.security.*
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.util.UUID
data class IssueGuestSessionRequest(val roomNumber:String,val ttlHours:Long=24)
data class GuestInboundRequest(val token:String,val providerMessageKey:String,val text:String)
data class SurveyScheduleRequest(val guestSessionId:UUID,val businessDate:java.time.LocalDate)
data class SurveyResponseRequest(val category:SatisfactionCategory)
@RestController @RequestMapping("/api/v1/internal/guest") class InternalGuestOperationsController(private val service:GuestOperationsService,private val current:CurrentUserContextResolver){
 @PostMapping("/sessions") @PreAuthorize(PermissionExpressions.GUEST_MESSAGING_OPERATIONS) fun issue(@RequestBody r:IssueGuestSessionRequest)=service.issue(current.current().hotelId,r.roomNumber,java.time.Duration.ofHours(r.ttlHours.coerceIn(1,168)))
 @DeleteMapping("/sessions/{id}") @PreAuthorize(PermissionExpressions.GUEST_MESSAGING_OPERATIONS) fun revoke(@PathVariable id:UUID)=service.revoke(id,current.current().hotelId)
 @PostMapping("/internal-demo/inbound") @PreAuthorize(PermissionExpressions.GUEST_MESSAGING_OPERATIONS) fun inbound(@RequestBody r:GuestInboundRequest)=service.inbound(r.token,r.providerMessageKey,r.text)
}

@RestController @RequestMapping("/api/v1/internal/guest/surveys") class InternalSatisfactionSurveyController(private val surveys:SatisfactionSurveyService,private val current:CurrentUserContextResolver){
 @PostMapping @PreAuthorize(PermissionExpressions.GUEST_MESSAGING_OPERATIONS) fun schedule(@RequestBody r:SurveyScheduleRequest)=surveys.schedule(current.current().hotelId,r.guestSessionId,r.businessDate)
 @PostMapping("/{id}/send") @PreAuthorize(PermissionExpressions.GUEST_MESSAGING_OPERATIONS) fun send(@PathVariable id:UUID)=surveys.sendPending()
 @PostMapping("/{id}/response") @PreAuthorize(PermissionExpressions.GUEST_MESSAGING_OPERATIONS) fun respond(@PathVariable id:UUID,@RequestBody r:SurveyResponseRequest)=surveys.respond(current.current().hotelId,id,r.category)
}
