package com.hotelopai.gamification.application

import com.hotelopai.task.application.TaskCompletionObserver
import com.hotelopai.task.domain.Task
import com.hotelopai.task.domain.TaskAssigneeType
import com.hotelopai.task.domain.TaskIntentType
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import java.util.UUID

@Component class GamificationTaskCompletionObserver(private val gamification:GamificationService,private val jdbc:NamedParameterJdbcTemplate):TaskCompletionObserver{
 override fun completed(task:Task){val assignment=task.assignment?.takeIf{it.assigneeType==TaskAssigneeType.USER}?:return;val employeeId=runCatching{UUID.fromString(assignment.assigneeId)}.getOrNull()?:return;val belongs=jdbc.queryForObject("select count(*) from employee where id=:employee and hotel_id=:hotel",mapOf("employee" to employeeId,"hotel" to task.hotelId),Long::class.java)?:0;if(belongs!=1L)return;val inspection=jdbc.query("select i.quality_score,i.result from housekeeping_workflow w join housekeeping_inspection i on i.workflow_id=w.id where w.task_id=:task and w.hotel_id=:hotel order by i.attempt desc limit 1",mapOf("task" to task.id,"hotel" to task.hotelId)){rs,_->rs.getObject(1) as? Int to rs.getString(2)}.firstOrNull();gamification.award(AwardXpCommand(task.hotelId,employeeId,"task:${task.id}",true,(task.completedAt?:task.updatedAt)<=task.slaDeadline,inspection?.first,inspection?.second==null||inspection.second=="PASS",vip=task.title.contains("VIP",true),maintenance=task.intentType==TaskIntentType.MAINTENANCE,guestHappiness=task.intentType==TaskIntentType.GUEST_REQUEST,teamContribution=false))}
}
