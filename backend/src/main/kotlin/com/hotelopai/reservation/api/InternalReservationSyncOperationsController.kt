package com.hotelopai.reservation.api

import com.hotelopai.pms.application.PmsFailureCategory
import com.hotelopai.reservation.application.ReservationSyncOperationRequest
import com.hotelopai.reservation.application.ReservationSyncOperationRejectedException
import com.hotelopai.reservation.application.ReservationSyncOperationsService
import com.hotelopai.reservation.application.ReservationSyncRun
import com.hotelopai.reservation.application.ReservationSyncRunFilter
import com.hotelopai.reservation.application.ReservationSyncRunId
import com.hotelopai.reservation.application.ReservationSyncRunNotFoundException
import com.hotelopai.reservation.application.ReservationSyncRunPage
import com.hotelopai.reservation.application.ReservationSyncRunStatus
import com.hotelopai.reservation.application.ReservationSyncScheduleLeaseState
import com.hotelopai.reservation.application.ReservationSyncScheduleStatus
import com.hotelopai.reservation.application.ReservationSyncStatus
import com.hotelopai.reservation.application.ReservationSyncStateView
import com.hotelopai.reservation.application.ReservationSyncTriggerType
import com.hotelopai.reservation.application.ReservationWebhookEventCategory
import com.hotelopai.reservation.application.ReservationWebhookInboxFilter
import com.hotelopai.reservation.application.ReservationWebhookInboxId
import com.hotelopai.reservation.application.ReservationWebhookInboxPage
import com.hotelopai.reservation.application.ReservationWebhookInboxRecord
import com.hotelopai.reservation.application.ReservationWebhookNotFoundException
import com.hotelopai.reservation.application.ReservationWebhookProcessingService
import com.hotelopai.reservation.application.ReservationWebhookProcessingSummary
import com.hotelopai.reservation.application.ReservationWebhookRejectedException
import com.hotelopai.reservation.application.ReservationWebhookScheduleStatus
import com.hotelopai.reservation.application.ReservationWebhookStatus
import com.hotelopai.shared.security.CurrentUserContextResolver
import com.hotelopai.shared.security.PermissionExpressions
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

