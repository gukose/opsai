package com.hotelopai.finance.application

import com.hotelopai.observability.OperationalObservability
import com.hotelopai.pms.application.PmsCapability
import com.hotelopai.pms.application.PmsProviderRegistry
import com.hotelopai.pms.domain.FolioChargeRequest
import com.hotelopai.shared.kernel.UuidV7Generator
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.util.UUID

enum class ChargeType { MINIBAR, DAMAGE }
enum class ChargeStatus { REVIEW_REQUIRED, APPROVED, REJECTED, POSTED, RETRYABLE_FAILURE, FAILED }
data class ChargeProposal(val id:UUID,val hotelId:UUID,val type:ChargeType,val sourceId:UUID,val roomNumber:String,val amount:BigDecimal,val currency:String,val status:ChargeStatus,val idempotencyKey:String,val requestedAt:Instant,val providerReference:String?=null)
data class CreateChargeProposal(val hotelId:UUID,val type:ChargeType,val sourceId:UUID,val roomNumber:String,val amount:BigDecimal,val currency:String,val idempotencyKey:String)

@Service
class FinancialChargeService(private val jdbc:NamedParameterJdbcTemplate,private val pms:PmsProviderRegistry,private val metrics:OperationalObservability,private val clock:Clock=Clock.systemUTC()) {
 @Transactional fun propose(c:CreateChargeProposal):ChargeProposal {
  require(c.amount>=BigDecimal.ZERO);require(c.currency.matches(Regex("[A-Z]{3}")));require(c.idempotencyKey.isNotBlank())
  findByKey(c.hotelId,c.idempotencyKey)?.let{return it};val now=clock.instant();val id=UuidV7Generator.generate(now)
  jdbc.update("""insert into financial_charge_proposal(id,hotel_id,charge_type,source_id,room_number,amount,currency,status,idempotency_key,requested_at)
   values(:id,:hotel,:type,:source,:room,:amount,:currency,'REVIEW_REQUIRED',:key,:now) on conflict do nothing""",mapOf("id" to id,"hotel" to c.hotelId,"type" to c.type.name,"source" to c.sourceId,"room" to c.roomNumber,"amount" to c.amount,"currency" to c.currency,"key" to c.idempotencyKey,"now" to now))
  return findByKey(c.hotelId,c.idempotencyKey)!!
 }
 @Transactional fun approveAndPost(id:UUID,hotelId:UUID,reviewer:UUID):ChargeProposal {
  val proposal=find(id,hotelId); if(proposal.status==ChargeStatus.POSTED)return proposal
  require(proposal.status in setOf(ChargeStatus.REVIEW_REQUIRED,ChargeStatus.RETRYABLE_FAILURE)){"Charge is not reviewable"}
  val provider=pms.activeProviderRequiring(PmsCapability.FOLIO_CHARGE)
  jdbc.update("update financial_charge_proposal set status='APPROVED',reviewed_by=:reviewer,reviewed_at=:now where id=:id and hotel_id=:hotel",mapOf("reviewer" to reviewer,"now" to clock.instant(),"id" to id,"hotel" to hotelId))
  val result=provider.postFolioCharge(FolioChargeRequest(proposal.roomNumber,proposal.amount,proposal.currency,"${proposal.type.name.lowercase()} charge",proposal.idempotencyKey))
  val status=if(result.success) ChargeStatus.POSTED else if(result.retryable) ChargeStatus.RETRYABLE_FAILURE else ChargeStatus.FAILED
  jdbc.update("update financial_charge_proposal set status=:status,provider_id=:provider,provider_reference=:reference,posted_at=:posted,failure_category=:failure where id=:id and hotel_id=:hotel",mapOf("status" to status.name,"provider" to provider.id.value,"reference" to result.providerReference,"posted" to if(result.success) result.occurredAt else null,"failure" to result.failureCategory,"id" to id,"hotel" to hotelId))
  metrics.incrementCounter("hotelopai.financial.charge.total","operation" to "post","outcome" to status.name.lowercase(),"charge_type" to proposal.type.name.lowercase())
  return find(id,hotelId)
 }
 @Transactional fun reject(id:UUID,hotelId:UUID,reviewer:UUID,reason:String):ChargeProposal { require(reason.isNotBlank());val p=find(id,hotelId);require(p.status==ChargeStatus.REVIEW_REQUIRED);jdbc.update("update financial_charge_proposal set status='REJECTED',reviewed_by=:reviewer,reviewed_at=:now,rejection_reason=:reason where id=:id and hotel_id=:hotel",mapOf("reviewer" to reviewer,"now" to clock.instant(),"reason" to reason,"id" to id,"hotel" to hotelId));return find(id,hotelId) }
 fun find(id:UUID,hotelId:UUID)=query("where id=:id and hotel_id=:hotel",mapOf("id" to id,"hotel" to hotelId)).firstOrNull()?:throw NoSuchElementException("Charge proposal not found")
 fun list(hotelId:UUID)=query("where hotel_id=:hotel",mapOf("hotel" to hotelId))
 private fun findByKey(hotelId:UUID,key:String)=query("where hotel_id=:hotel and idempotency_key=:key",mapOf("hotel" to hotelId,"key" to key)).firstOrNull()
 private fun query(where:String,params:Map<String,Any>)=jdbc.query("select * from financial_charge_proposal $where order by requested_at desc",params){rs,_->ChargeProposal(rs.getObject("id",UUID::class.java),rs.getObject("hotel_id",UUID::class.java),ChargeType.valueOf(rs.getString("charge_type")),rs.getObject("source_id",UUID::class.java),rs.getString("room_number"),rs.getBigDecimal("amount"),rs.getString("currency"),ChargeStatus.valueOf(rs.getString("status")),rs.getString("idempotency_key"),rs.getTimestamp("requested_at").toInstant(),rs.getString("provider_reference"))}
}
