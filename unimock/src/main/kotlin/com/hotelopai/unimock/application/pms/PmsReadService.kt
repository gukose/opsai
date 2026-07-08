package com.hotelopai.unimock.application.pms

import com.fasterxml.jackson.databind.ObjectMapper
import com.hotelopai.unimock.application.simulation.SimulationNotLoadedException
import org.springframework.stereotype.Service

@Service
class PmsReadService(
    private val pmsReadRepository: PmsReadRepository,
    private val objectMapper: ObjectMapper
) {
    fun listRooms(): List<RoomReadModel> = activeSimulation().rooms()

    fun getRoom(roomNumber: String): RoomReadModel =
        activeSimulation().rooms().firstOrNotFound(
            predicate = { it.roomNumber == roomNumber },
            resourceType = "Room",
            resourceId = roomNumber
        )

    fun getRoomStatus(roomNumber: String): RoomStatusReadModel =
        activeSimulation().roomStatuses().firstOrNotFound(
            predicate = { it.roomNumber == roomNumber },
            resourceType = "Room status",
            resourceId = roomNumber
        )

    fun getRoomOccupancy(roomNumber: String): RoomOccupancyReadModel =
        activeSimulation().occupancy().firstOrNotFound(
            predicate = { it.roomNumber == roomNumber },
            resourceType = "Occupancy",
            resourceId = roomNumber
        )

    fun getRoomAssets(roomNumber: String): List<AssetReadModel> =
        activeSimulation().let { simulation ->
            simulation.rooms().firstOrNotFound(
                predicate = { it.roomNumber == roomNumber },
                resourceType = "Room",
                resourceId = roomNumber
            )
            simulation.assets().filter { it.roomNumber == roomNumber }
        }

    fun getAsset(assetId: String): AssetReadModel =
        activeSimulation().assets().firstOrNotFound(
            predicate = { it.assetId == assetId },
            resourceType = "Asset",
            resourceId = assetId
        )

    fun listIssueTypes(): List<IssueTypeReadModel> = activeSimulation().issueTypes()

    fun listPublicAreas(): List<PublicAreaReadModel> = activeSimulation().publicAreas()

    fun listReservations(): List<ReservationReadModel> = activeSimulation().reservations()

    fun getReservation(reservationId: String): ReservationReadModel =
        activeSimulation().reservations().firstOrNotFound(
            predicate = { it.reservationId == reservationId },
            resourceType = "Reservation",
            resourceId = reservationId
        )

    fun getGuest(guestId: String): GuestReadModel =
        activeSimulation().guests().firstOrNotFound(
            predicate = { it.guestId == guestId },
            resourceType = "Guest",
            resourceId = guestId
        )

    fun listEvents(): List<EventReadModel> = activeSimulation().events()

    private fun activeSimulation(): ActiveSimulationDocuments =
        pmsReadRepository.findActiveSimulationDocuments() ?: throw SimulationNotLoadedException()

    private fun ActiveSimulationDocuments.rooms(): List<RoomReadModel> =
        document<RoomsDocument>("master/rooms.json").rooms

    private fun ActiveSimulationDocuments.roomStatuses(): List<RoomStatusReadModel> =
        document<RoomStatusesDocument>("operations/room-status.json").roomStatuses

    private fun ActiveSimulationDocuments.occupancy(): List<RoomOccupancyReadModel> =
        document<OccupancyDocument>("operations/occupancy.json").occupancy

    private fun ActiveSimulationDocuments.assets(): List<AssetReadModel> =
        document<AssetsDocument>("master/assets.json").assets

    private fun ActiveSimulationDocuments.issueTypes(): List<IssueTypeReadModel> =
        document<IssueTypesDocument>("master/issue-types.json").issueTypes

    private fun ActiveSimulationDocuments.publicAreas(): List<PublicAreaReadModel> =
        document<PublicAreasDocument>("master/public-areas.json").publicAreas

    private fun ActiveSimulationDocuments.reservations(): List<ReservationReadModel> =
        document<ReservationsDocument>("operations/reservations.json").reservations

    private fun ActiveSimulationDocuments.guests(): List<GuestReadModel> =
        document<GuestsDocument>("operations/guests.json").guests

    private fun ActiveSimulationDocuments.events(): List<EventReadModel> =
        document<EventsDocument>("operations/events.json").events

    private inline fun <reified T : Any> ActiveSimulationDocuments.document(path: String): T {
        val node = documents[path] ?: throw SimulationNotLoadedException()
        return objectMapper.treeToValue(node, T::class.java)
    }

    private inline fun <T> List<T>.firstOrNotFound(
        predicate: (T) -> Boolean,
        resourceType: String,
        resourceId: String
    ): T =
        firstOrNull(predicate) ?: throw PmsResourceNotFoundException(resourceType, resourceId)
}
