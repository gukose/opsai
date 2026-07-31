package com.hotelopai.reservation.recommendation

import com.hotelopai.observability.OperationalObservability
import com.hotelopai.reservation.application.ReservationSyncScheduleLeaseStatusRepository
import com.hotelopai.reservation.application.ReservationRepository
import com.hotelopai.reservation.domain.Reservation
import com.hotelopai.reservation.domain.ReservationId
import com.hotelopai.shared.kernel.PersistenceInstant
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.core.env.Environment
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.temporal.ChronoUnit
import java.util.UUID

@Service
@EnableConfigurationProperties(ReservationTaskRecommendationProperties::class)
class ExternalRecommendationPilotService(
    private val providerRegistry: TaskRecommendationProviderRegistry,
    private val smokeService: ExternalRecommendationProviderSmokeService,
    private val recommendationRepository: ReservationTaskRecommendationRepository,
    private val reservationRepository: ReservationRepository,
    private val pilotRepository: RecommendationPilotRepository,
    private val leaseStatusRepository: ReservationSyncScheduleLeaseStatusRepository,
    private val properties: ReservationTaskRecommendationProperties,
    private val clock: Clock,
    private val environment: Environment? = null,
    private val auditSink: ReservationTaskRecommendationAuditSink = NoOpReservationTaskRecommendationAuditSink,
    private val observability: OperationalObservability = OperationalObservability.noop()
) {
    init {
        validateEnabledConfiguration()
    }

    fun readiness(providerId: RecommendationProviderId = RecommendationProviderId(defaultProviderId()), actorUserId: UUID?): RecommendationPilotReadiness {
        audit("pilot_readiness", "inspected", actorUserId)
        val readiness = readinessInternal(providerId)
        recordMetric("readiness", "inspected", readiness.status.name.lowercase(), null)
        return readiness
    }

    @Transactional
    fun runPilot(providerId: RecommendationProviderId = RecommendationProviderId(defaultProviderId()), actorUserId: UUID?): RecommendationPilotRunSummary {
        return runPilot(providerId, RecommendationPilotTrigger.OPERATOR, properties.pilot.maxReservationsPerRun, actorUserId)
    }

    @Transactional
    fun runScheduledPilot(): RecommendationPilotRunSummary? {
        val now = PersistenceInstant.now(clock)
        val providerId = RecommendationProviderId(defaultProviderId())
        val state = pilotRepository.getOrCreateState(PILOT_STATE_ID, now)
        if (!properties.pilotSchedule.enabled || state.schedulePaused) {
            recordMetric("scheduled_run", "scheduled", "skipped", RecommendationFailureCategory.FEATURE_DISABLED)
            return null
        }
        if (!profileAllowed(properties.pilotSchedule.allowedProfiles) || productionProfile()) {
            val rejected = saveRejectedRun(providerId, RecommendationPilotTrigger.SCHEDULED, RecommendationFailureCategory.CONFIGURATION_ERROR, now)
            pilotRepository.recordScheduleAttempt(PILOT_STATE_ID, rejected, 0, now)
            audit("pilot_scheduled_run", "rejected", null)
            return RecommendationPilotRunSummary(rejected, readinessInternal(providerId))
        }
        if (state.lastScheduleAttemptedAt != null &&
            state.lastScheduleAttemptedAt.plus(properties.pilotSchedule.minimumIntervalBetweenRuns).isAfter(now)
        ) {
            recordMetric("scheduled_run", "scheduled", "skipped", RecommendationFailureCategory.RATE_LIMIT)
            return null
        }
        val budgetDate = now.atZone(properties.timezone).toLocalDate()
        if (pilotRepository.scheduledRunCount(providerId, budgetDate) >= properties.pilotSchedule.maxRunsPerDay) {
            val rejected = saveRejectedRun(providerId, RecommendationPilotTrigger.SCHEDULED, RecommendationFailureCategory.RATE_LIMIT, now)
            pilotRepository.recordScheduleAttempt(PILOT_STATE_ID, rejected, 1, now)
            audit("pilot_scheduled_run", "daily_limit_reached", null)
            recordMetric("scheduled_run", "scheduled", "daily_limit_reached", RecommendationFailureCategory.RATE_LIMIT)
            return RecommendationPilotRunSummary(rejected, readinessInternal(providerId))
        }
        val summary = runPilot(
            providerId = providerId,
            trigger = RecommendationPilotTrigger.SCHEDULED,
            candidateLimit = properties.pilotSchedule.batchSize.coerceAtMost(properties.pilot.maxReservationsPerRun),
            actorUserId = null
        )
        pilotRepository.recordScheduleAttempt(
            PILOT_STATE_ID,
            summary.run,
            if (summary.run.status == RecommendationPilotRunStatus.BUDGET_EXHAUSTED) 1 else 0,
            PersistenceInstant.now(clock)
        )
        return summary
    }

    @Transactional
    fun runPilotScheduleNow(actorUserId: UUID?): RecommendationPilotRunSummary {
        audit("pilot_schedule_run_now", "requested", actorUserId)
        return runPilot(
            providerId = RecommendationProviderId(defaultProviderId()),
            trigger = RecommendationPilotTrigger.OPERATOR,
            candidateLimit = properties.pilotSchedule.batchSize.coerceAtMost(properties.pilot.maxReservationsPerRun),
            actorUserId = actorUserId
        )
    }

    @Transactional
    fun runPilot(
        providerId: RecommendationProviderId,
        trigger: RecommendationPilotTrigger,
        candidateLimit: Int,
        actorUserId: UUID?
    ): RecommendationPilotRunSummary {
        audit("pilot_run", "requested", actorUserId)
        val now = PersistenceInstant.now(clock)
        val provider = providerRegistry.provider(providerId)
            ?: throw ReservationTaskRecommendationRejectedException("Recommendation pilot provider is not registered.")
        var run = pilotRepository.saveRun(
            RecommendationPilotRun(
                providerId = providerId,
                trigger = trigger,
                status = RecommendationPilotRunStatus.REQUESTED,
                startedAt = now,
                modelIdentifier = provider.modelIdentifier,
                promptVersion = provider.promptVersion
            )
        )
        val readiness = readinessInternal(providerId)
        if (readiness.status != RecommendationPilotReadinessStatus.READY) {
            val rejected = run.copy(
                status = RecommendationPilotRunStatus.REJECTED,
                completedAt = now,
                failureCategory = readiness.failureCategory(),
                updatedAt = now
            )
            audit("pilot_run", "rejected", actorUserId)
            recordMetric("pilot_run", "rejected", rejected.status.name.lowercase(), rejected.failureCategory)
            return RecommendationPilotRunSummary(pilotRepository.saveRun(rejected), readiness)
        }
        run = pilotRepository.saveRun(run.copy(status = RecommendationPilotRunStatus.RUNNING, updatedAt = now))
        val sources = selectCandidates(now, candidateLimit)
        var processed = 0
        var calls = 0
        var generated = 0
        var duplicates = 0
        var skipped = 0
        var failed = 0
        var requestBudgetUsed = 0
        var recommendationBudgetUsed = 0
        var failureCategory: RecommendationFailureCategory? = null
        val budgetDate = now.atZone(properties.timezone).toLocalDate()
        candidateLoop@ for (source in sources) {
            if (generated >= properties.pilot.maxRecommendationsPerRun) break
            val budget = budgetStatus(providerId)
            if (budget.exhausted) {
                failureCategory = RecommendationFailureCategory.RATE_LIMIT
                break
            }
            if (!pilotRepository.reserveRequest(providerId, budgetDate, properties.pilot.dailyRequestBudget, properties.pilot.dailyTokenBudget, 0, PersistenceInstant.now(clock))) {
                failureCategory = RecommendationFailureCategory.RATE_LIMIT
                break
            }
            requestBudgetUsed += 1
            calls += 1
            processed += 1
            val result = runCatching { generateForSource(provider, source, run.id, PersistenceInstant.now(clock)) }
                .getOrElse { exception ->
                    val category = classifyFailure(exception)
                    failureCategory = category
                    failed += 1
                    pilotRepository.releaseFailedRequest(providerId, budgetDate, PersistenceInstant.now(clock))
                    requestBudgetUsed = (requestBudgetUsed - 1).coerceAtLeast(0)
                    recordMetric("provider_call", "failed", "failed", category)
                    continue@candidateLoop
                }
            generated += result.generated
            duplicates += result.duplicates
            skipped += result.skipped
            recommendationBudgetUsed += result.generated
            pilotRepository.recordGeneratedRecommendations(providerId, budgetDate, result.generated, PersistenceInstant.now(clock))
            recordMetric("provider_call", "completed", "succeeded", null)
            if (budgetStatus(providerId).recommendationUsed >= properties.pilot.maxRecommendationsPerRun ||
                generated >= properties.pilot.maxRecommendationsPerRun
            ) {
                failureCategory = RecommendationFailureCategory.RATE_LIMIT
                break
            }
        }
        val completedAt = PersistenceInstant.now(clock)
        val status = when {
            failureCategory == RecommendationFailureCategory.RATE_LIMIT && generated == 0 && failed == 0 ->
                RecommendationPilotRunStatus.BUDGET_EXHAUSTED
            failureCategory == RecommendationFailureCategory.RATE_LIMIT ->
                RecommendationPilotRunStatus.PARTIALLY_SUCCEEDED
            failed > 0 && generated + duplicates + skipped > 0 ->
                RecommendationPilotRunStatus.PARTIALLY_SUCCEEDED
            failed > 0 -> RecommendationPilotRunStatus.FAILED
            else -> RecommendationPilotRunStatus.SUCCEEDED
        }
        val completed = pilotRepository.saveRun(
            run.copy(
                status = status,
                completedAt = completedAt,
                candidatesSelected = sources.size,
                candidatesProcessed = processed,
                providerCalls = calls,
                recommendationsGenerated = generated,
                duplicatesPrevented = duplicates,
                skippedCount = skipped,
                failedCount = failed,
                requestBudgetUsed = requestBudgetUsed,
                recommendationBudgetUsed = recommendationBudgetUsed,
                tokenBudgetUsed = 0,
                failureCategory = failureCategory,
                updatedAt = completedAt
            )
        )
        audit("pilot_run", completed.status.name.lowercase(), actorUserId)
        recordMetric("pilot_run", "completed", completed.status.name.lowercase(), completed.failureCategory)
        return RecommendationPilotRunSummary(completed, readinessInternal(providerId))
    }

    fun runs(filter: RecommendationPilotRunFilter, actorUserId: UUID?): RecommendationPilotRunPage {
        audit("pilot_runs", "inspected", actorUserId)
        return pilotRepository.findRuns(filter.copy(page = filter.page.coerceAtLeast(0), size = filter.size.coerceIn(1, 100)))
    }

    fun run(id: RecommendationPilotRunId, actorUserId: UUID?): RecommendationPilotRun =
        pilotRepository.findRun(id)?.also { audit("pilot_run", "inspected", actorUserId) }
            ?: throw ReservationTaskRecommendationRejectedException("Recommendation pilot run was not found.")

    fun budgetStatus(providerId: RecommendationProviderId = RecommendationProviderId(defaultProviderId()), actorUserId: UUID? = null): RecommendationPilotBudgetStatus {
        actorUserId?.let { audit("pilot_budget", "inspected", it) }
        return budgetStatus(providerId)
    }

    @Transactional
    fun disableFutureRuns(actorUserId: UUID?): RecommendationPilotState {
        val state = pilotRepository.disable(PILOT_STATE_ID, PersistenceInstant.now(clock))
        audit("pilot_disabled", "disabled", actorUserId)
        recordMetric("pilot_control", "disabled", "disabled", null)
        return state
    }

    @Transactional
    fun rollbackToInternalDemo(actorUserId: UUID?): RecommendationPilotState {
        val state = pilotRepository.rollback(PILOT_STATE_ID, PersistenceInstant.now(clock))
        audit("pilot_rollback", "internal_demo", actorUserId)
        recordMetric("pilot_control", "rollback", "internal_demo", null)
        return state
    }

    fun expirePilotRecommendations(actorUserId: UUID?): Int {
        val expired = recommendationRepository.expirePilotRecommendations(PersistenceInstant.now(clock), properties.pilot.maxRecommendationsPerRun)
        audit("pilot_recommendations", "expired_$expired", actorUserId)
        return expired
    }

    fun scheduleStatus(actorUserId: UUID?): RecommendationPilotScheduleStatus {
        audit("pilot_schedule", "inspected", actorUserId)
        val now = PersistenceInstant.now(clock)
        val providerId = RecommendationProviderId(defaultProviderId())
        val state = pilotRepository.getOrCreateState(PILOT_STATE_ID, now)
        val readiness = readinessInternal(providerId)
        val dailyLimitReached = pilotRepository.scheduledRunCount(providerId, now.atZone(properties.timezone).toLocalDate()) >=
            properties.pilotSchedule.maxRunsPerDay
        val effective = properties.pilotSchedule.enabled &&
            !state.schedulePaused &&
            readiness.status == RecommendationPilotReadinessStatus.READY &&
            !dailyLimitReached
        recordMetric("schedule_status", "operator", "inspected", null)
        return RecommendationPilotScheduleStatus(
            scheduleId = PILOT_SCHEDULE_JOB_NAME,
            configuredEnabled = properties.pilotSchedule.enabled,
            effectiveEnabled = effective,
            paused = state.schedulePaused,
            scheduleSummary = "fixed_delay:${properties.pilotSchedule.executionInterval}",
            providerId = providerId,
            batchSize = properties.pilotSchedule.batchSize,
            maxRunsPerDay = properties.pilotSchedule.maxRunsPerDay,
            lastAttemptedAt = state.lastScheduleAttemptedAt,
            lastSuccessfulAt = state.lastScheduleSuccessfulAt,
            nextExpectedExecutionAt = state.lastScheduleAttemptedAt?.plus(properties.pilotSchedule.executionInterval),
            lastRunOutcome = state.lastScheduleOutcome,
            lastSelectedCandidateCount = state.lastSelectedCandidateCount,
            lastGeneratedRecommendationCount = state.lastGeneratedRecommendationCount,
            lastBudgetRejectionCount = state.lastBudgetRejectionCount,
            lastFailureCategory = state.lastScheduleFailureCategory,
            leaseState = leaseStatusRepository.state(PILOT_SCHEDULE_JOB_NAME, now),
            dailyRunLimitReached = dailyLimitReached,
            readiness = readiness
        )
    }

    @Transactional
    fun pauseSchedule(actorUserId: UUID?): RecommendationPilotScheduleStatus {
        pilotRepository.pauseSchedule(PILOT_STATE_ID, PersistenceInstant.now(clock))
        audit("pilot_schedule", "paused", actorUserId)
        recordMetric("pilot_schedule", "operator", "paused", null)
        return scheduleStatus(actorUserId)
    }

    @Transactional
    fun resumeSchedule(actorUserId: UUID?): RecommendationPilotScheduleStatus {
        pilotRepository.resumeSchedule(PILOT_STATE_ID, PersistenceInstant.now(clock))
        audit("pilot_schedule", "resumed", actorUserId)
        recordMetric("pilot_schedule", "operator", "resumed", null)
        return scheduleStatus(actorUserId)
    }

    fun analytics(filter: RecommendationPilotAnalyticsFilter, actorUserId: UUID?): RecommendationPilotAnalytics {
        audit("pilot_analytics", "inspected", actorUserId)
        recordMetric("pilot_analytics", "operator", "inspected", null)
        return pilotRepository.analytics(filter, PersistenceInstant.now(clock))
    }

    @Transactional
    fun cleanupPilotRetention(actorUserId: UUID?): Int {
        val now = PersistenceInstant.now(clock)
        val deletedRuns = pilotRepository.cleanupPilotRuns(now.minus(properties.retention.completedRunRetention), properties.pilotSchedule.cleanupBatchSize)
        val expiredRecommendations = if (properties.pilotSchedule.retentionCleanupEnabled) {
            recommendationRepository.expirePilotRecommendations(now, properties.pilotSchedule.cleanupBatchSize)
        } else {
            0
        }
        val total = deletedRuns + expiredRecommendations
        audit("pilot_cleanup", "deleted_$total", actorUserId)
        recordMetric("pilot_cleanup", "operator", "completed", null)
        return total
    }

    private fun readinessInternal(providerId: RecommendationProviderId): RecommendationPilotReadiness {
        val now = PersistenceInstant.now(clock)
        val pilot = properties.pilot
        val state = pilotRepository.getOrCreateState(PILOT_STATE_ID, now)
        val providerReadiness = smokeService.readiness(providerId, null)
        val blocking = mutableListOf<String>()
        if (!pilot.enabled) blocking += "pilot_disabled"
        if (state.disabled) blocking += "pilot_disabled_by_operator"
        if (productionProfile()) blocking += "production_blocked"
        if (!profileAllowed(pilot.allowedProfiles)) blocking += "profile_not_allowed"
        if (providerId.value !in pilot.allowedProviderIds) blocking += "provider_not_allowed"
        if (!providerReadinessSatisfies(providerReadiness.readiness, pilot.minimumProviderReadiness)) {
            blocking += "provider_readiness_too_low"
        }
        if (providerReadiness.lastSuccessfulSmokeAt == null) {
            blocking += "successful_smoke_missing"
        } else if (providerReadiness.lastSuccessfulSmokeAt.isBefore(now.minus(pilot.requiredSuccessfulSmokeAge))) {
            blocking += "successful_smoke_expired"
        }
        if (!withinPilotWindow(now)) blocking += "outside_pilot_window"
        if (!pilot.mandatoryOperatorApprovalMode) blocking += "operator_approval_required"
        if (pilot.allowedPropertyScopes.isEmpty()) blocking += "property_scope_allowlist_empty"
        val budget = budgetStatus(providerId)
        if (budget.exhausted) blocking += "budget_exhausted"
        val status = when {
            !pilot.enabled || state.disabled -> RecommendationPilotReadinessStatus.DISABLED
            blocking.isEmpty() -> RecommendationPilotReadinessStatus.READY
            else -> RecommendationPilotReadinessStatus.BLOCKED
        }
        return RecommendationPilotReadiness(
            status = status,
            providerId = providerId,
            providerReadiness = providerReadiness.readiness,
            activeProvider = providerReadiness.active,
            allowedProfile = profileAllowed(pilot.allowedProfiles),
            productionBlocked = productionProfile(),
            smokeFresh = "successful_smoke_missing" !in blocking && "successful_smoke_expired" !in blocking,
            withinPilotWindow = withinPilotWindow(now),
            approvalModeRequired = pilot.mandatoryOperatorApprovalMode,
            budgetAvailable = !budget.exhausted,
            blockingReasons = blocking.sorted(),
            modelIdentifierPresent = providerReadiness.activeModel != null,
            promptVersion = providerReadiness.promptVersion,
            budget = budget
        )
    }

    private fun validateEnabledConfiguration() {
        val pilot = properties.pilot
        if (!pilot.enabled) {
            if (properties.pilotSchedule.enabled) {
                throw ReservationTaskRecommendationRejectedException("External recommendation pilot must be enabled before pilot scheduling is enabled.")
            }
            return
        }
        if (productionProfile()) {
            throw ReservationTaskRecommendationRejectedException("External recommendation pilot is blocked in production.")
        }
        if (!profileAllowed(pilot.allowedProfiles)) {
            throw ReservationTaskRecommendationRejectedException("External recommendation pilot is not allowed for the active profile.")
        }
        if (pilot.allowedPropertyScopes.isEmpty()) {
            throw ReservationTaskRecommendationRejectedException("External recommendation pilot property scope allowlist must be configured when enabled.")
        }
        if (!pilot.mandatoryOperatorApprovalMode) {
            throw ReservationTaskRecommendationRejectedException("External recommendation pilot requires mandatory operator approval mode.")
        }
        pilot.allowedProviderIds.forEach { providerId ->
            val provider = providerRegistry.provider(RecommendationProviderId(providerId))
                ?: throw ReservationTaskRecommendationRejectedException("External recommendation pilot provider is not registered.")
            if (provider.providerType != RecommendationProviderType.EXTERNAL) {
                throw ReservationTaskRecommendationRejectedException("External recommendation pilot supports only external recommendation providers.")
            }
        }
        if (properties.pilotSchedule.enabled && !profileAllowed(properties.pilotSchedule.allowedProfiles)) {
            throw ReservationTaskRecommendationRejectedException("External recommendation pilot schedule is not allowed for the active profile.")
        }
    }

    private fun generateForSource(
        provider: TaskRecommendationProvider,
        source: RecommendationSourceExecution,
        pilotRunId: RecommendationPilotRunId,
        now: java.time.Instant
    ): RecommendationGenerationSummary {
        val reservation = reservationRepository.findById(ReservationId(source.reservationId))
            ?: return RecommendationGenerationSummary(1, 0, 0, 1, 0)
        val remaining = properties.pilot.maxRecommendationsPerRun - budgetStatus(provider.id).recommendationUsed
        if (remaining <= 0) return RecommendationGenerationSummary(1, 0, 0, 0, 0)
        val proposals = provider.recommend(context(reservation, source, now))
            .take(properties.maxRecommendationsPerReservation)
            .take(remaining)
        if (proposals.isEmpty()) return RecommendationGenerationSummary(1, 0, 0, 1, 0)
        var generated = 0
        var duplicates = 0
        proposals.forEach { proposal ->
            val recommendation = ReservationTaskRecommendation(
                reservationId = source.reservationId,
                source = RecommendationSource.EXTERNAL_LLM,
                providerName = provider.providerName,
                modelIdentifier = provider.modelIdentifier,
                promptVersion = provider.promptVersion,
                contextSchemaVersion = RECOMMENDATION_CONTEXT_SCHEMA_VERSION,
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
                pilotRunId = pilotRunId,
                createdAt = now,
                updatedAt = now,
                expiresAt = now.plus(properties.expiration)
            )
            when (recommendationRepository.insert(recommendation)) {
                is ReservationTaskRecommendationInsertResult.Inserted -> generated += 1
                is ReservationTaskRecommendationInsertResult.Duplicate -> duplicates += 1
            }
        }
        return RecommendationGenerationSummary(1, generated, duplicates, 0, 0)
    }

    private fun selectCandidates(now: java.time.Instant, candidateLimit: Int): List<RecommendationSourceExecution> =
        recommendationRepository.claimEligibleAutomationExecutions(
            now,
            candidateLimit.coerceIn(1, properties.pilot.maxReservationsPerRun),
            now.minus(properties.pilot.maximumCandidateAge)
        ).filter { source ->
            val reservation = reservationRepository.findById(ReservationId(source.reservationId))
            reservation != null &&
                reservation.propertyId.value in properties.pilot.allowedPropertyScopes &&
                reservation.reservationStatus.name in SUPPORTED_RESERVATION_STATES
        }.take(candidateLimit.coerceIn(1, properties.pilot.maxReservationsPerRun))

    private fun context(reservation: Reservation, source: RecommendationSourceExecution, now: java.time.Instant): SanitizedReservationRecommendationContext =
        SanitizedReservationRecommendationContext(
            reservationId = reservation.id.value,
            reservationStatus = reservation.reservationStatus.name,
            stayStatus = reservation.stayStatus.name,
            arrivalDate = reservation.stayPeriod.arrival,
            departureDate = reservation.stayPeriod.departure,
            nights = ChronoUnit.DAYS.between(reservation.stayPeriod.arrival, reservation.stayPeriod.departure),
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
            propertyCapabilityFlags = setOf("canonical_reservation_snapshot", "pilot_generation"),
            now = now
        )

    private fun budgetStatus(providerId: RecommendationProviderId): RecommendationPilotBudgetStatus =
        pilotRepository.budgetStatus(
            providerId = providerId,
            budgetDate = PersistenceInstant.now(clock).atZone(properties.timezone).toLocalDate(),
            requestLimit = properties.pilot.dailyRequestBudget,
            recommendationLimit = properties.pilot.maxRecommendationsPerRun,
            tokenLimit = properties.pilot.dailyTokenBudget
        )

    private fun withinPilotWindow(now: java.time.Instant): Boolean {
        val today = now.atZone(properties.timezone).toLocalDate()
        val start = properties.pilotSchedule.pilotStartDateOverride ?: properties.pilot.pilotStartDate
        val end = properties.pilotSchedule.pilotEndDateOverride ?: properties.pilot.pilotEndDate
        val startOk = start?.let { !today.isBefore(it) } ?: true
        val endOk = end?.let { !today.isAfter(it) } ?: true
        return startOk && endOk
    }

    private fun saveRejectedRun(
        providerId: RecommendationProviderId,
        trigger: RecommendationPilotTrigger,
        failureCategory: RecommendationFailureCategory,
        now: java.time.Instant
    ): RecommendationPilotRun {
        val provider = providerRegistry.provider(providerId)
        return pilotRepository.saveRun(
            RecommendationPilotRun(
                providerId = providerId,
                trigger = trigger,
                status = RecommendationPilotRunStatus.REJECTED,
                startedAt = now,
                completedAt = now,
                modelIdentifier = provider?.modelIdentifier,
                promptVersion = provider?.promptVersion ?: properties.promptVersion,
                failureCategory = failureCategory
            )
        )
    }

    private fun profileAllowed(allowedProfiles: List<String>): Boolean =
        allowedProfiles.isEmpty() || activeProfiles().any { it in allowedProfiles }

    private fun productionProfile(): Boolean =
        activeProfiles().any { it == "prod" || it == "production" }

    private fun activeProfiles(): Set<String> =
        environment?.activeProfiles?.map { it.lowercase() }?.toSet().orEmpty()

    private fun defaultProviderId(): String =
        properties.pilot.allowedProviderIds.firstOrNull() ?: TaskRecommendationProviderRegistry.OPENAI_PROVIDER_ID

    private fun countBand(count: Long): String =
        when {
            count <= 0 -> "none"
            count <= 2 -> "low"
            count <= 5 -> "medium"
            else -> "high"
        }

    private fun stayProximityBand(arrival: java.time.LocalDate, now: java.time.Instant): String {
        val days = ChronoUnit.DAYS.between(now.atZone(properties.timezone).toLocalDate(), arrival)
        return when {
            days < 0 -> "past"
            days == 0L -> "today"
            days <= 2 -> "near"
            days <= 7 -> "upcoming"
            else -> "future"
        }
    }

    private fun lifecycleChangeRecencyBand(changedAt: java.time.Instant, now: java.time.Instant): String =
        when {
            changedAt.isAfter(now.minus(java.time.Duration.ofHours(1))) -> "recent"
            changedAt.isAfter(now.minus(java.time.Duration.ofDays(1))) -> "today"
            changedAt.isAfter(now.minus(java.time.Duration.ofDays(7))) -> "week"
            else -> "older"
        }

    private fun classifyFailure(exception: Throwable): RecommendationFailureCategory =
        when (exception) {
            is RecommendationProviderException -> exception.failureCategory
            is ReservationTaskRecommendationRejectedException -> RecommendationFailureCategory.VALIDATION
            else -> RecommendationFailureCategory.PROVIDER_FAILURE
        }

    private fun providerReadinessSatisfies(
        actual: RecommendationProviderReadinessStatus,
        minimum: RecommendationProviderReadinessStatus
    ): Boolean =
        when (minimum) {
            RecommendationProviderReadinessStatus.READY_FOR_NON_PRODUCTION ->
                actual == RecommendationProviderReadinessStatus.READY_FOR_NON_PRODUCTION
            RecommendationProviderReadinessStatus.READY_FOR_LOCAL_SMOKE ->
                actual in setOf(
                    RecommendationProviderReadinessStatus.READY_FOR_LOCAL_SMOKE,
                    RecommendationProviderReadinessStatus.READY_FOR_NON_PRODUCTION
                )
            else -> actual == minimum
        }

    private fun RecommendationPilotReadiness.failureCategory(): RecommendationFailureCategory =
        when {
            "budget_exhausted" in blockingReasons -> RecommendationFailureCategory.RATE_LIMIT
            "provider_readiness_too_low" in blockingReasons -> RecommendationFailureCategory.PROVIDER_UNAVAILABLE
            else -> RecommendationFailureCategory.CONFIGURATION_ERROR
        }

    private fun recordMetric(operation: String, trigger: String, outcome: String, failureCategory: RecommendationFailureCategory?) {
        observability.incrementCounter(
            "hotelopai.reservation.task_recommendation.pilot.total",
            "provider" to defaultProviderId(),
            "operation" to operation,
            "trigger" to trigger,
            "outcome" to outcome,
            "failure_category" to (failureCategory?.name?.lowercase() ?: "none")
        )
    }

    private fun audit(action: String, outcome: String, actorUserId: UUID?) {
        auditSink.record(ReservationTaskRecommendationAuditEvent(actorUserId, null, action, outcome, PersistenceInstant.now(clock)))
    }

    companion object {
        const val PILOT_STATE_ID = "external_recommendation_pilot"
        const val PILOT_SCHEDULE_JOB_NAME = "reservation-task-recommendation-pilot"
        const val PILOT_CLEANUP_JOB_NAME = "reservation-task-recommendation-pilot-cleanup"
        private val SUPPORTED_RESERVATION_STATES = setOf("CONFIRMED", "PENDING", "CANCELLED", "NO_SHOW")
    }
}
