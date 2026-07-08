package com.hotelopai.unimock.application.pms

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.hotelopai.unimock.application.simulation.SimulationNotLoadedException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration
import java.util.UUID

@Service
@Transactional
class PmsUpdateService(
    private val pmsReadRepository: PmsReadRepository,
    private val pmsDocumentRepository: PmsDocumentRepository,
    private val pmsVerificationRepository: PmsVerificationRepository,
    private val objectMapper: ObjectMapper,
    private val clock: Clock
) {
    fun updateRoomStatus(
        roomNumber: String,
        request: RoomStatusUpdateRequest,
        correlationId: String?
    ): PmsUpdateResponse {
        val startedAt = clock.instant()
        val simulation = activeSimulation()
        simulation.requireRoom(roomNumber)

        val roomStatuses = simulation.requireObjectDocument("operations/room-status.json")
        val status = upsertRoomStatus(roomStatuses, roomNumber, request.status)
        pmsDocumentRepository.replaceDocument(simulation.simulationId, "operations/room-status.json", roomStatuses)

        val responsePayload = mapOf("roomNumber" to roomNumber, "status" to status)
        val verificationLogId = recordVerification(
            simulation = simulation,
            entityType = "ROOM",
            entityId = roomNumber,
            operation = "ROOM_STATUS_UPDATE",
            requestPayload = request,
            responsePayload = responsePayload,
            correlationId = correlationId,
            startedAt = startedAt
        )

        return PmsUpdateResponse(
            verificationLogId = verificationLogId,
            entityId = roomNumber,
            operation = "ROOM_STATUS_UPDATE",
            status = status
        )
    }

    fun createGuestRequest(
        request: GuestRequestCreateRequest,
        correlationId: String?
    ): PmsUpdateResponse {
        val startedAt = clock.instant()
        val simulation = activeSimulation()
        simulation.requireRoom(request.roomNumber)

        val guestRequests = simulation.requireObjectDocument("operations/guest-requests.json")
        val guestRequestId = "REQ-${UUID.randomUUID().toString().take(8).uppercase()}"
        guestRequests.array("guestRequests").add(
            objectMapper.createObjectNode().apply {
                put("requestId", guestRequestId)
                put("roomNumber", request.roomNumber)
                put("description", request.description)
                put("status", request.status?.takeIf { it.isNotBlank() } ?: "OPEN")
            }
        )
        pmsDocumentRepository.replaceDocument(simulation.simulationId, "operations/guest-requests.json", guestRequests)

        val responsePayload = mapOf("guestRequestId" to guestRequestId, "roomNumber" to request.roomNumber)
        val verificationLogId = recordVerification(
            simulation = simulation,
            entityType = "GUEST_REQUEST",
            entityId = guestRequestId,
            operation = "GUEST_REQUEST_CREATE",
            requestPayload = request,
            responsePayload = responsePayload,
            correlationId = correlationId,
            startedAt = startedAt
        )

        return PmsUpdateResponse(
            verificationLogId = verificationLogId,
            entityId = guestRequestId,
            operation = "GUEST_REQUEST_CREATE",
            status = request.status?.takeIf { it.isNotBlank() } ?: "OPEN"
        )
    }

    fun updateGuestRequestStatus(
        guestRequestId: String,
        request: GuestRequestStatusUpdateRequest,
        correlationId: String?
    ): PmsUpdateResponse {
        val startedAt = clock.instant()
        val simulation = activeSimulation()
        val guestRequests = simulation.requireObjectDocument("operations/guest-requests.json")
        val target = guestRequests.array("guestRequests").firstOrNull {
            it.path("requestId").asText() == guestRequestId
        } ?: throw PmsResourceNotFoundException("Guest request", guestRequestId)

        (target as ObjectNode).put("status", request.status)
        pmsDocumentRepository.replaceDocument(simulation.simulationId, "operations/guest-requests.json", guestRequests)

        val responsePayload = mapOf("guestRequestId" to guestRequestId, "status" to request.status)
        val verificationLogId = recordVerification(
            simulation = simulation,
            entityType = "GUEST_REQUEST",
            entityId = guestRequestId,
            operation = "GUEST_REQUEST_STATUS_UPDATE",
            requestPayload = request,
            responsePayload = responsePayload,
            correlationId = correlationId,
            startedAt = startedAt
        )

        return PmsUpdateResponse(
            verificationLogId = verificationLogId,
            entityId = guestRequestId,
            operation = "GUEST_REQUEST_STATUS_UPDATE",
            status = request.status
        )
    }

    fun updateMinibar(
        request: MinibarUpdateRequest,
        correlationId: String?
    ): PmsUpdateResponse {
        val startedAt = clock.instant()
        val simulation = activeSimulation()
        simulation.requireRoom(request.roomNumber)

        val minibar = simulation.requireObjectDocument("operations/minibar.json")
        val items = minibar.array("items")
        request.items.forEach { item ->
            val existing = items.firstOrNull {
                it.path("roomNumber").asText() == request.roomNumber && it.path("sku").asText() == item.sku
            }
            if (existing == null) {
                items.add(
                    objectMapper.createObjectNode().apply {
                        put("roomNumber", request.roomNumber)
                        put("sku", item.sku)
                        put("name", item.name)
                        put("quantity", item.quantity)
                    }
                )
            } else {
                (existing as ObjectNode).put("name", item.name)
                existing.put("quantity", item.quantity)
            }
        }
        pmsDocumentRepository.replaceDocument(simulation.simulationId, "operations/minibar.json", minibar)

        val responsePayload = mapOf("roomNumber" to request.roomNumber, "items" to request.items.size)
        val verificationLogId = recordVerification(
            simulation = simulation,
            entityType = "MINIBAR",
            entityId = request.roomNumber,
            operation = "MINIBAR_UPDATE",
            requestPayload = request,
            responsePayload = responsePayload,
            correlationId = correlationId,
            startedAt = startedAt
        )

        return PmsUpdateResponse(
            verificationLogId = verificationLogId,
            entityId = request.roomNumber,
            operation = "MINIBAR_UPDATE",
            status = "UPDATED"
        )
    }

    fun updateMaintenance(
        request: MaintenanceUpdateRequest,
        correlationId: String?
    ): PmsUpdateResponse {
        val startedAt = clock.instant()
        val simulation = activeSimulation()
        simulation.requireRoom(request.roomNumber)
        simulation.requireIssueType(request.issueTypeCode)

        val events = simulation.requireObjectDocument("operations/events.json")
        val eventId = "EVT-${UUID.randomUUID().toString().take(8).uppercase()}"
        events.array("events").add(
            objectMapper.createObjectNode().apply {
                put("eventId", eventId)
                put("type", "MAINTENANCE_UPDATE")
                put("roomNumber", request.roomNumber)
                put("issueTypeCode", request.issueTypeCode)
                put("description", request.description)
                put("status", request.status)
                put("occurredAt", clock.instant().toString())
            }
        )
        pmsDocumentRepository.replaceDocument(simulation.simulationId, "operations/events.json", events)

        val responsePayload = mapOf("roomNumber" to request.roomNumber, "issueTypeCode" to request.issueTypeCode)
        val verificationLogId = recordVerification(
            simulation = simulation,
            entityType = "MAINTENANCE",
            entityId = request.roomNumber,
            operation = "MAINTENANCE_UPDATE",
            requestPayload = request,
            responsePayload = responsePayload,
            correlationId = correlationId,
            startedAt = startedAt
        )

        return PmsUpdateResponse(
            verificationLogId = verificationLogId,
            entityId = request.roomNumber,
            operation = "MAINTENANCE_UPDATE",
            status = request.status
        )
    }

    fun pushEvent(
        request: EventPushRequest,
        correlationId: String?
    ): PmsUpdateResponse {
        val startedAt = clock.instant()
        val simulation = activeSimulation()

        val events = simulation.requireObjectDocument("operations/events.json")
        val eventId = request.eventId?.takeIf { it.isNotBlank() } ?: "EVT-${UUID.randomUUID().toString().take(8).uppercase()}"
        events.array("events").add(
            objectMapper.createObjectNode().apply {
                put("eventId", eventId)
                put("type", request.type)
                put("occurredAt", request.occurredAt ?: clock.instant().toString())
            }
        )
        pmsDocumentRepository.replaceDocument(simulation.simulationId, "operations/events.json", events)

        val responsePayload = mapOf("eventId" to eventId, "type" to request.type)
        val verificationLogId = recordVerification(
            simulation = simulation,
            entityType = "EVENT",
            entityId = eventId,
            operation = "EVENT_PUSH",
            requestPayload = request,
            responsePayload = responsePayload,
            correlationId = correlationId,
            startedAt = startedAt
        )

        return PmsUpdateResponse(
            verificationLogId = verificationLogId,
            entityId = eventId,
            operation = "EVENT_PUSH",
            status = "CREATED"
        )
    }

    private fun recordVerification(
        simulation: ActiveSimulationDocuments,
        entityType: String,
        entityId: String?,
        operation: String,
        requestPayload: Any,
        responsePayload: Any,
        correlationId: String?,
        startedAt: java.time.Instant
    ): UUID {
        val createdAt = clock.instant()
        val entry = PmsVerificationLogEntry(
            id = UUID.randomUUID(),
            simulationId = simulation.simulationId.toString(),
            entityType = entityType,
            entityId = entityId,
            operation = operation,
            requestPayloadJson = objectMapper.writeValueAsString(requestPayload),
            responsePayloadJson = objectMapper.writeValueAsString(responsePayload),
            status = "SUCCESS",
            sourceSystem = "hotel-opai-backend",
            destinationSystem = "unimock",
            httpStatus = 200,
            durationMs = Duration.between(startedAt, createdAt).toMillis(),
            retryCount = 0,
            correlationId = correlationId?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString(),
            createdAt = createdAt
        )
        return pmsVerificationRepository.insert(entry).verificationLogId
    }

    private fun activeSimulation(): ActiveSimulationDocuments =
        pmsReadRepository.findActiveSimulationDocuments() ?: throw SimulationNotLoadedException()

    private fun ActiveSimulationDocuments.requireRoom(roomNumber: String) {
        requireDocument("master/rooms.json")
            .rooms()
            .firstOrNull { it.path("roomNumber").asText() == roomNumber }
            ?: throw PmsResourceNotFoundException("Room", roomNumber)
    }

    private fun ActiveSimulationDocuments.requireIssueType(issueTypeCode: String) {
        requireDocument("master/issue-types.json")
            .issueTypes()
            .firstOrNull { it.path("code").asText() == issueTypeCode }
            ?: throw PmsResourceNotFoundException("Issue type", issueTypeCode)
    }

    private fun ActiveSimulationDocuments.requireObjectDocument(path: String): ObjectNode =
        requireDocument(path)

    private fun ActiveSimulationDocuments.requireDocument(path: String): ObjectNode =
        documents[path] as? ObjectNode ?: throw SimulationNotLoadedException()

    private fun ObjectNode.rooms(): ArrayNode = array("rooms")
    private fun ObjectNode.roomStatuses(): ArrayNode = array("roomStatuses")
    private fun ObjectNode.guestRequests(): ArrayNode = array("guestRequests")
    private fun ObjectNode.items(): ArrayNode = array("items")
    private fun ObjectNode.events(): ArrayNode = array("events")
    private fun ObjectNode.issueTypes(): ArrayNode = array("issueTypes")

    private fun ObjectNode.array(fieldName: String): ArrayNode {
        val value = get(fieldName)
        if (value != null && !value.isArray) {
            throw PmsReadException("Field '$fieldName' must be an array")
        }
        if (value == null) {
            set<ArrayNode>(fieldName, objectMapper.createArrayNode())
        }
        return get(fieldName) as ArrayNode
    }

    private fun upsertRoomStatus(roomStatuses: ObjectNode, roomNumber: String, status: String): String {
        val array = roomStatuses.roomStatuses()
        val now = clock.instant().toString()
        val existing = array.firstOrNull { it.path("roomNumber").asText() == roomNumber }
        if (existing == null) {
            array.add(
                objectMapper.createObjectNode().apply {
                    put("roomNumber", roomNumber)
                    put("status", status)
                    put("updatedAt", now)
                }
            )
        } else {
            (existing as ObjectNode).put("status", status)
            existing.put("updatedAt", now)
        }
        return status
    }
}
