package com.hotelopai.housekeeping.infrastructure

import com.hotelopai.housekeeping.application.HousekeepingRepository
import com.hotelopai.housekeeping.domain.*
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@Repository
class HousekeepingJdbcRepository(private val jdbc: NamedParameterJdbcTemplate) : HousekeepingRepository {
    override fun insert(workflow: HousekeepingWorkflow): HousekeepingWorkflow {
        jdbc.update("""
            insert into housekeeping_workflow(id,hotel_id,task_id,workflow_type,room_number,status,inspection_required,idempotency_key,version,created_at,updated_at,template_id,template_version)
            values(:id,:hotelId,:taskId,:type,:room,:status,:inspectionRequired,:key,0,:createdAt,:updatedAt,:templateId,:templateVersion)
            on conflict(hotel_id,idempotency_key) do nothing
        """.trimIndent(), workflow.params())
        return findByIdempotencyKey(workflow.hotelId, workflow.idempotencyKey)!!
    }

    override fun findByIdAndHotelId(id: UUID, hotelId: UUID): HousekeepingWorkflow? = jdbc.query(
        "select * from housekeeping_workflow where id=:id and hotel_id=:hotelId", mapOf("id" to id, "hotelId" to hotelId), ::map
    ).firstOrNull()

    override fun findForUpdate(id: UUID, hotelId: UUID): HousekeepingWorkflow? = jdbc.query(
        "select * from housekeeping_workflow where id=:id and hotel_id=:hotelId for update", mapOf("id" to id, "hotelId" to hotelId), ::map
    ).firstOrNull()

    override fun findByIdempotencyKey(hotelId: UUID, key: String): HousekeepingWorkflow? = jdbc.query(
        "select * from housekeeping_workflow where hotel_id=:hotelId and idempotency_key=:key", mapOf("hotelId" to hotelId, "key" to key), ::map
    ).firstOrNull()

    override fun save(workflow: HousekeepingWorkflow): HousekeepingWorkflow {
        val changed = jdbc.update("""
            update housekeeping_workflow set status=:status,accepted_at=:acceptedAt,started_at=:startedAt,paused_at=:pausedAt,resumed_at=:resumedAt,
            cleaning_completed_at=:cleaningCompletedAt,inspection_started_at=:inspectionStartedAt,inspection_completed_at=:inspectionCompletedAt,closed_at=:closedAt,
            working_seconds=:workingSeconds,paused_seconds=:pausedSeconds,active_segment_started_at=:activeSegmentStartedAt,pause_segment_started_at=:pauseSegmentStartedAt,
            updated_at=:updatedAt,version=version+1 where id=:id and hotel_id=:hotelId
        """.trimIndent(), workflow.params())
        check(changed == 1) { "Housekeeping workflow update failed" }
        return findByIdAndHotelId(workflow.id, workflow.hotelId)!!
    }

    override fun list(hotelId: UUID): List<HousekeepingWorkflow> = jdbc.query(
        "select * from housekeeping_workflow where hotel_id=:hotelId order by created_at desc", mapOf("hotelId" to hotelId), ::map
    )

    override fun appendInspection(hotelId: UUID, inspection: HousekeepingInspection) {
        jdbc.update("""insert into housekeeping_inspection(id,hotel_id,workflow_id,inspector_user_id,attempt,result,rejection_reason,quality_score,started_at,completed_at,created_at)
            values(:id,:hotelId,:workflowId,:inspector,:attempt,:result,:reason,:score,:started,:completed,:completed)""",
            mapOf("id" to inspection.id,"hotelId" to hotelId,"workflowId" to inspection.workflowId,"inspector" to inspection.inspectorUserId,
                "attempt" to inspection.attempt,"result" to inspection.result.name,"reason" to inspection.rejectionReason,"score" to inspection.qualityScore,
                "started" to inspection.startedAt?.let(Timestamp::from),"completed" to inspection.completedAt?.let(Timestamp::from)))
        inspection.answers.forEach { answer -> jdbc.update(
            "insert into housekeeping_inspection_answer(inspection_id,checklist_item_id,passed,note) values(:inspection,:item,:passed,:note)",
            mapOf("inspection" to inspection.id,"item" to answer.checklistItemId,"passed" to answer.passed,"note" to answer.note)) }
    }

