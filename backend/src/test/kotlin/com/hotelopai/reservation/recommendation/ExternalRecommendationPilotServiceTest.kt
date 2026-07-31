package com.hotelopai.reservation.recommendation

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.hotelopai.integration.openai.recommendation.OpenAiRecommendationProvider
import com.hotelopai.reservation.application.ReservationSyncScheduleLeaseState
import com.hotelopai.reservation.application.ReservationSyncScheduleLeaseStatusRepository
import com.hotelopai.reservation.domain.DateRange
import com.hotelopai.reservation.domain.ExternalReservationReference
import com.hotelopai.reservation.domain.Guest
import com.hotelopai.reservation.domain.GuestId
import com.hotelopai.reservation.domain.Occupancy
import com.hotelopai.reservation.domain.PropertyId
import com.hotelopai.reservation.domain.Reservation
import com.hotelopai.reservation.domain.ReservationId
import com.hotelopai.reservation.domain.ReservationStatus
import com.hotelopai.reservation.domain.StayStatus
import com.hotelopai.reservation.infrastructure.InMemoryReservationRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.mock.env.MockEnvironment
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlin.math.ceil

class ExternalRecommendationPilotServiceTest {
    private val clock = Clock.fixed(Instant.parse("2026-07-31T10:00:00Z"), ZoneId.of("UTC"))
    private val actorId = UUID.fromString("00000000-0000-0000-0000-000000001401")

    @Test
    fun `pilot is disabled by default`() {
        val fixture = fixture(properties = baseProperties(pilot = RecommendationPilotProperties()))

        val readiness = fixture.pilotService.readiness(RecommendationProviderId("openai"), actorId)

        assertThat(readiness.status).isEqualTo(RecommendationPilotReadinessStatus.DISABLED)
        assertThat(readiness.blockingReasons).contains("pilot_disabled")
    }

    @Test
    fun `pilot readiness blocks missing successful smoke`() {
        val fixture = fixture()

        val readiness = fixture.pilotService.readiness(RecommendationProviderId("openai"), actorId)

        assertThat(readiness.status).isEqualTo(RecommendationPilotReadinessStatus.BLOCKED)
        assertThat(readiness.blockingReasons).contains("successful_smoke_missing")
        assertThat(readiness.toString()).doesNotContain("Ada", "RES-", "OPENAI_API_KEY", "test-token")
    }

    @Test
    fun `enabled pilot is rejected in production profile`() {
        assertThatThrownBy { fixture(activeProfile = "prod") }
            .isInstanceOf(ReservationTaskRecommendationRejectedException::class.java)
            .hasMessageContaining("blocked in production")
    }

    @Test
    fun `local stub pilot invokes real OpenAI adapter and persists review-required recommendations`() {
        val fixture = fixture()
        fixture.recordSuccessfulSmoke()
        fixture.recommendations.sources += source(fixture.reservation.id)

        val summary = fixture.pilotService.runPilot(RecommendationProviderId("openai"), actorId)

        assertThat(summary.run.status).isEqualTo(RecommendationPilotRunStatus.SUCCEEDED)
        assertThat(summary.run.providerCalls).isEqualTo(1)
        assertThat(summary.run.recommendationsGenerated).isEqualTo(1)
        assertThat(fixture.recommendations.recommendations).hasSize(1)
        val recommendation = fixture.recommendations.recommendations.single()
        assertThat(recommendation.status).isEqualTo(RecommendationStatus.REVIEW_REQUIRED)
        assertThat(recommendation.source).isEqualTo(RecommendationSource.EXTERNAL_LLM)
        assertThat(recommendation.providerName).isEqualTo("openai")
        assertThat(recommendation.pilotRunId).isEqualTo(summary.run.id)
        assertThat(recommendation.appliedTaskId).isNull()
        assertThat(recommendation.toString()).doesNotContain("Ada", "RES-", "MUC", "test-token")
    }

