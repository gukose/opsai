package com.hotelopai.pms.domain

import java.time.Instant
import java.util.UUID

@JvmInline
value class PmsProviderId(val value: String) {
    init {
        require(value.isNotBlank()) { "PMS provider id must not be blank" }
    }
}

data class PmsHotel(
    val hotelId: String,
    val code: String? = null,
    val name: String? = null
)

data class PmsRoom(
    val id: String,
    val number: String,
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

data class PmsStay(
    val reservationId: String? = null,
    val guestId: String? = null,
    val roomNumber: String,
    val occupied: Boolean = false
)

data class PmsGuest(
    val id: String,
    val fullName: String? = null
)

data class PmsReservation(
    val id: String,
    val guestId: String? = null,
    val roomNumber: String? = null,
    val arrivalDate: String? = null,
    val departureDate: String? = null,
    val status: String? = null
)

data class PmsHousekeepingTask(
    val id: String,
    val status: String,
    val roomId: String? = null,
    val roomNumber: String? = null,
    val guestName: String? = null,
    val description: String? = null,
    val source: String? = null,
    val createdAt: Instant? = null,
    val updatedAt: Instant? = null
)

data class PmsAsset(
    val id: String,
    val code: String? = null,
    val name: String,
    val type: String? = null,
    val roomId: String? = null,
    val publicAreaId: String? = null,
    val status: String? = null,
    val issueTypeId: String? = null
)

data class PmsIssueType(
    val id: String,
    val code: String,
    val name: String,
    val category: String? = null,
    val requiresPmsUpdate: Boolean = false,
    val active: Boolean = true
)

data class PmsEvent(
    val id: String,
    val type: String,
    val subject: String,
    val description: String? = null,
    val entityType: String? = null,
    val entityId: String? = null,
    val roomId: String? = null,
    val occurredAt: Instant? = null,
    val metadata: Map<String, String> = emptyMap()
)

data class RoomStatusUpdate(
    val status: String
)

data class MaintenanceUpdate(
    val roomNumber: String,
    val issueTypeCode: String,
    val description: String,
    val status: String = "OPEN"
)

data class HousekeepingTaskStatusUpdate(
    val status: String,
    val note: String? = null,
    val resolvedAt: Instant? = null
)

data class PmsEventCreateCommand(
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

data class PmsUpdateResult(
    val verificationLogId: UUID,
    val entityId: String?,
    val operation: String,
    val status: String
)

open class PmsProviderException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)

class PmsProviderUnavailableException(
    message: String,
    cause: Throwable? = null
) : PmsProviderException(message, cause)

class PmsProviderTimeoutException(
    message: String,
    cause: Throwable? = null
) : PmsProviderException(message, cause)

class PmsProviderAuthenticationException(
    message: String,
    cause: Throwable? = null
) : PmsProviderException(message, cause)

class PmsProviderPermissionException(
    message: String,
    cause: Throwable? = null
) : PmsProviderException(message, cause)

class PmsProviderInvalidRequestException(
    message: String,
    cause: Throwable? = null
) : PmsProviderException(message, cause)

class PmsProviderRateLimitException(
    message: String,
    val retryAfterSeconds: Long? = null,
    cause: Throwable? = null
) : PmsProviderException(message, cause)

class PmsProviderMalformedResponseException(
    message: String,
    cause: Throwable? = null
) : PmsProviderException(message, cause)

class PmsProviderConfigurationFailureException(
    message: String,
    cause: Throwable? = null
) : PmsProviderException(message, cause)

class PmsProviderCircuitOpenException(
    message: String,
    cause: Throwable? = null
) : PmsProviderException(message, cause)

class PmsProviderResourceNotFoundException(
    message: String
) : RuntimeException(message) {
    constructor(resourceType: String, resourceId: String) : this("$resourceType not found: $resourceId")
}
