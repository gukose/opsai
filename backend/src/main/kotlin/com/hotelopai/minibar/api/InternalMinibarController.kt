package com.hotelopai.minibar.api
import com.hotelopai.minibar.application.*
import com.hotelopai.shared.security.*
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal
import java.util.UUID
data class MinibarItemRequest(val itemId:UUID,val quantity:BigDecimal,val source:String="MANUAL")
data class CompleteMinibarRequest(val taskId:UUID,val roomNumber:String,val locationId:UUID,val items:List<MinibarItemRequest>,val idempotencyKey:String,val currency:String="EUR")
@RestController @RequestMapping("/api/v1/internal/minibar") class InternalMinibarController(private val service:MinibarService,private val current:CurrentUserContextResolver){
 @PostMapping("/complete") @PreAuthorize(PermissionExpressions.MINIBAR_OPERATIONS) fun complete(@RequestBody r:CompleteMinibarRequest)=service.complete(CompleteMinibarCommand(current.current().hotelId,r.taskId,r.roomNumber,r.locationId,r.items.map{MinibarItemInput(it.itemId,it.quantity,it.source)},r.idempotencyKey,current.current().userId,r.currency))
}
