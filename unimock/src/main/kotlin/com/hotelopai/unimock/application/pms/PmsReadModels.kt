package com.hotelopai.unimock.application.pms

import com.fasterxml.jackson.databind.JsonNode
import java.util.UUID

data class ActiveSimulationDocuments(
    val simulationId: UUID,
    val simulationCode: String,
    val simulationName: String,
    val seedPath: String,
    val documents: Map<String, JsonNode>
)

data class RoomReadModel(
    val roomNumber: String,
    val roomTypeCode: String,
    val floorCode: String,
    val status: String,
    val isOutOfOrder: Boolean
)

data class RoomStatusReadModel(
    val roomNumber: String,
    val status: String,
    val updatedAt: String
)

data class RoomOccupancyReadModel(
    val roomNumber: String,
    val reservationId: String?,
    val guestId: String?,
    val occupied: Boolean
)

data class AssetReadModel(
    val assetId: String,
    val roomNumber: String,
    val name: String
)

data class IssueTypeReadModel(
    val code: String,
    val name: String
)

data class PublicAreaReadModel(
    val code: String,
    val name: String
)

data class ReservationReadModel(
    val reservationId: String,
    val roomNumber: String,
    val guestId: String,
    val status: String
)

data class GuestReadModel(
    val guestId: String,
    val firstName: String,
    val lastName: String
)

data class EventReadModel(
    val eventId: String,
    val type: String,
    val occurredAt: String
)

data class RoomsDocument(val rooms: List<RoomReadModel>)
data class RoomStatusesDocument(val roomStatuses: List<RoomStatusReadModel>)
data class OccupancyDocument(val occupancy: List<RoomOccupancyReadModel>)
data class AssetsDocument(val assets: List<AssetReadModel>)
data class IssueTypesDocument(val issueTypes: List<IssueTypeReadModel>)
data class PublicAreasDocument(val publicAreas: List<PublicAreaReadModel>)
data class ReservationsDocument(val reservations: List<ReservationReadModel>)
data class GuestsDocument(val guests: List<GuestReadModel>)
data class EventsDocument(val events: List<EventReadModel>)
