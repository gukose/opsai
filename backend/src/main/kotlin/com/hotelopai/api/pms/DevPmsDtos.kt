package com.hotelopai.integration.unimock.api

import java.util.UUID

data class RoomResponse(
    val roomNumber: String,
    val roomTypeId: String?,
    val roomTypeName: String?,
    val floor: String?,
    val occupied: Boolean,
    val status: String?
)

data class RoomStatusResponse(
    val roomNumber: String,
    val status: String,
    val updatedAt: String
)

data class RoomOccupancyResponse(
    val roomNumber: String,
    val reservationId: String?,
    val guestId: String?,
    val occupied: Boolean
)

data class AssetResponse(
    val assetId: String,
    val assetCode: String?,
    val assetName: String,
    val assetType: String?,
    val roomId: String?,
    val publicAreaId: String?,
    val status: String?,
    val issueTypeId: String?
)

data class IssueTypeResponse(
    val issueTypeId: String,
    val code: String,
    val name: String,
    val category: String?,
    val requiresPmsUpdate: Boolean,
    val active: Boolean
)

data class PmsUpdateResponse(
    val verificationLogId: UUID,
    val entityId: String?,
    val operation: String,
    val status: String
)

data class EventResponse(
    val eventId: String,
    val type: String,
    val subject: String,
    val description: String?,
    val entityType: String?,
    val entityId: String?,
    val roomId: String?,
    val occurredAt: String?,
    val metadata: Map<String, String>
)

data class RoomStatusUpdateRequest(
    val status: String
)

data class MaintenanceUpdateRequest(
    val roomNumber: String,
    val issueTypeCode: String,
    val description: String,
    val status: String = "OPEN"
)

data class EventPushRequest(
    val eventId: String? = null,
    val type: String,
    val subject: String,
    val description: String? = null,
    val entityType: String? = null,
    val entityId: String? = null,
    val roomId: String? = null,
    val occurredAt: String? = null,
    val metadata: Map<String, String> = emptyMap()
)