    @Test
    fun `pilot stops safely when daily request budget is exhausted`() {
        val fixture = fixture(
            properties = pilotProperties().copy(
                pilot = pilotProperties().pilot.copy(dailyRequestBudget = 1, maxReservationsPerRun = 2)
            ),
            secondReservation = true
        )
        fixture.recordSuccessfulSmoke()
        fixture.recommendations.sources += source(fixture.reservation.id)
        fixture.recommendations.sources += source(fixture.secondReservation!!.id)

        val summary = fixture.pilotService.runPilot(RecommendationProviderId("openai"), actorId)

        assertThat(summary.run.status).isIn(RecommendationPilotRunStatus.PARTIALLY_SUCCEEDED, RecommendationPilotRunStatus.SUCCEEDED)
        assertThat(summary.run.providerCalls).isEqualTo(1)
        assertThat(fixture.pilotRepository.budget.requestUsed).isEqualTo(1)
        assertThat(fixture.recommendations.recommendations).hasSize(1)
    }

    @Test
    fun `scheduled pilot uses scheduled trigger and keeps recommendations review required`() {
        val fixture = fixture(properties = pilotProperties().copy(pilotSchedule = RecommendationPilotScheduleProperties(enabled = true, batchSize = 1)))
        fixture.recordSuccessfulSmoke()
        fixture.recommendations.sources += source(fixture.reservation.id)

        val summary = fixture.pilotService.runScheduledPilot()

        assertThat(summary).isNotNull()
        assertThat(summary!!.run.trigger).isEqualTo(RecommendationPilotTrigger.SCHEDULED)
        assertThat(summary.run.status).isEqualTo(RecommendationPilotRunStatus.SUCCEEDED)
        assertThat(fixture.recommendations.recommendations.single().status).isEqualTo(RecommendationStatus.REVIEW_REQUIRED)
        assertThat(fixture.pilotRepository.state.lastScheduleOutcome).isEqualTo(RecommendationPilotRunStatus.SUCCEEDED)
    }

    @Test
    fun `pilot schedule pause survives status and prevents scheduled work`() {
        val fixture = fixture(properties = pilotProperties().copy(pilotSchedule = RecommendationPilotScheduleProperties(enabled = true)))

        val paused = fixture.pilotService.pauseSchedule(actorId)
        val scheduled = fixture.pilotService.runScheduledPilot()

        assertThat(paused.paused).isTrue()
        assertThat(scheduled).isNull()
        assertThat(fixture.pilotService.scheduleStatus(actorId).paused).isTrue()
    }

    @Test
    fun `enabled pilot schedule is rejected in disallowed profile`() {
        assertThatThrownBy {
            fixture(
                properties = pilotProperties().copy(
                    pilotSchedule = RecommendationPilotScheduleProperties(enabled = true, allowedProfiles = listOf("local"))
                ),
                activeProfile = "test"
            )
        }.isInstanceOf(ReservationTaskRecommendationRejectedException::class.java)
            .hasMessageContaining("schedule is not allowed")
    }

