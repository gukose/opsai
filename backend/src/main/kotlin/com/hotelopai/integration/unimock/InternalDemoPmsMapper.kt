package com.hotelopai.integration.unimock

import com.hotelopai.pms.domain.HousekeepingTaskStatusUpdate
import com.hotelopai.pms.domain.MaintenanceUpdate
import com.hotelopai.pms.domain.PmsEventCreateCommand
import com.hotelopai.pms.domain.RoomStatusUpdate
import com.hotelopai.pms.domain.PmsAsset as DomainPmsAsset
import com.hotelopai.pms.domain.PmsEvent as DomainPmsEvent
import com.hotelopai.pms.domain.PmsHousekeepingTask as DomainPmsHousekeepingTask
import com.hotelopai.pms.domain.PmsIssueType as DomainPmsIssueType
import com.hotelopai.pms.domain.PmsRoom as DomainPmsRoom
import com.hotelopai.pms.domain.PmsRoomStatus as DomainPmsRoomStatus
import com.hotelopai.pms.domain.PmsStay as DomainPmsStay
import com.hotelopai.pms.domain.PmsUpdateResult as DomainPmsUpdateResult

object InternalDemoPmsMapper {
    fun PmsRoom.toDomain(): DomainPmsRoom =
        DomainPmsRoom(
            id = roomId,
            number = roomNumber,
            roomTypeId = roomTypeId,
            roomTypeName = roomTypeName,
            floor = floor,
            occupied = occupied,
            status = status
        )

    fun PmsRoomStatus.toDomain(): DomainPmsRoomStatus =
        DomainPmsRoomStatus(
            roomNumber = roomNumber,
            status = status,
            updatedAt = updatedAt
        )

    fun PmsRoomOccupancy.toDomain(): DomainPmsStay =
        DomainPmsStay(
            reservationId = reservationId,
            guestId = guestId,
            roomNumber = roomNumber,
            occupied = occupied
        )

    fun PmsAsset.toDomain(): DomainPmsAsset =
        DomainPmsAsset(
            id = assetId,
            code = assetCode,
            name = assetName,
            type = assetType,
            roomId = roomId,
            publicAreaId = publicAreaId,
            status = status,
            issueTypeId = issueTypeId
        )

    fun PmsIssueType.toDomain(): DomainPmsIssueType =
        DomainPmsIssueType(
            id = issueTypeId,
            code = code,
            name = name,
            category = category,
            requiresPmsUpdate = requiresPmsUpdate,
            active = active
        )

    fun PmsGuestRequest.toDomain(): DomainPmsHousekeepingTask =
        DomainPmsHousekeepingTask(
            id = guestRequestId,
            status = status,
            roomId = roomId,
            roomNumber = roomNumber,
            guestName = guestName,
            description = description,
            source = source,
            createdAt = createdAt,
            updatedAt = updatedAt
        )

    fun PmsEvent.toDomain(): DomainPmsEvent =
        DomainPmsEvent(
            id = eventId,
            type = type,
            subject = subject,
            description = description,
            entityType = entityType,
            entityId = entityId,
            roomId = roomId,
            occurredAt = occurredAt,
            metadata = metadata
        )

    fun PmsUpdateResult.toDomain(): DomainPmsUpdateResult =
        DomainPmsUpdateResult(
            verificationLogId = verificationLogId,
            entityId = entityId,
            operation = operation,
            status = status
        )

    fun RoomStatusUpdate.toInternalDemoRequest(): PmsRoomStatusUpdateRequest =
        PmsRoomStatusUpdateRequest(status = status)

    fun MaintenanceUpdate.toInternalDemoRequest(): PmsMaintenanceUpdateRequest =
        PmsMaintenanceUpdateRequest(
            roomNumber = roomNumber,
            issueTypeCode = issueTypeCode,
            description = description,
            status = status
        )

    fun HousekeepingTaskStatusUpdate.toInternalDemoRequest(): PmsGuestRequestStatusUpdateRequest =
        PmsGuestRequestStatusUpdateRequest(
            status = status,
            note = note,
            resolvedAt = resolvedAt
        )

    fun PmsEventCreateCommand.toInternalDemoRequest(): PmsEventCreateRequest =
        PmsEventCreateRequest(
            eventId = eventId,
            type = type,
            subject = subject,
            description = description,
            entityType = entityType,
            entityId = entityId,
            roomId = roomId,
            occurredAt = occurredAt,
            metadata = metadata
        )
}
