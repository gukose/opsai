package com.hotelopai.reservation.application

import com.hotelopai.observability.OperationalObservability
import com.hotelopai.pms.application.PmsCapability
import com.hotelopai.pms.application.PmsFailureCategory
import com.hotelopai.pms.application.PmsProviderRegistry
import com.hotelopai.shared.kernel.PersistenceInstant
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.core.env.Environment
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

@Service
@EnableConfigurationProperties(ReservationWebhookProperties::class, ReservationWebhookScheduleProperties::class)
class ReservationWebhookProcessingService(
    adapters: List<ReservationWebhookAdapter>,
    private val inboxRepository: ReservationWebhookInboxRepository,
    private val syncOperationsService: ReservationSyncOperationsService,
    private val pmsProviderRegistry: PmsProviderRegistry,
    private val auditSink: ReservationSyncOperationsAuditSink,
    private val clock: Clock,
    private val properties: ReservationWebhookProperties,
    private val scheduleStateRepository: ReservationSyncScheduleStateRepository? = null,
    private val scheduleLeaseStatusRepository: ReservationSyncScheduleLeaseStatusRepository? = null,
    private val scheduleProperties: ReservationWebhookScheduleProperties = ReservationWebhookScheduleProperties(),
    private val environment: Environment? = null,
    private val observability: OperationalObservability = OperationalObservability.noop()
) {
    private val adaptersByProvider = adapters.associateBy { it.providerId }

    init {
        if (scheduleProperties.enabled) {
            validateActivation()
        }
    }

    fun ingest(request: ReservationWebhookRequest): ReservationWebhookInboxRecord {
        val now = PersistenceInstant.now(clock)
        if (!properties.enabled) {
            audit(ReservationSyncOperationsAuditAction.WEBHOOK_REJECTED, request.providerId, null, "disabled", now, PmsFailureCategory.CONFIGURATION)
            throw ReservationWebhookRejectedException("Reservation webhook ingestion is disabled.")
        }
        val adapter = adaptersByProvider[request.providerId]
            ?: throw ReservationWebhookRejectedException("Reservation webhook provider is not supported.")
        val result = adapter.verifyAndExtract(request)
        if (result is ReservationWebhookVerificationResult.Rejected) {
            val rejected = ReservationWebhookInboxRecord(
                providerId = request.providerId,
                providerEventId = "rejected:${request.receivedAt.toEpochMilli()}:${request.body.size}",
                eventCategory = ReservationWebhookEventCategory.UNSUPPORTED,
                propertyScopeHash = "rejected",
                propertyScopeLabel = "not_configured",
                externalEntityHash = null,
                providerEventTimestamp = null,
                receivedAt = now,
                status = ReservationWebhookStatus.REJECTED,
                failureCategory = result.failureCategory,
                completedAt = now,
                payloadFingerprint = request.body.sha256(),
                safeMetadata = mapOf("reason" to result.reasonCode)
            )
            inboxRepository.insertIfAbsent(rejected)
            audit(ReservationSyncOperationsAuditAction.WEBHOOK_REJECTED, request.providerId, null, result.reasonCode, now, result.failureCategory)
            recordRequestMetric(request.providerId, "unsupported", "rejected", result.failureCategory)
            throw ReservationWebhookRejectedException("Reservation webhook request was rejected.")
        }
        val event = (result as ReservationWebhookVerificationResult.Verified).event
        val status = if (event.category == ReservationWebhookEventCategory.UNSUPPORTED) {
            ReservationWebhookStatus.IGNORED
        } else {
            ReservationWebhookStatus.VERIFIED
        }
        val record = ReservationWebhookInboxRecord(
            providerId = event.providerId,
            providerEventId = event.providerEventId,
            eventCategory = event.category,
            propertyScopeHash = event.propertyScopeHash,
            propertyScopeLabel = event.propertyScopeLabel,
            externalEntityHash = event.externalEntityHash,
            providerEventTimestamp = event.providerEventTimestamp,
            receivedAt = event.receivedAt,
            status = status,
            completedAt = if (status == ReservationWebhookStatus.IGNORED) now else null,
            payloadFingerprint = event.payloadFingerprint,
            safeMetadata = event.safeMetadata
        )
        return when (val insert = inboxRepository.insertIfAbsent(record)) {
            is ReservationWebhookInsertResult.Inserted -> {
                val saved = insert.record
                audit(ReservationSyncOperationsAuditAction.WEBHOOK_ACCEPTED, saved.providerId, null, saved.eventCategory.name.lowercase(), now)
                recordRequestMetric(saved.providerId, saved.eventCategory.name.lowercase(), saved.status.name.lowercase(), PmsFailureCategory.NONE)
                saved
            }
            is ReservationWebhookInsertResult.Duplicate -> {
                val duplicate = insert.existing.copy(status = ReservationWebhookStatus.DUPLICATE, completedAt = now, updatedAt = now)
                audit(ReservationSyncOperationsAuditAction.WEBHOOK_DUPLICATE, duplicate.providerId, null, duplicate.eventCategory.name.lowercase(), now)
                recordRequestMetric(duplicate.providerId, duplicate.eventCategory.name.lowercase(), "duplicate", PmsFailureCategory.NONE)
                duplicate
            }
        }
    }

    fun processBatch(limit: Int = properties.batchSize): ReservationWebhookProcessingSummary {
        if (!properties.processingEnabled) {
            return ReservationWebhookProcessingSummary(0, 0, 0, 0, 0, 0)
        }
        val now = PersistenceInstant.now(clock)
        inboxRepository.recoverAbandoned(now.minus(properties.abandonedProcessingTimeout), now)
        val records = inboxRepository.claimReady(limit.coerceIn(1, properties.batchSize), now, properties.maxAttempts)
        var succeeded = 0
        var failed = 0
        var ignored = 0
        var retried = 0
        var deadLetter = 0
        records.forEach { record ->
            when (process(record)) {
                ReservationWebhookStatus.SUCCEEDED -> succeeded += 1
                ReservationWebhookStatus.IGNORED -> ignored += 1
                ReservationWebhookStatus.FAILED -> failed += 1
                ReservationWebhookStatus.DEAD_LETTER -> deadLetter += 1
                else -> retried += 1
            }
        }
        return ReservationWebhookProcessingSummary(records.size, succeeded, failed, ignored, retried, deadLetter)
    }

    fun retry(id: ReservationWebhookInboxId, actorUserId: UUID?): ReservationWebhookInboxRecord {
        val now = PersistenceInstant.now(clock)
        val record = inboxRepository.findById(id) ?: throw ReservationWebhookNotFoundException(id)
        if (record.status !in setOf(ReservationWebhookStatus.FAILED, ReservationWebhookStatus.VERIFIED, ReservationWebhookStatus.DEAD_LETTER)) {
            throw ReservationWebhookRejectedException("Reservation webhook event is not eligible for retry.")
        }
        val retried = inboxRepository.save(
            record.copy(status = ReservationWebhookStatus.VERIFIED, nextAttemptAt = now, updatedAt = now)
        )
        val action = if (record.status == ReservationWebhookStatus.DEAD_LETTER) {
            ReservationSyncOperationsAuditAction.WEBHOOK_DEAD_LETTER_RETRY_REQUESTED
        } else {
            ReservationSyncOperationsAuditAction.WEBHOOK_RETRY_REQUESTED
        }
        audit(action, record.providerId, null, "requested", now, actorUserId = actorUserId)
        return retried
    }

    fun history(filter: ReservationWebhookInboxFilter): ReservationWebhookInboxPage =
        inboxRepository.find(filter.copy(page = filter.page.coerceAtLeast(0), size = filter.size.coerceIn(1, 100)))

    fun find(id: ReservationWebhookInboxId): ReservationWebhookInboxRecord =
        inboxRepository.findById(id) ?: throw ReservationWebhookNotFoundException(id)

    fun cleanup(): Int {
        val now = java.time.ZonedDateTime.now(clock)
        val deleted = inboxRepository.deleteCompletedBefore(
            completedCutoff = now.minus(properties.completedRetention).toInstant(),
            rejectedCutoff = now.minus(properties.rejectedRetention).toInstant(),
            deadLetterCutoff = now.minus(scheduleProperties.deadLetterRetention).toInstant(),
            limit = properties.cleanupBatchSize
        )
        audit(ReservationSyncOperationsAuditAction.WEBHOOK_CLEANUP_EXECUTED, pmsProviderRegistry.activeProviderId(), null, "deleted_$deleted", PersistenceInstant.now(clock))
        observability.incrementCounter(
            "hotelopai.reservation.webhook.cleanup.total",
            deleted.toDouble(),
            "provider" to pmsProviderRegistry.activeProviderId(),
            "event_category" to "all",
            "outcome" to "success",
            "failure_category" to "none"
        )
        return deleted
    }

    fun processOperatorBatch(actorUserId: UUID?): ReservationWebhookProcessingSummary {
        validateActivation()
        audit(
            ReservationSyncOperationsAuditAction.WEBHOOK_RUN_NOW_REQUESTED,
            pmsProviderRegistry.activeProviderId(),
            null,
            "requested",
            PersistenceInstant.now(clock),
            actorUserId = actorUserId
        )
        val summary = processBatch(scheduleProperties.maxRecordsPerExecution.coerceAtMost(scheduleProperties.batchSize))
        recordSchedulerAttempt(summary, trigger = "operator")
        return summary
    }

    fun processScheduledBatch(): ReservationWebhookProcessingSummary {
        if (!scheduleProperties.enabled) {
            return ReservationWebhookProcessingSummary(0, 0, 0, 0, 0, 0)
        }
        val now = PersistenceInstant.now(clock)
        val state = requiredScheduleStateRepository().getOrCreate(WEBHOOK_PROCESSING_SCHEDULE_ID, now)
        if (state.paused) {
            val skipped = ReservationWebhookProcessingSummary(0, 0, 0, 0, 0, 0)
            recordSchedulerAttempt(skipped, trigger = "scheduled", failureCategory = PmsFailureCategory.VALIDATION)
            return skipped
        }
        validateActivation()
        audit(ReservationSyncOperationsAuditAction.WEBHOOK_SCHEDULER_STARTED, pmsProviderRegistry.activeProviderId(), null, "scheduled", now)
        val summary = processBatch(scheduleProperties.maxRecordsPerExecution.coerceAtMost(scheduleProperties.batchSize))
        recordSchedulerAttempt(summary, trigger = "scheduled")
        audit(
            ReservationSyncOperationsAuditAction.WEBHOOK_SCHEDULER_COMPLETED,
            pmsProviderRegistry.activeProviderId(),
            null,
            "processed_${summary.processedCount}",
            PersistenceInstant.now(clock)
        )
        return summary
    }

    fun cleanupScheduled(): Int {
        if (!scheduleProperties.retentionCleanupEnabled) return 0
        val deleted = cleanup()
        recordSchedulerMetric("cleanup", "success", PmsFailureCategory.NONE)
        return deleted
    }

    fun schedulerStatus(actorUserId: UUID?): ReservationWebhookScheduleStatus {
        val now = PersistenceInstant.now(clock)
        val state = requiredScheduleStateRepository().getOrCreate(WEBHOOK_PROCESSING_SCHEDULE_ID, now)
        audit(
            ReservationSyncOperationsAuditAction.HISTORY_INSPECTED,
            pmsProviderRegistry.activeProviderId(),
            null,
            "webhook_schedule_status",
            now,
            actorUserId = actorUserId
        )
        val effectiveEnabled = scheduleProperties.enabled &&
            properties.enabled &&
            properties.processingEnabled &&
            !state.paused &&
            scheduleProfileAllowed()
        return ReservationWebhookScheduleStatus(
            scheduleId = WEBHOOK_PROCESSING_SCHEDULE_ID,
            configuredEnabled = scheduleProperties.enabled,
            effectiveEnabled = effectiveEnabled,
            paused = state.paused,
            scheduleSummary = "every ${scheduleProperties.executionInterval}; batch ${scheduleProperties.batchSize}; max ${scheduleProperties.maxRecordsPerExecution}",
            batchSize = scheduleProperties.batchSize,
            maxRecordsPerExecution = scheduleProperties.maxRecordsPerExecution,
            lastAttemptedAt = state.lastAttemptedAt,
            lastSuccessfulAt = state.lastSuccessfulAt,
            nextExpectedExecutionAt = state.lastAttemptedAt?.plus(scheduleProperties.executionInterval),
            lastProcessedCount = state.lastProcessedCount,
            lastFailureCategory = state.lastFailureCategory,
            leaseState = scheduleLeaseStatusRepository
                ?.state(WEBHOOK_PROCESSING_JOB_NAME, now)
                ?: ReservationSyncScheduleLeaseState.HELD_OR_UNKNOWN,
            backlogCounts = inboxRepository.backlogCounts()
        )
    }

    fun pauseScheduler(actorUserId: UUID?): ReservationWebhookScheduleStatus {
        val now = PersistenceInstant.now(clock)
        requiredScheduleStateRepository().markPaused(WEBHOOK_PROCESSING_SCHEDULE_ID, now)
        audit(ReservationSyncOperationsAuditAction.WEBHOOK_SCHEDULER_PAUSED, pmsProviderRegistry.activeProviderId(), null, "paused", now, actorUserId = actorUserId)
        recordSchedulerMetric("operator", "paused", PmsFailureCategory.NONE)
        return schedulerStatus(actorUserId)
    }

    fun resumeScheduler(actorUserId: UUID?): ReservationWebhookScheduleStatus {
        val now = PersistenceInstant.now(clock)
        requiredScheduleStateRepository().markResumed(WEBHOOK_PROCESSING_SCHEDULE_ID, now)
        audit(ReservationSyncOperationsAuditAction.WEBHOOK_SCHEDULER_RESUMED, pmsProviderRegistry.activeProviderId(), null, "resumed", now, actorUserId = actorUserId)
        recordSchedulerMetric("operator", "resumed", PmsFailureCategory.NONE)
        return schedulerStatus(actorUserId)
    }

    private fun process(record: ReservationWebhookInboxRecord): ReservationWebhookStatus {
        val now = PersistenceInstant.now(clock)
        audit(ReservationSyncOperationsAuditAction.WEBHOOK_PROCESSING_STARTED, record.providerId, null, record.eventCategory.name.lowercase(), now)
        val timer = observability.startTimer()
        try {
            if (record.eventCategory == ReservationWebhookEventCategory.HEALTHCHECK || record.eventCategory == ReservationWebhookEventCategory.UNSUPPORTED) {
                inboxRepository.save(record.copy(status = ReservationWebhookStatus.IGNORED, completedAt = now, updatedAt = now))
                return ReservationWebhookStatus.IGNORED
            }
            val affectedDate = record.providerEventTimestamp?.atZone(ZoneOffset.UTC)?.toLocalDate() ?: record.receivedAt.atZone(ZoneOffset.UTC).toLocalDate()
            val run = syncOperationsService.runWebhookSync(
                ReservationSyncOperationRequest(
                    startDate = affectedDate.minusDays(1),
                    endDate = affectedDate.plusDays(2),
                    triggerType = ReservationSyncTriggerType.WEBHOOK
                )
            )
            val saved = inboxRepository.save(
                record.copy(
                    status = if (run.status == ReservationSyncRunStatus.FAILED || run.status == ReservationSyncRunStatus.REJECTED) {
                        ReservationWebhookStatus.FAILED
                    } else {
                        ReservationWebhookStatus.SUCCEEDED
                    },
                    failureCategory = run.failureCategory,
                    attemptCount = record.attemptCount + 1,
                    syncRunId = run.id,
                    completedAt = PersistenceInstant.now(clock),
                    updatedAt = PersistenceInstant.now(clock)
                ).withRetryIfNeeded()
            )
            audit(ReservationSyncOperationsAuditAction.WEBHOOK_PROCESSING_COMPLETED, record.providerId, run.id.value, run.status.name.lowercase(), PersistenceInstant.now(clock), run.failureCategory)
            recordProcessingMetric(record.providerId, record.eventCategory, run.status.name.lowercase(), run.failureCategory ?: PmsFailureCategory.NONE)
            return saved.status
        } catch (exception: RuntimeException) {
            val failed = record.copy(
                status = ReservationWebhookStatus.FAILED,
                failureCategory = PmsFailureCategory.UNKNOWN,
                attemptCount = record.attemptCount + 1,
                completedAt = PersistenceInstant.now(clock),
                updatedAt = PersistenceInstant.now(clock)
            ).withRetryIfNeeded()
            inboxRepository.save(failed)
            audit(ReservationSyncOperationsAuditAction.WEBHOOK_PROCESSING_COMPLETED, record.providerId, null, "failed", PersistenceInstant.now(clock), PmsFailureCategory.UNKNOWN)
            recordProcessingMetric(record.providerId, record.eventCategory, "failed", PmsFailureCategory.UNKNOWN)
            return failed.status
        } finally {
            observability.stopTimer(
                timer,
                "hotelopai.reservation.webhook.processing.duration",
                "provider" to record.providerId,
                "event_category" to record.eventCategory.name.lowercase(),
                "outcome" to "completed"
            )
        }
    }

    private fun ReservationWebhookInboxRecord.withRetryIfNeeded(): ReservationWebhookInboxRecord =
        if (status == ReservationWebhookStatus.FAILED) {
            if (attemptCount < properties.maxAttempts) {
                copy(nextAttemptAt = PersistenceInstant.now(clock).plus(backoff(attemptCount)))
            } else {
                audit(
                    ReservationSyncOperationsAuditAction.WEBHOOK_DEAD_LETTER_CREATED,
                    providerId,
                    syncRunId?.value,
                    "exhausted",
                    PersistenceInstant.now(clock),
                    failureCategory
                )
                copy(status = ReservationWebhookStatus.DEAD_LETTER, nextAttemptAt = null)
            }
        } else {
            copy(nextAttemptAt = null)
        }

    private fun backoff(attemptCount: Int): java.time.Duration {
        val multiplier = 1L shl attemptCount.coerceIn(0, 8)
        val delay = properties.initialBackoff.multipliedBy(multiplier)
        return if (delay > properties.maxBackoff) properties.maxBackoff else delay
    }

    private fun audit(
        action: ReservationSyncOperationsAuditAction,
        providerId: String,
        runId: UUID?,
        outcome: String,
        occurredAt: Instant,
        failureCategory: PmsFailureCategory? = null,
        actorUserId: UUID? = null
    ) {
        auditSink.record(ReservationSyncOperationsAuditEvent(actorUserId, providerId, runId, action, outcome, occurredAt, failureCategory))
    }

    private fun recordRequestMetric(providerId: String, category: String, outcome: String, failureCategory: PmsFailureCategory) {
        observability.incrementCounter(
            "hotelopai.reservation.webhook.requests.total",
            "provider" to providerId,
            "event_category" to category,
            "outcome" to outcome,
            "failure_category" to failureCategory.name.lowercase()
        )
    }

    private fun recordProcessingMetric(providerId: String, category: ReservationWebhookEventCategory, outcome: String, failureCategory: PmsFailureCategory) {
        observability.incrementCounter(
            "hotelopai.reservation.webhook.processing.total",
            "provider" to providerId,
            "event_category" to category.name.lowercase(),
            "outcome" to outcome,
            "failure_category" to failureCategory.name.lowercase()
        )
    }

    private fun validateActivation() {
        if (!properties.enabled) {
            throw ReservationWebhookRejectedException("Reservation webhook ingestion must be enabled before processing is enabled.")
        }
        if (!properties.processingEnabled) {
            throw ReservationWebhookRejectedException("Reservation webhook processing is disabled.")
        }
        if (!scheduleProfileAllowed()) {
            throw ReservationWebhookRejectedException("Reservation webhook processing is not allowed for the active profiles.")
        }
        val provider = pmsProviderRegistry.activeProvider()
        if (!provider.capabilities.supports(PmsCapability.WEBHOOKS)) {
            throw ReservationWebhookRejectedException("Active PMS provider does not support webhook handling.")
        }
        val adapter = adaptersByProvider[pmsProviderRegistry.activeProviderId()]
            ?: throw ReservationWebhookRejectedException("Active PMS provider has no webhook adapter.")
        adapter.validateConfiguration()?.let {
            throw ReservationWebhookRejectedException("Reservation webhook adapter is not configured: ${it.reasonCode}.")
        }
    }

    private fun recordSchedulerAttempt(
        summary: ReservationWebhookProcessingSummary,
        trigger: String,
        failureCategory: PmsFailureCategory? = null
    ) {
        requiredScheduleStateRepository().recordProcessingAttempt(
            WEBHOOK_PROCESSING_SCHEDULE_ID,
            summary.processedCount,
            success = failureCategory == null,
            now = PersistenceInstant.now(clock),
            failureCategory = failureCategory
        )
        recordSchedulerMetric(trigger, "processed", failureCategory ?: PmsFailureCategory.NONE)
        val backlog = inboxRepository.backlogCounts()
        observability.setGauge(
            "hotelopai.reservation.webhook.backlog.eligible",
            backlog.eligibleCount,
            "provider" to pmsProviderRegistry.activeProviderId()
        )
        observability.setGauge(
            "hotelopai.reservation.webhook.dead_letter.count",
            backlog.deadLetterCount,
            "provider" to pmsProviderRegistry.activeProviderId()
        )
    }

    private fun requiredScheduleStateRepository(): ReservationSyncScheduleStateRepository =
        scheduleStateRepository
            ?: throw ReservationWebhookRejectedException("Reservation webhook schedule state repository is not configured.")

    private fun scheduleProfileAllowed(): Boolean {
        val allowedProfiles = scheduleProperties.allowedProfiles
        if (allowedProfiles.isEmpty()) return true
        val activeProfiles = environment?.activeProfiles?.toSet().orEmpty()
        return activeProfiles.any { it in allowedProfiles }
    }

    private fun recordSchedulerMetric(trigger: String, outcome: String, failureCategory: PmsFailureCategory) {
        observability.incrementCounter(
            "hotelopai.reservation.webhook.scheduler.total",
            "provider" to pmsProviderRegistry.activeProviderId(),
            "trigger" to trigger,
            "outcome" to outcome,
            "failure_category" to failureCategory.name.lowercase()
        )
    }

    private fun ByteArray.sha256(): String {
        val bytes = java.security.MessageDigest.getInstance("SHA-256").digest(this)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    companion object {
        const val WEBHOOK_PROCESSING_SCHEDULE_ID = "reservation_webhook_processing_default"
        const val WEBHOOK_PROCESSING_JOB_NAME = "reservation_webhook_processing_scheduler"
        const val WEBHOOK_CLEANUP_JOB_NAME = "reservation_webhook_cleanup_scheduler"
    }
}

class ReservationWebhookRejectedException(message: String) : RuntimeException(message)

class ReservationWebhookNotFoundException(id: ReservationWebhookInboxId) : RuntimeException("Reservation webhook event not found: ${id.value}")