    @Test
    fun `pilot analytics aggregate safe review outcomes and distributions`() {
        val fixture = fixture()
        fixture.recordSuccessfulSmoke()
        fixture.recommendations.sources += source(fixture.reservation.id)
        val summary = fixture.pilotService.runPilot(RecommendationProviderId("openai"), actorId)
        val base = fixture.recommendations.recommendations.single()
        fixture.recommendations.recommendations.clear()
        fixture.recommendations.recommendations += base.copy(
            status = RecommendationStatus.APPROVED,
            reviewedAt = clock.instant().plus(Duration.ofMinutes(10)),
            updatedAt = clock.instant().plus(Duration.ofMinutes(10))
        )
        fixture.recommendations.recommendations += base.copy(
            id = RecommendationId.generate(),
            deduplicationKey = "${base.deduplicationKey}:rejected",
            status = RecommendationStatus.REJECTED,
            confidence = RecommendationConfidence.LOW,
            reviewedAt = clock.instant().plus(Duration.ofHours(3)),
            updatedAt = clock.instant().plus(Duration.ofHours(3))
        )
        fixture.recommendations.recommendations += base.copy(
            id = RecommendationId.generate(),
            deduplicationKey = "${base.deduplicationKey}:applied",
            status = RecommendationStatus.APPLIED,
            updatedAt = clock.instant().plus(Duration.ofMinutes(4))
        )
        fixture.pilotRepository.runs.replaceAll { it.copy(id = summary.run.id, duplicatesPrevented = 2) }

        val analytics = fixture.pilotService.analytics(RecommendationPilotAnalyticsFilter(pilotRunId = summary.run.id), actorId)

        assertThat(analytics.summary.generatedCount).isEqualTo(3)
        assertThat(analytics.summary.approvedCount).isEqualTo(1)
        assertThat(analytics.summary.rejectedCount).isEqualTo(1)
        assertThat(analytics.summary.appliedCount).isEqualTo(1)
        assertThat(analytics.summary.approvalRate).isEqualTo(1.0 / 3.0)
        assertThat(analytics.summary.averageReviewTimeBand).isEqualTo("30_minutes_to_2_hours")
        assertThat(analytics.summary.duplicatePreventionCount).isEqualTo(2)
        assertThat(analytics.reviewOutcomes.map { it.key }).contains("APPROVED", "REJECTED", "APPLIED")
        assertThat(analytics.confidenceDistribution.map { it.key }).contains(base.confidence.name, "LOW")
        assertThat(analytics.categoryDistribution.map { it.key }).contains(base.category.name)
        assertThat(analytics.toString()).doesNotContain("Ada", "RES-", "MUC", "test-token", base.title, base.description)
    }

    @Test
    fun `rollback disables future pilot runs and preserves existing recommendations`() {
        val fixture = fixture()
        fixture.recordSuccessfulSmoke()
        fixture.recommendations.sources += source(fixture.reservation.id)
        fixture.pilotService.runPilot(RecommendationProviderId("openai"), actorId)

        val state = fixture.pilotService.rollbackToInternalDemo(actorId)
        val readiness = fixture.pilotService.readiness(RecommendationProviderId("openai"), actorId)

        assertThat(state.disabled).isTrue()
        assertThat(state.lastRollbackAt).isNotNull()
        assertThat(readiness.status).isEqualTo(RecommendationPilotReadinessStatus.DISABLED)
        assertThat(fixture.recommendations.recommendations).hasSize(1)
        assertThat(fixture.registry.activeProvider().id.value).isEqualTo("internal-demo")
    }

    private fun fixture(
        properties: ReservationTaskRecommendationProperties = pilotProperties(),
        secondReservation: Boolean = false,
        activeProfile: String = "test"
    ): Fixture {
        val objectMapper = jacksonObjectMapper()
        val environment = MockEnvironment().apply { setActiveProfiles(activeProfile) }
        val credentialResolver = RecommendationCredentialResolver { "test-token" }
        val openAiProvider = OpenAiRecommendationProvider(
            properties = properties,
            privacyGateway = RecommendationPrivacyGateway(objectMapper),
            promptFactory = RecommendationPromptFactory(),
            responseValidator = StructuredRecommendationResponseValidator(properties),
            credentialResolver = credentialResolver,
            httpClient = SmokeAwareRecommendationHttpClient(),
            objectMapper = objectMapper,
            clock = clock
        )
        val providers = listOf(InternalDemoRecommendationProvider(properties), openAiProvider)
        val registry = TaskRecommendationProviderRegistry(providers, properties, environment)
        val diagnostics = InMemoryDiagnosticRepository()
        val smokeService = ExternalRecommendationProviderSmokeService(
            providerRegistry = registry,
            diagnostics = diagnostics,
            credentialResolver = credentialResolver,
            properties = properties,
            clock = clock,
            environment = environment
        )
        val reservations = InMemoryReservationRepository()
        val reservation = reservation("RES-1401", "MUC")
        reservations.save(reservation)
        val second = if (secondReservation) reservation("RES-1402", "MUC") else null
        second?.let(reservations::save)
        val recommendations = InMemoryRecommendationRepository()
        val pilotRepository = InMemoryPilotRepository(clock, recommendations)
        val pilotService = ExternalRecommendationPilotService(
            providerRegistry = registry,
            smokeService = smokeService,
            recommendationRepository = recommendations,
            reservationRepository = reservations,
            pilotRepository = pilotRepository,
            leaseStatusRepository = StaticLeaseStatusRepository(),
            properties = properties,
            clock = clock,
            environment = environment
        )
        return Fixture(pilotService, smokeService, registry, diagnostics, recommendations, pilotRepository, reservation, second)
    }

