package com.hotelopai.minibar.application

import com.hotelopai.finance.application.*
import com.hotelopai.inventory.application.InventoryService
import com.hotelopai.inventory.domain.*
import com.hotelopai.shared.kernel.UuidV7Generator
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Clock
import java.util.UUID

enum class MinibarResult { NO_CONSUMPTION, CONSUMPTION_RECORDED }
data class MinibarItemInput(val itemId:UUID,val quantity:BigDecimal,val source:String="MANUAL")
data class CompleteMinibarCommand(val hotelId:UUID,val taskId:UUID,val roomNumber:String,val locationId:UUID,val items:List<MinibarItemInput>,val idempotencyKey:String,val actorUserId:UUID,val currency:String="EUR")
data class MinibarCompletion(val inspectionId:UUID,val result:MinibarResult,val chargeProposal:ChargeProposal?)

@Service class MinibarService(private val jdbc:NamedParameterJdbcTemplate,private val inventory:InventoryService,private val charges:FinancialChargeService,private val clock:Clock=Clock.systemUTC()){
 @Transactional fun complete(c:CompleteMinibarCommand):MinibarCompletion {
  require(c.idempotencyKey.isNotBlank()); existing(c.hotelId,c.idempotencyKey)?.let{return it}
  val now=clock.instant();val id=UuidV7Generator.generate(now);val result=if(c.items.isEmpty())MinibarResult.NO_CONSUMPTION else MinibarResult.CONSUMPTION_RECORDED
  jdbc.update("insert into minibar_inspection(id,hotel_id,task_id,room_number,result,completed_at,idempotency_key,created_at) values(:id,:hotel,:task,:room,:result,:now,:key,:now)",mapOf("id" to id,"hotel" to c.hotelId,"task" to c.taskId,"room" to c.roomNumber,"result" to result.name,"now" to now,"key" to c.idempotencyKey))
  var amount=BigDecimal.ZERO
  c.items.forEach { input -> require(input.quantity>BigDecimal.ZERO);val item=inventory.item(c.hotelId,input.itemId);require(item.active);jdbc.update("insert into minibar_inspection_item(inspection_id,inventory_item_id,quantity,source) values(:inspection,:item,:quantity,:source)",mapOf("inspection" to id,"item" to input.itemId,"quantity" to input.quantity,"source" to input.source));inventory.record(RecordInventoryTransaction(c.hotelId,input.itemId,c.locationId,null,InventoryTransactionType.MINIBAR_CONSUMPTION,input.quantity,"minibar:${c.idempotencyKey}",c.actorUserId));amount += (item.unitPrice?:BigDecimal.ZERO)*input.quantity }
  val proposal=if(amount>BigDecimal.ZERO)charges.propose(CreateChargeProposal(c.hotelId,ChargeType.MINIBAR,id,c.roomNumber,amount,c.currency,"minibar-charge:${c.idempotencyKey}")) else null
  return MinibarCompletion(id,result,proposal)
 }
 private fun existing(hotelId:UUID,key:String)=jdbc.query("select id,result from minibar_inspection where hotel_id=:hotel and idempotency_key=:key",mapOf("hotel" to hotelId,"key" to key)){rs,_->MinibarCompletion(rs.getObject(1,UUID::class.java),MinibarResult.valueOf(rs.getString(2)),null)}.firstOrNull()
}