@RestController
@RequestMapping("/api/v1/internal/reservations")
class InternalReservationSyncOperationsController(
    private val operationsService: ReservationSyncOperationsService,
    private val webhookProcessingService: ReservationWebhookProcessingService,
    private val currentUserContextResolver: CurrentUserContextResolver
) {
    @PostMapping("/sync")
    @PreAuthorize(PermissionExpressions.RESERVATION_SYNC_OPERATIONS)
    fun runManualSync(@RequestBody request: ReservationSyncRequest): ReservationSyncRunResponse =
        safely {
            operationsService.runManualSync(
                request = ReservationSyncOperationRequest(
                    startDate = request.startDate,
                    endDate = request.endDate,
                    triggerType = request.triggerType ?: ReservationSyncTriggerType.MANUAL
                ),
                actorUserId = actorUserId()
            ).toResponse()
        }

    @GetMapping("/sync-runs")
    @PreAuthorize(PermissionExpressions.RESERVATION_SYNC_OPERATIONS)
    fun syncRuns(
        @RequestParam(required = false) providerId: String?,
        @RequestParam(required = false) status: ReservationSyncRunStatus?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ReservationSyncRunPageResponse =
        operationsService.history(
            ReservationSyncRunFilter(providerId = providerId, status = status, page = page, size = size),
            actorUserId()
        ).toResponse()

    @GetMapping("/sync-runs/{runId}")
    @PreAuthorize(PermissionExpressions.RESERVATION_SYNC_OPERATIONS)
    fun syncRun(@PathVariable runId: UUID): ReservationSyncRunResponse =
        safely { operationsService.run(ReservationSyncRunId(runId), actorUserId()).toResponse() }

    @GetMapping("/sync-state")
    @PreAuthorize(PermissionExpressions.RESERVATION_SYNC_OPERATIONS)
    fun syncState(): ReservationSyncStateResponse =
        operationsService.syncState(actorUserId()).toResponse()

    @GetMapping("/sync-schedule")
    @PreAuthorize(PermissionExpressions.RESERVATION_SYNC_OPERATIONS)
    fun syncSchedule(): ReservationSyncScheduleStatusResponse =
        safely { operationsService.schedulerStatus(actorUserId()).toResponse() }

    @PostMapping("/sync-schedule/run-now")
    @PreAuthorize(PermissionExpressions.RESERVATION_SYNC_OPERATIONS)
    fun runScheduledPolicyNow(): ReservationSyncRunResponse =
        safely { operationsService.runScheduledPolicyNow(actorUserId()).toResponse() }

    @PostMapping("/sync-schedule/pause")
    @PreAuthorize(PermissionExpressions.RESERVATION_SYNC_OPERATIONS)
    fun pauseSyncSchedule(): ReservationSyncScheduleStatusResponse =
        safely { operationsService.pauseScheduler(actorUserId()).toResponse() }

    @PostMapping("/sync-schedule/resume")
    @PreAuthorize(PermissionExpressions.RESERVATION_SYNC_OPERATIONS)
    fun resumeSyncSchedule(): ReservationSyncScheduleStatusResponse =
        safely { operationsService.resumeScheduler(actorUserId()).toResponse() }

    @GetMapping("/webhooks")
    @PreAuthorize(PermissionExpressions.RESERVATION_SYNC_OPERATIONS)
    fun webhookInbox(
        @RequestParam(required = false) providerId: String?,
        @RequestParam(required = false) status: ReservationWebhookStatus?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ReservationWebhookInboxPageResponse =
        webhookProcessingService.history(
            ReservationWebhookInboxFilter(providerId = providerId, status = status, page = page, size = size)
        ).toResponse()

    @GetMapping("/webhooks/{eventId}")
    @PreAuthorize(PermissionExpressions.RESERVATION_SYNC_OPERATIONS)
    fun webhookInboxRecord(@PathVariable eventId: UUID): ReservationWebhookInboxResponse =
        safely { webhookProcessingService.find(ReservationWebhookInboxId(eventId)).toResponse() }

    @PostMapping("/webhooks/{eventId}/retry")
    @PreAuthorize(PermissionExpressions.RESERVATION_SYNC_OPERATIONS)
    fun retryWebhook(@PathVariable eventId: UUID): ReservationWebhookInboxResponse =
        safely { webhookProcessingService.retry(ReservationWebhookInboxId(eventId), actorUserId()).toResponse() }

    @PostMapping("/webhooks/process-batch")
    @PreAuthorize(PermissionExpressions.RESERVATION_SYNC_OPERATIONS)
    fun processWebhookBatch(): ReservationWebhookProcessingSummaryResponse =
        safely { webhookProcessingService.processOperatorBatch(actorUserId()).toResponse() }

    @PostMapping("/webhooks/cleanup")
    @PreAuthorize(PermissionExpressions.RESERVATION_SYNC_OPERATIONS)
    fun cleanupWebhooks(): ReservationWebhookCleanupResponse =
        ReservationWebhookCleanupResponse(deletedCount = webhookProcessingService.cleanup())

    @GetMapping("/webhooks/schedule")
    @PreAuthorize(PermissionExpressions.RESERVATION_SYNC_OPERATIONS)
    fun webhookSchedule(): ReservationWebhookScheduleStatusResponse =
        safely { webhookProcessingService.schedulerStatus(actorUserId()).toResponse() }

    @PostMapping("/webhooks/schedule/run-now")
    @PreAuthorize(PermissionExpressions.RESERVATION_SYNC_OPERATIONS)
    fun runWebhookScheduleNow(): ReservationWebhookProcessingSummaryResponse =
        safely { webhookProcessingService.processOperatorBatch(actorUserId()).toResponse() }

    @PostMapping("/webhooks/schedule/pause")
    @PreAuthorize(PermissionExpressions.RESERVATION_SYNC_OPERATIONS)
    fun pauseWebhookSchedule(): ReservationWebhookScheduleStatusResponse =
        safely { webhookProcessingService.pauseScheduler(actorUserId()).toResponse() }

    @PostMapping("/webhooks/schedule/resume")
    @PreAuthorize(PermissionExpressions.RESERVATION_SYNC_OPERATIONS)
    fun resumeWebhookSchedule(): ReservationWebhookScheduleStatusResponse =
        safely { webhookProcessingService.resumeScheduler(actorUserId()).toResponse() }

    private fun actorUserId(): UUID =
        currentUserContextResolver.current().userId

    private fun <T> safely(block: () -> T): T =
        try {
            block()
        } catch (exception: ReservationSyncRunNotFoundException) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Reservation sync run not found.", exception)
        } catch (exception: ReservationWebhookNotFoundException) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Reservation webhook event not found.", exception)
        } catch (exception: ReservationSyncOperationRejectedException) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Reservation synchronization request was rejected.", exception)
        } catch (exception: ReservationWebhookRejectedException) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Reservation webhook operation was rejected.", exception)
        } catch (exception: IllegalArgumentException) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid reservation synchronization request.", exception)
        }
}

