package com.hotelopai.unimock.api.pms

import com.hotelopai.unimock.application.pms.EventPushRequest
import com.hotelopai.unimock.application.pms.GuestRequestCreateRequest
import com.hotelopai.unimock.application.pms.GuestRequestStatusUpdateRequest
import com.hotelopai.unimock.application.pms.MaintenanceUpdateRequest
import com.hotelopai.unimock.application.pms.MinibarUpdateRequest
import com.hotelopai.unimock.application.pms.PmsUpdateService
import com.hotelopai.unimock.application.pms.RoomStatusUpdateRequest
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/pms")
class PmsUpdateController(
    private val pmsUpdateService: PmsUpdateService
) {
    @PostMapping("/rooms/{roomNumber}/status")
    fun updateRoomStatus(
        @PathVariable roomNumber: String,
        @RequestBody request: RoomStatusUpdateRequest,
        @RequestHeader(value = "X-Correlation-Id", required = false) correlationId: String?
    ): PmsUpdateResponse =
        pmsUpdateService.updateRoomStatus(roomNumber, request, correlationId).toResponse()

    @PostMapping("/guest-requests")
    fun createGuestRequest(
        @RequestBody request: GuestRequestCreateRequest,
        @RequestHeader(value = "X-Correlation-Id", required = false) correlationId: String?
    ): PmsUpdateResponse =
        pmsUpdateService.createGuestRequest(request, correlationId).toResponse()

    @PostMapping("/guest-requests/{guestRequestId}/status")
    fun updateGuestRequestStatus(
        @PathVariable guestRequestId: String,
        @RequestBody request: GuestRequestStatusUpdateRequest,
        @RequestHeader(value = "X-Correlation-Id", required = false) correlationId: String?
    ): PmsUpdateResponse =
        pmsUpdateService.updateGuestRequestStatus(guestRequestId, request, correlationId).toResponse()

    @PostMapping("/minibar/updates")
    fun updateMinibar(
        @RequestBody request: MinibarUpdateRequest,
        @RequestHeader(value = "X-Correlation-Id", required = false) correlationId: String?
    ): PmsUpdateResponse =
        pmsUpdateService.updateMinibar(request, correlationId).toResponse()

    @PostMapping("/maintenance/updates")
    fun updateMaintenance(
        @RequestBody request: MaintenanceUpdateRequest,
        @RequestHeader(value = "X-Correlation-Id", required = false) correlationId: String?
    ): PmsUpdateResponse =
        pmsUpdateService.updateMaintenance(request, correlationId).toResponse()

    @PostMapping("/events")
    fun pushEvent(
        @RequestBody request: EventPushRequest,
        @RequestHeader(value = "X-Correlation-Id", required = false) correlationId: String?
    ): PmsUpdateResponse =
        pmsUpdateService.pushEvent(request, correlationId).toResponse()
}

private fun com.hotelopai.unimock.application.pms.PmsUpdateResponse.toResponse() =
    PmsUpdateResponse(verificationLogId, entityId, operation, status)
