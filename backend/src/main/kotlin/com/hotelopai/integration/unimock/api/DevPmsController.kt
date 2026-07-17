package com.hotelopai.integration.unimock.api

import com.hotelopai.integration.unimock.PmsLookupService
import com.hotelopai.integration.unimock.PmsEventCreateRequest
import com.hotelopai.integration.unimock.PmsRoomStatusUpdateRequest
import com.hotelopai.shared.security.PermissionExpressions
import org.springframework.context.annotation.Profile
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@Profile("local", "test")
@RequestMapping("/api/v1/dev/pms")
class DevPmsController(
    private val pmsLookupService: PmsLookupService
) {
    @GetMapping("/rooms")
    @PreAuthorize(PermissionExpressions.DEV_PMS_ACCESS)
    fun listRooms(): List<RoomResponse> =
        pmsLookupService.listRooms().map { it.toResponse() }

    @GetMapping("/rooms/{roomNumber}")
    @PreAuthorize(PermissionExpressions.DEV_PMS_ACCESS)
    fun getRoom(@PathVariable roomNumber: String): RoomResponse =
        pmsLookupService.getRoom(roomNumber).toResponse()

    @GetMapping("/rooms/{roomNumber}/status")
    @PreAuthorize(PermissionExpressions.DEV_PMS_ACCESS)
    fun getRoomStatus(@PathVariable roomNumber: String): RoomStatusResponse =
        pmsLookupService.getRoomStatus(roomNumber).toResponse()

    @GetMapping("/rooms/{roomNumber}/occupancy")
    @PreAuthorize(PermissionExpressions.DEV_PMS_ACCESS)
    fun getRoomOccupancy(@PathVariable roomNumber: String): RoomOccupancyResponse =
        pmsLookupService.getRoomOccupancy(roomNumber).toResponse()

    @GetMapping("/rooms/{roomNumber}/assets")
    @PreAuthorize(PermissionExpressions.DEV_PMS_ACCESS)
    fun getRoomAssets(@PathVariable roomNumber: String): List<AssetResponse> =
        pmsLookupService.getRoomAssets(roomNumber).map { it.toResponse() }

    @GetMapping("/assets/{assetId}")
    @PreAuthorize(PermissionExpressions.DEV_PMS_ACCESS)
    fun getAsset(@PathVariable assetId: String): AssetResponse =
        pmsLookupService.getAsset(assetId).toResponse()

    @GetMapping("/issue-types")
    @PreAuthorize(PermissionExpressions.DEV_PMS_ACCESS)
    fun listIssueTypes(): List<IssueTypeResponse> =
        pmsLookupService.listIssueTypes().map { it.toResponse() }

    @PostMapping("/rooms/{roomNumber}/status")
    @PreAuthorize(PermissionExpressions.DEV_PMS_ACCESS)
    fun updateRoomStatus(
        @PathVariable roomNumber: String,
        @RequestBody request: RoomStatusUpdateRequest
    ): PmsUpdateResponse =
        pmsLookupService.updateRoomStatus(
            roomNumber = roomNumber,
            request = PmsRoomStatusUpdateRequest(status = request.status)
        ).toResponse()

    @PostMapping("/maintenance/updates")
    @PreAuthorize(PermissionExpressions.DEV_PMS_ACCESS)
    fun updateMaintenance(
        @RequestBody request: MaintenanceUpdateRequest
    ): PmsUpdateResponse =
        pmsLookupService.updateMaintenance(
            roomNumber = request.roomNumber,
            issueTypeCode = request.issueTypeCode,
            description = request.description,
            status = request.status
        ).toResponse()

    @PostMapping("/events")
    @PreAuthorize(PermissionExpressions.DEV_PMS_ACCESS)
    fun pushEvent(
        @RequestBody request: EventPushRequest
    ): EventResponse =
        pmsLookupService.createEvent(
            PmsEventCreateRequest(
                eventId = request.eventId,
                type = request.type,
                subject = request.subject,
                description = request.description,
                entityType = request.entityType,
                entityId = request.entityId,
                roomId = request.roomId,
                occurredAt = request.occurredAt?.let(java.time.Instant::parse),
                metadata = request.metadata
            )
        ).toResponse()
}

private fun com.hotelopai.integration.unimock.PmsRoom.toResponse() =
    RoomResponse(roomNumber, roomTypeId, roomTypeName, floor, occupied, status)

private fun com.hotelopai.integration.unimock.PmsRoomStatus.toResponse() =
    RoomStatusResponse(roomNumber, status, updatedAt)

private fun com.hotelopai.integration.unimock.PmsRoomOccupancy.toResponse() =
    RoomOccupancyResponse(roomNumber, reservationId, guestId, occupied)

private fun com.hotelopai.integration.unimock.PmsAsset.toResponse() =
    AssetResponse(assetId, assetCode, assetName, assetType, roomId, publicAreaId, status, issueTypeId)

private fun com.hotelopai.integration.unimock.PmsIssueType.toResponse() =
    IssueTypeResponse(issueTypeId, code, name, category, requiresPmsUpdate, active)

private fun com.hotelopai.integration.unimock.PmsUpdateResult.toResponse() =
    PmsUpdateResponse(verificationLogId, entityId, operation, status)

private fun com.hotelopai.integration.unimock.PmsEvent.toResponse() =
    EventResponse(
        eventId = eventId,
        type = type,
        subject = subject,
        description = description,
        entityType = entityType,
        entityId = entityId,
        roomId = roomId,
        occurredAt = occurredAt?.toString(),
        metadata = metadata
    )
