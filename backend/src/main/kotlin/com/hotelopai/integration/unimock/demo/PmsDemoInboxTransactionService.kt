package com.hotelopai.integration.unimock.demo

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.sql.Timestamp
import java.time.Clock
import java.util.UUID
import com.hotelopai.shared.kernel.UuidV7Generator

data class PmsInboxClaim(val hotelId: UUID, val inboxId: UUID, val duplicate: Boolean)

@Service
class PmsDemoInboxTransactionService(private val jdbc: NamedParameterJdbcTemplate, private val clock: Clock) {
    @Transactional
    fun claim(request: PmsDemoEventRequest, hotelId: UUID): PmsInboxClaim {
        val now = clock.instant(); val id = UuidV7Generator.generate(now)
        val inserted = jdbc.update("""insert into pms_demo_event_inbox(id,hotel_id,provider_event_id,event_type,room_number,destination_room_number,status,occurred_at,created_at)
            values(:id,:hotel,:event,:type,:room,:destination,'PROCESSING',:occurred,:now) on conflict(hotel_id,provider_event_id) do nothing""", mapOf("id" to id,"hotel" to hotelId,"event" to request.eventId,"type" to request.eventType.name,"room" to request.roomNumber,"destination" to request.toRoomNumber,"occurred" to Timestamp.from(request.occurredAt),"now" to Timestamp.from(now)))
        return PmsInboxClaim(hotelId, id, inserted == 0)
    }

    @Transactional
    fun finalize(inboxId: UUID, result: Pair<String, UUID>?, now: java.time.Instant) {
        jdbc.update("update pms_demo_event_inbox set status='PROCESSED',result_type=:resultType,result_id=:resultId,processed_at=:now where id=:id", mapOf("id" to inboxId,"resultType" to result?.first,"resultId" to result?.second,"now" to Timestamp.from(now)))
    }

    @Transactional
    fun release(inboxId: UUID) {
        jdbc.update("delete from pms_demo_event_inbox where id=:id and status='PROCESSING'", mapOf("id" to inboxId))
    }
}