    private fun pilotProperties(): ReservationTaskRecommendationProperties =
        baseProperties(
            openAi = OpenAiRecommendationProviderProperties(
                enabled = true,
                endpoint = "http://localhost/stub/v1/chat/completions",
                model = "gpt-test",
                credentialReference = RecommendationCredentialReference(name = "OPENAI_API_KEY"),
                smoke = RecommendationProviderSmokeProperties(enabled = true, fixtureModeEnabled = false),
                retryPolicy = RecommendationProviderRetryProperties(maxAttempts = 1, initialBackoff = Duration.ofMillis(1), maxBackoff = Duration.ofMillis(1))
            ),
            pilot = RecommendationPilotProperties(
                enabled = true,
                allowedPropertyScopes = listOf("MUC"),
                maxReservationsPerRun = 2,
                maxRecommendationsPerRun = 2,
                dailyRequestBudget = 10,
                requiredSuccessfulSmokeAge = Duration.ofHours(24)
            )
        )

    private fun baseProperties(
        openAi: OpenAiRecommendationProviderProperties = OpenAiRecommendationProviderProperties(),
        pilot: RecommendationPilotProperties
    ): ReservationTaskRecommendationProperties =
        ReservationTaskRecommendationProperties(
            enabled = false,
            activeProvider = "internal-demo",
            providers = RecommendationProviderGovernanceProperties(openai = openAi),
            pilot = pilot
        )

    private fun Fixture.recordSuccessfulSmoke() {
        diagnostics.save(
            RecommendationProviderDiagnostic(
                providerId = RecommendationProviderId("openai"),
                diagnosticType = RecommendationProviderDiagnosticType.SMOKE_TEST,
                triggerType = RecommendationProviderDiagnosticTrigger.OPERATOR,
                startedAt = clock.instant().minus(Duration.ofMinutes(5)),
                completedAt = clock.instant().minus(Duration.ofMinutes(5)),
                outcome = RecommendationProviderDiagnosticOutcome.SUCCEEDED,
                failureCategory = null,
                latencyBand = "lt_100ms",
                retryCount = 0,
                responseValidationOutcome = RecommendationResponseValidationOutcome.VALID,
                promptVersion = "reservation-task-recommendation-openai-v1",
                modelIdentifier = "gpt-test",
                environmentClass = "test",
                endpointClassification = RecommendationEndpointClassification.LOCAL_STUB
            )
        )
    }

    private fun reservation(reference: String, propertyId: String): Reservation =
        Reservation.create(
            externalReference = ExternalReservationReference(reference),
            propertyId = PropertyId(propertyId),
            primaryGuest = Guest(GuestId("guest-$reference"), "Ada Lovelace"),
            stayPeriod = DateRange(LocalDate.parse("2026-08-01"), LocalDate.parse("2026-08-03")),
            reservationStatus = ReservationStatus.CONFIRMED,
            stayStatus = StayStatus.NOT_ARRIVED,
            roomAssignment = null,
            occupancy = Occupancy(adults = 2),
            createdAt = clock.instant(),
            modifiedAt = clock.instant()
        )

