package com.hotelopai.recovery.application
import com.hotelopai.shared.kernel.UuidV7Generator
import com.hotelopai.task.application.*
import com.hotelopai.task.domain.*
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Clock
import java.util.UUID

enum class RiskLevel { LOW, MEDIUM, HIGH }
data class RiskSignals(val unresolvedComplaints:Int=0,val delayedTasks:Int=0,val maintenanceDelayMinutes:Int=0,val repeatedIssues:Int=0,val serviceRecoveryActivities:Int=0,val satisfactionScore:Int?=null,val slaBreaches:Int=0)
data class RiskAssessment(val id:UUID,val level:RiskLevel,val confidence:BigDecimal,val reasonCodes:List<String>,val ruleVersion:String,val serviceRecoveryId:UUID?)
@Service class RiskRecoveryService(private val jdbc:NamedParameterJdbcTemplate,private val tasks:TaskLifecycleService,private val clock:Clock=Clock.systemUTC()){
 @Transactional fun assess(hotelId:UUID,guestSessionId:UUID,signals:RiskSignals):RiskAssessment { val reasons=mutableListOf<String>();var points=0;if(signals.unresolvedComplaints>0){points+=3;reasons+="unresolved_complaint"};if(signals.delayedTasks>0){points+=2;reasons+="delayed_task"};if(signals.maintenanceDelayMinutes>=60){points+=2;reasons+="maintenance_delay"};if(signals.repeatedIssues>=2){points+=2;reasons+="repeated_issue"};if(signals.satisfactionScore!=null&&signals.satisfactionScore<=2){points+=4;reasons+="poor_satisfaction"};if(signals.slaBreaches>0){points+=2;reasons+="sla_breach"};val level=when{points>=7->RiskLevel.HIGH;points>=3->RiskLevel.MEDIUM;else->RiskLevel.LOW};val now=clock.instant();val id=UuidV7Generator.generate(now);val confidence=BigDecimal("0.90");jdbc.update("insert into guest_risk_assessment(id,hotel_id,guest_session_id,level,confidence,rule_version,reason_codes,assessed_at) values(:id,:hotel,:session,:level,:confidence,'mvp-v1',:reasons,:now)",mapOf("id" to id,"hotel" to hotelId,"session" to guestSessionId,"level" to level.name,"confidence" to confidence,"reasons" to reasons.toTypedArray(),"now" to now));val recovery=if(level==RiskLevel.HIGH)createRecovery(hotelId,id,"HIGH_REVIEW_RISK","risk:$id")else null;return RiskAssessment(id,level,confidence,reasons,"mvp-v1",recovery) }
 @Transactional fun createRecovery(hotelId:UUID,originId:UUID,reason:String,key:String):UUID { jdbc.query("select id from service_recovery where hotel_id=:hotel and idempotency_key=:key",mapOf("hotel" to hotelId,"key" to key)){rs,_->rs.getObject(1,UUID::class.java)}.firstOrNull()?.let{return it};val now=clock.instant();val id=UuidV7Generator.generate(now);val task=tasks.createTask(CreateTaskCommand(hotelId,TaskIntentType.GUEST_REQUEST,TaskSource.API,"Service recovery review","Human follow-up required; compensation is not promised",null,TaskPriority.HIGH,now.plusSeconds(1800)));jdbc.update("""insert into service_recovery(id,hotel_id,origin_type,origin_id,reason_code,status,recommended_action,compensation_required,follow_up_status,created_at,idempotency_key)
 values(:id,:hotel,'RISK',:origin,:reason,'OPEN','Contact guest and review operational recovery',false,'PENDING',:now,:key)""",mapOf("id" to id,"hotel" to hotelId,"origin" to originId,"reason" to reason,"now" to now,"key" to key));return id }
 @Transactional fun complete(id:UUID,hotelId:UUID,outcome:String,followUp:String){require(outcome.isNotBlank());val changed=jdbc.update("update service_recovery set status='COMPLETED',outcome=:outcome,follow_up_status=:follow,completed_at=:now where id=:id and hotel_id=:hotel and status<>'COMPLETED'",mapOf("outcome" to outcome,"follow" to followUp,"now" to clock.instant(),"id" to id,"hotel" to hotelId));if(changed!=1)throw NoSuchElementException("Service recovery not found or already complete")}
}
