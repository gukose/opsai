package com.hotelopai.unimock.api.pms

import java.time.Instant
import java.util.UUID

data class RoomResponse(
    val roomId: String,
    val roomNumber: String,
    val roomTypeId: String? = null,
    val roomTypeName: String? = null,
    val floor: String? = null,
    val occupied: Boolean = false,
    val status: String? = null,
    val roomTypeCode: String? = null,
    val floorCode: String? = null,
    val isOutOfOrder: Boolean = false
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
    val roomNumber: String,
    val name: String
)

data class IssueTypeResponse(
    val code: String,
    val name: String
)

data class PublicAreaResponse(
    val code: String,
    val name: String
)

data class ReservationResponse(
    val reservationId: String,
    val roomNumber: String,
    val guestId: String,
    val status: String
)

data class GuestResponse(
    val guestId: String,
    val firstName: String,
    val lastName: String
)

data class EventResponse(
    val eventId: String,
    val type: String,
    val occurredAt: String,
    val roomNumber: String? = null,
    val toRoomNumber: String? = null,
    val deliveryStatus: String? = null,
    val message: String? = null
)

data class PmsUpdateResponse(
    val verificationLogId: UUID,
    val entityId: String?,
    val operation: String,
    val status: String
)

data class PmsVerificationLogResponse(
    val verificationLogId: UUID,
    val simulationId: String,
    val entityType: String,
    val entityId: String?,
    val operation: String,
    val requestPayloadJson: String?,
    val responsePayloadJson: String?,
    val status: String,
    val sourceSystem: String,
    val destinationSystem: String,
    val httpStatus: Int?,
    val durationMs: Long?,
    val retryCount: Int,
    val correlationId: String?,
    val createdAt: Instant
)
