package com.hotelopai.task.application

import com.hotelopai.integration.unimock.PmsMaintenanceUpdateRequest
import com.hotelopai.integration.unimock.PmsUpdateResult
import com.hotelopai.integration.unimock.UniMockClient
import com.hotelopai.task.domain.Task
import com.hotelopai.task.domain.TaskIntentType
import com.hotelopai.task.domain.TaskPriority
import com.hotelopai.task.domain.TaskSource
import com.hotelopai.integration.unimock.UniMockClientException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class TaskCompletionPolicyTest {
    @Test
    fun `maintenance task triggers PMS verification and returns verification id`() {
        val client = RecordingUniMockClient()
        val policy = TaskCompletionPolicy(client)
        val task = maintenanceTask()

        val decision = policy.evaluate(task, Instant.parse("2026-07-08T10:30:00Z"))

        assertEquals(true, decision.requiresPmsUpdate)
        assertEquals(client.result.verificationLogId, decision.verificationLogId)
        assertEquals("101", client.request.roomNumber)
        assertEquals("MAINTENANCE_AC", client.request.issueTypeCode)
    }

    @Test
    fun `maintenance verification failures are wrapped`() {
        val policy = TaskCompletionPolicy(FailingUniMockClient())
        val task = maintenanceTask()

        assertThrows(TaskCompletionPolicyException::class.java) {
            policy.evaluate(task, Instant.parse("2026-07-08T10:30:00Z"))
        }
    }

    @Test
    fun `non maintenance tasks do not require PMS verification`() {
        val policy = TaskCompletionPolicy(RecordingUniMockClient())
        val task = Task.create(
            hotelId = UUID.fromString("018f6b7a-4f22-7caa-8f60-9e4b0f7f1003"),
            intentType = TaskIntentType.GUEST_REQUEST,
            source = TaskSource.ASSISTANT,
            title = "Need towels",
            description = "Guest needs towels",
            priority = TaskPriority.MEDIUM,
            slaDeadline = Instant.parse("2026-07-08T11:30:00Z"),
            createdAt = Instant.parse("2026-07-08T10:00:00Z")
        )

        val decision = policy.evaluate(task, Instant.parse("2026-07-08T10:30:00Z"))

        assertEquals(false, decision.requiresPmsUpdate)
        assertEquals(null, decision.verificationLogId)
    }

    private fun maintenanceTask(): Task =
        Task.create(
            hotelId = UUID.fromString("018f6b7a-4f22-7caa-8f60-9e4b0f7f1001"),
            intentType = TaskIntentType.MAINTENANCE,
            source = TaskSource.ASSISTANT,
            title = "Room 101 AC not working",
            description = "Room 101 AC not working",
            priority = TaskPriority.HIGH,
            slaDeadline = Instant.parse("2026-07-08T11:30:00Z"),
            createdAt = Instant.parse("2026-07-08T10:00:00Z")
        )

    private class RecordingUniMockClient : UniMockClient {
        val request: PmsMaintenanceUpdateRequest
            get() = lastRequest ?: error("no request recorded")
        val result = PmsUpdateResult(
            verificationLogId = UUID.fromString("018f6b7a-4f22-7caa-8f60-9e4b0f7f2001"),
            entityId = "101",
            operation = "maintenance",
            status = "success"
        )
        private var lastRequest: PmsMaintenanceUpdateRequest? = null

        override fun updateMaintenance(request: PmsMaintenanceUpdateRequest): PmsUpdateResult {
            lastRequest = request
            return result
        }

        override fun getRoom(roomId: String) = null
        override fun getRoomStatus(roomNumber: String) = null
        override fun getRoomOccupancy(roomNumber: String) = null
        override fun getRoomAssets(roomNumber: String) = emptyList<com.hotelopai.integration.unimock.PmsAsset>()
        override fun listRooms() = emptyList<com.hotelopai.integration.unimock.PmsRoom>()
        override fun getAsset(assetId: String) = null
        override fun listIssueTypes() = emptyList<com.hotelopai.integration.unimock.PmsIssueType>()
        override fun getGuestRequest(guestRequestId: String) = null
        override fun updateGuestRequestStatus(
            guestRequestId: String,
            request: com.hotelopai.integration.unimock.PmsGuestRequestStatusUpdateRequest
        ) = throw UnsupportedOperationException()

        override fun updateRoomStatus(
            roomNumber: String,
            request: com.hotelopai.integration.unimock.PmsRoomStatusUpdateRequest
        ) = throw UnsupportedOperationException()

        override fun createEvent(request: com.hotelopai.integration.unimock.PmsEventCreateRequest) =
            throw UnsupportedOperationException()
    }

    private class FailingUniMockClient : UniMockClient {
        override fun updateMaintenance(request: PmsMaintenanceUpdateRequest): PmsUpdateResult =
            throw UniMockClientException("UniMock down")

        override fun getRoom(roomId: String) = null
        override fun getRoomStatus(roomNumber: String) = null
        override fun getRoomOccupancy(roomNumber: String) = null
        override fun getRoomAssets(roomNumber: String) = emptyList<com.hotelopai.integration.unimock.PmsAsset>()
        override fun listRooms() = emptyList<com.hotelopai.integration.unimock.PmsRoom>()
        override fun getAsset(assetId: String) = null
        override fun listIssueTypes() = emptyList<com.hotelopai.integration.unimock.PmsIssueType>()
        override fun getGuestRequest(guestRequestId: String) = null
        override fun updateGuestRequestStatus(
            guestRequestId: String,
            request: com.hotelopai.integration.unimock.PmsGuestRequestStatusUpdateRequest
        ) = throw UnsupportedOperationException()

        override fun updateRoomStatus(
            roomNumber: String,
            request: com.hotelopai.integration.unimock.PmsRoomStatusUpdateRequest
        ) = throw UnsupportedOperationException()

        override fun createEvent(request: com.hotelopai.integration.unimock.PmsEventCreateRequest) =
            throw UnsupportedOperationException()
    }
}