data class ReservationSyncRequest(
    @field:DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    val startDate: LocalDate,
    @field:DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    val endDate: LocalDate,
    val triggerType: ReservationSyncTriggerType? = ReservationSyncTriggerType.MANUAL
)

data class ReservationSyncRunResponse(
    val runId: UUID,
    val providerId: String,
    val propertyScope: String,
    val requestedStartDate: LocalDate,
    val requestedEndDate: LocalDate,
    val triggerType: ReservationSyncTriggerType,
    val status: ReservationSyncRunStatus,
    val startedAt: Instant,
    val completedAt: Instant?,
    val fetchedCount: Int,
    val createdCount: Int,
    val updatedCount: Int,
    val unchangedCount: Int,
    val staleCount: Int,
    val conflictCount: Int,
    val boundedPageCount: Int,
    val failureCategory: PmsFailureCategory?,
    val actorRecorded: Boolean
)

data class ReservationSyncRunPageResponse(
    val content: List<ReservationSyncRunResponse>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int
)

data class ReservationSyncStateResponse(
    val providerId: String,
    val propertyScope: String,
    val status: ReservationSyncStatus,
    val lastAttemptedAt: Instant?,
    val lastSuccessfulAt: Instant?,
    val lastFailureCategory: PmsFailureCategory?,
    val windowStartDate: LocalDate?,
    val windowEndDate: LocalDate?,
    val fetchedCount: Int,
    val createdCount: Int,
    val updatedCount: Int,
    val unchangedCount: Int,
    val staleCount: Int,
    val conflictCount: Int
)

data class ReservationSyncScheduleStatusResponse(
    val scheduleId: String,
    val enabled: Boolean,
    val paused: Boolean,
    val providerId: String,
    val propertyScope: String,
    val scheduleSummary: String,
    val timezone: ZoneId,
    val windowStartDate: LocalDate,
    val windowEndDate: LocalDate,
    val lastAttemptedAt: Instant?,
    val lastSuccessfulAt: Instant?,
    val nextExpectedExecutionAt: Instant?,
    val leaseState: ReservationSyncScheduleLeaseState,
    val lastFailureCategory: PmsFailureCategory?
)

data class ReservationWebhookInboxResponse(
    val id: UUID,
    val providerId: String,
    val providerEventId: String,
    val eventCategory: ReservationWebhookEventCategory,
    val propertyScope: String,
    val externalEntityRecorded: Boolean,
    val providerEventTimestamp: Instant?,
    val receivedAt: Instant,
    val status: ReservationWebhookStatus,
    val failureCategory: PmsFailureCategory?,
    val attemptCount: Int,
    val nextAttemptAt: Instant?,
    val processingStartedAt: Instant?,
    val completedAt: Instant?,
    val syncRunId: UUID?,
    val safeMetadata: Map<String, String>
)

data class ReservationWebhookInboxPageResponse(
    val content: List<ReservationWebhookInboxResponse>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int
)

data class ReservationWebhookProcessingSummaryResponse(
    val processedCount: Int,
    val succeededCount: Int,
    val failedCount: Int,
    val ignoredCount: Int,
    val retriedCount: Int,
    val deadLetterCount: Int
)

data class ReservationWebhookCleanupResponse(
    val deletedCount: Int
)

data class ReservationWebhookScheduleStatusResponse(
    val scheduleId: String,
    val configuredEnabled: Boolean,
    val effectiveEnabled: Boolean,
    val paused: Boolean,
    val scheduleSummary: String,
    val batchSize: Int,
    val maxRecordsPerExecution: Int,
    val lastAttemptedAt: Instant?,
    val lastSuccessfulAt: Instant?,
    val nextExpectedExecutionAt: Instant?,
    val lastProcessedCount: Int,
    val lastFailureCategory: PmsFailureCategory?,
    val leaseState: ReservationSyncScheduleLeaseState,
    val backlogCounts: Map<ReservationWebhookStatus, Long>,
    val eligibleBacklogCount: Long,
    val deadLetterCount: Long
)

