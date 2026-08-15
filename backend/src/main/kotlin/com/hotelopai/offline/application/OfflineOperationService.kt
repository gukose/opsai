package com.hotelopai.offline.application
import com.hotelopai.shared.kernel.UuidV7Generator
import com.hotelopai.task.application.TaskLifecycleService
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.time.Clock
import java.util.UUID
enum class OfflineOperationType { TASK_START, TASK_PAUSE, TASK_RESUME, TASK_COMPLETE }
data class OfflineSubmission(val clientOperationId:String,val type:OfflineOperationType,val resourceId:String)
data class OfflineResult(val clientOperationId:String,val status:String,val resultReference:String?)
@Service class OfflineOperationService(private val jdbc:NamedParameterJdbcTemplate,private val tasks:TaskLifecycleService,private val clock:Clock=Clock.systemUTC()){
 @Transactional fun submit(hotelId:UUID,userId:UUID,s:OfflineSubmission):OfflineResult {require(s.clientOperationId.length in 8..128);existing(hotelId,userId,s.clientOperationId)?.let{return it};val now=clock.instant();val result=when(s.type){OfflineOperationType.TASK_START->tasks.startTask(s.resourceId,hotelId,now);OfflineOperationType.TASK_PAUSE->tasks.pauseTask(s.resourceId,hotelId,now);OfflineOperationType.TASK_RESUME->tasks.resumeTask(s.resourceId,hotelId,now);OfflineOperationType.TASK_COMPLETE->tasks.completeTask(s.resourceId,hotelId,now)};jdbc.update("""insert into offline_operation(id,hotel_id,user_id,client_operation_id,operation_type,resource_id,payload_hash,status,result_reference,submitted_at,processed_at)
 values(:id,:hotel,:user,:client,:type,:resource,:hash,'PROCESSED',:result,:now,:now)""",mapOf("id" to UuidV7Generator.generate(now),"hotel" to hotelId,"user" to userId,"client" to s.clientOperationId,"type" to s.type.name,"resource" to s.resourceId,"hash" to hash("${s.type}:${s.resourceId}"),"result" to result.id.toString(),"now" to now));return OfflineResult(s.clientOperationId,"PROCESSED",result.id.toString())}
 private fun existing(hotel:UUID,user:UUID,client:String)=jdbc.query("select status,result_reference from offline_operation where hotel_id=:hotel and user_id=:user and client_operation_id=:client",mapOf("hotel" to hotel,"user" to user,"client" to client)){rs,_->OfflineResult(client,rs.getString(1),rs.getString(2))}.firstOrNull()
 private fun hash(v:String)=MessageDigest.getInstance("SHA-256").digest(v.toByteArray()).joinToString(""){"%02x".format(it)}
}
