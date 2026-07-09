package com.hotelopai.integration.unimock

interface UniMockClient {
    fun getRoom(roomId: String): PmsRoom?

    fun getRoomStatus(roomNumber: String): PmsRoomStatus?

    fun getRoomOccupancy(roomNumber: String): PmsRoomOccupancy?

    fun getRoomAssets(roomNumber: String): List<PmsAsset>

    fun listRooms(): List<PmsRoom>

    fun getAsset(assetId: String): PmsAsset?

    fun listIssueTypes(): List<PmsIssueType>

    fun getGuestRequest(guestRequestId: String): PmsGuestRequest?

    fun updateGuestRequestStatus(
        guestRequestId: String,
        request: PmsGuestRequestStatusUpdateRequest
    ): PmsGuestRequest

    fun updateRoomStatus(
        roomNumber: String,
        request: PmsRoomStatusUpdateRequest
    ): PmsUpdateResult

    fun updateMaintenance(request: PmsMaintenanceUpdateRequest): PmsUpdateResult

    fun createEvent(request: PmsEventCreateRequest): PmsEvent
}
