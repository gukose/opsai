package com.hotelopai.pms.application
import com.hotelopai.observability.OperationalObservability
import com.hotelopai.shared.kernel.UuidV7Generator
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.UUID
import java.sql.Timestamp
import org.slf4j.LoggerFactory
data class RoomReadyResult(val operationId:UUID,val status:String,val providerId:String)
@Service class RoomReadyService(private val jdbc:NamedParameterJdbcTemplate,private val registry:PmsProviderRegistry,private val metrics:OperationalObservability,private val clock:Clock=Clock.systemUTC()){
 @Transactional fun markReady(hotelId:UUID,roomNumber:String,idempotencyKey:String):RoomReadyResult {require(roomNumber.isNotBlank()&&idempotencyKey.isNotBlank());existing(hotelId,idempotencyKey)?.let{return it};val provider=registry.activeProviderRequiring(PmsCapability.ROOM_READY_UPDATE);val now=clock.instant();val id=UuidV7Generator.generate(now);jdbc.update("insert into pms_outbound_operation(id,hotel_id,operation_type,resource_reference,provider_id,idempotency_key,status,attempt_count,requested_at) values(:id,:hotel,'ROOM_READY',:resource,:provider,:key,'PENDING',1,:now)",mapOf("id" to id,"hotel" to hotelId,"resource" to roomNumber,"provider" to provider.id.value,"key" to idempotencyKey,"now" to Timestamp.from(now)));logger.info("ROOM_READY_OUTBOUND_OPERATION hotelId={} room={} operationId={} requestedAtType=Timestamp outcome=SUCCESS",hotelId,roomNumber,id);return try{val result=provider.markRoomReady(roomNumber,idempotencyKey);jdbc.update("update pms_outbound_operation set status='SUCCEEDED',provider_reference=:reference,completed_at=:now where id=:id",mapOf("reference" to (result.entityId?:result.verificationLogId.toString()),"now" to Timestamp.from(clock.instant()),"id" to id));metrics.incrementCounter("hotelopai.pms.outbound.total","operation" to "room_ready","outcome" to "success","provider" to provider.id.value);RoomReadyResult(id,"SUCCEEDED",provider.id.value)}catch(e:RuntimeException){jdbc.update("update pms_outbound_operation set status='RETRYABLE_FAILURE',failure_category='PROVIDER_FAILURE' where id=:id",mapOf("id" to id));metrics.incrementCounter("hotelopai.pms.outbound.total","operation" to "room_ready","outcome" to "failure","provider" to provider.id.value);throw e}}
 private fun existing(hotel:UUID,key:String)=jdbc.query("select id,status,provider_id from pms_outbound_operation where hotel_id=:hotel and idempotency_key=:key",mapOf("hotel" to hotel,"key" to key)){rs,_->RoomReadyResult(rs.getObject(1,UUID::class.java),rs.getString(2),rs.getString(3))}.firstOrNull()
 private val logger = LoggerFactory.getLogger(RoomReadyService::class.java)
}