    override fun inspections(workflowId: UUID, hotelId: UUID): List<HousekeepingInspection> {
        val base = jdbc.query(
        "select * from housekeeping_inspection where workflow_id=:workflowId and hotel_id=:hotelId order by attempt",
        mapOf("workflowId" to workflowId,"hotelId" to hotelId)
        ) { rs, _ -> HousekeepingInspection(rs.uuid("id"), workflowId, rs.uuid("inspector_user_id"), rs.getInt("attempt"), InspectionResult.valueOf(rs.getString("result")), rs.getString("rejection_reason"), rs.getObject("quality_score") as? Int, emptyList(), rs.instant("started_at")!!, rs.instant("completed_at")!!) }
        if (base.isEmpty()) return base
        val answers = jdbc.query(
            "select inspection_id,checklist_item_id,passed,note from housekeeping_inspection_answer where inspection_id in (:ids)",
            mapOf("ids" to base.map { it.id })
        ) { rs, _ -> rs.uuid("inspection_id") to InspectionAnswer(rs.uuid("checklist_item_id"), rs.getBoolean("passed"), rs.getString("note")) }
            .groupBy({ it.first }, { it.second })
        return base.map { it.copy(answers = answers[it.id].orEmpty()) }
    }

    private fun HousekeepingWorkflow.params() = MapSqlParameterSource()
        .addValue("id",id).addValue("hotelId",hotelId).addValue("taskId",taskId).addValue("type",type.name).addValue("room",roomNumber)
        .addValue("status",status.name).addValue("inspectionRequired",inspectionRequired).addValue("key",idempotencyKey)
        .addValue("acceptedAt",acceptedAt?.let(Timestamp::from)).addValue("startedAt",startedAt?.let(Timestamp::from)).addValue("pausedAt",pausedAt?.let(Timestamp::from)).addValue("resumedAt",resumedAt?.let(Timestamp::from))
        .addValue("cleaningCompletedAt",cleaningCompletedAt?.let(Timestamp::from)).addValue("inspectionStartedAt",inspectionStartedAt?.let(Timestamp::from)).addValue("inspectionCompletedAt",inspectionCompletedAt?.let(Timestamp::from))
        .addValue("closedAt",closedAt).addValue("workingSeconds",workingSeconds).addValue("pausedSeconds",pausedSeconds)
        .addValue("activeSegmentStartedAt",activeSegmentStartedAt?.let(Timestamp::from)).addValue("pauseSegmentStartedAt",pauseSegmentStartedAt?.let(Timestamp::from))
        .addValue("createdAt",Timestamp.from(createdAt)).addValue("updatedAt",Timestamp.from(updatedAt))
        .addValue("templateId",templateId).addValue("templateVersion",templateVersion)

    private fun map(rs: ResultSet, ignored: Int) = HousekeepingWorkflow(
        id=rs.uuid("id"),hotelId=rs.uuid("hotel_id"),taskId=rs.uuid("task_id"),type=HousekeepingWorkflowType.valueOf(rs.getString("workflow_type")),roomNumber=rs.getString("room_number"),
        status=HousekeepingStatus.valueOf(rs.getString("status")),inspectionRequired=rs.getBoolean("inspection_required"),acceptedAt=rs.instant("accepted_at"),startedAt=rs.instant("started_at"),
        pausedAt=rs.instant("paused_at"),resumedAt=rs.instant("resumed_at"),cleaningCompletedAt=rs.instant("cleaning_completed_at"),inspectionStartedAt=rs.instant("inspection_started_at"),
        inspectionCompletedAt=rs.instant("inspection_completed_at"),closedAt=rs.instant("closed_at"),workingSeconds=rs.getLong("working_seconds"),pausedSeconds=rs.getLong("paused_seconds"),
        activeSegmentStartedAt=rs.instant("active_segment_started_at"),pauseSegmentStartedAt=rs.instant("pause_segment_started_at"),idempotencyKey=rs.getString("idempotency_key"),createdAt=rs.instant("created_at")!!,updatedAt=rs.instant("updated_at")!!,
        templateId=rs.getObject("template_id",UUID::class.java),templateVersion=rs.getObject("template_version") as? Int)

    private fun ResultSet.uuid(name:String)=getObject(name,UUID::class.java)
    private fun ResultSet.instant(name:String)=getTimestamp(name)?.toInstant()
}