    private fun source(reservationId: ReservationId): RecommendationSourceExecution =
        RecommendationSourceExecution(
            outboxEventId = UUID.randomUUID(),
            reservationId = reservationId.value,
            triggerEventType = "RESERVATION_IMPORTED",
            automationOutcome = "CREATED",
            taskCreated = true,
            createdAt = clock.instant()
        )

    private data class Fixture(
        val pilotService: ExternalRecommendationPilotService,
        val smokeService: ExternalRecommendationProviderSmokeService,
        val registry: TaskRecommendationProviderRegistry,
        val diagnostics: InMemoryDiagnosticRepository,
        val recommendations: InMemoryRecommendationRepository,
        val pilotRepository: InMemoryPilotRepository,
        val reservation: Reservation,
        val secondReservation: Reservation?
    )

    private class InMemoryDiagnosticRepository : RecommendationProviderDiagnosticRepository {
        val records = mutableListOf<RecommendationProviderDiagnostic>()
        override fun save(diagnostic: RecommendationProviderDiagnostic): RecommendationProviderDiagnostic {
            records.removeIf { it.id == diagnostic.id }
            records += diagnostic
            return diagnostic
        }
        override fun find(id: RecommendationProviderDiagnosticId): RecommendationProviderDiagnostic? = records.firstOrNull { it.id == id }
        override fun find(filter: RecommendationProviderDiagnosticFilter): RecommendationProviderDiagnosticPage {
            val page = filter.page.coerceAtLeast(0)
            val size = filter.size.coerceIn(1, 100)
            val filtered = records
                .filter { filter.providerId == null || it.providerId == filter.providerId }
                .filter { filter.outcome == null || it.outcome == filter.outcome }
                .sortedWith(compareByDescending<RecommendationProviderDiagnostic> { it.startedAt }.thenByDescending { it.id.value })
            return RecommendationProviderDiagnosticPage(filtered.drop(page * size).take(size), page, size, filtered.size.toLong(), if (filtered.isEmpty()) 0 else ceil(filtered.size.toDouble() / size).toInt())
        }
        override fun latest(providerId: RecommendationProviderId): RecommendationProviderDiagnostic? =
            find(RecommendationProviderDiagnosticFilter(providerId = providerId, size = 1)).content.firstOrNull()
        override fun latestSuccessful(providerId: RecommendationProviderId): RecommendationProviderDiagnostic? =
            records.filter { it.providerId == providerId && it.outcome == RecommendationProviderDiagnosticOutcome.SUCCEEDED }.maxByOrNull { it.startedAt }
        override fun cleanupCompleted(olderThan: Instant, limit: Int): Int = 0
    }

