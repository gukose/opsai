package com.hotelopai.housekeeping.application

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import java.time.Clock
import java.sql.Timestamp
import java.util.UUID

@Service
class MinibarReadinessService(private val jdbc: NamedParameterJdbcTemplate, private val clock: Clock = Clock.systemUTC()) {
    fun markPending(hotelId: UUID, roomNumber: String) {
        val now = Timestamp.from(clock.instant())
        jdbc.update("insert into room_minibar_readiness(hotel_id,room_number,status,updated_at) values(:hotel,:room,'PENDING',:now) on conflict(hotel_id,room_number) do update set status='PENDING',completed_at=null,updated_at=:now", mapOf("hotel" to hotelId,"room" to roomNumber,"now" to now))
    }

    fun markCompleted(hotelId: UUID, roomNumber: String): Boolean {
        val now = Timestamp.from(clock.instant())
        jdbc.update("update room_minibar_readiness set status='COMPLETED',completed_at=:now,updated_at=:now where hotel_id=:hotel and room_number=:room and status='PENDING'", mapOf("hotel" to hotelId,"room" to roomNumber,"now" to now))
        return reevaluateRoomReadiness(hotelId, roomNumber)
    }

    fun reevaluateRoomReadiness(hotelId: UUID, roomNumber: String): Boolean {
        val now = Timestamp.from(clock.instant())
        val cleaningComplete = (jdbc.queryForObject("select count(*) from housekeeping_workflow w join task t on t.id=w.task_id and t.hotel_id=w.hotel_id where w.hotel_id=:hotel and w.room_number=:room and w.workflow_type='DEPARTURE_CLEANING' and t.status='COMPLETED'", mapOf("hotel" to hotelId,"room" to roomNumber), Long::class.java) ?: 0L) > 0L
        val minibarPending = (jdbc.queryForObject("select count(*) from room_minibar_readiness where hotel_id=:hotel and room_number=:room and status='PENDING'", mapOf("hotel" to hotelId,"room" to roomNumber), Long::class.java) ?: 0L) > 0L
        val minibarComplete = !minibarPending
        if (cleaningComplete && minibarComplete) jdbc.update("insert into room_operational_state(hotel_id,room_number,status,source_type,source_reference,updated_at) values(:hotel,:room,'READY','HOUSEKEEPING','readiness-reevaluated',:now) on conflict(hotel_id,room_number) do update set status='READY',source_reference='readiness-reevaluated',updated_at=:now", mapOf("hotel" to hotelId,"room" to roomNumber,"now" to now))
        return cleaningComplete && minibarComplete
    }
}
