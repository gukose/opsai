package com.hotelopai.integration.unimock

import org.springframework.stereotype.Service

@Service
class PmsLookupService(
    private val uniMockClient: UniMockClient
) {
    fun findRoom(roomId: String): PmsRoom? = withIntegrationHandling {
        uniMockClient.getRoom(roomId)
    }

    fun getRoom(roomId: String): PmsRoom =
        findRoom(roomId) ?: throw PmsResourceNotFoundException("Room", roomId)

    fun findRoomStatus(roomNumber: String): PmsRoomStatus? = withIntegrationHandling {
        uniMockClient.getRoomStatus(roomNumber)
    }

    fun getRoomStatus(roomNumber: String): PmsRoomStatus =
        findRoomStatus(roomNumber) ?: throw PmsResourceNotFoundException("Room status", roomNumber)

    fun findRoomOccupancy(roomNumber: String): PmsRoomOccupancy? = withIntegrationHandling {
        uniMockClient.getRoomOccupancy(roomNumber)
    }

    fun getRoomOccupancy(roomNumber: String): PmsRoomOccupancy =
        findRoomOccupancy(roomNumber) ?: throw PmsResourceNotFoundException("Room occupancy", roomNumber)

    fun getRoomAssets(roomNumber: String): List<PmsAsset> = withIntegrationHandling {
        uniMockClient.getRoomAssets(roomNumber)
    }

    fun listRooms(): List<PmsRoom> = withIntegrationHandling {
        uniMockClient.listRooms()
    }

    fun findAsset(assetId: String): PmsAsset? = withIntegrationHandling {
        uniMockClient.getAsset(assetId)
    }

    fun getAsset(assetId: String): PmsAsset =
        findAsset(assetId) ?: throw PmsResourceNotFoundException("Asset", assetId)

    fun listIssueTypes(): List<PmsIssueType> = withIntegrationHandling {
        uniMockClient.listIssueTypes()
    }

    fun findGuestRequest(guestRequestId: String): PmsGuestRequest? =
        withIntegrationHandling {
            uniMockClient.getGuestRequest(guestRequestId)
        }

    fun getGuestRequest(guestRequestId: String): PmsGuestRequest =
        findGuestRequest(guestRequestId) ?: throw PmsResourceNotFoundException("Guest request", guestRequestId)

    fun updateGuestRequestStatus(
        guestRequestId: String,
        request: PmsGuestRequestStatusUpdateRequest
    ): PmsGuestRequest = withIntegrationHandling {
        uniMockClient.updateGuestRequestStatus(guestRequestId, request)
    }

    fun updateRoomStatus(
        roomNumber: String,
        request: PmsRoomStatusUpdateRequest
    ): PmsUpdateResult = withIntegrationHandling {
        requireRoom(roomNumber)
        uniMockClient.updateRoomStatus(roomNumber, request)
    }

    fun updateMaintenance(
        roomNumber: String,
        issueTypeCode: String,
        description: String,
        status: String
    ): PmsUpdateResult = withIntegrationHandling {
        requireRoom(roomNumber)
        requireIssueType(issueTypeCode)
        uniMockClient.updateMaintenance(
            PmsMaintenanceUpdateRequest(
                roomNumber = roomNumber,
                issueTypeCode = issueTypeCode,
                description = description,
                status = status
            )
        )
    }

    fun createEvent(request: PmsEventCreateRequest): PmsEvent =
        withIntegrationHandling {
            uniMockClient.createEvent(request)
        }

    private fun requireRoom(roomNumber: String) {
        if (uniMockClient.getRoom(roomNumber) == null) {
            throw PmsResourceNotFoundException("Room", roomNumber)
        }
    }

    private fun requireIssueType(issueTypeCode: String) {
        if (uniMockClient.listIssueTypes().none { it.code == issueTypeCode }) {
            throw PmsResourceNotFoundException("Issue type", issueTypeCode)
        }
    }

    private inline fun <T> withIntegrationHandling(operation: () -> T): T {
        try {
            return operation()
        } catch (exception: com.hotelopai.integration.unimock.UniMockClientTimeoutException) {
            throw PmsIntegrationTimeoutException(exception.message ?: "UniMock timed out", exception)
        } catch (exception: com.hotelopai.integration.unimock.UniMockClientUnavailableException) {
            throw PmsIntegrationUnavailableException(exception.message ?: "UniMock unavailable", exception)
        } catch (exception: com.hotelopai.integration.unimock.UniMockClientRateLimitedException) {
            throw PmsIntegrationUnavailableException(exception.message ?: "UniMock rate limited", exception)
        } catch (exception: com.hotelopai.integration.unimock.UniMockClientNotFoundException) {
            throw PmsResourceNotFoundException(exception.message ?: "PMS resource not found")
        }
    }
}
