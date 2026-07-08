package com.hotelopai.unimock.api.pms

import com.hotelopai.unimock.application.pms.PmsReadService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/pms")
class PmsReadController(
    private val pmsReadService: PmsReadService
) {
    @GetMapping("/rooms")
    fun listRooms(): List<RoomResponse> =
        pmsReadService.listRooms().map { it.toResponse() }

    @GetMapping("/rooms/{roomNumber}")
    fun getRoom(@PathVariable roomNumber: String): RoomResponse =
        pmsReadService.getRoom(roomNumber).toResponse()

    @GetMapping("/rooms/{roomNumber}/status")
    fun getRoomStatus(@PathVariable roomNumber: String): RoomStatusResponse =
        pmsReadService.getRoomStatus(roomNumber).toResponse()

    @GetMapping("/rooms/{roomNumber}/occupancy")
    fun getRoomOccupancy(@PathVariable roomNumber: String): RoomOccupancyResponse =
        pmsReadService.getRoomOccupancy(roomNumber).toResponse()

    @GetMapping("/rooms/{roomNumber}/assets")
    fun getRoomAssets(@PathVariable roomNumber: String): List<AssetResponse> =
        pmsReadService.getRoomAssets(roomNumber).map { it.toResponse() }

    @GetMapping("/assets/{assetId}")
    fun getAsset(@PathVariable assetId: String): AssetResponse =
        pmsReadService.getAsset(assetId).toResponse()

    @GetMapping("/issue-types")
    fun listIssueTypes(): List<IssueTypeResponse> =
        pmsReadService.listIssueTypes().map { it.toResponse() }

    @GetMapping("/public-areas")
    fun listPublicAreas(): List<PublicAreaResponse> =
        pmsReadService.listPublicAreas().map { it.toResponse() }

    @GetMapping("/reservations")
    fun listReservations(): List<ReservationResponse> =
        pmsReadService.listReservations().map { it.toResponse() }

    @GetMapping("/reservations/{reservationId}")
    fun getReservation(@PathVariable reservationId: String): ReservationResponse =
        pmsReadService.getReservation(reservationId).toResponse()

    @GetMapping("/guests/{guestId}")
    fun getGuest(@PathVariable guestId: String): GuestResponse =
        pmsReadService.getGuest(guestId).toResponse()

    @GetMapping("/events")
    fun listEvents(): List<EventResponse> =
        pmsReadService.listEvents().map { it.toResponse() }
}

private fun com.hotelopai.unimock.application.pms.RoomReadModel.toResponse() =
    RoomResponse(roomNumber, roomTypeCode, floorCode, status, isOutOfOrder)

private fun com.hotelopai.unimock.application.pms.RoomStatusReadModel.toResponse() =
    RoomStatusResponse(roomNumber, status, updatedAt)

private fun com.hotelopai.unimock.application.pms.RoomOccupancyReadModel.toResponse() =
    RoomOccupancyResponse(roomNumber, reservationId, guestId, occupied)

private fun com.hotelopai.unimock.application.pms.AssetReadModel.toResponse() =
    AssetResponse(assetId, roomNumber, name)

private fun com.hotelopai.unimock.application.pms.IssueTypeReadModel.toResponse() =
    IssueTypeResponse(code, name)

private fun com.hotelopai.unimock.application.pms.PublicAreaReadModel.toResponse() =
    PublicAreaResponse(code, name)

private fun com.hotelopai.unimock.application.pms.ReservationReadModel.toResponse() =
    ReservationResponse(reservationId, roomNumber, guestId, status)

private fun com.hotelopai.unimock.application.pms.GuestReadModel.toResponse() =
    GuestResponse(guestId, firstName, lastName)

private fun com.hotelopai.unimock.application.pms.EventReadModel.toResponse() =
    EventResponse(eventId, type, occurredAt)
