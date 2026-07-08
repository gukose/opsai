package com.hotelopai.unimock.application.pms

import java.time.Instant
import java.util.UUID

data class RoomStatusUpdateRequest(
    val status: String
)

data class GuestRequestCreateRequest(
    val roomNumber: String,
    val description: String,
    val status: String? = null
)

data class GuestRequestStatusUpdateRequest(
    val status: String
)

data class MinibarItemUpdateRequest(
    val sku: String,
    val name: String,
    val quantity: Int
)

data class MinibarUpdateRequest(
    val roomNumber: String,
    val items: List<MinibarItemUpdateRequest>
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
    val occurredAt: String? = null
)

data class PmsVerificationLogEntry(
    val id: UUID,
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

data class PmsVerificationLogReadModel(
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

data class PmsUpdateResponse(
    val verificationLogId: UUID,
    val entityId: String?,
    val operation: String,
    val status: String
)
