package com.hotelopai.housekeeping.application

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant
import java.sql.Timestamp
import java.util.UUID

enum class RoomOperationalStatus { DIRTY, CLEANING, INSPECTION_REQUIRED, REWORK, READY }

data class RoomOperationalState(
    val hotelId: UUID,
    val roomNumber: String,
    val status: RoomOperationalStatus,
    val updatedAt: Instant
)

@Service
class RoomOperationalStateService(
    private val jdbc: NamedParameterJdbcTemplate,
    private val clock: Clock = Clock.systemUTC()
) {
    fun set(hotelId: UUID, roomNumber: String, status: RoomOperationalStatus, sourceReference: String): RoomOperationalState {
        require(roomNumber.isNotBlank() && sourceReference.isNotBlank())
        val now = clock.instant()
        val effectiveStatus = if (status == RoomOperationalStatus.READY && minibarPending(hotelId, roomNumber)) RoomOperationalStatus.CLEANING else status
        jdbc.update(
            """insert into room_operational_state(hotel_id,room_number,status,source_type,source_reference,updated_at)
               values(:hotel,:room,:status,'HOUSEKEEPING',:source,:now)
               on conflict(hotel_id,room_number) do update set status=excluded.status,source_type=excluded.source_type,
               source_reference=excluded.source_reference,updated_at=excluded.updated_at""",
               mapOf("hotel" to hotelId, "room" to roomNumber, "status" to effectiveStatus.name, "source" to sourceReference, "now" to Timestamp.from(now))
        )
        return RoomOperationalState(hotelId, roomNumber, effectiveStatus, now)
    }

    fun markMinibarCompleted(hotelId: UUID, roomNumber: String): Boolean =
        jdbc.update("update room_minibar_readiness set status='COMPLETED',completed_at=:now,updated_at=:now where hotel_id=:hotel and room_number=:room and status='PENDING'", mapOf("hotel" to hotelId,"room" to roomNumber,"now" to Timestamp.from(clock.instant()))) > 0

    private fun minibarPending(hotelId: UUID, roomNumber: String): Boolean =
        jdbc.queryForObject("select count(*) from room_minibar_readiness where hotel_id=:hotel and room_number=:room and status='PENDING'", mapOf("hotel" to hotelId,"room" to roomNumber), Long::class.java) == 1L

    fun get(hotelId: UUID, roomNumber: String): RoomOperationalState? = jdbc.query(
        "select status,updated_at from room_operational_state where hotel_id=:hotel and room_number=:room",
        mapOf("hotel" to hotelId, "room" to roomNumber)
    ) { rs, _ -> RoomOperationalState(hotelId, roomNumber, RoomOperationalStatus.valueOf(rs.getString(1)), rs.getTimestamp(2).toInstant()) }.firstOrNull()
}
