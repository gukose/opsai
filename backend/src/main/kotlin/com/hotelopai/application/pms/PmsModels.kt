package com.hotelopai.integration.unimock

import java.time.Instant
import java.util.UUID

data class PmsRoom(
    val roomId: String,
    val roomNumber: String,
    val roomTypeId: String? = null,
    val roomTypeName: String? = null,
    val floor: String? = null,
    val occupied: Boolean = false,
    val status: String? = null
)

data class PmsRoomStatus(
    val roomNumber: String,
    val status: String,
    val updatedAt: String
)

data class PmsRoomOccupancy(
    val roomNumber: String,
    val reservationId: String? = null,
    val guestId: String? = null,
    val occupied: Boolean = false
)

data class PmsAsset(
    val assetId: String,
    val assetCode: String? = null,
    val assetName: String,
    val assetType: String? = null,
    val roomId: String? = null,
    val publicAreaId: String? = null,
    val status: String? = null,
    val issueTypeId: String? = null
)

data class PmsIssueType(
    val issueTypeId: String,
    val code: String,
    val name: String,
    val category: String? = null,
    val requiresPmsUpdate: Boolean = false,
    val active: Boolean = true
)

data class PmsGuestRequest(
    val guestRequestId: String,
    val status: String,
    val roomId: String? = null,
    val roomNumber: String? = null,
    val guestName: String? = null,
    val description: String? = null,
    val source: String? = null,
    val createdAt: Instant? = null,
    val updatedAt: Instant? = null
)

data class PmsEvent(
    val eventId: String,
    val type: String,
    val subject: String,
    val description: String? = null,
    val entityType: String? = null,
    val entityId: String? = null,
    val roomId: String? = null,
    val occurredAt: Instant? = null,
    val metadata: Map<String, String> = emptyMap()
)

data class PmsRoomStatusUpdateRequest(
    val status: String
)

data class PmsMaintenanceUpdateRequest(
    val roomNumber: String,
    val issueTypeCode: String,
    val description: String,
    val status: String = "OPEN"
)

data class PmsEventCreateRequest(
    val eventId: String? = null,
    val type: String,
    val subject: String,
    val description: String? = null,
    val entityType: String? = null,
    val entityId: String? = null,
    val roomId: String? = null,
    val occurredAt: Instant? = null,
    val metadata: Map<String, String> = emptyMap()
)

data class PmsGuestRequestStatusUpdateRequest(
    val status: String,
    val note: String? = null,
    val resolvedAt: Instant? = null
)

data class PmsUpdateResult(
    val verificationLogId: UUID,
    val entityId: String?,
    val operation: String,
    val status: String
)
