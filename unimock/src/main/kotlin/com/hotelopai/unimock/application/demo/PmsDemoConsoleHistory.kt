package com.hotelopai.unimock.application.demo

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.time.Clock
import java.time.Instant
import java.util.UUID

data class DemoHistoryEvent(val eventId:String,val roomNumber:String,val toRoomNumber:String?,val eventType:String,val occurredAt:Instant,val deliveryStatus:String,val message:String?)

@Repository
class PmsDemoConsoleHistoryRepository(private val jdbc:NamedParameterJdbcTemplate,private val clock:Clock) {
    fun list(limit:Int=25):List<DemoHistoryEvent> = jdbc.query("select event_id,room_number,destination_room_number,event_type,occurred_at,delivery_status,message from pms_demo_console_event order by created_at desc limit :limit",mapOf("limit" to limit)) { rs,_ -> DemoHistoryEvent(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getTimestamp(5).toInstant(),rs.getString(6),rs.getString(7)) }
    fun record(request:DemoEventRequest,eventId:String,occurredAt:Instant,status:String,message:String?) {
        val now=clock.instant()
        jdbc.update("""insert into pms_demo_console_event(id,event_id,room_number,destination_room_number,event_type,occurred_at,delivery_status,message,created_at,updated_at)
            values(:id,:event,:room,:destination,:type,:occurred,:status,:message,:now,:now)
            on conflict(event_id) do update set delivery_status=excluded.delivery_status,message=excluded.message,updated_at=excluded.updated_at""",
            mapOf("id" to UUID.randomUUID(),"event" to eventId,"room" to request.roomNumber,"destination" to request.toRoomNumber,"type" to request.eventType.name,"occurred" to occurredAt,"status" to status,"message" to message,"now" to now))
    }
}
