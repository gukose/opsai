package com.hotelopai.reservation.automation.api

import com.hotelopai.reservation.automation.ReservationTaskAutomationBatchSummary
import com.hotelopai.reservation.automation.ReservationTaskAutomationExecution
import com.hotelopai.reservation.automation.ReservationTaskAutomationExecutionFilter
import com.hotelopai.reservation.automation.ReservationTaskAutomationExecutionId
import com.hotelopai.reservation.automation.ReservationTaskAutomationExecutionPage
import com.hotelopai.reservation.automation.ReservationTaskAutomationNotFoundException
import com.hotelopai.reservation.automation.ReservationTaskAutomationOutcome
import com.hotelopai.reservation.automation.ReservationTaskAutomationRejectedException
import com.hotelopai.reservation.automation.ReservationTaskAutomationRuleDescriptor
import com.hotelopai.reservation.automation.ReservationTaskAutomationRuleId
import com.hotelopai.reservation.automation.ReservationTaskAutomationScheduleStatus
import com.hotelopai.reservation.automation.ReservationTaskAutomationService
import com.hotelopai.reservation.automation.ReservationTaskAutomationSkipReason
import com.hotelopai.reservation.application.ReservationSyncScheduleLeaseState
import com.hotelopai.shared.security.CurrentUserContextResolver
import com.hotelopai.shared.security.PermissionExpressions
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/api/v1/internal/reservations/task-automation")
class InternalReservationTaskAutomationController(
    private val automationService: ReservationTaskAutomationService,
    private val currentUserContextResolver: CurrentUserContextResolver
) {
    @GetMapping("/rules")
    @PreAuthorize(PermissionExpressions.RESERVATION_SYNC_OPERATIONS)
    fun rules(): List<ReservationTaskAutomationRuleResponse> =
        automationService.registeredRules().map { it.toResponse() }

    @PostMapping("/process-batch")
    @PreAuthorize(PermissionExpressions.RESERVATION_SYNC_OPERATIONS)
    fun processBatch(): ReservationTaskAutomationBatchSummaryResponse =
        safely { automationService.processBatch(actorUserId()).toResponse() }

    @GetMapping("/schedule")
    @PreAuthorize(PermissionExpressions.RESERVATION_SYNC_OPERATIONS)
    fun scheduleStatus(): ReservationTaskAutomationScheduleStatusResponse =
        safely { automationService.schedulerStatus(actorUserId()).toResponse() }

    @PostMapping("/schedule/run-now")
    @PreAuthorize(PermissionExpressions.RESERVATION_SYNC_OPERATIONS)
    fun runScheduleNow(): ReservationTaskAutomationBatchSummaryResponse =
        safely { automationService.processOperatorBatch(actorUserId()).toResponse() }

    @PostMapping("/schedule/pause")
    @PreAuthorize(PermissionExpressions.RESERVATION_SYNC_OPERATIONS)
    fun pauseSchedule(): ReservationTaskAutomationScheduleStatusResponse =
        safely { automationService.pauseScheduler(actorUserId()).toResponse() }

    @PostMapping("/schedule/resume")
    @PreAuthorize(PermissionExpressions.RESERVATION_SYNC_OPERATIONS)
    fun resumeSchedule(): ReservationTaskAutomationScheduleStatusResponse =
        safely { automationService.resumeScheduler(actorUserId()).toResponse() }

    @GetMapping("/executions")
    @PreAuthorize(PermissionExpressions.RESERVATION_SYNC_OPERATIONS)
    fun executions(
        @RequestParam(required = false) outcome: ReservationTaskAutomationOutcome?,
        @RequestParam(required = false) ruleId: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ReservationTaskAutomationExecutionPageResponse =
        automationService.history(ReservationTaskAutomationExecutionFilter(outcome = outcome, ruleId = ruleId, page = page, size = size)).toResponse()

    @GetMapping("/executions/{executionId}")
    @PreAuthorize(PermissionExpressions.RESERVATION_SYNC_OPERATIONS)
    fun execution(@PathVariable executionId: UUID): ReservationTaskAutomationExecutionResponse =
        safely { automationService.execution(ReservationTaskAutomationExecutionId(executionId)).toResponse() }

    @PostMapping("/executions/{executionId}/retry")
    @PreAuthorize(PermissionExpressions.RESERVATION_SYNC_OPERATIONS)
    fun retry(@PathVariable executionId: UUID): ReservationTaskAutomationExecutionResponse =
        safely { automationService.retryExecution(ReservationTaskAutomationExecutionId(executionId), actorUserId()).toResponse() }

    private fun actorUserId(): UUID =
        currentUserContextResolver.current().userId

    private fun <T> safely(block: () -> T): T =
        try {
            block()
        } catch (exception: ReservationTaskAutomationNotFoundException) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Reservation task automation execution not found.", exception)
        } catch (exception: ReservationTaskAutomationRejectedException) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Reservation task automation operation was rejected.", exception)
        } catch (exception: IllegalArgumentException) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid reservation task automation request.", exception)
        }
}

