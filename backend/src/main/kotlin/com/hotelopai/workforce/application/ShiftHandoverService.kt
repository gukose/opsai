package com.hotelopai.workforce.application
import com.hotelopai.shared.kernel.UuidV7Generator
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.util.UUID
enum class HandoverImportance { NORMAL, IMPORTANT, CRITICAL }
data class ShiftHandover(val id:UUID,val authorUserId:UUID,val targetDepartmentId:UUID,val roomNumber:String?,val tags:List<String>,val note:String,val importance:HandoverImportance,val requiredRead:Boolean,val taskId:UUID?,val createdAt:Instant,val acknowledged:Boolean)
data class CreateShiftHandover(val hotelId:UUID,val author:UUID,val departmentId:UUID,val roomNumber:String?,val tags:List<String>,val note:String,val importance:HandoverImportance,val requiredRead:Boolean,val taskId:UUID?)
@Service class ShiftHandoverService(private val jdbc:NamedParameterJdbcTemplate,private val clock:Clock=Clock.systemUTC()){
 @Transactional fun create(c:CreateShiftHandover):ShiftHandover{require(c.note.length in 1..2000);val now=clock.instant();val id=UuidV7Generator.generate(now);jdbc.update("insert into shift_handover(id,hotel_id,author_user_id,target_department_id,room_number,tags,note,importance,required_read,task_id,created_at) values(:id,:hotel,:author,:department,:room,:tags,:note,:importance,:required,:task,:now)",mapOf("id" to id,"hotel" to c.hotelId,"author" to c.author,"department" to c.departmentId,"room" to c.roomNumber,"tags" to c.tags.map{it.take(30)}.distinct().toTypedArray(),"note" to c.note,"importance" to c.importance.name,"required" to c.requiredRead,"task" to c.taskId,"now" to now));return get(id,c.hotelId,c.author)}
 fun unread(hotelId:UUID,userId:UUID,departmentId:UUID)=jdbc.query("""select h.*,a.user_id acknowledged_user from shift_handover h left join shift_handover_ack a on a.handover_id=h.id and a.user_id=:user where h.hotel_id=:hotel and h.target_department_id=:department and h.required_read=true and a.user_id is null order by case h.importance when 'CRITICAL' then 0 when 'IMPORTANT' then 1 else 2 end,h.created_at desc""",mapOf("user" to userId,"hotel" to hotelId,"department" to departmentId)){rs,_->map(rs,false)}
 @Transactional fun acknowledge(id:UUID,hotelId:UUID,userId:UUID){get(id,hotelId,userId);jdbc.update("insert into shift_handover_ack(handover_id,user_id,acknowledged_at) values(:id,:user,:now) on conflict do nothing",mapOf("id" to id,"user" to userId,"now" to clock.instant()))}
 private fun get(id:UUID,hotelId:UUID,userId:UUID)=jdbc.query("select h.*,exists(select 1 from shift_handover_ack a where a.handover_id=h.id and a.user_id=:user) ack from shift_handover h where h.id=:id and h.hotel_id=:hotel",mapOf("id" to id,"hotel" to hotelId,"user" to userId)){rs,_->map(rs,rs.getBoolean("ack"))}.firstOrNull()?:throw NoSuchElementException("Handover not found")
 private fun map(rs:java.sql.ResultSet,ack:Boolean)=ShiftHandover(rs.getObject("id",UUID::class.java),rs.getObject("author_user_id",UUID::class.java),rs.getObject("target_department_id",UUID::class.java),rs.getString("room_number"),(rs.getArray("tags").array as Array<*>).filterIsInstance<String>(),rs.getString("note"),HandoverImportance.valueOf(rs.getString("importance")),rs.getBoolean("required_read"),rs.getObject("task_id",UUID::class.java),rs.getTimestamp("created_at").toInstant(),ack)
}
