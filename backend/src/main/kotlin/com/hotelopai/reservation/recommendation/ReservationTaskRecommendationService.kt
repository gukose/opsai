package com.hotelopai.reservation.recommendation

import com.hotelopai.observability.OperationalObservability
import com.hotelopai.reservation.application.ReservationSyncScheduleLeaseState
import com.hotelopai.reservation.application.ReservationSyncScheduleLeaseStatusRepository
import com.hotelopai.reservation.application.ReservationRepository
import com.hotelopai.reservation.domain.ReservationId
import com.hotelopai.shared.kernel.PersistenceInstant
import com.hotelopai.task.application.CreateTaskCommand
import com.hotelopai.task.application.TaskApplicationPort
import com.hotelopai.task.domain.TaskSource
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.core.env.Environment
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.temporal.ChronoUnit
import java.util.UUID

@Service
@EnableConfigurationProperties(ReservationTaskRecommendationProperties::class)
class ReservationTaskRecommendationService(
    providers: List<TaskRecommendationProvider>,
    private val providerRegistry: TaskRecommendationProviderRegistry,
    private val recommendationRepository: ReservationTaskRecommendationRepository,
    private val reservationRepository: ReservationRepository,
    private val taskApplicationPort: TaskApplicationPort,
    private val properties: ReservationTaskRecommendationProperties,
    private val clock: Clock,
    private val environment: Environment? = null,
    private val scheduleLeaseStatusRepository: ReservationSyncScheduleLeaseStatusRepository? = null,
    private val auditSink: ReservationTaskRecommendationAuditSink = NoOpReservationTaskRecommendationAuditSink,
    private val observability: OperationalObservability = OperationalObservability.noop()
) {
    @Suppress("unused")
    private val providerCount = providers.size

    init {
        if (properties.enabled) {
            validateEnabled()
        }
        if (properties.schedule.enabled) {
            validateScheduleConfiguration()
        }
    }

    @Transactional
    fun generateBatch(actorUserId: UUID?): RecommendationGenerationSummary {
        if (!properties.enabled || !profileAllowed()) return RecommendationGenerationSummary(0, 0, 0, 0, 0)
        return runGeneration(RecommendationGenerationTrigger.OPERATOR, actorUserId).toSummary()
    }

    @Transactional
    fun runGeneration(trigger: RecommendationGenerationTrigger, actorUserId: UUID?): RecommendationGenerationRun {
        val now = PersistenceInstant.now(clock)
        val providerId = RecommendationProviderId(properties.activeProvider)
        var run = recommendationRepository.saveRun(
            RecommendationGenerationRun(
                trigger = trigger,
                providerId = providerId,
                status = RecommendationGenerationRunStatus.REQUESTED,
                startedAt = now
            )
        )
        if (!properties.enabled || !profileAllowed()) {
            val rejected = run.copy(
                status = RecommendationGenerationRunStatus.REJECTED,
                completedAt = now,
                failureCategory = RecommendationFailureCategory.FEATURE_DISABLED,
                updatedAt = now
            )
            audit("generation_run", "rejected", actorUserId, null, now)
            recordGenerationRunMetric(trigger, rejected.status, RecommendationFailureCategory.FEATURE_DISABLED)
            return recommendationRepository.saveRun(rejected)
        }
        return try {
            validateEnabled()
            run = recommendationRepository.saveRun(run.copy(status = RecommendationGenerationRunStatus.RUNNING, updatedAt = now))
            audit("generation_run", "started", actorUserId, null, now)
            val sourceExecutions = recommendationRepository.claimEligibleAutomationExecutions(
                now,
                batchSize(trigger),
                now.minus(properties.maximumReservationAge)
            )
            val aggregate = generateForSources(sourceExecutions, now)
            val summary = aggregate.summary
            val status = when {
                summary.failed > 0 && summary.generated + summary.duplicates + summary.skipped > 0 ->
                    RecommendationGenerationRunStatus.PARTIALLY_SUCCEEDED
                summary.failed > 0 -> RecommendationGenerationRunStatus.FAILED
                else -> RecommendationGenerationRunStatus.SUCCEEDED
            }
            val failureCategory = if (summary.failed > 0) aggregate.failureCategory else null
            val completed = recommendationRepository.saveRun(
                run.copy(
                    status = status,
                    completedAt = PersistenceInstant.now(clock),
                    candidatesSelected = sourceExecutions.size,
                    candidatesProcessed = summary.processedReservations,
                    recommendationsGenerated = summary.generated,
                    duplicatesPrevented = summary.duplicates,
                    skippedCount = summary.skipped,
                    failedCount = summary.failed,
                    failureCategory = failureCategory,
                    updatedAt = PersistenceInstant.now(clock)
                )
            )
            audit("generation_run", status.name.lowercase(), actorUserId, null, PersistenceInstant.now(clock))
            recordGenerationRunMetric(trigger, status, failureCategory)
            completed
        } catch (exception: RuntimeException) {
            val failed = recommendationRepository.saveRun(
                run.copy(
                    status = RecommendationGenerationRunStatus.FAILED,
                    completedAt = PersistenceInstant.now(clock),
                    failureCategory = classifyFailure(exception),
                    updatedAt = PersistenceInstant.now(clock)
                )
            )
            audit("generation_run", "failed", actorUserId, null, PersistenceInstant.now(clock))
            recordGenerationRunMetric(trigger, failed.status, failed.failureCategory)
            failed
        }
    }

    fun providerStatus(actorUserId: UUID?): List<RecommendationProviderSummary> {
        audit("provider_configuration", "inspected", actorUserId, null, PersistenceInstant.now(clock))
        return providerRegistry.summaries()
    }

    fun generationRuns(filter: RecommendationGenerationRunFilter): RecommendationGenerationRunPage =
        recommendationRepository.findRuns(filter.copy(page = filter.page.coerceAtLeast(0), size = filter.size.coerceIn(1, 100)))

    fun generationRun(id: RecommendationGenerationRunId): RecommendationGenerationRun =
        recommendationRepository.findRun(id)
            ?: throw ReservationTaskRecommendationRejectedException("Reservation task recommendation generation run was not found.")

    fun schedulerStatus(actorUserId: UUID?): RecommendationScheduleStatus {
        val now = PersistenceInstant.now(clock)
        val state = recommendationRepository.getOrCreateScheduleState(SCHEDULE_ID, now)
        audit("scheduler_status", "inspected", actorUserId, null, now)
        val effectiveEnabled = properties.enabled &&
            properties.schedule.enabled &&
            !state.paused &&
            profileAllowed() &&
            scheduleProfileAllowed()
        return RecommendationScheduleStatus(
            scheduleId = SCHEDULE_ID,
            configuredEnabled = properties.schedule.enabled,
            effectiveEnabled = effectiveEnabled,
            paused = state.paused,
            scheduleSummary = "every ${properties.schedule.executionInterval}; batch ${properties.schedule.batchSize}; max ${properties.schedule.maxReservationsPerExecution}",
            batchSize = properties.schedule.batchSize,
            maxReservationsPerExecution = properties.schedule.maxReservationsPerExecution,
            enabledProviderId = RecommendationProviderId(properties.activeProvider),
            lastAttemptedAt = state.lastAttemptedAt,
            lastSuccessfulAt = state.lastSuccessfulAt,
            nextExpectedExecutionAt = state.lastAttemptedAt?.plus(properties.schedule.executionInterval),
            lastProcessedCandidateCount = state.lastProcessedCandidateCount,
            lastGeneratedRecommendationCount = state.lastGeneratedRecommendationCount,
            lastFailureCategory = state.lastFailureCategory,
            leaseState = scheduleLeaseStatusRepository?.state(SCHEDULE_JOB_NAME, now)
                ?: ReservationSyncScheduleLeaseState.HELD_OR_UNKNOWN,
            eligibleCandidateBacklogCount = recommendationRepository.eligibleCandidateBacklogCount(now, now.minus(properties.maximumReservationAge)),
            failedRunCount = recommendationRepository.runCount(setOf(RecommendationGenerationRunStatus.FAILED))
        )
    }

    fun pauseScheduler(actorUserId: UUID?): RecommendationScheduleStatus {
        val now = PersistenceInstant.now(clock)
        recommendationRepository.markSchedulePaused(SCHEDULE_ID, now)
        audit("scheduler_paused", "paused", actorUserId, null, now)
        recordScheduleMetric("operator", "paused", null)
        return schedulerStatus(actorUserId)
    }

    fun resumeScheduler(actorUserId: UUID?): RecommendationScheduleStatus {
        val now = PersistenceInstant.now(clock)
        recommendationRepository.markScheduleResumed(SCHEDULE_ID, now)
        audit("scheduler_resumed", "resumed", actorUserId, null, now)
        recordScheduleMetric("operator", "resumed", null)
        return schedulerStatus(actorUserId)
    }

    fun runScheduleNow(actorUserId: UUID?): RecommendationGenerationRun {
        audit("scheduler_run_now", "requested", actorUserId, null, PersistenceInstant.now(clock))
        val run = runGeneration(RecommendationGenerationTrigger.OPERATOR, actorUserId)
        recordScheduleAttempt(run, "operator", run.failureCategory.takeIf { run.status in setOf(RecommendationGenerationRunStatus.FAILED, RecommendationGenerationRunStatus.REJECTED) })
        return run
    }

    fun processScheduledBatch(): RecommendationGenerationRun {
        if (!properties.schedule.enabled) {
            return disabledScheduleRun()
        }
        val now = PersistenceInstant.now(clock)
        val state = recommendationRepository.getOrCreateScheduleState(SCHEDULE_ID, now)
        if (state.paused) {
            val rejected = disabledScheduleRun(RecommendationFailureCategory.FEATURE_DISABLED)
            recordScheduleAttempt(rejected, "scheduled", RecommendationFailureCategory.FEATURE_DISABLED)
            return rejected
        }
        validateScheduleConfiguration()
        audit("scheduler_execution", "started", null, null, now)
        val run = runGeneration(RecommendationGenerationTrigger.SCHEDULED, null)
        recordScheduleAttempt(run, "scheduled", run.failureCategory.takeIf { run.status in setOf(RecommendationGenerationRunStatus.FAILED, RecommendationGenerationRunStatus.REJECTED) })
        audit("scheduler_execution", run.status.name.lowercase(), null, null, PersistenceInstant.now(clock))
        return run
    }

    fun expireEligible(actorUserId: UUID?): Int {
        val now = PersistenceInstant.now(clock)
        val expired = recommendationRepository.expireEligibleRecommendations(
            now,
            now.minus(properties.maximumReviewAge),
            properties.schedule.cleanupBatchSize
        )
        audit("recommendation_expiration", "expired_$expired", actorUserId, null, now)
        recordScheduleMetric("operator", "expired", null)
        return expired
    }

    fun cleanupRetention(actorUserId: UUID?): Int {
        val now = PersistenceInstant.now(clock)
        val deleted = recommendationRepository.cleanupTerminalRecords(
            runOlderThan = now.minus(properties.retention.completedRunRetention.coerceAtMost(properties.retention.failedRunRetention)),
            recommendationOlderThan = now.minus(properties.retention.rejectedRecommendationRetention),
            appliedOlderThan = now.minus(properties.retention.appliedRecommendationRetention),
            limit = properties.schedule.cleanupBatchSize
        )
        audit("recommendation_cleanup", "deleted_$deleted", actorUserId, null, now)
        recordScheduleMetric("operator", "cleanup", null)
        return deleted
    }

    private fun generateForSources(
        sourceExecutions: List<RecommendationSourceExecution>,
        now: java.time.Instant
    ): RecommendationGenerationAggregate {
        var generated = 0
        var duplicates = 0
        var skipped = 0
        var failed = 0
        var failureCategory: RecommendationFailureCategory? = null
        sourceExecutions.forEach { source ->
            val result = runCatching { generateForSource(source, now) }
                .getOrElse { exception ->
                    val category = classifyFailure(exception)
                    failureCategory = category
                    recordMetric("none", "failed", category)
                    failed += 1
                    return@forEach
                }
            generated += result.generated
            duplicates += result.duplicates
            skipped += result.skipped
        }
        return RecommendationGenerationAggregate(
            summary = RecommendationGenerationSummary(sourceExecutions.size, generated, duplicates, skipped, failed),
            failureCategory = failureCategory
        )
    }

    fun list(filter: RecommendationFilter): RecommendationPage =
        recommendationRepository.find(filter.copy(page = filter.page.coerceAtLeast(0), size = filter.size.coerceIn(1, 100)))

    fun pilotReviewQueue(filter: RecommendationPilotReviewQueueFilter, actorUserId: UUID?): RecommendationPage {
        audit("pilot_review_queue", "inspected", actorUserId, null, PersistenceInstant.now(clock))
        return recommendationRepository.findPilotReviewQueue(filter.copy(page = filter.page.coerceAtLeast(0), size = filter.size.coerceIn(1, 100)), PersistenceInstant.now(clock))
    }

    fun detail(id: RecommendationId): ReservationTaskRecommendation =
        recommendationRepository.find(id) ?: throw ReservationTaskRecommendationNotFoundException(id)

    fun approve(id: RecommendationId, actorUserId: UUID?): ReservationTaskRecommendation =
        transitionReview(id, actorUserId, RecommendationStatus.APPROVED, "approved", null, null)

    fun approve(id: RecommendationId, actorUserId: UUID?, reason: RecommendationDecisionReason, note: String?): ReservationTaskRecommendation =
        transitionReview(id, actorUserId, RecommendationStatus.APPROVED, "approved", reason, note)

    fun reject(id: RecommendationId, actorUserId: UUID?): ReservationTaskRecommendation =
        transitionReview(id, actorUserId, RecommendationStatus.REJECTED, "rejected", null, null)

    fun reject(id: RecommendationId, actorUserId: UUID?, reason: RecommendationDecisionReason, note: String?): ReservationTaskRecommendation =
        transitionReview(id, actorUserId, RecommendationStatus.REJECTED, "rejected", reason, note)

    fun expire(id: RecommendationId, actorUserId: UUID?): ReservationTaskRecommendation =
        transitionReview(id, actorUserId, RecommendationStatus.EXPIRED, "expired", null, null)

    fun expire(id: RecommendationId, actorUserId: UUID?, reason: RecommendationDecisionReason, note: String?): ReservationTaskRecommendation =
        transitionReview(id, actorUserId, RecommendationStatus.EXPIRED, "expired", reason, note)

    fun bulkApprove(request: RecommendationBulkReviewRequest, actorUserId: UUID?): RecommendationBulkReviewResult =
        bulkTransition(request, actorUserId, RecommendationStatus.APPROVED, "approved")

    fun bulkReject(request: RecommendationBulkReviewRequest, actorUserId: UUID?): RecommendationBulkReviewResult =
        bulkTransition(request, actorUserId, RecommendationStatus.REJECTED, "rejected")

    fun bulkExpire(request: RecommendationBulkReviewRequest, actorUserId: UUID?): RecommendationBulkReviewResult =
        bulkTransition(request, actorUserId, RecommendationStatus.EXPIRED, "expired")

    @Transactional
    fun apply(id: RecommendationId, actorUserId: UUID?): ReservationTaskRecommendation {
        val now = PersistenceInstant.now(clock)
        val recommendation = detail(id)
        if (recommendation.status != RecommendationStatus.APPROVED) {
            throw ReservationTaskRecommendationRejectedException("Reservation task recommendation is not approved for application.")
        }
        if (recommendation.appliedTaskId != null) {
            throw ReservationTaskRecommendationRejectedException("Reservation task recommendation was already applied.")
        }
        val task = try {
            taskApplicationPort.createTask(
                CreateTaskCommand(
                    hotelId = requireNotNull(properties.hotelId),
                    intentType = recommendation.intentType,
                    source = TaskSource.IMPORT,
                    title = recommendation.title,
                    description = recommendation.description,
                    roomNumber = null,
                    priority = recommendation.priority,
                    slaDeadline = recommendation.dueAt.let { if (it.isAfter(now)) it else now.plus(1, ChronoUnit.HOURS) }
                ),
                now
            )
        } catch (_: RuntimeException) {
            val failed = recommendationRepository.save(
                recommendation.copy(
                    status = RecommendationStatus.FAILED,
                    failureCategory = RecommendationFailureCategory.TASK_CREATION_FAILED,
                    attemptCount = recommendation.attemptCount + 1,
                    nextAttemptAt = nextAttemptAt(recommendation.attemptCount + 1),
                    updatedAt = now
                )
            )
            recordMetric(recommendation.category.name.lowercase(), "failed", RecommendationFailureCategory.TASK_CREATION_FAILED)
            audit("application", "failed", actorUserId, failed.id.value, now)
            return failed
        }
        val applied = recommendationRepository.save(
            recommendation.copy(
                status = RecommendationStatus.APPLIED,
                appliedTaskId = task.id,
                reviewedBy = actorUserId,
                reviewedAt = now,
                failureCategory = null,
                nextAttemptAt = null,
                updatedAt = now
            )
        )
        recordMetric(applied.category.name.lowercase(), "applied", null)
        audit("application", "applied", actorUserId, applied.id.value, now)
        return applied
    }

    fun retry(id: RecommendationId, actorUserId: UUID?): ReservationTaskRecommendation {
        val current = detail(id)
        if (current.status != RecommendationStatus.FAILED) {
            throw ReservationTaskRecommendationRejectedException("Reservation task recommendation is not eligible for retry.")
        }
        val retried = recommendationRepository.retry(id, PersistenceInstant.now(clock))
        audit("retry", "requested", actorUserId, retried.id.value, PersistenceInstant.now(clock))
        return retried
    }

    private fun bulkTransition(
        request: RecommendationBulkReviewRequest,
        actorUserId: UUID?,
        status: RecommendationStatus,
        outcome: String
    ): RecommendationBulkReviewResult {
        val ids = request.recommendationIds.distinctBy { it.value }.take(properties.pilotReview.maxBulkReviewItems)
        val sanitizedNote = sanitizeDecisionNote(request.note)
        val results = ids.map { id ->
            runCatching { transitionReview(id, actorUserId, status, outcome, request.reason, sanitizedNote) }
                .fold(
                    onSuccess = { RecommendationBulkReviewItemResult(id, outcome, it.status) },
                    onFailure = {
                        RecommendationBulkReviewItemResult(id, "rejected", null, classifyFailure(it))
                    }
                )
        }
        audit("pilot_bulk_review", outcome, actorUserId, null, PersistenceInstant.now(clock))
        return RecommendationBulkReviewResult(results)
    }

    private fun generateForSource(source: RecommendationSourceExecution, now: java.time.Instant): RecommendationGenerationSummary {
        val reservation = reservationRepository.findById(ReservationId(source.reservationId))
            ?: return RecommendationGenerationSummary(1, 0, 0, 1, 0)
        val context = SanitizedReservationRecommendationContext(
            reservationId = reservation.id.value,
            reservationStatus = reservation.reservationStatus.name,
            stayStatus = reservation.stayStatus.name,
            arrivalDate = reservation.stayPeriod.arrival,
            departureDate = reservation.stayPeriod.departure,
            nights = java.time.temporal.ChronoUnit.DAYS.between(reservation.stayPeriod.arrival, reservation.stayPeriod.departure),
            adultOccupancy = reservation.occupancy.adults,
            childOccupancy = reservation.occupancy.children,
            roomAssigned = reservation.roomAssignment != null,
            deterministicTaskCreated = source.taskCreated,
            deterministicAutomationOutcomes = setOf(source.automationOutcome),
            taskBacklogBand = "unknown",
            openTaskCountBand = "unknown",
            overdueTaskCountBand = "unknown",
            unresolvedAutomationFailure = recommendationRepository.unresolvedAutomationFailureExists(source.reservationId),
            activeRecommendationCountBand = countBand(recommendationRepository.activeRecommendationCount(source.reservationId)),
            roomAssignmentCompleteness = if (reservation.roomAssignment == null) "unassigned" else "assigned",
            stayProximityBand = stayProximityBand(reservation.stayPeriod.arrival, now),
            lifecycleChangeRecencyBand = lifecycleChangeRecencyBand(source.createdAt, now),
            propertyCapabilityFlags = setOf("canonical_reservation_snapshot"),
            now = now
        )
        val selectedProvider = providerRegistry.activeProvider()
        val proposals = selectedProvider.recommend(context).take(properties.maxRecommendationsPerReservation)
        if (proposals.isEmpty()) return RecommendationGenerationSummary(1, 0, 0, 1, 0)
        var generated = 0
        var duplicates = 0
        proposals.forEach { proposal ->
            val recommendation = ReservationTaskRecommendation(
                reservationId = source.reservationId,
                source = if (selectedProvider.providerType == RecommendationProviderType.EXTERNAL) {
                    RecommendationSource.EXTERNAL_LLM
                } else {
                    RecommendationSource.INTERNAL_DEMO_AI
                },
                providerName = selectedProvider.providerName,
                modelIdentifier = selectedProvider.modelIdentifier,
                promptVersion = selectedProvider.promptVersion,
                contextSchemaVersion = context.contextSchemaVersion,
                category = proposal.category,
                confidence = proposal.confidence,
                explanation = proposal.explanation,
                intentType = proposal.intentType,
                title = proposal.title,
                description = proposal.description,
                priority = proposal.priority,
                dueAt = proposal.dueAt,
                deduplicationKey = proposal.deduplicationKey,
                status = RecommendationStatus.REVIEW_REQUIRED,
                createdAt = now,
                updatedAt = now,
                expiresAt = now.plus(properties.expiration)
            )
            when (recommendationRepository.insert(recommendation)) {
                is ReservationTaskRecommendationInsertResult.Inserted -> {
                    generated += 1
                    recordMetric(proposal.category.name.lowercase(), "generated", null)
                }
                is ReservationTaskRecommendationInsertResult.Duplicate -> {
                    duplicates += 1
                    recordMetric(proposal.category.name.lowercase(), "duplicate", RecommendationFailureCategory.DUPLICATE)
                }
            }
        }
        return RecommendationGenerationSummary(1, generated, duplicates, 0, 0)
    }

    private fun transitionReview(
        id: RecommendationId,
        actorUserId: UUID?,
        status: RecommendationStatus,
        outcome: String,
        reason: RecommendationDecisionReason?,
        note: String?
    ): ReservationTaskRecommendation {
        val now = PersistenceInstant.now(clock)
        val recommendation = detail(id)
        if (recommendation.status !in setOf(RecommendationStatus.REVIEW_REQUIRED, RecommendationStatus.APPROVED)) {
            throw ReservationTaskRecommendationRejectedException("Reservation task recommendation is not eligible for review transition.")
        }
        if (recommendation.status == RecommendationStatus.APPLIED) {
            throw ReservationTaskRecommendationRejectedException("Applied recommendation cannot be changed.")
        }
        val updated = recommendationRepository.save(
            recommendation.copy(
                status = status,
                reviewedBy = actorUserId,
                reviewedAt = now,
                decisionReason = reason,
                decisionNote = sanitizeDecisionNote(note),
                updatedAt = now
            )
        )
        recordMetric(updated.category.name.lowercase(), outcome, null)
        audit("review", outcome, actorUserId, updated.id.value, now)
        return updated
    }

    private fun sanitizeDecisionNote(note: String?): String? =
        note?.trim()?.takeIf { it.isNotBlank() }?.take(properties.pilotReview.maxDecisionNoteLength)

    private fun validateEnabled() {
        requireNotNull(properties.hotelId) { "reservation task recommendations hotel id must be configured when enabled" }
        if (properties.hotelId == UUID(0L, 0L)) {
            throw ReservationTaskRecommendationRejectedException("Reservation task recommendations hotel id must not be empty.")
        }
        providerRegistry.validateActiveProvider()
        if (!profileAllowed()) {
            throw ReservationTaskRecommendationRejectedException("Reservation task recommendations are not allowed for the active profiles.")
        }
    }

    private fun validateScheduleConfiguration() {
        if (!properties.enabled) {
            throw ReservationTaskRecommendationRejectedException("Reservation task recommendations must be enabled before schedule generation is enabled.")
        }
        validateEnabled()
        providerRegistry.validateScheduleProvider()
        if (!scheduleProfileAllowed()) {
            throw ReservationTaskRecommendationRejectedException("Reservation task recommendation schedule is not allowed for the active profiles.")
        }
    }

    private fun profileAllowed(): Boolean {
        val allowedProfiles = properties.allowedProfiles
        if (allowedProfiles.isEmpty()) return true
        val activeProfiles = environment?.activeProfiles?.toSet().orEmpty()
        return activeProfiles.any { it in allowedProfiles }
    }

    private fun nextAttemptAt(attempt: Int): java.time.Instant? =
        if (attempt >= properties.maxAttempts) null else PersistenceInstant.now(clock).plus(properties.retryDelay)

    private fun batchSize(trigger: RecommendationGenerationTrigger): Int =
        when (trigger) {
            RecommendationGenerationTrigger.OPERATOR -> properties.batchSize.coerceAtMost(properties.maxReservationsPerBatch)
            RecommendationGenerationTrigger.SCHEDULED -> properties.schedule.batchSize
                .coerceAtMost(properties.schedule.maxReservationsPerExecution)
                .coerceAtMost(properties.maxReservationsPerBatch)
        }.coerceIn(1, 100)

    private fun disabledScheduleRun(
        failureCategory: RecommendationFailureCategory = RecommendationFailureCategory.FEATURE_DISABLED
    ): RecommendationGenerationRun {
        val now = PersistenceInstant.now(clock)
        return RecommendationGenerationRun(
            trigger = RecommendationGenerationTrigger.SCHEDULED,
            providerId = RecommendationProviderId(properties.activeProvider),
            status = RecommendationGenerationRunStatus.REJECTED,
            startedAt = now,
            completedAt = now,
            failureCategory = failureCategory
        )
    }

    private fun recordScheduleAttempt(
        run: RecommendationGenerationRun?,
        trigger: String,
        failureCategory: RecommendationFailureCategory?
    ) {
        recommendationRepository.recordScheduleAttempt(SCHEDULE_ID, run, PersistenceInstant.now(clock), failureCategory)
        recordScheduleMetric(trigger, if (failureCategory == null) "processed" else "failed", failureCategory)
    }

    private fun scheduleProfileAllowed(): Boolean {
        val allowedProfiles = properties.schedule.allowedProfiles
        if (allowedProfiles.isEmpty()) return true
        val activeProfiles = environment?.activeProfiles?.toSet().orEmpty()
        return activeProfiles.any { it in allowedProfiles }
    }

    private fun countBand(count: Long): String =
        when {
            count <= 0 -> "none"
            count <= 2 -> "low"
            count <= 5 -> "medium"
            else -> "high"
        }

    private fun stayProximityBand(arrival: java.time.LocalDate, now: java.time.Instant): String {
        val today = now.atZone(properties.timezone).toLocalDate()
        val days = java.time.temporal.ChronoUnit.DAYS.between(today, arrival)
        return when {
            days < 0 -> "past"
            days == 0L -> "same_day"
            days <= 2 -> "near"
            else -> "future"
        }
    }

    private fun lifecycleChangeRecencyBand(changedAt: java.time.Instant, now: java.time.Instant): String {
        val age = java.time.Duration.between(changedAt, now).abs()
        return when {
            age <= java.time.Duration.ofHours(1) -> "recent"
            age <= java.time.Duration.ofDays(1) -> "same_day"
            else -> "older"
        }
    }

    private fun classifyFailure(exception: Throwable): RecommendationFailureCategory =
        when (exception) {
            is RecommendationProviderException -> exception.failureCategory
            is ReservationTaskRecommendationRejectedException -> RecommendationFailureCategory.CONFIGURATION_ERROR
            is IllegalArgumentException -> RecommendationFailureCategory.PERMANENT_VALIDATION_FAILURE
            else -> RecommendationFailureCategory.PROVIDER_FAILURE
        }

    private fun RecommendationGenerationRun.toSummary(): RecommendationGenerationSummary =
        RecommendationGenerationSummary(candidatesProcessed, recommendationsGenerated, duplicatesPrevented, skippedCount, failedCount)

    private fun recordMetric(category: String, outcome: String, failureCategory: RecommendationFailureCategory?) {
        observability.incrementCounter(
            "hotelopai.reservation.task_recommendation.total",
            "provider" to properties.activeProvider,
            "category" to category,
            "outcome" to outcome,
            "confidence_bucket" to "not_recorded",
            "failure_category" to (failureCategory?.name?.lowercase() ?: "none"),
            "context_schema_version" to RECOMMENDATION_CONTEXT_SCHEMA_VERSION
        )
    }

    private fun recordGenerationRunMetric(
        trigger: RecommendationGenerationTrigger,
        status: RecommendationGenerationRunStatus,
        failureCategory: RecommendationFailureCategory?
    ) {
        observability.incrementCounter(
            "hotelopai.reservation.task_recommendation.generation_run.total",
            "provider" to properties.activeProvider,
            "trigger" to trigger.name.lowercase(),
            "outcome" to status.name.lowercase(),
            "failure_category" to (failureCategory?.name?.lowercase() ?: "none"),
            "context_schema_version" to RECOMMENDATION_CONTEXT_SCHEMA_VERSION
        )
    }

    private fun recordScheduleMetric(trigger: String, outcome: String, failureCategory: RecommendationFailureCategory?) {
        observability.incrementCounter(
            "hotelopai.reservation.task_recommendation.scheduler.total",
            "provider" to properties.activeProvider,
            "trigger" to trigger,
            "outcome" to outcome,
            "failure_category" to (failureCategory?.name?.lowercase() ?: "none"),
            "context_schema_version" to RECOMMENDATION_CONTEXT_SCHEMA_VERSION
        )
    }

    private fun audit(action: String, outcome: String, actorUserId: UUID?, recommendationId: UUID?, now: java.time.Instant) {
        auditSink.record(ReservationTaskRecommendationAuditEvent(actorUserId, recommendationId, action, outcome, now))
    }

    companion object {
        const val SCHEDULE_ID = "reservation_task_recommendation_default"
        const val SCHEDULE_JOB_NAME = "reservation_task_recommendation_scheduler"
        const val CLEANUP_JOB_NAME = "reservation_task_recommendation_cleanup"
    }
}

private data class RecommendationGenerationAggregate(
    val summary: RecommendationGenerationSummary,
    val failureCategory: RecommendationFailureCategory?
)

data class ReservationTaskRecommendationAuditEvent(
    val actorUserId: UUID?,
    val recommendationId: UUID?,
    val action: String,
    val outcome: String,
    val occurredAt: java.time.Instant
)

interface ReservationTaskRecommendationAuditSink {
    fun record(event: ReservationTaskRecommendationAuditEvent)
}

object NoOpReservationTaskRecommendationAuditSink : ReservationTaskRecommendationAuditSink {
    override fun record(event: ReservationTaskRecommendationAuditEvent) = Unit
}

class ReservationTaskRecommendationNotFoundException(id: RecommendationId) :
    RuntimeException("Reservation task recommendation not found: ${id.value}")

class ReservationTaskRecommendationRejectedException(message: String) : RuntimeException(message)