    private class InMemoryRecommendationRepository : ReservationTaskRecommendationRepository {
        val recommendations = mutableListOf<ReservationTaskRecommendation>()
        val sources = mutableListOf<RecommendationSourceExecution>()
        override fun insert(recommendation: ReservationTaskRecommendation): ReservationTaskRecommendationInsertResult {
            recommendations.firstOrNull { it.deduplicationKey == recommendation.deduplicationKey }?.let {
                return ReservationTaskRecommendationInsertResult.Duplicate(it)
            }
            recommendations += recommendation
            return ReservationTaskRecommendationInsertResult.Inserted(recommendation)
        }
        override fun save(recommendation: ReservationTaskRecommendation): ReservationTaskRecommendation {
            recommendations.removeIf { it.id == recommendation.id }
            recommendations += recommendation
            return recommendation
        }
        override fun find(id: RecommendationId): ReservationTaskRecommendation? = recommendations.firstOrNull { it.id == id }
        override fun find(filter: RecommendationFilter): RecommendationPage = RecommendationPage(recommendations, filter.page, filter.size, recommendations.size.toLong(), 1)
        override fun claimEligibleAutomationExecutions(now: Instant, batchSize: Int, createdAfter: Instant): List<RecommendationSourceExecution> =
            sources.filter { !it.createdAt.isBefore(createdAfter) }
                .filterNot { source -> recommendations.any { it.reservationId == source.reservationId && it.status in setOf(RecommendationStatus.GENERATED, RecommendationStatus.REVIEW_REQUIRED, RecommendationStatus.APPROVED, RecommendationStatus.APPLIED) } }
                .sortedWith(compareBy<RecommendationSourceExecution> { it.createdAt }.thenBy { it.outboxEventId })
                .take(batchSize)
        override fun retry(id: RecommendationId, now: Instant): ReservationTaskRecommendation = requireNotNull(find(id))
        override fun saveRun(run: RecommendationGenerationRun): RecommendationGenerationRun = run
        override fun findRun(id: RecommendationGenerationRunId): RecommendationGenerationRun? = null
        override fun findRuns(filter: RecommendationGenerationRunFilter): RecommendationGenerationRunPage = RecommendationGenerationRunPage(emptyList(), 0, 20, 0, 0)
        override fun runCount(statuses: Set<RecommendationGenerationRunStatus>): Long = 0
        override fun getOrCreateScheduleState(scheduleId: String, now: Instant): RecommendationScheduleState = RecommendationScheduleState(scheduleId, false, updatedAt = now)
        override fun markSchedulePaused(scheduleId: String, now: Instant): RecommendationScheduleState = RecommendationScheduleState(scheduleId, true, updatedAt = now)
        override fun markScheduleResumed(scheduleId: String, now: Instant): RecommendationScheduleState = RecommendationScheduleState(scheduleId, false, updatedAt = now)
        override fun recordScheduleAttempt(scheduleId: String, run: RecommendationGenerationRun?, now: Instant, failureCategory: RecommendationFailureCategory?): RecommendationScheduleState = RecommendationScheduleState(scheduleId, false, updatedAt = now)
        override fun eligibleCandidateBacklogCount(now: Instant, createdAfter: Instant): Long = claimEligibleAutomationExecutions(now, 100, createdAfter).size.toLong()
        override fun activeRecommendationCount(reservationId: UUID): Long = recommendations.count { it.reservationId == reservationId && it.status in setOf(RecommendationStatus.REVIEW_REQUIRED, RecommendationStatus.APPROVED) }.toLong()
        override fun unresolvedAutomationFailureExists(reservationId: UUID): Boolean = false
        override fun expireEligibleRecommendations(now: Instant, olderThan: Instant, limit: Int): Int = 0
        override fun expirePilotRecommendations(now: Instant, limit: Int): Int {
            var expired = 0
            recommendations.replaceAll {
                if (expired < limit && it.pilotRunId != null && it.status in setOf(RecommendationStatus.REVIEW_REQUIRED, RecommendationStatus.APPROVED)) {
                    expired += 1
                    it.copy(status = RecommendationStatus.EXPIRED, updatedAt = now)
                } else {
                    it
                }
            }
            return expired
        }
        override fun cleanupTerminalRecords(runOlderThan: Instant, recommendationOlderThan: Instant, appliedOlderThan: Instant, limit: Int): Int = 0
    }

