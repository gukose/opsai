package com.hotelopai.unimock.application.pms

/** Single business-facing room catalogue used to keep demo PMS lookups aligned with Hotel OpAI. */
object CanonicalDemoRoomCatalog {
    val roomNumbers: Set<String> = (1..5).flatMap { hundred -> (1..20).map { room -> "${hundred}${room.toString().padStart(2, '0')}" } }.toSet()

    fun contains(roomNumber: String): Boolean = roomNumber in roomNumbers

    fun room(roomNumber: String): RoomReadModel? = roomNumber.takeIf(::contains)?.let {
        RoomReadModel(it, "STANDARD", "F${it.first()}", "VACANT", false)
    }
}
