package com.hotelopai.integration.unimock

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.ObjectMapper
import com.hotelopai.integration.unimock.PmsAsset
import com.hotelopai.integration.unimock.PmsEvent
import com.hotelopai.integration.unimock.PmsEventCreateRequest
import com.hotelopai.integration.unimock.PmsGuestRequest
import com.hotelopai.integration.unimock.PmsGuestRequestStatusUpdateRequest
import com.hotelopai.integration.unimock.PmsIssueType
import com.hotelopai.integration.unimock.PmsMaintenanceUpdateRequest
import com.hotelopai.integration.unimock.PmsRoom
import com.hotelopai.integration.unimock.PmsRoomOccupancy
import com.hotelopai.integration.unimock.PmsRoomStatus
import com.hotelopai.integration.unimock.PmsRoomStatusUpdateRequest
import com.hotelopai.integration.unimock.PmsUpdateResult
import com.hotelopai.integration.unimock.UniMockClient
import com.hotelopai.integration.unimock.dto.UniMockAssetDto
import com.hotelopai.integration.unimock.dto.UniMockEventCreateRequestDto
import com.hotelopai.integration.unimock.dto.UniMockEventDto
import com.hotelopai.integration.unimock.dto.UniMockGuestRequestDto
import com.hotelopai.integration.unimock.dto.UniMockGuestRequestStatusUpdateRequestDto
import com.hotelopai.integration.unimock.dto.UniMockIssueTypeDto
import com.hotelopai.integration.unimock.dto.UniMockMaintenanceUpdateRequestDto
import com.hotelopai.integration.unimock.dto.UniMockPmsUpdateResponseDto
import com.hotelopai.integration.unimock.dto.UniMockRoomDto
import com.hotelopai.integration.unimock.dto.UniMockRoomOccupancyDto
import com.hotelopai.integration.unimock.dto.UniMockRoomStatusDto
import com.hotelopai.integration.unimock.dto.UniMockRoomStatusUpdateRequestDto
import com.hotelopai.shared.kernel.CorrelationIdContextHolder
import com.hotelopai.shared.pms.MaintenanceCompletionPort
import com.hotelopai.shared.pms.MaintenanceCompletionRequest
import com.hotelopai.shared.pms.MaintenanceCompletionResult
import com.hotelopai.shared.pms.PmsCompletionException
import org.springframework.http.MediaType
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlin.math.min