private fun ReservationSyncRun.toResponse(): ReservationSyncRunResponse =
    ReservationSyncRunResponse(
        runId = id.value,
        providerId = providerId,
        propertyScope = propertyScopeLabel,
        requestedStartDate = requestedDateRange.arrival,
        requestedEndDate = requestedDateRange.departure,
        triggerType = triggerType,
        status = status,
        startedAt = startedAt,
        completedAt = completedAt,
        fetchedCount = fetchedCount,
        createdCount = createdCount,
        updatedCount = updatedCount,
        unchangedCount = unchangedCount,
        staleCount = staleCount,
        conflictCount = conflictCount,
        boundedPageCount = boundedPageCount,
        failureCategory = failureCategory,
        actorRecorded = actorUserId != null
    )

private fun ReservationSyncRunPage.toResponse(): ReservationSyncRunPageResponse =
    ReservationSyncRunPageResponse(
        content = content.map { it.toResponse() },
        page = page,
        size = size,
        totalElements = totalElements,
        totalPages = totalPages
    )

private fun ReservationSyncStateView.toResponse(): ReservationSyncStateResponse =
    ReservationSyncStateResponse(
        providerId = providerId,
        propertyScope = propertyScopeLabel,
        status = status,
        lastAttemptedAt = lastAttemptedAt,
        lastSuccessfulAt = lastSuccessfulAt,
        lastFailureCategory = lastFailureCategory,
        windowStartDate = windowStartDate,
        windowEndDate = windowEndDate,
        fetchedCount = fetchedCount,
        createdCount = createdCount,
        updatedCount = updatedCount,
        unchangedCount = unchangedCount,
        staleCount = staleCount,
        conflictCount = conflictCount
    )

private fun ReservationSyncScheduleStatus.toResponse(): ReservationSyncScheduleStatusResponse =
    ReservationSyncScheduleStatusResponse(
        scheduleId = scheduleId,
        enabled = enabled,
        paused = paused,
        providerId = providerId,
        propertyScope = propertyScopeLabel,
        scheduleSummary = scheduleSummary,
        timezone = timezone,
        windowStartDate = windowStartDate,
        windowEndDate = windowEndDate,
        lastAttemptedAt = lastAttemptedAt,
        lastSuccessfulAt = lastSuccessfulAt,
        nextExpectedExecutionAt = nextExpectedExecutionAt,
        leaseState = leaseState,
        lastFailureCategory = lastFailureCategory
    )

private fun ReservationWebhookInboxRecord.toResponse(): ReservationWebhookInboxResponse =
    ReservationWebhookInboxResponse(
        id = id.value,
        providerId = providerId,
        providerEventId = providerEventId,
        eventCategory = eventCategory,
        propertyScope = propertyScopeLabel,
        externalEntityRecorded = externalEntityHash != null,
        providerEventTimestamp = providerEventTimestamp,
        receivedAt = receivedAt,
        status = status,
        failureCategory = failureCategory,
        attemptCount = attemptCount,
        nextAttemptAt = nextAttemptAt,
        processingStartedAt = processingStartedAt,
        completedAt = completedAt,
        syncRunId = syncRunId?.value,
        safeMetadata = safeMetadata
    )

private fun ReservationWebhookInboxPage.toResponse(): ReservationWebhookInboxPageResponse =
    ReservationWebhookInboxPageResponse(
        content = content.map { it.toResponse() },
        page = page,
        size = size,
        totalElements = totalElements,
        totalPages = totalPages
    )

private fun ReservationWebhookProcessingSummary.toResponse(): ReservationWebhookProcessingSummaryResponse =
    ReservationWebhookProcessingSummaryResponse(
        processedCount = processedCount,
        succeededCount = succeededCount,
        failedCount = failedCount,
        ignoredCount = ignoredCount,
        retriedCount = retriedCount,
        deadLetterCount = deadLetterCount
    )

private fun ReservationWebhookScheduleStatus.toResponse(): ReservationWebhookScheduleStatusResponse =
    ReservationWebhookScheduleStatusResponse(
        scheduleId = scheduleId,
        configuredEnabled = configuredEnabled,
        effectiveEnabled = effectiveEnabled,
        paused = paused,
        scheduleSummary = scheduleSummary,
        batchSize = batchSize,
        maxRecordsPerExecution = maxRecordsPerExecution,
        lastAttemptedAt = lastAttemptedAt,
        lastSuccessfulAt = lastSuccessfulAt,
        nextExpectedExecutionAt = nextExpectedExecutionAt,
        lastProcessedCount = lastProcessedCount,
        lastFailureCategory = lastFailureCategory,
        leaseState = leaseState,
        backlogCounts = backlogCounts.byStatus,
        eligibleBacklogCount = backlogCounts.eligibleCount,
        deadLetterCount = backlogCounts.deadLetterCount
    )
