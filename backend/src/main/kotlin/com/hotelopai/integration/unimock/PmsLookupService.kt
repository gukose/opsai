package com.hotelopai.integration.unimock

import com.hotelopai.pms.application.PmsProviderRegistry
import com.hotelopai.pms.application.PmsCapability
import com.hotelopai.pms.application.UnsupportedPmsCapabilityException
import com.hotelopai.pms.domain.HousekeepingTaskStatusUpdate
import com.hotelopai.pms.domain.MaintenanceUpdate
import com.hotelopai.pms.domain.PmsEventCreateCommand
import com.hotelopai.pms.domain.PmsProviderResourceNotFoundException
import com.hotelopai.pms.domain.PmsProviderTimeoutException
import com.hotelopai.pms.domain.PmsProviderUnavailableException
import com.hotelopai.pms.domain.RoomStatusUpdate
import org.springframework.stereotype.Service
import com.hotelopai.pms.domain.PmsAsset as DomainPmsAsset
import com.hotelopai.pms.domain.PmsEvent as DomainPmsEvent
import com.hotelopai.pms.domain.PmsHousekeepingTask as DomainPmsHousekeepingTask
import com.hotelopai.pms.domain.PmsIssueType as DomainPmsIssueType
import com.hotelopai.pms.domain.PmsRoom as DomainPmsRoom
import com.hotelopai.pms.domain.PmsRoomStatus as DomainPmsRoomStatus
import com.hotelopai.pms.domain.PmsStay as DomainPmsStay
import com.hotelopai.pms.domain.PmsUpdateResult as DomainPmsUpdateResult

@Service
class PmsLookupService(
    private val pmsProviderRegistry: PmsProviderRegistry
) {
    fun findRoom(roomId: String): PmsRoom? = withIntegrationHandling {
        activeProvider(PmsCapability.ROOM_LISTING).findRoom(roomId)?.toLegacy()
    }

    fun getRoom(roomId: String): PmsRoom =
        findRoom(roomId) ?: throw PmsResourceNotFoundException("Room", roomId)

    fun findRoomStatus(roomNumber: String): PmsRoomStatus? = withIntegrationHandling {
        activeProvider(PmsCapability.ROOM_STATUS_LOOKUP).findRoomStatus(roomNumber)?.toLegacy()
    }

    fun getRoomStatus(roomNumber: String): PmsRoomStatus =
        findRoomStatus(roomNumber) ?: throw PmsResourceNotFoundException("Room status", roomNumber)

    fun findRoomOccupancy(roomNumber: String): PmsRoomOccupancy? = withIntegrationHandling {
        activeProvider(PmsCapability.STAY_LOOKUP).findStay(roomNumber)?.toLegacy()
    }

    fun getRoomOccupancy(roomNumber: String): PmsRoomOccupancy =
        findRoomOccupancy(roomNumber) ?: throw PmsResourceNotFoundException("Room occupancy", roomNumber)

    fun getRoomAssets(roomNumber: String): List<PmsAsset> = withIntegrationHandling {
        activeProvider(PmsCapability.ASSET_LOOKUP).getRoomAssets(roomNumber).map { it.toLegacy() }
    }

    fun listRooms(): List<PmsRoom> = withIntegrationHandling {
        activeProvider(PmsCapability.ROOM_LISTING).listRooms().map { it.toLegacy() }
    }

    fun findAsset(assetId: String): PmsAsset? = withIntegrationHandling {
        activeProvider(PmsCapability.ASSET_LOOKUP).findAsset(assetId)?.toLegacy()
    }

    fun getAsset(assetId: String): PmsAsset =
        findAsset(assetId) ?: throw PmsResourceNotFoundException("Asset", assetId)

    fun listIssueTypes(): List<PmsIssueType> = withIntegrationHandling {
        activeProvider(PmsCapability.ISSUE_TYPE_LOOKUP).listIssueTypes().map { it.toLegacy() }
    }

    fun findGuestRequest(guestRequestId: String): PmsGuestRequest? =
        withIntegrationHandling {
            activeProvider(PmsCapability.HOUSEKEEPING_STATUS_UPDATE).findHousekeepingTask(guestRequestId)?.toLegacy()
        }

    fun getGuestRequest(guestRequestId: String): PmsGuestRequest =
        findGuestRequest(guestRequestId) ?: throw PmsResourceNotFoundException("Guest request", guestRequestId)

    fun updateGuestRequestStatus(
        guestRequestId: String,
        request: PmsGuestRequestStatusUpdateRequest
    ): PmsGuestRequest = withIntegrationHandling {
        activeProvider(PmsCapability.HOUSEKEEPING_STATUS_UPDATE).updateHousekeepingTaskStatus(
            guestRequestId,
            HousekeepingTaskStatusUpdate(
                status = request.status,
                note = request.note,
                resolvedAt = request.resolvedAt
            )
        ).toLegacy()
    }

    fun updateRoomStatus(
        roomNumber: String,
        request: PmsRoomStatusUpdateRequest
    ): PmsUpdateResult = withIntegrationHandling {
        activeProvider(PmsCapability.ROOM_STATUS_UPDATE).updateRoomStatus(
            roomNumber,
            RoomStatusUpdate(status = request.status)
        ).toLegacy()
    }

    fun updateMaintenance(
        roomNumber: String,
        issueTypeCode: String,
        description: String,
        status: String
    ): PmsUpdateResult = withIntegrationHandling {
        activeProvider(PmsCapability.MAINTENANCE_UPDATE).updateMaintenance(
            MaintenanceUpdate(
                roomNumber = roomNumber,
                issueTypeCode = issueTypeCode,
                description = description,
                status = status
            )
        ).toLegacy()
    }

    fun createEvent(request: PmsEventCreateRequest): PmsEvent =
        withIntegrationHandling {
            activeProvider(PmsCapability.EVENT_CREATION).createEvent(
                PmsEventCreateCommand(
                    eventId = request.eventId,
                    type = request.type,
                    subject = request.subject,
                    description = request.description,
                    entityType = request.entityType,
                    entityId = request.entityId,
                    roomId = request.roomId,
                    occurredAt = request.occurredAt,
                    metadata = request.metadata
                )
            ).toLegacy()
        }

    private fun activeProvider(capability: PmsCapability) =
        pmsProviderRegistry.activeProviderRequiring(capability)

    private inline fun <T> withIntegrationHandling(operation: () -> T): T {
        try {
            return operation()
        } catch (exception: PmsProviderTimeoutException) {
            throw PmsIntegrationTimeoutException(exception.message ?: "UniMock timed out", exception)
        } catch (exception: PmsProviderUnavailableException) {
            throw PmsIntegrationUnavailableException(exception.message ?: "UniMock unavailable", exception)
        } catch (exception: PmsProviderResourceNotFoundException) {
            throw PmsResourceNotFoundException(exception.message ?: "PMS resource not found")
        } catch (exception: UnsupportedPmsCapabilityException) {
            throw IllegalStateException(exception.message ?: "PMS provider capability is unsupported", exception)
        }
    }
}