class RestUniMockClient(
    private val properties: UniMockClientProperties,
    private val objectMapper: ObjectMapper
) : UniMockClient, MaintenanceCompletionPort {
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(properties.connectTimeout)
        .build()

    override fun getRoom(roomId: String): PmsRoom? =
        getNullable("/rooms/$roomId", object : TypeReference<UniMockRoomDto>() {})?.toRoom()

    override fun getRoomStatus(roomNumber: String): PmsRoomStatus? =
        getNullable("/rooms/$roomNumber/status", object : TypeReference<UniMockRoomStatusDto>() {})?.toRoomStatus()

    override fun getRoomOccupancy(roomNumber: String): PmsRoomOccupancy? =
        getNullable("/rooms/$roomNumber/occupancy", object : TypeReference<UniMockRoomOccupancyDto>() {})?.toRoomOccupancy()

    override fun getRoomAssets(roomNumber: String): List<PmsAsset> =
        getRequired("/rooms/$roomNumber/assets", object : TypeReference<List<UniMockAssetDto>>() {})
            .map { it.toAsset() }

    override fun listRooms(): List<PmsRoom> =
        getRequired("/rooms", object : TypeReference<List<UniMockRoomDto>>() {})
            .map { it.toRoom() }

    override fun getAsset(assetId: String): PmsAsset? =
        getNullable("/assets/$assetId", object : TypeReference<UniMockAssetDto>() {})?.toAsset()

    override fun listIssueTypes(): List<PmsIssueType> =
        getRequired("/issue-types", object : TypeReference<List<UniMockIssueTypeDto>>() {})
            .map { it.toIssueType() }

    override fun getGuestRequest(guestRequestId: String): PmsGuestRequest? =
        getNullable("/guest-requests/$guestRequestId", object : TypeReference<UniMockGuestRequestDto>() {})?.toGuestRequest()

    override fun updateGuestRequestStatus(
        guestRequestId: String,
        request: PmsGuestRequestStatusUpdateRequest
    ): PmsGuestRequest =
        post("/guest-requests/$guestRequestId/status", request.toUniMockRequest(), object : TypeReference<UniMockGuestRequestDto>() {})
            .toGuestRequest()

    override fun updateRoomStatus(
        roomNumber: String,
        request: PmsRoomStatusUpdateRequest
    ): PmsUpdateResult =
        post("/rooms/$roomNumber/status", request.toUniMockRequest(), object : TypeReference<UniMockPmsUpdateResponseDto>() {})
            .toUpdateResult()

    override fun updateMaintenance(request: PmsMaintenanceUpdateRequest): PmsUpdateResult =
        try {
            post("/maintenance/updates", request.toUniMockRequest(), object : TypeReference<UniMockPmsUpdateResponseDto>() {})
                .toUpdateResult()
        } catch (exception: UniMockClientException) {
            throw PmsCompletionException(exception.message ?: "UniMock maintenance update failed", exception)
        }

    override fun updateMaintenance(request: MaintenanceCompletionRequest): MaintenanceCompletionResult =
        try {
            post("/maintenance/updates", request.toUniMockRequest(), object : TypeReference<UniMockPmsUpdateResponseDto>() {})
                .toMaintenanceCompletionResult()
        } catch (exception: UniMockClientException) {
            throw PmsCompletionException(exception.message ?: "UniMock maintenance update failed", exception)
        }

    override fun createEvent(request: PmsEventCreateRequest): PmsEvent =
        post("/events", request.toUniMockRequest(), object : TypeReference<UniMockEventDto>() {})
            .toEvent()

    private inline fun <reified T> getRequired(path: String, typeRef: TypeReference<T>): T =
        execute(
            method = "GET",
            path = path,
            requestBody = null,
            typeRef = typeRef,
            retryable = true,
            allowNotFound = false
        )

    private inline fun <reified T> getNullable(path: String, typeRef: TypeReference<T>): T? =
        executeNullable(
            method = "GET",
            path = path,
            requestBody = null,
            typeRef = typeRef,
            retryable = true
        )

    private inline fun <reified T, reified B> post(
        path: String,
        requestBody: B,
        typeRef: TypeReference<T>
    ): T =
        execute(
            method = "POST",
            path = path,
            requestBody = requestBody,
            typeRef = typeRef,
            retryable = false,
            allowNotFound = false
        )

    private inline fun <reified T> executeNullable(
        method: String,
        path: String,
        requestBody: Any?,
        typeRef: TypeReference<T>,
        retryable: Boolean
    ): T? {
        val response = executeRaw(
            method = method,
            path = path,
            requestBody = requestBody,
            retryable = retryable,
            allowNotFound = true
        )

        if (response.statusCode() == 404) {
            return null
        }

        return deserialize(response.body(), typeRef)
    }

    private inline fun <reified T> execute(
        method: String,
        path: String,
        requestBody: Any?,
        typeRef: TypeReference<T>,
        retryable: Boolean,
        allowNotFound: Boolean
    ): T {
        val response = executeRaw(
            method = method,
            path = path,
            requestBody = requestBody,
            retryable = retryable,
            allowNotFound = allowNotFound
        )

        return deserialize(response.body(), typeRef)
    }

    private fun executeRaw(
        method: String,
        path: String,
        requestBody: Any?,
        retryable: Boolean,
        allowNotFound: Boolean
    ): HttpResponse<String> {
        var attempt = 1
        val maxAttempts = if (retryable) properties.retries.maxAttempts.coerceAtLeast(1) else 1
        var lastFailure: UniMockClientException? = null

        while (attempt <= maxAttempts) {
            try {
                val response = httpClient.send(
                    buildRequest(method, path, requestBody),
                    HttpResponse.BodyHandlers.ofString()
                )

                if (response.statusCode() in 200..299) {
                    return response
                }

                if (allowNotFound && response.statusCode() == 404) {
                    return response
                }

                val failure = UniMockErrorMapper.map(
                    statusCode = response.statusCode(),
                    rawBody = response.body(),
                    fallbackMessage = "UniMock request failed with status ${response.statusCode()}"
                )

                if (!shouldRetry(failure, attempt, maxAttempts)) {
                    throw failure
                }

                lastFailure = failure
            } catch (exception: Exception) {
                val failure = when (exception) {
                    is UniMockClientException -> exception
                    is java.net.http.HttpTimeoutException -> UniMockErrorMapper.mapTransportFailure(
                        message = "UniMock request timed out",
                        cause = exception
                    )
                    is IOException -> UniMockErrorMapper.mapTransportFailure(
                        message = "UniMock request failed during transport",
                        cause = exception
                    )
                    else -> UniMockClientUnavailableException(
                        message = exception.message ?: "UniMock request failed",
                        cause = exception
                    )
                }

                if (!shouldRetry(failure, attempt, maxAttempts)) {
                    throw failure
                }

                lastFailure = failure
            }

            sleepForBackoff(attempt)
            attempt += 1
        }

        throw lastFailure ?: UniMockClientUnavailableException("UniMock request failed")
    }

    private fun buildRequest(
        method: String,
        path: String,
        requestBody: Any?
    ): HttpRequest {
        val correlationId = CorrelationIdContextHolder.currentOrCreate()
        val builder = HttpRequest.newBuilder()
            .uri(URI.create(resolveUrl(path)))
            .timeout(properties.requestTimeout)
            .header("Accept", MediaType.APPLICATION_JSON_VALUE)
            .header("X-Correlation-Id", correlationId)

        if (requestBody != null) {
            builder.header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
        }

        return when (method) {
            "GET" -> builder.GET().build()
            "POST" -> builder.POST(
                HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody))
            ).build()
            "PUT" -> builder.PUT(
                HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody))
            ).build()
            "PATCH" -> builder.method(
                "PATCH",
                HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody))
            ).build()
            "DELETE" -> builder.DELETE().build()
            else -> throw IllegalArgumentException("Unsupported HTTP method: $method")
        }
    }

    private fun resolveUrl(path: String): String {
        val base = properties.baseUrl.trimEnd('/')
        val prefix = properties.apiPrefix.trim()
            .let { if (it.isEmpty()) "" else it.trimEnd('/') }
        val normalizedPath = path.trimStart('/')
        return "$base$prefix/$normalizedPath"
    }

    private fun shouldRetry(
        failure: UniMockClientException,
        attempt: Int,
        maxAttempts: Int
    ): Boolean {
        if (attempt >= maxAttempts) {
            return false
        }

        return failure is UniMockClientUnavailableException ||
            failure is UniMockClientTimeoutException ||
            failure is UniMockClientRateLimitedException
    }

    private fun sleepForBackoff(attempt: Int) {
        if (attempt >= properties.retries.maxAttempts) {
            return
        }

        val multiplier = 1L shl (attempt - 1).coerceAtLeast(0)
        val computedMillis = properties.retries.initialBackoff.toMillis() * multiplier
        val cappedMillis = min(computedMillis, properties.retries.maxBackoff.toMillis())

        if (cappedMillis <= 0) {
            return
        }

        try {
            Thread.sleep(cappedMillis)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            throw UniMockClientUnavailableException("UniMock retry interrupted")
        }
    }

    private fun <T> deserialize(body: String, typeRef: TypeReference<T>): T {
        val javaType: JavaType = objectMapper.typeFactory.constructType(typeRef.type)
        return objectMapper.readValue(body, javaType)
    }
}

