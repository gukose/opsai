package com.hotelopai.guest.application

import com.hotelopai.recovery.application.RiskRecoveryService
import com.hotelopai.recovery.application.RiskSignals
import com.hotelopai.shared.kernel.UuidV7Generator
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

enum class SatisfactionCategory(val score:Int){VERY_DISSATISFIED(1),DISSATISFIED(2),NEUTRAL(3),SATISFIED(4),VERY_SATISFIED(5)}
data class SatisfactionSurvey(val id:UUID,val guestSessionId:UUID,val businessDate:LocalDate,val status:String,val category:SatisfactionCategory?)

@Service class SatisfactionSurveyService(private val jdbc:NamedParameterJdbcTemplate,private val guest:GuestOperationsService,private val risk:RiskRecoveryService,private val clock:Clock=Clock.systemUTC()){
 @Transactional fun scheduleEligible(hotelId:UUID,businessDate:LocalDate):Int {val sessions=jdbc.query("select id from guest_session where hotel_id=:hotel and revoked_at is null and expires_at>:now",mapOf("hotel" to hotelId,"now" to clock.instant())){rs,_->rs.getObject(1,UUID::class.java)};return sessions.count{schedule(hotelId,it,businessDate)!=null}}
 @Transactional fun schedule(hotelId:UUID,sessionId:UUID,businessDate:LocalDate):SatisfactionSurvey? {val key="checkout-survey:$sessionId:$businessDate";find(hotelId,key)?.let{return it};val exists=jdbc.queryForObject("select count(*) from guest_session where id=:id and hotel_id=:hotel and revoked_at is null",mapOf("id" to sessionId,"hotel" to hotelId),Long::class.java)?:0;if(exists==0L)return null;val now=clock.instant();val id=UuidV7Generator.generate(now);jdbc.update("insert into satisfaction_survey(id,hotel_id,guest_session_id,business_date,status,idempotency_key,created_at) values(:id,:hotel,:session,:date,'SCHEDULED',:key,:now)",mapOf("id" to id,"hotel" to hotelId,"session" to sessionId,"date" to businessDate,"key" to key,"now" to now));return SatisfactionSurvey(id,sessionId,businessDate,"SCHEDULED",null)}
 @Transactional fun sendPending(limit:Int=100):Int {val rows=jdbc.query("select id,hotel_id,guest_session_id,business_date from satisfaction_survey where status='SCHEDULED' order by created_at limit :limit for update skip locked",mapOf("limit" to limit.coerceIn(1,500))){rs,_->arrayOf(rs.getObject(1,UUID::class.java),rs.getObject(2,UUID::class.java),rs.getObject(3,UUID::class.java),rs.getDate(4).toLocalDate())};rows.forEach{r->val id=r[0] as UUID;guest.outbound(r[1] as UUID,r[2] as UUID,"SATISFACTION_SURVEY",null,null,"survey:$id");jdbc.update("update satisfaction_survey set status='SENT',sent_at=:now where id=:id",mapOf("now" to clock.instant(),"id" to id))};return rows.size}
 @Transactional fun respond(hotelId:UUID,surveyId:UUID,category:SatisfactionCategory):SatisfactionSurvey {val row=jdbc.query("select guest_session_id,business_date,status from satisfaction_survey where id=:id and hotel_id=:hotel for update",mapOf("id" to surveyId,"hotel" to hotelId)){rs,_->Triple(rs.getObject(1,UUID::class.java),rs.getDate(2).toLocalDate(),rs.getString(3))}.firstOrNull()?:throw NoSuchElementException("Survey not found");if(row.third=="RESPONDED")return load(hotelId,surveyId);jdbc.update("update satisfaction_survey set status='RESPONDED',score=:score,category=:category,responded_at=:now where id=:id",mapOf("score" to category.score,"category" to category.name,"now" to clock.instant(),"id" to surveyId));if(category.score<=2)risk.assess(hotelId,row.first,RiskSignals(satisfactionScore=category.score));return load(hotelId,surveyId)}
 private fun find(hotelId:UUID,key:String)=jdbc.query("select id from satisfaction_survey where hotel_id=:hotel and idempotency_key=:key",mapOf("hotel" to hotelId,"key" to key)){rs,_->rs.getObject(1,UUID::class.java)}.firstOrNull()?.let{load(hotelId,it)}
 private fun load(hotelId:UUID,id:UUID)=jdbc.query("select guest_session_id,business_date,status,category from satisfaction_survey where id=:id and hotel_id=:hotel",mapOf("id" to id,"hotel" to hotelId)){rs,_->SatisfactionSurvey(id,rs.getObject(1,UUID::class.java),rs.getDate(2).toLocalDate(),rs.getString(3),rs.getString(4)?.let(SatisfactionCategory::valueOf))}.first()
}
