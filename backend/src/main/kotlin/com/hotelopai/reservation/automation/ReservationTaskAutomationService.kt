package com.hotelopai.reservation.automation

import com.fasterxml.jackson.databind.ObjectMapper
import com.hotelopai.observability.OperationalObservability
import com.hotelopai.outbox.domain.OperationalOutboxEvent
import com.hotelopai.reservation.application.ReservationSyncScheduleLeaseState
import com.hotelopai.reservation.application.ReservationSyncScheduleLeaseStatusRepository
import com.hotelopai.reservation.application.ReservationOutboxPayload
import com.hotelopai.reservation.application.ReservationRepository
import com.hotelopai.reservation.domain.ReservationId
import com.hotelopai.shared.kernel.PersistenceInstant
import com.hotelopai.task.application.CreateTaskCommand
import com.hotelopai.task.application.TaskApplicationPort
import com.hotelopai.task.domain.TaskSource
import com.hotelopai.housekeeping.application.CreateHousekeepingCommand
import com.hotelopai.housekeeping.application.HousekeepingService
import com.hotelopai.housekeeping.domain.HousekeepingWorkflowType
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.core.env.Environment
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID

@Service
@EnableConfigurationProperties(ReservationTaskAutomationProperties::class)
class ReservationTaskAutomationService(
    rules: List<ReservationTaskAutomationRule>,
    private val repository: ReservationTaskAutomationRepository,
    private val reservationRepository: ReservationRepository,
    private val taskApplicationPort: TaskApplicationPort,
    private val housekeepingService: HousekeepingService? = null,
    private val objectMapper: ObjectMapper,
    private val properties: ReservationTaskAutomationProperties,
    private val clock: Clock,
    private val environment: Environment? = null,
    private val scheduleStateRepository: ReservationTaskAutomationScheduleStateRepository? = null,
    private val scheduleLeaseStatusRepository: ReservationSyncScheduleLeaseStatusRepository? = null,
    private val auditSink: ReservationTaskAutomationAuditSink = NoOpReservationTaskAutomationAuditSink,
    private val observability: OperationalObservability = OperationalObservability.noop()
) {
    private val rulesById = rules.associateBy { it.id.value }
    private val orderedRules = rules.sortedBy { it.id.value }
    private val dueDatePolicy = ReservationTaskAutomationDueDatePolicy(properties, clock)

    init {
        require(rulesById.size == rules.size) { "Duplicate reservation task automation rule ids are not allowed." }
        if (properties.enabled) {
            validateEnabledConfiguration()
        }
        if (properties.schedule.enabled) {
            validateScheduleConfiguration()
        }
    }

    fun registeredRules(): List<ReservationTaskAutomationRuleDescriptor> =
        orderedRules.map {
            ReservationTaskAutomationRuleDescriptor(
                ruleId = it.id,
                version = it.version,
                supportedEventTypes = it.supportedEventTypes.toSortedSet(),
                enabled = properties.enabled && properties.ruleEnabled(it.id)
            )
        }

    @Transactional
    fun processBatch(actorUserId: UUID? = null, batchSizeOverride: Int? = null): ReservationTaskAutomationBatchSummary {
        if (!properties.enabled || !profileAllowed()) {
            return ReservationTaskAutomationBatchSummary(0, 0, 0, 0, 0, 0, 0)
        }
        validateEnabledConfiguration()
        val now = PersistenceInstant.now(clock)
        auditSink.record(ReservationTaskAutomationAuditEvent(actorUserId, "process_batch", "started", now))
        val batchSize = (batchSizeOverride ?: properties.batchSize).coerceIn(1, properties.batchSize)
        val claimed = repository.claimReservationEvents(now, batchSize, properties.processorId)
        var rulesEvaluated = 0
        var tasksCreated = 0
        var alreadyExists = 0
        var skipped = 0
        var failed = 0
        var deadLetter = 0
        claimed.forEach { event ->
            val result = processEvent(event)
            rulesEvaluated += result.rulesEvaluated
            tasksCreated += result.tasksCreated
            alreadyExists += result.alreadyExists
            skipped += result.skipped
            failed += result.failed
            deadLetter += result.deadLetter
        }
        recordBacklog()
        auditSink.record(ReservationTaskAutomationAuditEvent(actorUserId, "process_batch", "processed_${claimed.size}", PersistenceInstant.now(clock)))
        return ReservationTaskAutomationBatchSummary(claimed.size, rulesEvaluated, tasksCreated, alreadyExists, skipped, failed, deadLetter)
    }

    fun processOperatorBatch(actorUserId: UUID?): ReservationTaskAutomationBatchSummary {
        auditSink.record(ReservationTaskAutomationAuditEvent(actorUserId, "operator_run_now", "requested", PersistenceInstant.now(clock)))
        val summary = processBatch(actorUserId, properties.schedule.maxRecordsPerExecution.coerceAtMost(properties.schedule.batchSize))
        recordScheduleAttempt(summary, "operator")
        return summary
    }

    fun processScheduledBatch(): ReservationTaskAutomationBatchSummary {
        if (!properties.schedule.enabled) {
            return ReservationTaskAutomationBatchSummary(0, 0, 0, 0, 0, 0, 0)
        }
        val now = PersistenceInstant.now(clock)
        val state = requiredScheduleStateRepository().getOrCreate(SCHEDULE_ID, now)
        if (state.paused) {
            val summary = ReservationTaskAutomationBatchSummary(0, 0, 0, 0, 0, 0, 0)
            recordScheduleAttempt(summary, "scheduled", ReservationTaskAutomationSkipReason.AUTOMATION_DISABLED)
            return summary
        }
        validateScheduleConfiguration()
        auditSink.record(ReservationTaskAutomationAuditEvent(null, "scheduler_execution", "started", now))
        return try {
            val summary = processBatch(null, properties.schedule.maxRecordsPerExecution.coerceAtMost(properties.schedule.batchSize))
            recordScheduleAttempt(summary, "scheduled")
            auditSink.record(ReservationTaskAutomationAuditEvent(null, "scheduler_execution", "processed_${summary.processedEvents}", PersistenceInstant.now(clock)))
            summary
        } catch (exception: RuntimeException) {
            recordScheduleAttempt(null, "scheduled", ReservationTaskAutomationSkipReason.TRANSIENT_FAILURE)
            throw exception
        }
    }

    fun schedulerStatus(actorUserId: UUID?): ReservationTaskAutomationScheduleStatus {
        val now = PersistenceInstant.now(clock)
        val state = requiredScheduleStateRepository().getOrCreate(SCHEDULE_ID, now)
        auditSink.record(ReservationTaskAutomationAuditEvent(actorUserId, "scheduler_status", "inspected", now))
        val enabledRules = registeredRules().count { it.enabled }
        val effectiveEnabled = properties.enabled &&
            properties.schedule.enabled &&
            !state.paused &&
            profileAllowed() &&
            scheduleProfileAllowed()
        return ReservationTaskAutomationScheduleStatus(
            scheduleId = SCHEDULE_ID,
            configuredEnabled = properties.schedule.enabled,
            effectiveEnabled = effectiveEnabled,
            paused = state.paused,
            scheduleSummary = "every ${properties.schedule.executionInterval}; batch ${properties.schedule.batchSize}; max ${properties.schedule.maxRecordsPerExecution}",
            batchSize = properties.schedule.batchSize,
            maxRecordsPerExecution = properties.schedule.maxRecordsPerExecution,
            enabledRuleCount = enabledRules,
            lastAttemptedAt = state.lastAttemptedAt,
            lastSuccessfulAt = state.lastSuccessfulAt,
            nextExpectedExecutionAt = state.lastAttemptedAt?.plus(properties.schedule.executionInterval),
            lastProcessedCount = state.lastProcessedCount,
            lastCreatedTaskCount = state.lastCreatedTaskCount,
            lastFailureCategory = state.lastFailureCategory,
            leaseState = scheduleLeaseStatusRepository
                ?.state(SCHEDULE_JOB_NAME, now)
                ?: ReservationSyncScheduleLeaseState.HELD_OR_UNKNOWN,
            eligibleBacklogCount = backlogCount(),
            failedExecutionCount = repository.executionCount(setOf(ReservationTaskAutomationOutcome.FAILED)),
            deadLetterExecutionCount = repository.executionCount(setOf(ReservationTaskAutomationOutcome.DEAD_LETTER))
        )
    }

    fun pauseScheduler(actorUserId: UUID?): ReservationTaskAutomationScheduleStatus {
        val now = PersistenceInstant.now(clock)
        requiredScheduleStateRepository().markPaused(SCHEDULE_ID, now)
        auditSink.record(ReservationTaskAutomationAuditEvent(actorUserId, "scheduler_paused", "paused", now))
        recordScheduleMetric("operator", "paused", null)
        return schedulerStatus(actorUserId)
    }

    fun resumeScheduler(actorUserId: UUID?): ReservationTaskAutomationScheduleStatus {
        val now = PersistenceInstant.now(clock)
        requiredScheduleStateRepository().markResumed(SCHEDULE_ID, now)
        auditSink.record(ReservationTaskAutomationAuditEvent(actorUserId, "scheduler_resumed", "resumed", now))
        recordScheduleMetric("operator", "resumed", null)
        return schedulerStatus(actorUserId)
    }

    fun history(filter: ReservationTaskAutomationExecutionFilter): ReservationTaskAutomationExecutionPage =
        repository.findExecutions(filter.copy(page = filter.page.coerceAtLeast(0), size = filter.size.coerceIn(1, 100)))

    fun execution(id: ReservationTaskAutomationExecutionId): ReservationTaskAutomationExecution =
        repository.findExecution(id) ?: throw ReservationTaskAutomationNotFoundException(id)

    fun retryExecution(id: ReservationTaskAutomationExecutionId, actorUserId: UUID?): ReservationTaskAutomationExecution {
        val current = execution(id)
        if (current.outcome !in setOf(ReservationTaskAutomationOutcome.FAILED, ReservationTaskAutomationOutcome.DEAD_LETTER)) {
            throw ReservationTaskAutomationRejectedException("Reservation task automation execution is not eligible for retry.")
        }
        val now = PersistenceInstant.now(clock)
        repository.markOutboxRetryable(current.outboxEventId, current.attemptCount, now, "operator_retry", now)
        val retried = repository.retryExecution(id, now)
        auditSink.record(ReservationTaskAutomationAuditEvent(actorUserId, "retry_execution", "requested", now))
        return retried
    }

    fun backlogCount(): Long =
        repository.backlogCount(PersistenceInstant.now(clock))

    private fun processEvent(event: OperationalOutboxEvent): EventProcessingCounters {
        val now = PersistenceInstant.now(clock)
        val payload = parsePayload(event)
        if (payload == null) {
            markOutboxFailure(event, ReservationTaskAutomationSkipReason.INVALID_CONFIGURATION)
            return EventProcessingCounters(failed = 1)
        }
        val reservation = reservationRepository.findById(ReservationId(payload.reservationId))
        if (reservation == null) {
            recordSyntheticExecution(event, payload, ReservationTaskAutomationOutcome.SKIPPED, ReservationTaskAutomationSkipReason.RESERVATION_NOT_FOUND)
            repository.markOutboxCompleted(event.id, now)
            recordMetric("none", event.eventType, "skipped", ReservationTaskAutomationSkipReason.RESERVATION_NOT_FOUND)
            return EventProcessingCounters(skipped = 1)
        }
        val context = ReservationTaskAutomationContext(
            outboxEvent = event,
            payload = payload,
            reservation = reservation,
            occurredAt = runCatching { Instant.parse(payload.occurredAt) }.getOrDefault(now),
            now = now,
            dueDatePolicy = dueDatePolicy
        )
        var counters = EventProcessingCounters()
        val candidateRules = orderedRules.filter { event.eventType in it.supportedEventTypes }
        candidateRules.forEach { rule ->
            if (!properties.ruleEnabled(rule.id)) {
                counters += recordNotApplicable(event, payload, rule, ReservationTaskAutomationSkipReason.RULE_DISABLED)
                return@forEach
            }
            counters = counters.copy(rulesEvaluated = counters.rulesEvaluated + 1)
            val proposals = runCatching { rule.evaluate(context) }
                .getOrElse {
                    counters += recordNotApplicable(event, payload, rule, ReservationTaskAutomationSkipReason.RULE_FAILURE, failed = true)
                    return@forEach
                }
            if (proposals.isEmpty()) {
                counters += recordNotApplicable(event, payload, rule, ReservationTaskAutomationSkipReason.NOT_APPLICABLE)
            } else {
                proposals.forEach { proposal ->
                    counters += createTaskForProposal(event, payload, proposal)
                }
            }
        }
        if (candidateRules.isEmpty()) {
            recordSyntheticExecution(event, payload, ReservationTaskAutomationOutcome.SKIPPED, ReservationTaskAutomationSkipReason.UNSUPPORTED_EVENT)
            counters = counters.copy(skipped = counters.skipped + 1)
        }
        repository.markOutboxCompleted(event.id, PersistenceInstant.now(clock))
        return counters
    }

    private fun createTaskForProposal(
        event: OperationalOutboxEvent,
        payload: ReservationOutboxPayload,
        proposal: ReservationTaskProposal
    ): EventProcessingCounters {
        val now = PersistenceInstant.now(clock)
        val pending = ReservationTaskAutomationExecution(
            outboxEventId = event.id,
            reservationId = payload.reservationId,
            ruleId = proposal.ruleId,
            ruleVersion = proposal.ruleVersion,
            triggerEventType = event.eventType,
            deduplicationKey = proposal.deduplicationKey,
            outcome = ReservationTaskAutomationOutcome.SKIPPED,
            skipReason = ReservationTaskAutomationSkipReason.DUPLICATE,
            createdAt = now,
            updatedAt = now
        )
        return when (val inserted = repository.insertExecution(pending)) {
            is ReservationTaskAutomationInsertResult.Duplicate -> {
                recordMetric(proposal.ruleId.value, event.eventType, "already_exists", ReservationTaskAutomationSkipReason.DUPLICATE)
                EventProcessingCounters(alreadyExists = 1)
            }
            is ReservationTaskAutomationInsertResult.Inserted -> {
                try {
                    val housekeepingType = proposal.safeMetadata["housekeeping_type"]?.let(HousekeepingWorkflowType::valueOf)
                    val createdTaskId = if (housekeepingType != null) {
                        requireNotNull(housekeepingService) { "Housekeeping automation requires HousekeepingService" }.create(CreateHousekeepingCommand(
                            hotelId = requireNotNull(properties.hotelId),
                            roomNumber = requireNotNull(proposal.roomNumber),
                            type = housekeepingType,
                            inspectionRequired = proposal.safeMetadata["inspection_required"].toBoolean(),
                            idempotencyKey = proposal.deduplicationKey
                        )).taskId
                    } else taskApplicationPort.createTask(
                        CreateTaskCommand(
                            hotelId = requireNotNull(properties.hotelId),
                            intentType = proposal.intentType,
                            source = TaskSource.IMPORT,
                            title = proposal.title,
                            description = proposal.description,
                            roomNumber = proposal.roomNumber,
                            priority = proposal.priority,
                            slaDeadline = proposal.dueAt.ensureAfter(now)
                        ),
                        now
                    ).id
                    repository.saveExecution(
                        inserted.execution.copy(
                            outcome = ReservationTaskAutomationOutcome.CREATED,
                            createdTaskId = createdTaskId,
                            skipReason = null,
                            completedAt = PersistenceInstant.now(clock),
                            updatedAt = PersistenceInstant.now(clock)
                        )
                    )
                    recordMetric(proposal.ruleId.value, event.eventType, "created", null)
                    EventProcessingCounters(tasksCreated = 1)
                } catch (_: RuntimeException) {
                    val failed = inserted.execution.copy(
                        outcome = ReservationTaskAutomationOutcome.FAILED,
                        failureCategory = ReservationTaskAutomationSkipReason.TASK_CREATION_FAILED,
                        skipReason = ReservationTaskAutomationSkipReason.TASK_CREATION_FAILED,
                        attemptCount = event.attemptCount + 1,
                        nextAttemptAt = retryAt(event.attemptCount + 1),
                        completedAt = PersistenceInstant.now(clock),
                        updatedAt = PersistenceInstant.now(clock)
                    ).terminalIfExhausted()
                    repository.saveExecution(failed)
                    recordMetric(proposal.ruleId.value, event.eventType, failed.outcome.name.lowercase(), ReservationTaskAutomationSkipReason.TASK_CREATION_FAILED)
                    EventProcessingCounters(failed = if (failed.outcome == ReservationTaskAutomationOutcome.FAILED) 1 else 0, deadLetter = if (failed.outcome == ReservationTaskAutomationOutcome.DEAD_LETTER) 1 else 0)
                }
            }
        }
    }

    private fun recordNotApplicable(
        event: OperationalOutboxEvent,
        payload: ReservationOutboxPayload,
        rule: ReservationTaskAutomationRule,
        reason: ReservationTaskAutomationSkipReason,
        failed: Boolean = false
    ): EventProcessingCounters {
        val now = PersistenceInstant.now(clock)
        val outcome = if (failed) ReservationTaskAutomationOutcome.FAILED else ReservationTaskAutomationOutcome.NOT_APPLICABLE
        repository.insertExecution(
            ReservationTaskAutomationExecution(
                outboxEventId = event.id,
                reservationId = payload.reservationId,
                ruleId = rule.id,
                ruleVersion = rule.version,
                triggerEventType = event.eventType,
                deduplicationKey = "${rule.id.value}:${rule.version}:${payload.reservationId}:${event.eventType}:${payload.occurredAt}:not_applicable",
                outcome = outcome,
                failureCategory = if (failed) reason else null,
                skipReason = reason,
                completedAt = now,
                createdAt = now,
                updatedAt = now
            )
        )
        recordMetric(rule.id.value, event.eventType, outcome.name.lowercase(), reason)
        return if (failed) EventProcessingCounters(failed = 1) else EventProcessingCounters(skipped = 1)
    }

    private fun recordSyntheticExecution(
        event: OperationalOutboxEvent,
        payload: ReservationOutboxPayload,
        outcome: ReservationTaskAutomationOutcome,
        reason: ReservationTaskAutomationSkipReason
    ) {
        val now = PersistenceInstant.now(clock)
        repository.insertExecution(
            ReservationTaskAutomationExecution(
                outboxEventId = event.id,
                reservationId = payload.reservationId,
                ruleId = ReservationTaskAutomationRuleId("system"),
                ruleVersion = 1,
                triggerEventType = event.eventType,
                deduplicationKey = "system:1:${payload.reservationId}:${event.eventType}:${payload.occurredAt}:${reason.name.lowercase()}",
                outcome = outcome,
                skipReason = reason,
                completedAt = now,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    private fun markOutboxFailure(event: OperationalOutboxEvent, reason: ReservationTaskAutomationSkipReason) {
        val now = PersistenceInstant.now(clock)
        val attempt = event.attemptCount + 1
        if (attempt >= properties.maxAttempts) {
            repository.markOutboxFailed(event.id, attempt, reason.name.lowercase(), now)
        } else {
            repository.markOutboxRetryable(event.id, attempt, now.plus(properties.retryDelay), reason.name.lowercase(), now)
        }
    }

    private fun parsePayload(event: OperationalOutboxEvent): ReservationOutboxPayload? =
        runCatching { objectMapper.readValue(event.payloadJson, ReservationOutboxPayload::class.java) }
            .getOrNull()
            ?.takeIf { it.payloadVersion == ReservationOutboxPayload.VERSION }

    private fun ReservationTaskAutomationExecution.terminalIfExhausted(): ReservationTaskAutomationExecution =
        if (attemptCount >= properties.maxAttempts) {
            copy(outcome = ReservationTaskAutomationOutcome.DEAD_LETTER, nextAttemptAt = null)
        } else {
            this
        }

    private fun retryAt(attemptCount: Int): Instant? =
        if (attemptCount >= properties.maxAttempts) null else PersistenceInstant.now(clock).plus(properties.retryDelay)

    private fun Instant.ensureAfter(now: Instant): Instant =
        if (isAfter(now)) this else now.plus(Duration.ofHours(1))

    private fun validateEnabledConfiguration() {
        requireNotNull(properties.hotelId) { "reservation task automation hotel id must be configured when enabled" }
        if (properties.hotelId == UUID(0L, 0L)) {
            throw ReservationTaskAutomationRejectedException("Reservation task automation hotel id must not be empty.")
        }
        if (!profileAllowed()) {
            throw ReservationTaskAutomationRejectedException("Reservation task automation is not allowed for the active profiles.")
        }
        val unknownRuleIds = properties.enabledRuleIds.filterNot { it in rulesById.keys }
        if (unknownRuleIds.isNotEmpty()) {
            throw ReservationTaskAutomationRejectedException("Reservation task automation references unknown enabled rule ids.")
        }
        val unknownPolicyRuleIds = properties.rules.keys.filterNot { it in rulesById.keys }
        if (unknownPolicyRuleIds.isNotEmpty()) {
            throw ReservationTaskAutomationRejectedException("Reservation task automation references unknown rule policy ids.")
        }
        if (orderedRules.none { properties.ruleEnabled(it.id) }) {
            throw ReservationTaskAutomationRejectedException("Reservation task automation requires at least one enabled rule.")
        }
    }

    private fun validateScheduleConfiguration() {
        if (!properties.enabled) {
            throw ReservationTaskAutomationRejectedException("Reservation task automation must be enabled before schedule processing is enabled.")
        }
        validateEnabledConfiguration()
        if (!scheduleProfileAllowed()) {
            throw ReservationTaskAutomationRejectedException("Reservation task automation schedule is not allowed for the active profiles.")
        }
    }

    private fun profileAllowed(): Boolean {
        val allowedProfiles = properties.allowedProfiles
        if (allowedProfiles.isEmpty()) return true
        val activeProfiles = environment?.activeProfiles?.toSet().orEmpty()
        return activeProfiles.any { it in allowedProfiles }
    }

    private fun scheduleProfileAllowed(): Boolean {
        val allowedProfiles = properties.schedule.allowedProfiles
        if (allowedProfiles.isEmpty()) return true
        val activeProfiles = environment?.activeProfiles?.toSet().orEmpty()
        return activeProfiles.any { it in allowedProfiles }
    }

    private fun recordMetric(ruleId: String, eventType: String, outcome: String, reason: ReservationTaskAutomationSkipReason?) {
        observability.incrementCounter(
            "hotelopai.reservation.task_automation.total",
            "rule_id" to ruleId,
            "event_type" to eventType.lowercase(),
            "outcome" to outcome,
            "failure_category" to (reason?.name?.lowercase() ?: "none")
        )
    }

    private fun recordBacklog() {
        observability.setGauge(
            "hotelopai.reservation.task_automation.backlog",
            backlogCount(),
            "status" to "eligible"
        )
    }

    private fun recordScheduleAttempt(
        summary: ReservationTaskAutomationBatchSummary?,
        trigger: String,
        failureCategory: ReservationTaskAutomationSkipReason? = null
    ) {
        requiredScheduleStateRepository().recordAttempt(SCHEDULE_ID, summary, PersistenceInstant.now(clock), failureCategory)
        recordScheduleMetric(trigger, if (failureCategory == null) "processed" else "failed", failureCategory)
        observability.setGauge(
            "hotelopai.reservation.task_automation.dead_letter.count",
            repository.executionCount(setOf(ReservationTaskAutomationOutcome.DEAD_LETTER)),
            "status" to "dead_letter"
        )
    }

    private fun recordScheduleMetric(trigger: String, outcome: String, reason: ReservationTaskAutomationSkipReason?) {
        observability.incrementCounter(
            "hotelopai.reservation.task_automation.scheduler.total",
            "trigger" to trigger,
            "outcome" to outcome,
            "failure_category" to (reason?.name?.lowercase() ?: "none")
        )
    }

    private fun requiredScheduleStateRepository(): ReservationTaskAutomationScheduleStateRepository =
        scheduleStateRepository
            ?: throw ReservationTaskAutomationRejectedException("Reservation task automation schedule state repository is not configured.")

    private data class EventProcessingCounters(
        val rulesEvaluated: Int = 0,
        val tasksCreated: Int = 0,
        val alreadyExists: Int = 0,
        val skipped: Int = 0,
        val failed: Int = 0,
        val deadLetter: Int = 0
    ) {
        operator fun plus(other: EventProcessingCounters): EventProcessingCounters =
            EventProcessingCounters(
                rulesEvaluated + other.rulesEvaluated,
                tasksCreated + other.tasksCreated,
                alreadyExists + other.alreadyExists,
                skipped + other.skipped,
                failed + other.failed,
                deadLetter + other.deadLetter
            )
    }

    companion object {
        const val SCHEDULE_ID = "reservation_task_automation_default"
        const val SCHEDULE_JOB_NAME = "reservation_task_automation_scheduler"
    }
}

data class ReservationTaskAutomationAuditEvent(
    val actorUserId: UUID?,
    val action: String,
    val outcome: String,
    val occurredAt: Instant
)

interface ReservationTaskAutomationAuditSink {
    fun record(event: ReservationTaskAutomationAuditEvent)
}

object NoOpReservationTaskAutomationAuditSink : ReservationTaskAutomationAuditSink {
    override fun record(event: ReservationTaskAutomationAuditEvent) = Unit
}

class ReservationTaskAutomationNotFoundException(id: ReservationTaskAutomationExecutionId) :
    RuntimeException("Reservation task automation execution not found: ${id.value}")

class ReservationTaskAutomationRejectedException(message: String) : RuntimeException(message)