private fun UniMockRoomDto.toRoom(): PmsRoom =
    PmsRoom(
        roomId = roomId,
        roomNumber = roomNumber,
        roomTypeId = roomTypeId,
        roomTypeName = roomTypeName,
        floor = floor,
        occupied = occupied,
        status = status
    )

private fun UniMockRoomStatusDto.toRoomStatus(): PmsRoomStatus =
    PmsRoomStatus(
        roomNumber = roomNumber,
        status = status,
        updatedAt = updatedAt
    )

private fun UniMockRoomOccupancyDto.toRoomOccupancy(): PmsRoomOccupancy =
    PmsRoomOccupancy(
        roomNumber = roomNumber,
        reservationId = reservationId,
        guestId = guestId,
        occupied = occupied
    )

private fun UniMockAssetDto.toAsset(): PmsAsset =
    PmsAsset(
        assetId = assetId,
        assetCode = assetCode,
        assetName = assetName,
        assetType = assetType,
        roomId = roomId,
        publicAreaId = publicAreaId,
        status = status,
        issueTypeId = issueTypeId
    )

private fun UniMockIssueTypeDto.toIssueType(): PmsIssueType =
    PmsIssueType(
        issueTypeId = issueTypeId,
        code = code,
        name = name,
        category = category,
        requiresPmsUpdate = requiresPmsUpdate,
        active = active
    )

private fun UniMockGuestRequestDto.toGuestRequest(): PmsGuestRequest =
    PmsGuestRequest(
        guestRequestId = guestRequestId,
        status = status,
        roomId = roomId,
        roomNumber = roomNumber,
        guestName = guestName,
        description = description,
        source = source,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

private fun UniMockEventDto.toEvent(): PmsEvent =
    PmsEvent(
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

private fun UniMockPmsUpdateResponseDto.toUpdateResult(): PmsUpdateResult =
    PmsUpdateResult(
        verificationLogId = verificationLogId,
        entityId = entityId,
        operation = operation,
        status = status
    )

private fun UniMockPmsUpdateResponseDto.toMaintenanceCompletionResult(): MaintenanceCompletionResult =
    MaintenanceCompletionResult(
        verificationLogId = verificationLogId,
        entityId = entityId,
        operation = operation,
        status = status
    )

private fun PmsRoomStatusUpdateRequest.toUniMockRequest(): UniMockRoomStatusUpdateRequestDto =
    UniMockRoomStatusUpdateRequestDto(status = status)

private fun MaintenanceCompletionRequest.toUniMockRequest(): UniMockMaintenanceUpdateRequestDto =
    UniMockMaintenanceUpdateRequestDto(
        roomNumber = roomNumber,
        issueTypeCode = issueTypeCode,
        description = description,
        status = status
    )

private fun PmsMaintenanceUpdateRequest.toUniMockRequest(): UniMockMaintenanceUpdateRequestDto =
    UniMockMaintenanceUpdateRequestDto(
        roomNumber = roomNumber,
        issueTypeCode = issueTypeCode,
        description = description,
        status = status
    )

private fun PmsEventCreateRequest.toUniMockRequest(): UniMockEventCreateRequestDto =
    UniMockEventCreateRequestDto(
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

private fun PmsGuestRequestStatusUpdateRequest.toUniMockRequest(): UniMockGuestRequestStatusUpdateRequestDto =
    UniMockGuestRequestStatusUpdateRequestDto(
        status = status,
        note = note,
        resolvedAt = resolvedAt
    )