data class ReservationTaskAutomationRuleResponse(
    val ruleId: ReservationTaskAutomationRuleId,
    val version: Int,
    val supportedEventTypes: Set<String>,
    val enabled: Boolean
)

data class ReservationTaskAutomationExecutionResponse(
    val id: UUID,
    val reservationRecorded: Boolean,
    val ruleId: ReservationTaskAutomationRuleId,
    val ruleVersion: Int,
    val triggerEventType: String,
    val outcome: ReservationTaskAutomationOutcome,
    val taskCreated: Boolean,
    val failureCategory: ReservationTaskAutomationSkipReason?,
    val skipReason: ReservationTaskAutomationSkipReason?,
    val attemptCount: Int,
    val nextAttemptAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val completedAt: Instant?
)

data class ReservationTaskAutomationExecutionPageResponse(
    val content: List<ReservationTaskAutomationExecutionResponse>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int
)

data class ReservationTaskAutomationBatchSummaryResponse(
    val processedEvents: Int,
    val rulesEvaluated: Int,
    val tasksCreated: Int,
    val alreadyExists: Int,
    val skipped: Int,
    val failed: Int,
    val deadLetter: Int
)

data class ReservationTaskAutomationScheduleStatusResponse(
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
    val leaseState: ReservationSyncScheduleLeaseState,
    val eligibleBacklogCount: Long,
    val failedExecutionCount: Long,
    val deadLetterExecutionCount: Long
)

private fun ReservationTaskAutomationRuleDescriptor.toResponse(): ReservationTaskAutomationRuleResponse =
    ReservationTaskAutomationRuleResponse(ruleId, version, supportedEventTypes, enabled)

private fun ReservationTaskAutomationExecution.toResponse(): ReservationTaskAutomationExecutionResponse =
    ReservationTaskAutomationExecutionResponse(
        id = id.value,
        reservationRecorded = true,
        ruleId = ruleId,
        ruleVersion = ruleVersion,
        triggerEventType = triggerEventType,
        outcome = outcome,
        taskCreated = createdTaskId != null,
        failureCategory = failureCategory,
        skipReason = skipReason,
        attemptCount = attemptCount,
        nextAttemptAt = nextAttemptAt,
        createdAt = createdAt,
        updatedAt = updatedAt,
        completedAt = completedAt
    )

private fun ReservationTaskAutomationExecutionPage.toResponse(): ReservationTaskAutomationExecutionPageResponse =
    ReservationTaskAutomationExecutionPageResponse(
        content = content.map { it.toResponse() },
        page = page,
        size = size,
        totalElements = totalElements,
        totalPages = totalPages
    )

private fun ReservationTaskAutomationBatchSummary.toResponse(): ReservationTaskAutomationBatchSummaryResponse =
    ReservationTaskAutomationBatchSummaryResponse(
        processedEvents = processedEvents,
        rulesEvaluated = rulesEvaluated,
        tasksCreated = tasksCreated,
        alreadyExists = alreadyExists,
        skipped = skipped,
        failed = failed,
        deadLetter = deadLetter
    )

private fun ReservationTaskAutomationScheduleStatus.toResponse(): ReservationTaskAutomationScheduleStatusResponse =
    ReservationTaskAutomationScheduleStatusResponse(
        scheduleId = scheduleId,
        configuredEnabled = configuredEnabled,
        effectiveEnabled = effectiveEnabled,
        paused = paused,
        scheduleSummary = scheduleSummary,
        batchSize = batchSize,
        maxRecordsPerExecution = maxRecordsPerExecution,
        enabledRuleCount = enabledRuleCount,
        lastAttemptedAt = lastAttemptedAt,
        lastSuccessfulAt = lastSuccessfulAt,
        nextExpectedExecutionAt = nextExpectedExecutionAt,
        lastProcessedCount = lastProcessedCount,
        lastCreatedTaskCount = lastCreatedTaskCount,
        lastFailureCategory = lastFailureCategory,
        leaseState = leaseState,
        eligibleBacklogCount = eligibleBacklogCount,
        failedExecutionCount = failedExecutionCount,
        deadLetterExecutionCount = deadLetterExecutionCount
    )
