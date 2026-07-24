package com.hotelopai.reservation.application

import com.hotelopai.observability.OperationalObservability
import com.hotelopai.pms.application.PmsCapability
import com.hotelopai.pms.application.PmsFailureCategory
import com.hotelopai.pms.application.PmsHealthState
import com.hotelopai.pms.application.PmsProviderRegistry
import com.hotelopai.reservation.domain.PropertyId
import com.hotelopai.shared.kernel.PersistenceInstant
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.core.env.Environment
import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.util.UUID

@Service
@EnableConfigurationProperties(ReservationSyncOperationsProperties::class, ReservationSyncScheduleProperties::class)
class ReservationSyncOperationsService(
    private val pmsProviderRegistry: PmsProviderRegistry,
    private val synchronizationService: ReservationSynchronizationService,
    private val syncStateRepository: ReservationSyncStateRepository,
    private val runRepository: ReservationSyncRunRepository,
    private val lockRepository: ReservationSyncRunLockRepository,
    private val auditSink: ReservationSyncOperationsAuditSink,
    private val clock: Clock,
    private val properties: ReservationSyncOperationsProperties = ReservationSyncOperationsProperties(),
    private val scheduleStateRepository: ReservationSyncScheduleStateRepository? = null,
    private val scheduleLeaseStatusRepository: ReservationSyncScheduleLeaseStatusRepository? = null,
    private val scheduleProperties: ReservationSyncScheduleProperties = ReservationSyncScheduleProperties(),
    private val windowPolicy: ReservationSyncWindowPolicy = ReservationSyncWindowPolicy(clock),
    private val environment: Environment? = null,
    private val observability: OperationalObservability = OperationalObservability.noop()
) {
    fun runManualSync(request: ReservationSyncOperationRequest, actorUserId: UUID?): ReservationSyncRun {
        return runSync(request, actorUserId, allowAutomaticTrigger = false)
    }

    fun runScheduledSync(request: ReservationSyncOperationRequest): ReservationSyncRun {
        return runSync(request.copy(triggerType = ReservationSyncTriggerType.SCHEDULED), actorUserId = null, allowAutomaticTrigger = true)
    }

    fun runWebhookSync(request: ReservationSyncOperationRequest): ReservationSyncRun {
        return runSync(request.copy(triggerType = ReservationSyncTriggerType.WEBHOOK), actorUserId = null, allowAutomaticTrigger = true)
    }

    fun runScheduledPolicy(): ReservationSyncRun? {
        val now = PersistenceInstant.now(clock)
        val scheduleStateRepository = requiredScheduleStateRepository()
        val state = scheduleStateRepository.getOrCreate(SCHEDULE_ID, now)
        if (!scheduleProperties.enabled) {
            recordScheduleMetric("skipped", "disabled")
            return null
        }
        if (state.paused) {
            scheduleStateRepository.recordAttempt(SCHEDULE_ID, null, now, PmsFailureCategory.VALIDATION)
            recordScheduleMetric("skipped", "paused")
            return null
        }
        if (!scheduleProfileAllowed()) {
            scheduleStateRepository.recordAttempt(SCHEDULE_ID, null, now, PmsFailureCategory.CONFIGURATION)
            recordScheduleMetric("skipped", "profile_not_allowed")
            return null
        }
        if (scheduleProperties.providerId != null && scheduleProperties.providerId != pmsProviderRegistry.activeProviderId()) {
            scheduleStateRepository.recordAttempt(SCHEDULE_ID, null, now, PmsFailureCategory.CONFIGURATION)
            recordScheduleMetric("rejected", "provider_mismatch")
            return null
        }
        val window = windowPolicy.window(scheduleProperties)
        val run = runScheduledSync(
            ReservationSyncOperationRequest(
                startDate = window.arrival,
                endDate = window.departure,
                triggerType = ReservationSyncTriggerType.SCHEDULED
            )
        )
        scheduleStateRepository.recordAttempt(SCHEDULE_ID, run, PersistenceInstant.now(clock), run.failureCategory)
        recordScheduleMetric(run.status.name.lowercase(), run.failureCategory?.name?.lowercase() ?: "none")
        return run
    }

    fun runScheduledPolicyNow(actorUserId: UUID?): ReservationSyncRun {
        val window = windowPolicy.window(scheduleProperties)
        return runSync(
            ReservationSyncOperationRequest(
                startDate = window.arrival,
                endDate = window.departure,
                triggerType = ReservationSyncTriggerType.MANUAL
            ),
            actorUserId,
            allowAutomaticTrigger = false
        )
    }

    fun schedulerStatus(actorUserId: UUID?): ReservationSyncScheduleStatus {
        val now = PersistenceInstant.now(clock)
        val state = requiredScheduleStateRepository().getOrCreate(SCHEDULE_ID, now)
        val scope = activePropertyScopeOrNull()
        val window = windowPolicy.window(scheduleProperties)
        audit(ReservationSyncOperationsAuditAction.HISTORY_INSPECTED, pmsProviderRegistry.activeProviderId(), null, actorUserId, "schedule_status", now)
        return ReservationSyncScheduleStatus(
            scheduleId = SCHEDULE_ID,
            enabled = scheduleProperties.enabled,
            paused = state.paused,
            providerId = scheduleProperties.providerId ?: pmsProviderRegistry.activeProviderId(),
            propertyScopeLabel = scope?.label ?: "not_configured",
            scheduleSummary = "every ${scheduleProperties.executionInterval}; window ${scheduleProperties.lookbackDays}d lookback, ${scheduleProperties.lookaheadDays}d lookahead",
            timezone = scheduleProperties.timezone,
            windowStartDate = window.arrival,
            windowEndDate = window.departure,
            lastAttemptedAt = state.lastAttemptedAt,
            lastSuccessfulAt = state.lastSuccessfulAt,
            nextExpectedExecutionAt = state.lastAttemptedAt?.plus(scheduleProperties.executionInterval),
            leaseState = scheduleLeaseStatusRepository
                ?.state(SCHEDULED_SYNC_JOB_NAME, now)
                ?: ReservationSyncScheduleLeaseState.HELD_OR_UNKNOWN,
            lastFailureCategory = state.lastFailureCategory
        )
    }

    fun pauseScheduler(actorUserId: UUID?): ReservationSyncScheduleStatus {
        val now = PersistenceInstant.now(clock)
        requiredScheduleStateRepository().markPaused(SCHEDULE_ID, now)
        audit(ReservationSyncOperationsAuditAction.SCHEDULER_PAUSED, pmsProviderRegistry.activeProviderId(), null, actorUserId, "paused", now)
        recordScheduleMetric("paused", "none")
        return schedulerStatus(actorUserId)
    }

    fun resumeScheduler(actorUserId: UUID?): ReservationSyncScheduleStatus {
        val now = PersistenceInstant.now(clock)
        requiredScheduleStateRepository().markResumed(SCHEDULE_ID, now)
        audit(ReservationSyncOperationsAuditAction.SCHEDULER_RESUMED, pmsProviderRegistry.activeProviderId(), null, actorUserId, "resumed", now)
        recordScheduleMetric("resumed", "none")
        return schedulerStatus(actorUserId)
    }

    private fun runSync(
        request: ReservationSyncOperationRequest,
        actorUserId: UUID?,
        allowAutomaticTrigger: Boolean
    ): ReservationSyncRun {
        val now = PersistenceInstant.now(clock)
        val provider = pmsProviderRegistry.activeProvider()
        val providerId = provider.id.value
        val scope = activePropertyScope()
        val run = runRepository.save(
            ReservationSyncRun(
                providerId = providerId,
                propertyScopeHash = scope.hash,
                propertyScopeLabel = scope.label,
                requestedDateRange = request.dateRange(),
                triggerType = request.triggerType,
                status = ReservationSyncRunStatus.REQUESTED,
                startedAt = now,
                actorUserId = actorUserId
            )
        )
        audit(ReservationSyncOperationsAuditAction.SYNC_REQUESTED, providerId, run.id.value, actorUserId, "requested", now)

        fun reject(category: PmsFailureCategory, reason: String): ReservationSyncRun {
            val rejected = runRepository.save(
                run.copy(
                    status = ReservationSyncRunStatus.REJECTED,
                    completedAt = now,
                    failureCategory = category,
                    updatedAt = now
                )
            )
            audit(ReservationSyncOperationsAuditAction.SYNC_REJECTED, providerId, run.id.value, actorUserId, reason, now, category)
            recordRunMetric(providerId, request.triggerType, "rejected", category)
            return rejected
        }

        val rejection = validateRequest(request, providerId, allowAutomaticTrigger)
        if (rejection != null) {
            return reject(rejection.first, rejection.second)
        }

        val lockResult = lockRepository.acquire(
            providerId = providerId,
            propertyScopeHash = scope.hash,
            dateRange = request.dateRange(),
            runId = run.id,
            lockedUntil = now.plus(properties.normalizedLockTtl()),
            now = now
        )
        if (lockResult is ReservationSyncRunLockResult.Rejected) {
            return reject(PmsFailureCategory.VALIDATION, "overlapping_run")
        }

        val started = runRepository.save(run.copy(status = ReservationSyncRunStatus.RUNNING, updatedAt = now))
        audit(ReservationSyncOperationsAuditAction.SYNC_STARTED, providerId, run.id.value, actorUserId, "running", now)
        val timer = observability.startTimer()
        try {
            val summary = synchronizationService.synchronize(
                ReservationSynchronizationCommand(
                    propertyId = scope.propertyId,
                    dateRange = request.dateRange(),
                    sourceDataTimestamp = now
                )
            )
            val completedAt = PersistenceInstant.now(clock)
            val status = if (summary.conflictCount > 0 || summary.staleCount > 0) {
                ReservationSyncRunStatus.PARTIALLY_SUCCEEDED
            } else {
                ReservationSyncRunStatus.SUCCEEDED
            }
            val completed = runRepository.save(
                started.copy(
                    status = status,
                    completedAt = completedAt,
                    fetchedCount = summary.fetchedCount,
                    createdCount = summary.createdCount,
                    updatedCount = summary.updatedCount,
                    unchangedCount = summary.unchangedCount,
                    staleCount = summary.staleCount,
                    conflictCount = summary.conflictCount,
                    boundedPageCount = 1,
                    failureCategory = summary.failureCategory,
                    updatedAt = completedAt
                )
            )
            audit(ReservationSyncOperationsAuditAction.SYNC_COMPLETED, providerId, run.id.value, actorUserId, status.name.lowercase(), completedAt, summary.failureCategory)
            recordRunMetric(providerId, request.triggerType, status.name.lowercase(), summary.failureCategory ?: PmsFailureCategory.NONE)
            observability.stopTimer(timer, "hotelopai.reservation.sync.run.duration", "provider" to providerId, "trigger" to request.triggerType.name.lowercase(), "outcome" to status.name.lowercase())
            return completed
        } catch (exception: RuntimeException) {
            val failureCategory = exception.toFailureCategory()
            val completedAt = PersistenceInstant.now(clock)
            val failed = runRepository.save(
                started.copy(
                    status = ReservationSyncRunStatus.FAILED,
                    completedAt = completedAt,
                    failureCategory = failureCategory,
                    updatedAt = completedAt
                )
            )
            audit(ReservationSyncOperationsAuditAction.SYNC_COMPLETED, providerId, run.id.value, actorUserId, "failed", completedAt, failureCategory)
            recordRunMetric(providerId, request.triggerType, "failed", failureCategory)
            observability.stopTimer(timer, "hotelopai.reservation.sync.run.duration", "provider" to providerId, "trigger" to request.triggerType.name.lowercase(), "outcome" to "failed")
            return failed
        } finally {
            lockRepository.release(run.id)
        }
    }

    fun history(filter: ReservationSyncRunFilter, actorUserId: UUID?): ReservationSyncRunPage {
        audit(ReservationSyncOperationsAuditAction.HISTORY_INSPECTED, filter.providerId ?: pmsProviderRegistry.activeProviderId(), null, actorUserId, "success", PersistenceInstant.now(clock))
        return runRepository.find(
            filter.copy(
                page = filter.page.coerceAtLeast(0),
                size = filter.size.coerceIn(1, properties.normalizedMaxPageSize())
            )
        )
    }

    fun run(runId: ReservationSyncRunId, actorUserId: UUID?): ReservationSyncRun {
        val run = runRepository.findById(runId) ?: throw ReservationSyncRunNotFoundException(runId)
        audit(ReservationSyncOperationsAuditAction.HISTORY_INSPECTED, run.providerId, run.id.value, actorUserId, "success", PersistenceInstant.now(clock))
        return run
    }

    fun syncState(actorUserId: UUID?): ReservationSyncStateView {
        val providerId = pmsProviderRegistry.activeProviderId()
        val scope = activePropertyScope()
        val state = syncStateRepository.find(providerId, scope.propertyId)
        audit(ReservationSyncOperationsAuditAction.HISTORY_INSPECTED, providerId, null, actorUserId, "sync_state", PersistenceInstant.now(clock))
        return ReservationSyncStateView(
            providerId = providerId,
            propertyScopeLabel = scope.label,
            status = state?.status ?: ReservationSyncStatus.NEVER_SYNCED,
            lastAttemptedAt = state?.lastAttemptedAt,
            lastSuccessfulAt = state?.lastSuccessfulAt,
            lastFailureCategory = state?.lastFailureCategory,
            windowStartDate = state?.window?.arrival,
            windowEndDate = state?.window?.departure,
            fetchedCount = state?.fetchedCount ?: 0,
            createdCount = state?.createdCount ?: 0,
            updatedCount = state?.updatedCount ?: 0,
            unchangedCount = state?.unchangedCount ?: 0,
            staleCount = state?.staleCount ?: 0,
            conflictCount = state?.conflictCount ?: 0
        )
    }

    fun cleanupCompletedRuns(actorUserId: UUID?, limit: Int = Int.MAX_VALUE): Int {
        val cutoff = java.time.ZonedDateTime.now(clock).minus(properties.historyRetention).toInstant()
        val deleted = runRepository.deleteCompletedBefore(cutoff, limit)
        audit(ReservationSyncOperationsAuditAction.RETENTION_CLEANUP_EXECUTED, pmsProviderRegistry.activeProviderId(), null, actorUserId, "deleted_$deleted", PersistenceInstant.now(clock))
        observability.incrementCounter(
            "hotelopai.reservation.sync.cleanup.total",
            deleted.toDouble(),
            "provider" to pmsProviderRegistry.activeProviderId(),
            "trigger" to "manual",
            "outcome" to "success",
            "failure_category" to "none"
        )
        return deleted
    }

    private fun validateRequest(
        request: ReservationSyncOperationRequest,
        providerId: String,
        allowAutomaticTrigger: Boolean
    ): Pair<PmsFailureCategory, String>? {
        if (request.triggerType != ReservationSyncTriggerType.MANUAL && !allowAutomaticTrigger && !properties.enabledAutomaticTriggers) {
            return PmsFailureCategory.VALIDATION to "automatic_trigger_disabled"
        }
        val dateRange = runCatching { request.dateRange() }.getOrElse {
            return PmsFailureCategory.VALIDATION to "invalid_date_window"
        }
        if (dateRange.arrival.plus(properties.maxWindow).isBefore(dateRange.departure)) {
            return PmsFailureCategory.VALIDATION to "date_window_too_large"
        }
        val provider = pmsProviderRegistry.activeProvider()
        val config = pmsProviderRegistry.providerConfig(providerId)
        val readiness = provider.readiness(config)
        val health = provider.health(config)
        if (!readiness.enabled || !readiness.configured || health.state == PmsHealthState.MISCONFIGURED) {
            return PmsFailureCategory.CONFIGURATION to "provider_not_ready"
        }
        if (!provider.capabilities.supports(PmsCapability.RESERVATION_LOOKUP) || !provider.capabilities.supports(PmsCapability.GUEST_LOOKUP)) {
            return PmsFailureCategory.CONFIGURATION to "unsupported_capability"
        }
        return null
    }

    private fun activePropertyScope(): ReservationPropertyScope {
        val providerId = pmsProviderRegistry.activeProviderId()
        val propertyId = pmsProviderRegistry.providerConfig(providerId)
            ?.hotelPropertyIdentifier
            ?.takeIf { it.isNotBlank() }
            ?: throw ReservationSyncOperationRejectedException("Active PMS provider has no configured property scope.")
        val hash = sha256("$providerId:$propertyId")
        return ReservationPropertyScope(
            propertyId = PropertyId(propertyId),
            hash = hash,
            label = "configured:${hash.take(12)}"
        )
    }

    private fun activePropertyScopeOrNull(): ReservationPropertyScope? =
        try {
            activePropertyScope()
        } catch (_: ReservationSyncOperationRejectedException) {
            null
        }

    private fun audit(
        action: ReservationSyncOperationsAuditAction,
        providerId: String,
        runId: UUID?,
        actorUserId: UUID?,
        outcome: String,
        occurredAt: Instant,
        failureCategory: PmsFailureCategory? = null
    ) {
        auditSink.record(
            ReservationSyncOperationsAuditEvent(
                actorUserId = actorUserId,
                providerId = providerId,
                runId = runId,
                action = action,
                outcome = outcome,
                occurredAt = occurredAt,
                failureCategory = failureCategory
            )
        )
    }

    private fun recordRunMetric(
        providerId: String,
        trigger: ReservationSyncTriggerType,
        outcome: String,
        failureCategory: PmsFailureCategory
    ) {
        observability.incrementCounter(
            "hotelopai.reservation.sync.runs.requested.total",
            "provider" to providerId,
            "trigger" to trigger.name.lowercase(),
            "outcome" to outcome,
            "failure_category" to failureCategory.name.lowercase()
        )
    }

    private fun requiredScheduleStateRepository(): ReservationSyncScheduleStateRepository =
        scheduleStateRepository
            ?: throw ReservationSyncOperationRejectedException("Reservation sync schedule state repository is not configured.")

    private fun scheduleProfileAllowed(): Boolean {
        val allowedProfiles = scheduleProperties.allowedProfiles
        if (allowedProfiles.isEmpty()) return true
        val activeProfiles = environment?.activeProfiles?.toSet().orEmpty()
        return activeProfiles.any { it in allowedProfiles }
    }

    private fun recordScheduleMetric(outcome: String, reasonCode: String) {
        observability.incrementCounter(
            "hotelopai.reservation.sync.schedule.total",
            "provider" to (scheduleProperties.providerId ?: pmsProviderRegistry.activeProviderId()),
            "trigger" to "scheduled",
            "outcome" to outcome,
            "failure_category" to reasonCode
        )
    }

    private fun RuntimeException.toFailureCategory(): PmsFailureCategory =
        when (this) {
            is ReservationSyncOperationRejectedException -> PmsFailureCategory.VALIDATION
            is ReservationSyncBoundExceededException -> PmsFailureCategory.VALIDATION
            else -> PmsFailureCategory.UNKNOWN
        }

    private fun sha256(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(StandardCharsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    companion object {
        const val SCHEDULE_ID = "reservation_sync_default"
        const val SCHEDULED_SYNC_JOB_NAME = "reservation_sync_scheduler"
    }
}

class ReservationSyncOperationRejectedException(message: String) : RuntimeException(message)

class ReservationSyncRunNotFoundException(runId: ReservationSyncRunId) : RuntimeException("Reservation sync run not found: ${runId.value}")