private fun DomainPmsRoom.toLegacy(): PmsRoom =
    PmsRoom(
        roomId = id,
        roomNumber = number,
        roomTypeId = roomTypeId,
        roomTypeName = roomTypeName,
        floor = floor,
        occupied = occupied,
        status = status
    )

private fun DomainPmsRoomStatus.toLegacy(): PmsRoomStatus =
    PmsRoomStatus(
        roomNumber = roomNumber,
        status = status,
        updatedAt = updatedAt
    )

private fun DomainPmsStay.toLegacy(): PmsRoomOccupancy =
    PmsRoomOccupancy(
        roomNumber = roomNumber,
        reservationId = reservationId,
        guestId = guestId,
        occupied = occupied
    )

private fun DomainPmsAsset.toLegacy(): PmsAsset =
    PmsAsset(
        assetId = id,
        assetCode = code,
        assetName = name,
        assetType = type,
        roomId = roomId,
        publicAreaId = publicAreaId,
        status = status,
        issueTypeId = issueTypeId
    )

private fun DomainPmsIssueType.toLegacy(): PmsIssueType =
    PmsIssueType(
        issueTypeId = id,
        code = code,
        name = name,
        category = category,
        requiresPmsUpdate = requiresPmsUpdate,
        active = active
    )

private fun DomainPmsHousekeepingTask.toLegacy(): PmsGuestRequest =
    PmsGuestRequest(
        guestRequestId = id,
        status = status,
        roomId = roomId,
        roomNumber = roomNumber,
        guestName = guestName,
        description = description,
        source = source,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

private fun DomainPmsUpdateResult.toLegacy(): PmsUpdateResult =
    PmsUpdateResult(
        verificationLogId = verificationLogId,
        entityId = entityId,
        operation = operation,
        status = status
    )

private fun DomainPmsEvent.toLegacy(): PmsEvent =
    PmsEvent(
        eventId = id,
        type = type,
        subject = subject,
        description = description,
        entityType = entityType,
        entityId = entityId,
        roomId = roomId,
        occurredAt = occurredAt,
        metadata = metadata
    )