    private class InMemoryPilotRepository(
        private val clock: Clock,
        private val recommendationRepository: InMemoryRecommendationRepository
    ) : RecommendationPilotRepository {
        val runs = mutableListOf<RecommendationPilotRun>()
        var budget = RecommendationPilotBudgetStatus(RecommendationProviderId("openai"), LocalDate.parse("2026-07-31"), 10, 0, 2, 0, null, 0, false)
        var state = RecommendationPilotState(ExternalRecommendationPilotService.PILOT_STATE_ID, false, null, null, updatedAt = clock.instant())
        override fun saveRun(run: RecommendationPilotRun): RecommendationPilotRun {
            val stored = run.copy(version = (runs.firstOrNull { it.id == run.id }?.version ?: -1) + 1)
            runs.removeIf { it.id == run.id }
            runs += stored
            return stored
        }
        override fun findRun(id: RecommendationPilotRunId): RecommendationPilotRun? = runs.firstOrNull { it.id == id }
        override fun findRuns(filter: RecommendationPilotRunFilter): RecommendationPilotRunPage = RecommendationPilotRunPage(runs, filter.page, filter.size, runs.size.toLong(), if (runs.isEmpty()) 0 else 1)
        override fun budgetStatus(providerId: RecommendationProviderId, budgetDate: LocalDate, requestLimit: Int, recommendationLimit: Int, tokenLimit: Long?): RecommendationPilotBudgetStatus =
            budget.copy(providerId = providerId, budgetDate = budgetDate, requestLimit = requestLimit, recommendationLimit = recommendationLimit, tokenLimit = tokenLimit, exhausted = budget.requestUsed >= requestLimit || budget.recommendationUsed >= recommendationLimit)
        override fun reserveRequest(providerId: RecommendationProviderId, budgetDate: LocalDate, requestLimit: Int, tokenLimit: Long?, expectedTokens: Long, now: Instant): Boolean {
            val current = budgetStatus(providerId, budgetDate, requestLimit, budget.recommendationLimit, tokenLimit)
            if (current.exhausted || current.requestUsed >= requestLimit) return false
            budget = current.copy(requestUsed = current.requestUsed + 1)
            return true
        }
        override fun recordGeneratedRecommendations(providerId: RecommendationProviderId, budgetDate: LocalDate, count: Int, now: Instant) {
            budget = budget.copy(recommendationUsed = budget.recommendationUsed + count)
        }
        override fun releaseFailedRequest(providerId: RecommendationProviderId, budgetDate: LocalDate, now: Instant) {
            budget = budget.copy(requestUsed = (budget.requestUsed - 1).coerceAtLeast(0))
        }
        override fun getOrCreateState(stateId: String, now: Instant): RecommendationPilotState = state
        override fun disable(stateId: String, now: Instant): RecommendationPilotState = state.copy(disabled = true, disabledAt = now, updatedAt = now).also { state = it }
        override fun rollback(stateId: String, now: Instant): RecommendationPilotState = state.copy(disabled = true, disabledAt = state.disabledAt ?: now, lastRollbackAt = now, updatedAt = now).also { state = it }
        override fun pauseSchedule(stateId: String, now: Instant): RecommendationPilotState = state.copy(schedulePaused = true, schedulePausedAt = now, updatedAt = now).also { state = it }
        override fun resumeSchedule(stateId: String, now: Instant): RecommendationPilotState = state.copy(schedulePaused = false, scheduleResumedAt = now, updatedAt = now).also { state = it }
        override fun recordScheduleAttempt(stateId: String, run: RecommendationPilotRun?, budgetRejections: Int, now: Instant): RecommendationPilotState =
            state.copy(
                lastScheduleAttemptedAt = now,
                lastScheduleSuccessfulAt = if (run?.status in setOf(RecommendationPilotRunStatus.SUCCEEDED, RecommendationPilotRunStatus.PARTIALLY_SUCCEEDED)) now else state.lastScheduleSuccessfulAt,
                lastScheduleOutcome = run?.status,
                lastSelectedCandidateCount = run?.candidatesSelected ?: 0,
                lastGeneratedRecommendationCount = run?.recommendationsGenerated ?: 0,
                lastBudgetRejectionCount = budgetRejections,
                lastScheduleFailureCategory = run?.failureCategory,
                updatedAt = now
            ).also { state = it }
        override fun scheduledRunCount(providerId: RecommendationProviderId, budgetDate: LocalDate): Long =
            runs.count { it.providerId == providerId && it.trigger == RecommendationPilotTrigger.SCHEDULED && it.startedAt.atZone(ZoneId.of("UTC")).toLocalDate() == budgetDate }.toLong()
        override fun cleanupPilotRuns(completedBefore: Instant, limit: Int): Int {
            val deleted = runs.filter { it.completedAt != null && it.completedAt.isBefore(completedBefore) }.take(limit)
            runs.removeAll(deleted.toSet())
            return deleted.size
        }
        override fun analytics(filter: RecommendationPilotAnalyticsFilter, now: Instant): RecommendationPilotAnalytics {
            val recommendations = recommendationRepository.recommendations
                .filter { it.pilotRunId != null }
                .filter { filter.pilotRunId == null || it.pilotRunId == filter.pilotRunId }
                .filter { filter.providerId == null || it.providerName == filter.providerId.value }
                .filter { filter.category == null || it.category == filter.category }
                .filter { filter.confidence == null || it.confidence == filter.confidence }
                .filter { filter.status == null || it.status == filter.status }
            val generated = recommendations.size.toLong()
            val approved = recommendations.count { it.status == RecommendationStatus.APPROVED }.toLong()
            val rejected = recommendations.count { it.status == RecommendationStatus.REJECTED }.toLong()
            val applied = recommendations.count { it.status == RecommendationStatus.APPLIED }.toLong()
            val reviewSeconds = recommendations
                .filter { it.status in setOf(RecommendationStatus.APPROVED, RecommendationStatus.REJECTED, RecommendationStatus.APPLIED, RecommendationStatus.EXPIRED) }
                .map { Duration.between(it.createdAt, it.reviewedAt ?: it.updatedAt).seconds }
            return RecommendationPilotAnalytics(
                summary = RecommendationPilotAnalyticsSummary(
                    generatedCount = generated,
                    approvedCount = approved,
                    rejectedCount = rejected,
                    expiredCount = recommendations.count { it.status == RecommendationStatus.EXPIRED }.toLong(),
                    appliedCount = applied,
                    approvalRate = if (generated == 0L) 0.0 else approved.toDouble() / generated.toDouble(),
                    rejectionRate = if (generated == 0L) 0.0 else rejected.toDouble() / generated.toDouble(),
                    applyRate = if (generated == 0L) 0.0 else applied.toDouble() / generated.toDouble(),
                    averageReviewTimeBand = reviewBand(reviewSeconds.average().takeUnless { it.isNaN() } ?: 0.0),
                    duplicatePreventionCount = runs.sumOf { it.duplicatesPrevented.toLong() },
                    failureCount = runs.sumOf { it.failedCount.toLong() }
                ),
                reviewOutcomes = breakdown(recommendations) { it.status.name },
                confidenceDistribution = breakdown(recommendations) { it.confidence.name },
                categoryDistribution = breakdown(recommendations) { it.category.name },
                providerModelDistribution = breakdown(recommendations) { "${it.providerName}:${it.modelIdentifier ?: "none"}" },
                recommendationAgeBands = breakdown(recommendations) { "under_30_minutes" }
            )
        }

        private fun breakdown(
            recommendations: List<ReservationTaskRecommendation>,
            key: (ReservationTaskRecommendation) -> String
        ): List<RecommendationPilotBreakdown> =
            recommendations.groupingBy(key).eachCount().map { RecommendationPilotBreakdown(it.key, it.value.toLong()) }

        private fun reviewBand(seconds: Double): String =
            when {
                seconds <= 0.0 -> "none"
                seconds < 300.0 -> "under_5_minutes"
                seconds < 1800.0 -> "5_to_30_minutes"
                seconds < 7200.0 -> "30_minutes_to_2_hours"
                seconds < 86400.0 -> "2_to_24_hours"
                else -> "over_24_hours"
            }
    }

    private class StaticLeaseStatusRepository : ReservationSyncScheduleLeaseStatusRepository {
        override fun state(jobName: String, now: Instant): ReservationSyncScheduleLeaseState =
            ReservationSyncScheduleLeaseState.AVAILABLE
    }
}
