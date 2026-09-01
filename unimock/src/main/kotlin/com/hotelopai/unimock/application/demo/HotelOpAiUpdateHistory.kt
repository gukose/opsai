package com.hotelopai.unimock.application.demo

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Clock
import java.util.UUID

data class HotelOpAiRoomUpdate(val id: UUID, val roomNumber: String, val status: String, val source: String, val receivedAt: java.time.Instant)

@Repository
class HotelOpAiUpdateHistoryRepository(private val jdbc: NamedParameterJdbcTemplate, private val clock: Clock) {
    fun append(roomNumber: String, status: String) {
        jdbc.update("insert into unimock.hotel_opai_room_update(id,room_number,status,source,received_at) values(:id,:room,:status,'HOTEL_OPAI',:at)", mapOf("id" to UUID.randomUUID(), "room" to roomNumber, "status" to status, "at" to Timestamp.from(clock.instant())))
    }
    fun list(limit: Int = 25): List<HotelOpAiRoomUpdate> = jdbc.query("select id,room_number,status,source,received_at from unimock.hotel_opai_room_update order by received_at desc limit :limit", mapOf("limit" to limit.coerceIn(1,100))) { rs, _ -> HotelOpAiRoomUpdate(rs.getObject(1,UUID::class.java),rs.getString(2),rs.getString(3),rs.getString(4),rs.getTimestamp(5).toInstant()) }
}
