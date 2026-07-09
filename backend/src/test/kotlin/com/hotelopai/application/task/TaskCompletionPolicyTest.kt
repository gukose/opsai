package com.hotelopai.task.application

import com.hotelopai.task.domain.Task
import com.hotelopai.task.domain.TaskIntentType
import com.hotelopai.task.domain.TaskPriority
import com.hotelopai.task.domain.TaskSource
import com.hotelopai.shared.pms.MaintenanceCompletionPort
import com.hotelopai.shared.pms.MaintenanceCompletionRequest
import com.hotelopai.shared.pms.MaintenanceCompletionResult
import com.hotelopai.shared.pms.PmsCompletionException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class TaskCompletionPolicyTest {
    @Test
    fun `maintenance task triggers PMS verification and returns verification id`() {
        val client = RecordingMaintenanceCompletionPort()
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
        val policy = TaskCompletionPolicy(FailingMaintenanceCompletionPort())
        val task = maintenanceTask()

        assertThrows(TaskCompletionPolicyException::class.java) {
            policy.evaluate(task, Instant.parse("2026-07-08T10:30:00Z"))
        }
    }

    @Test
    fun `non maintenance tasks do not require PMS verification`() {
        val policy = TaskCompletionPolicy(RecordingMaintenanceCompletionPort())
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

    private class RecordingMaintenanceCompletionPort : MaintenanceCompletionPort {
        val request: MaintenanceCompletionRequest
            get() = lastRequest ?: error("no request recorded")
        val result = MaintenanceCompletionResult(
            verificationLogId = UUID.fromString("018f6b7a-4f22-7caa-8f60-9e4b0f7f2001"),
            entityId = "101",
            operation = "maintenance",
            status = "success"
        )
        private var lastRequest: MaintenanceCompletionRequest? = null

        override fun updateMaintenance(request: MaintenanceCompletionRequest): MaintenanceCompletionResult {
            lastRequest = request
            return result
        }
    }

    private class FailingMaintenanceCompletionPort : MaintenanceCompletionPort {
        override fun updateMaintenance(request: MaintenanceCompletionRequest): MaintenanceCompletionResult =
            throw PmsCompletionException("UniMock down")
    }
}
