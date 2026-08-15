package com.hotelopai.billing.application
import com.hotelopai.shared.kernel.UuidV7Generator
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.time.*
import java.util.UUID
data class BillingCounter(val id:UUID,val hotelId:UUID,val businessDate:LocalDate,val occupiedRoomCount:Int,val sourceProvider:String,val recordedAt:Instant)
data class BillingCorrection(val id:UUID,val billingCounterId:UUID,val previousCount:Int,val correctedCount:Int,val reasonCode:String,val actorUserId:UUID,val createdAt:Instant)
@Service class BillingCounterService(private val jdbc:NamedParameterJdbcTemplate,private val clock:Clock=Clock.systemUTC()){
 @Transactional fun record(hotelId:UUID,date:LocalDate,count:Int,provider:String,sourceEvent:String,key:String):BillingCounter {require(count>=0&&provider.isNotBlank()&&sourceEvent.isNotBlank()&&key.isNotBlank());findByKey(hotelId,key)?.let{return it};val now=clock.instant();val id=UuidV7Generator.generate(now);jdbc.update("""insert into hotel_billing_counter(id,hotel_id,business_date,occupied_room_count,source_provider,source_event_hash,idempotency_key,recorded_at)
 values(:id,:hotel,:date,:count,:provider,:event,:key,:now) on conflict(hotel_id,idempotency_key) do nothing""",mapOf("id" to id,"hotel" to hotelId,"date" to date,"count" to count,"provider" to provider,"event" to hash(sourceEvent),"key" to key,"now" to now));return findByKey(hotelId,key)!! }
 fun list(hotelId:UUID)=query("hotel_id=:hotel",mapOf("hotel" to hotelId))
 @Transactional fun correct(hotelId:UUID,counterId:UUID,correctedCount:Int,reasonCode:String,actorUserId:UUID):BillingCorrection {
  require(correctedCount>=0);require(reasonCode.matches(Regex("[A-Z0-9_]{3,64}"))){"reasonCode must be a safe code"}
  val current=jdbc.query("select occupied_room_count from hotel_billing_counter where id=:id and hotel_id=:hotel for update",mapOf("id" to counterId,"hotel" to hotelId)){rs,_->rs.getInt(1)}.firstOrNull()?:throw NoSuchElementException("Billing counter not found")
  val now=clock.instant();val id=UuidV7Generator.generate(now)
  jdbc.update("insert into billing_counter_correction(id,hotel_id,billing_counter_id,previous_count,corrected_count,reason_code,actor_user_id,created_at) values(:id,:hotel,:counter,:previous,:corrected,:reason,:actor,:now)",mapOf("id" to id,"hotel" to hotelId,"counter" to counterId,"previous" to current,"corrected" to correctedCount,"reason" to reasonCode,"actor" to actorUserId,"now" to now))
  jdbc.update("update hotel_billing_counter set occupied_room_count=:count,correction_reason=:reason,corrected_by=:actor where id=:id and hotel_id=:hotel",mapOf("count" to correctedCount,"reason" to reasonCode,"actor" to actorUserId,"id" to counterId,"hotel" to hotelId))
  return BillingCorrection(id,counterId,current,correctedCount,reasonCode,actorUserId,now)
 }
 fun corrections(hotelId:UUID,counterId:UUID)=jdbc.query("select * from billing_counter_correction where hotel_id=:hotel and billing_counter_id=:counter order by created_at",mapOf("hotel" to hotelId,"counter" to counterId)){rs,_->BillingCorrection(rs.getObject("id",UUID::class.java),counterId,rs.getInt("previous_count"),rs.getInt("corrected_count"),rs.getString("reason_code"),rs.getObject("actor_user_id",UUID::class.java),rs.getTimestamp("created_at").toInstant())}
 private fun findByKey(hotelId:UUID,key:String)=query("hotel_id=:hotel and idempotency_key=:key",mapOf("hotel" to hotelId,"key" to key)).firstOrNull()
 private fun query(where:String,p:Map<String,Any>)=jdbc.query("select * from hotel_billing_counter where $where order by business_date desc",p){rs,_->BillingCounter(rs.getObject("id",UUID::class.java),rs.getObject("hotel_id",UUID::class.java),rs.getDate("business_date").toLocalDate(),rs.getInt("occupied_room_count"),rs.getString("source_provider"),rs.getTimestamp("recorded_at").toInstant())}
 private fun hash(v:String)=MessageDigest.getInstance("SHA-256").digest(v.toByteArray()).joinToString(""){"%02x".format(it)}
}
