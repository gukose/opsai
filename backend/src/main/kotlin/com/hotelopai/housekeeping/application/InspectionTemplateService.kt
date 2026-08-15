package com.hotelopai.housekeeping.application

import com.hotelopai.housekeeping.domain.HousekeepingWorkflowType
import com.hotelopai.housekeeping.domain.InspectionAnswer
import com.hotelopai.shared.kernel.UuidV7Generator
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.UUID

data class InspectionTemplateItem(val id:UUID,val code:String,val label:String,val required:Boolean,val order:Int)
data class InspectionTemplate(val id:UUID,val workflowType:HousekeepingWorkflowType,val name:String,val version:Int,val enabled:Boolean,val items:List<InspectionTemplateItem>)
data class CreateInspectionTemplate(val workflowType:HousekeepingWorkflowType,val name:String,val items:List<CreateInspectionTemplateItem>)
data class CreateInspectionTemplateItem(val code:String,val label:String,val required:Boolean=true,val order:Int)

@Service class InspectionTemplateService(private val jdbc:NamedParameterJdbcTemplate,private val clock:Clock=Clock.systemUTC()) {
 @Transactional fun createVersion(hotelId:UUID,c:CreateInspectionTemplate):InspectionTemplate {require(c.name.isNotBlank()&&c.items.isNotEmpty());require(c.items.map{it.code}.distinct().size==c.items.size);val version=(jdbc.queryForObject("select coalesce(max(version_number),0) from housekeeping_checklist_template where hotel_id=:hotel and workflow_type=:type",mapOf("hotel" to hotelId,"type" to c.workflowType.name),Int::class.java)?:0)+1;val now=clock.instant();val id=UuidV7Generator.generate(now);jdbc.update("insert into housekeeping_checklist_template(id,hotel_id,workflow_type,name,active,enabled,version_number,created_at,updated_at) values(:id,:hotel,:type,:name,true,true,:version,:now,:now)",mapOf("id" to id,"hotel" to hotelId,"type" to c.workflowType.name,"name" to c.name,"version" to version,"now" to now));c.items.sortedBy{it.order}.forEach{item->require(item.code.matches(Regex("[A-Za-z0-9_-]{2,64}"))&&item.label.isNotBlank());jdbc.update("insert into housekeeping_checklist_item(id,template_id,code,label,required,display_order) values(:id,:template,:code,:label,:required,:order)",mapOf("id" to UuidV7Generator.generate(now),"template" to id,"code" to item.code.uppercase(),"label" to item.label,"required" to item.required,"order" to item.order))};return get(hotelId,id)}
 fun list(hotelId:UUID)=jdbc.query("select id from housekeeping_checklist_template where hotel_id=:hotel order by workflow_type,version_number desc",mapOf("hotel" to hotelId)){rs,_->rs.getObject(1,UUID::class.java)}.map{get(hotelId,it)}
 fun applicable(hotelId:UUID,type:HousekeepingWorkflowType):InspectionTemplate?=jdbc.query("select id from housekeeping_checklist_template where hotel_id=:hotel and workflow_type=:type and active=true and enabled=true order by version_number desc limit 1",mapOf("hotel" to hotelId,"type" to type.name)){rs,_->rs.getObject(1,UUID::class.java)}.firstOrNull()?.let{get(hotelId,it)}
 fun get(hotelId:UUID,id:UUID):InspectionTemplate {val row=jdbc.query("select workflow_type,name,version_number,enabled from housekeeping_checklist_template where id=:id and hotel_id=:hotel",mapOf("id" to id,"hotel" to hotelId)){rs,_->arrayOf(rs.getString(1),rs.getString(2),rs.getInt(3),rs.getBoolean(4))}.firstOrNull()?:throw NoSuchElementException("Inspection template not found");val items=jdbc.query("select id,code,label,required,display_order from housekeeping_checklist_item where template_id=:id order by display_order",mapOf("id" to id)){rs,_->InspectionTemplateItem(rs.getObject(1,UUID::class.java),rs.getString(2),rs.getString(3),rs.getBoolean(4),rs.getInt(5))};return InspectionTemplate(id,HousekeepingWorkflowType.valueOf(row[0] as String),row[1] as String,row[2] as Int,row[3] as Boolean,items)}
 @Transactional fun disable(hotelId:UUID,id:UUID){if(jdbc.update("update housekeeping_checklist_template set enabled=false,active=false,updated_at=:now where id=:id and hotel_id=:hotel",mapOf("now" to clock.instant(),"id" to id,"hotel" to hotelId))!=1)throw NoSuchElementException("Inspection template not found")}
 fun validate(template:InspectionTemplate?,answers:List<InspectionAnswer>){if(template==null)return;val answerIds=answers.map{it.checklistItemId}.toSet();require(template.items.filter{it.required}.all{it.id in answerIds}){"All required checklist items must be answered"};require(answerIds.all{id->template.items.any{it.id==id}}){"Checklist answer does not belong to the workflow template"}}
}
