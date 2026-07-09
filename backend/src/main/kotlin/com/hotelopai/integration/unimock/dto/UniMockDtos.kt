package com.hotelopai.integration.unimock.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.time.Instant

@JsonIgnoreProperties(ignoreUnknown = true)
data class UniMockRoomDto(
    val roomId: String,
    val roomNumber: String,
    val roomTypeId: String? = null,
    val roomTypeName: String? = null,
    val floor: String? = null,
    val occupied: Boolean = false,
    val status: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class UniMockRoomStatusDto(
    val roomNumber: String,
    val status: String,
    val updatedAt: String
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class UniMockRoomOccupancyDto(
    val roomNumber: String,
    val reservationId: String? = null,
    val guestId: String? = null,
    val occupied: Boolean = false
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class UniMockAssetDto(
    val assetId: String,
    val assetCode: String? = null,
    val assetName: String,
    val assetType: String? = null,
    val roomId: String? = null,
    val publicAreaId: String? = null,
    val status: String? = null,
    val issueTypeId: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class UniMockIssueTypeDto(
    val issueTypeId: String,
    val code: String,
    val name: String,
    val category: String? = null,
    val requiresPmsUpdate: Boolean = false,
    val active: Boolean = true
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class UniMockGuestRequestDto(
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

@JsonIgnoreProperties(ignoreUnknown = true)
data class UniMockEventDto(
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

@JsonIgnoreProperties(ignoreUnknown = true)
data class UniMockGuestRequestStatusUpdateRequestDto(
    val status: String,
    val note: String? = null,
    val resolvedAt: Instant? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class UniMockEventCreateRequestDto(
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

@JsonIgnoreProperties(ignoreUnknown = true)
data class UniMockRoomStatusUpdateRequestDto(
    val status: String
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class UniMockMaintenanceUpdateRequestDto(
    val roomNumber: String,
    val issueTypeCode: String,
    val description: String,
    val status: String = "OPEN"
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class UniMockPmsUpdateResponseDto(
    val verificationLogId: java.util.UUID,
    val entityId: String?,
    val operation: String,
    val status: String
)
