package com.hotelopai.housekeeping.application

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant
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
        jdbc.update(
            """insert into room_operational_state(hotel_id,room_number,status,source_type,source_reference,updated_at)
               values(:hotel,:room,:status,'HOUSEKEEPING',:source,:now)
               on conflict(hotel_id,room_number) do update set status=excluded.status,source_type=excluded.source_type,
               source_reference=excluded.source_reference,updated_at=excluded.updated_at""",
            mapOf("hotel" to hotelId, "room" to roomNumber, "status" to status.name, "source" to sourceReference, "now" to now)
        )
        return RoomOperationalState(hotelId, roomNumber, status, now)
    }

    fun get(hotelId: UUID, roomNumber: String): RoomOperationalState? = jdbc.query(
        "select status,updated_at from room_operational_state where hotel_id=:hotel and room_number=:room",
        mapOf("hotel" to hotelId, "room" to roomNumber)
    ) { rs, _ -> RoomOperationalState(hotelId, roomNumber, RoomOperationalStatus.valueOf(rs.getString(1)), rs.getTimestamp(2).toInstant()) }.firstOrNull()
}
