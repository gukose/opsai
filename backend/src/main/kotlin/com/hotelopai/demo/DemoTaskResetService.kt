package com.hotelopai.demo

import com.hotelopai.observability.OperationalObservability
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.security.access.AccessDeniedException
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.dao.DataAccessException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

data class DemoTaskResetResult(
    val tasksDeleted: Int,
    val relatedRecordsDeleted: Int,
    val remainingTasks: Int
)

data class DemoTaskResetStatus(
    val hotelCode: String,
    val taskCount: Int,
    val resetAvailable: Boolean = true
)

@Service
@Profile("!prod & (demo | local | test)")
class DemoTaskResetService(
    private val jdbc: NamedParameterJdbcTemplate,
    private val observability: OperationalObservability = OperationalObservability.noop()
) {
    @Transactional
    fun resetTasks(requestingHotelId: UUID): DemoTaskResetResult {
        val hotelId = demoHotelId()
        requireDemoHotelAccess(requestingHotelId, hotelId)
        val taskIds = taskIds(hotelId)
        if (taskIds.isEmpty()) {
            audit(0, 0)
            return DemoTaskResetResult(0, 0, 0)
        }

        val parameters = mapOf(
            "hotel" to hotelId,
            "taskIds" to taskIds,
            "taskIdTexts" to taskIds.map(UUID::toString)
        )
        var related = 0

        related += delete(
            """delete from pms_outbound_operation p where p.hotel_id=:hotel and exists (
               select 1 from housekeeping_workflow w where w.task_id in (:taskIds)
                 and p.idempotency_key like 'housekeeping-room-ready:' || w.id::text || ':%')""",
            parameters
        )
        related += delete(
            """delete from room_operational_state r where r.hotel_id=:hotel and exists (
               select 1 from housekeeping_workflow w where w.task_id in (:taskIds)
                 and r.source_type='HOUSEKEEPING' and r.source_reference='housekeeping:' || w.id::text)""",
            parameters
        )
        related += delete(
            """delete from housekeeping_inspection_answer a where exists (
               select 1 from housekeeping_inspection i join housekeeping_workflow w on w.id=i.workflow_id
               where a.inspection_id=i.id and w.task_id in (:taskIds))""",
            parameters
        )
        related += delete(
            """delete from housekeeping_inspection i where exists (
               select 1 from housekeeping_workflow w where w.id=i.workflow_id
                 and w.task_id in (:taskIds))""",
            parameters
        )
        related += delete("delete from housekeeping_workflow where task_id in (:taskIds)", parameters)

        related += delete(
            """delete from financial_charge_proposal f where f.hotel_id=:hotel and exists (
               select 1 from minibar_inspection m where m.id=f.source_id and m.hotel_id=:hotel
                 and m.task_id in (:taskIds) and f.charge_type='MINIBAR')""",
            parameters
        )
        related += delete(
            """delete from inventory_transaction i where i.hotel_id=:hotel and exists (
               select 1 from minibar_inspection m where m.hotel_id=:hotel and m.task_id in (:taskIds)
                 and i.operational_reference='minibar:' || m.idempotency_key)""",
            parameters
        )
        related += delete(
            """delete from minibar_inspection_item i where exists (
               select 1 from minibar_inspection m where m.id=i.inspection_id
                 and m.hotel_id=:hotel and m.task_id in (:taskIds))""",
            parameters
        )
        related += delete("delete from minibar_inspection where hotel_id=:hotel and task_id in (:taskIds)", parameters)

        related += delete(
            """delete from financial_charge_proposal f where f.hotel_id=:hotel and exists (
               select 1 from damage_report d where d.id=f.source_id and d.hotel_id=:hotel
                 and d.task_id in (:taskIds) and f.charge_type='DAMAGE')""",
            parameters
        )
        related += delete(
            """delete from damage_approval_history h where exists (
               select 1 from damage_report d where d.id=h.damage_report_id
                 and d.hotel_id=:hotel and d.task_id in (:taskIds))""",
            parameters
        )
        related += delete(
            """delete from damage_attachment a where exists (
               select 1 from damage_report d where d.id=a.damage_report_id
                 and d.hotel_id=:hotel and d.task_id in (:taskIds))""",
            parameters
        )
        related += delete("delete from damage_report where hotel_id=:hotel and task_id in (:taskIds)", parameters)

        related += delete("delete from task_interruption where hotel_id=:hotel and (paused_task_id in (:taskIds) or interrupting_task_id in (:taskIds))", parameters)
        related += delete("delete from guest_message where hotel_id=:hotel and task_id in (:taskIds)", parameters)
        related += delete(
            "delete from shift_handover_ack where handover_id in (select id from shift_handover where hotel_id=:hotel and task_id in (:taskIds))",
            parameters
        )
        related += delete("delete from shift_handover where hotel_id=:hotel and task_id in (:taskIds)", parameters)
        related += delete(
            """delete from operational_outbox o using reservation_task_automation_execution e
               where o.id=e.outbox_event_id and o.hotel_id=:hotel
                 and e.created_task_id in (:taskIds)""",
            parameters
        )
        related += delete(
            """delete from reservation_task_automation_execution
               where created_task_id in (:taskIds)""",
            parameters
        )
        related += delete(
            """delete from reservation_task_recommendation
               where applied_task_id in (:taskIds)""",
            parameters
        )
        related += delete("delete from assistant_task_confirmation where created_task_id in (:taskIdTexts)", parameters)
        jdbc.update(
            """update assistant_conversation set state='RESET',created_task_id=null,confirmation_idempotency_key=null,
               task_preview_json=null,active_draft_id=null,updated_at=now()
               where hotel_id=:hotelText and created_task_id in (:taskIdTexts)""",
            parameters + ("hotelText" to hotelId.toString())
        )
        related += delete("delete from offline_operation where hotel_id=:hotel and resource_id in (:taskIdTexts)", parameters)
        related += delete("delete from employee_badge where hotel_id=:hotel and source_reference in (:taskSources)", parameters + ("taskSources" to taskIds.map { "task:$it" }))
        related += delete("delete from gamification_ledger where hotel_id=:hotel and source_reference in (:taskSources)", parameters + ("taskSources" to taskIds.map { "task:$it" }))
        related += delete("delete from operational_outbox where hotel_id=:hotel and aggregate_type='TASK' and aggregate_id in (:taskIds)", parameters)
        related += delete("delete from notifications where hotel_id=:hotel and source_task_id in (:taskIds)", parameters)
        related += delete("delete from task_attachment_link where task_id in (:taskIds)", parameters)
        related += delete("delete from task_assignment_audit where hotel_id=:hotel and task_id in (:taskIds)", parameters)
        related += delete("delete from task_assignment_orchestration where hotel_id=:hotel and task_id in (:taskIds)", parameters)
        related += delete("delete from task_state_history where hotel_id=:hotel and task_id in (:taskIds)", parameters)
        related += delete("delete from task_log where hotel_id=:hotel and task_id in (:taskIds)", parameters)

        val tasksDeleted = delete("delete from task where hotel_id=:hotel and id in (:taskIds)", parameters)
        val remaining = taskCount(hotelId)
        check(remaining == 0) { "Demo task reset did not remove every demo task" }
        audit(tasksDeleted, related)
        return DemoTaskResetResult(tasksDeleted, related, remaining)
    }

    @Transactional(readOnly = true)
    fun status(requestingHotelId: UUID): DemoTaskResetStatus {
        val hotelId = demoHotelId()
        requireDemoHotelAccess(requestingHotelId, hotelId)
        return DemoTaskResetStatus(DEMO_HOTEL_CODE, taskCount(hotelId))
    }

    private fun requireDemoHotelAccess(requestingHotelId: UUID, demoHotelId: UUID) {
        if (requestingHotelId != demoHotelId) throw AccessDeniedException("Demo reset is restricted to the demo hotel")
    }

    private fun demoHotelId(): UUID = jdbc.query(
        "select id from hotel where code=:code",
        mapOf("code" to DEMO_HOTEL_CODE)
    ) { resultSet, _ -> resultSet.getObject("id", UUID::class.java) }
        .singleOrNull()
        ?: throw IllegalStateException("Configured demo hotel is unavailable")

    private fun taskIds(hotelId: UUID): List<UUID> = jdbc.query(
        "select id from task where hotel_id=:hotel",
        mapOf("hotel" to hotelId)
    ) { resultSet, _ -> resultSet.getObject("id", UUID::class.java) }

    private fun taskCount(hotelId: UUID): Int =
        jdbc.queryForObject("select count(*) from task where hotel_id=:hotel", mapOf("hotel" to hotelId), Int::class.java) ?: 0

    private fun delete(sql: String, parameters: Map<String, *>): Int = try {
        jdbc.update(sql, parameters)
    } catch (exception: DataAccessException) {
        val table = Regex("delete\\s+from\\s+([a-zA-Z0-9_]+)", RegexOption.IGNORE_CASE)
            .find(sql)?.groupValues?.getOrNull(1) ?: "unknown"
        logger.error(
            "event=demo_task_reset outcome=failure operation=delete table={} exceptionType={}",
            table,
            exception::class.simpleName
        )
        throw exception
    }

    private fun audit(tasksDeleted: Int, relatedRecordsDeleted: Int) {
        logger.info(
            "event=demo_task_reset outcome=success hotelCode={} tasksDeleted={} relatedRecordsDeleted={}",
            DEMO_HOTEL_CODE,
            tasksDeleted,
            relatedRecordsDeleted
        )
        observability.incrementCounter("hotelopai.demo.task_reset.total", "outcome" to "success")
    }

    companion object {
        const val DEMO_HOTEL_CODE = "hotel-opai-demo"
        private val logger = LoggerFactory.getLogger(DemoTaskResetService::class.java)
    }
}
