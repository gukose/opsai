package com.hotelopai.reservation.automation

import com.hotelopai.outbox.domain.OperationalOutboxEvent
import com.hotelopai.reservation.application.ReservationOutboxPayload
import com.hotelopai.reservation.domain.Reservation
import com.hotelopai.shared.kernel.UuidV7Generator
import com.hotelopai.task.domain.TaskIntentType
import com.hotelopai.task.domain.TaskPriority
import java.time.Instant
import java.util.UUID

@JvmInline
value class ReservationTaskAutomationRuleId(val value: String) {
    init {
        require(value.isNotBlank()) { "automation rule id must not be blank" }
    }
}

@JvmInline
value class ReservationTaskAutomationExecutionId(val value: UUID) {
    companion object {
        fun generate(): ReservationTaskAutomationExecutionId =
            ReservationTaskAutomationExecutionId(UuidV7Generator.generate())
    }
}

enum class ReservationTaskAutomationOutcome {
    CREATED,
    ALREADY_EXISTS,
    SKIPPED,
    NOT_APPLICABLE,
    FAILED,
    DEAD_LETTER
}

enum class ReservationTaskAutomationSkipReason {
    AUTOMATION_DISABLED,
    RULE_DISABLED,
    UNSUPPORTED_EVENT,
    RESERVATION_NOT_FOUND,
    NOT_APPLICABLE,
    DUPLICATE,
    INVALID_CONFIGURATION,
    TASK_CREATION_FAILED,
    RULE_FAILURE,
    TRANSIENT_FAILURE
}

data class ReservationTaskAutomationContext(
    val outboxEvent: OperationalOutboxEvent,
    val payload: ReservationOutboxPayload,
    val reservation: Reservation,
    val occurredAt: Instant,
    val now: Instant,
    val dueDatePolicy: ReservationTaskAutomationDueDatePolicy
)

data class ReservationTaskProposal(
    val ruleId: ReservationTaskAutomationRuleId,
    val ruleVersion: Int,
    val triggerEventType: String,
    val intentType: TaskIntentType,
    val title: String,
    val description: String,
    val roomNumber: String?,
    val priority: TaskPriority,
    val dueAt: Instant,
    val deduplicationKey: String,
    val safeMetadata: Map<String, String> = emptyMap()
) {
    init {
        require(ruleVersion > 0) { "automation rule version must be positive" }
        require(title.isNotBlank()) { "task proposal title must not be blank" }
        require(description.isNotBlank()) { "task proposal description must not be blank" }
        require(deduplicationKey.isNotBlank()) { "automation deduplication key must not be blank" }
    }
}

data class ReservationTaskAutomationExecution(
    val id: ReservationTaskAutomationExecutionId = ReservationTaskAutomationExecutionId.generate(),
    val outboxEventId: UUID,
    val reservationId: UUID,
    val ruleId: ReservationTaskAutomationRuleId,
    val ruleVersion: Int,
    val triggerEventType: String,
    val deduplicationKey: String,
    val outcome: ReservationTaskAutomationOutcome,
    val createdTaskId: UUID? = null,
    val failureCategory: ReservationTaskAutomationSkipReason? = null,
    val skipReason: ReservationTaskAutomationSkipReason? = null,
    val attemptCount: Int = 0,
    val nextAttemptAt: Instant? = null,
    val createdAt: Instant,
    val updatedAt: Instant = createdAt,
    val completedAt: Instant? = null,
    val version: Long = 0
)

data class ReservationTaskAutomationExecutionFilter(
    val outcome: ReservationTaskAutomationOutcome? = null,
    val ruleId: String? = null,
    val page: Int = 0,
    val size: Int = 20
)

data class ReservationTaskAutomationExecutionPage(
    val content: List<ReservationTaskAutomationExecution>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int
)

data class ReservationTaskAutomationBatchSummary(
    val processedEvents: Int,
    val rulesEvaluated: Int,
    val tasksCreated: Int,
    val alreadyExists: Int,
    val skipped: Int,
    val failed: Int,
    val deadLetter: Int
)

data class ReservationTaskAutomationRuleDescriptor(
    val ruleId: ReservationTaskAutomationRuleId,
    val version: Int,
    val supportedEventTypes: Set<String>,
    val enabled: Boolean
)

data class ReservationTaskAutomationScheduleState(
    val scheduleId: String,
    val paused: Boolean,
    val pausedAt: Instant? = null,
    val resumedAt: Instant? = null,
    val lastAttemptedAt: Instant? = null,
    val lastSuccessfulAt: Instant? = null,
    val lastProcessedCount: Int = 0,
    val lastCreatedTaskCount: Int = 0,
    val lastFailureCategory: ReservationTaskAutomationSkipReason? = null,
    val updatedAt: Instant
)

data class ReservationTaskAutomationScheduleStatus(
    val scheduleId: String,
    val configuredEnabled: Boolean,
    val effectiveEnabled: Boolean,
    val paused: Boolean,
    val scheduleSummary: String,
    val batchSize: Int,
    val maxRecordsPerExecution: Int,
    val enabledRuleCount: Int,
    val lastAttemptedAt: Instant?,
    val lastSuccessfulAt: Instant?,
    val nextExpectedExecutionAt: Instant?,
    val lastProcessedCount: Int,
    val lastCreatedTaskCount: Int,
    val lastFailureCategory: ReservationTaskAutomationSkipReason?,
    val leaseState: com.hotelopai.reservation.application.ReservationSyncScheduleLeaseState,
    val eligibleBacklogCount: Long,
    val failedExecutionCount: Long,
    val deadLetterExecutionCount: Long
)

interface ReservationTaskAutomationRule {
    val id: ReservationTaskAutomationRuleId
    val version: Int
    val supportedEventTypes: Set<String>
    fun evaluate(context: ReservationTaskAutomationContext): List<ReservationTaskProposal>
}
