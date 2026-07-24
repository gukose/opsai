package com.hotelopai.task.application

import com.hotelopai.pms.application.PmsProvider
import com.hotelopai.pms.application.PmsProviderProperties
import com.hotelopai.pms.application.PmsProviderRegistry
import com.hotelopai.pms.application.PmsCapabilities
import com.hotelopai.pms.domain.HousekeepingTaskStatusUpdate
import com.hotelopai.pms.domain.MaintenanceUpdate
import com.hotelopai.pms.domain.PmsAsset
import com.hotelopai.pms.domain.PmsEvent
import com.hotelopai.pms.domain.PmsEventCreateCommand
import com.hotelopai.pms.domain.PmsHousekeepingTask
import com.hotelopai.pms.domain.PmsIssueType
import com.hotelopai.pms.domain.PmsProviderException
import com.hotelopai.pms.domain.PmsProviderId
import com.hotelopai.pms.domain.PmsRoom
import com.hotelopai.pms.domain.PmsRoomStatus
import com.hotelopai.pms.domain.PmsStay
import com.hotelopai.pms.domain.PmsUpdateResult
import com.hotelopai.pms.domain.RoomStatusUpdate
import com.hotelopai.task.domain.Task
import com.hotelopai.task.domain.TaskIntentType
import com.hotelopai.task.domain.TaskPriority
import com.hotelopai.task.domain.TaskSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class TaskCompletionPolicyTest {
    @Test
    fun `maintenance task triggers PMS verification and returns verification id`() {
        val provider = RecordingPmsProvider()
        val policy = TaskCompletionPolicy(registry(provider))
        val task = maintenanceTask()

        val decision = policy.evaluate(task, Instant.parse("2026-07-08T10:30:00Z"))

        assertEquals(true, decision.requiresPmsUpdate)
        assertEquals(provider.result.verificationLogId, decision.verificationLogId)
        assertEquals("101", provider.request.roomNumber)
        assertEquals("MAINTENANCE_AC", provider.request.issueTypeCode)
    }

    @Test
    fun `maintenance task uses structured room number when available`() {
        val provider = RecordingPmsProvider()
        val policy = TaskCompletionPolicy(registry(provider))
        val task = Task.create(
            hotelId = UUID.fromString("018f6b7a-4f22-7caa-8f60-9e4b0f7f1002"),
            intentType = TaskIntentType.MAINTENANCE,
            source = TaskSource.ASSISTANT,
            title = "Air conditioner not cooling",
            description = "Guest reported the air conditioner is not cooling",
            roomNumber = "101",
            priority = TaskPriority.HIGH,
            slaDeadline = Instant.parse("2026-07-08T11:30:00Z"),
            createdAt = Instant.parse("2026-07-08T10:00:00Z")
        )

        val decision = policy.evaluate(task, Instant.parse("2026-07-08T10:30:00Z"))

        assertEquals(true, decision.requiresPmsUpdate)
        assertEquals("101", provider.request.roomNumber)
    }

    @Test
    fun `maintenance verification failures are wrapped`() {
        val policy = TaskCompletionPolicy(registry(FailingPmsProvider()))
        val task = maintenanceTask()

        assertThrows(TaskCompletionPolicyException::class.java) {
            policy.evaluate(task, Instant.parse("2026-07-08T10:30:00Z"))
        }
    }

    @Test
    fun `unsupported maintenance capability fails with application exception`() {
        val policy = TaskCompletionPolicy(registry(UnsupportedMaintenancePmsProvider()))
        val task = maintenanceTask()

        val exception = assertThrows(TaskCompletionPolicyException::class.java) {
            policy.evaluate(task, Instant.parse("2026-07-08T10:30:00Z"))
        }

        assertEquals("Active PMS provider does not support maintenance updates", exception.message)
    }

    @Test
    fun `maintenance task without a room number fails validation`() {
        val policy = TaskCompletionPolicy(registry(RecordingPmsProvider()))
        val task = Task.create(
            hotelId = UUID.fromString("018f6b7a-4f22-7caa-8f60-9e4b0f7f1003"),
            intentType = TaskIntentType.MAINTENANCE,
            source = TaskSource.ASSISTANT,
            title = "Air conditioner not cooling",
            description = "Guest reported the air conditioner is not cooling",
            priority = TaskPriority.HIGH,
            slaDeadline = Instant.parse("2026-07-08T11:30:00Z"),
            createdAt = Instant.parse("2026-07-08T10:00:00Z")
        )

        val exception = assertThrows(TaskCompletionValidationException::class.java) {
            policy.evaluate(task, Instant.parse("2026-07-08T10:30:00Z"))
        }

        assertEquals("Maintenance task requires a room number before completion", exception.message)
    }

    @Test
    fun `non maintenance tasks do not require PMS verification`() {
        val policy = TaskCompletionPolicy(registry(RecordingPmsProvider()))
        val task = Task.create(
            hotelId = UUID.fromString("018f6b7a-4f22-7caa-8f60-9e4b0f7f1003"),
            intentType = TaskIntentType.GUEST_REQUEST,
            source = TaskSource.ASSISTANT,
            title = "Need towels",
            description = "Guest needs towels",
            roomNumber = null,
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
            title = "Air conditioner not cooling",
            description = "Guest reported the air conditioner is not cooling",
            roomNumber = "101",
            priority = TaskPriority.HIGH,
            slaDeadline = Instant.parse("2026-07-08T11:30:00Z"),
            createdAt = Instant.parse("2026-07-08T10:00:00Z")
        )

    private fun registry(provider: PmsProvider): PmsProviderRegistry =
        PmsProviderRegistry(listOf(provider), PmsProviderProperties(activeProvider = provider.id.value))

    private open class RecordingPmsProvider : PmsProvider {
        override val id = PmsProviderId("internal-demo")
        override val displayName = "Internal Demo PMS"
        override val capabilities = PmsCapabilities(
            roomListing = true,
            issueTypeLookup = true,
            maintenanceUpdate = true
        )

        val request: MaintenanceUpdate
            get() = lastRequest ?: error("no request recorded")
        val result = PmsUpdateResult(
            verificationLogId = UUID.fromString("018f6b7a-4f22-7caa-8f60-9e4b0f7f2001"),
            entityId = "101",
            operation = "maintenance",
            status = "success"
        )
        private var lastRequest: MaintenanceUpdate? = null

        override fun updateMaintenance(request: MaintenanceUpdate): PmsUpdateResult {
            lastRequest = request
            return result
        }

        override fun listRooms(): List<PmsRoom> = emptyList()
        override fun findRoom(roomNumber: String): PmsRoom? = null
        override fun findRoomStatus(roomNumber: String): PmsRoomStatus? = null
        override fun findStay(roomNumber: String): PmsStay? = null
        override fun getRoomAssets(roomNumber: String): List<PmsAsset> = emptyList()
        override fun findAsset(assetId: String): PmsAsset? = null
        override fun listIssueTypes(): List<PmsIssueType> = emptyList()
        override fun findHousekeepingTask(taskId: String): PmsHousekeepingTask? = null
        override fun updateHousekeepingTaskStatus(
            taskId: String,
            request: HousekeepingTaskStatusUpdate
        ): PmsHousekeepingTask = error("not used")

        override fun updateRoomStatus(
            roomNumber: String,
            request: RoomStatusUpdate
        ): PmsUpdateResult = error("not used")

        override fun createEvent(command: PmsEventCreateCommand): PmsEvent = error("not used")
    }

    private class FailingPmsProvider : RecordingPmsProvider() {
        override fun updateMaintenance(request: MaintenanceUpdate): PmsUpdateResult =
            throw PmsProviderException("UniMock down")
    }

    private class UnsupportedMaintenancePmsProvider : RecordingPmsProvider() {
        override val capabilities = PmsCapabilities(roomListing = true)
    }
}
