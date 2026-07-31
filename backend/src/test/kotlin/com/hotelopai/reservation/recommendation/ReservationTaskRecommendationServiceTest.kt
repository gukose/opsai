package com.hotelopai.reservation.recommendation

import com.hotelopai.reservation.domain.DateRange
import com.hotelopai.reservation.domain.ExternalReservationReference
import com.hotelopai.reservation.domain.Guest
import com.hotelopai.reservation.domain.GuestId
import com.hotelopai.reservation.domain.Occupancy
import com.hotelopai.reservation.domain.PropertyId
import com.hotelopai.reservation.domain.Reservation
import com.hotelopai.reservation.domain.ReservationId
import com.hotelopai.reservation.domain.ReservationStatus
import com.hotelopai.reservation.domain.RoomAssignment
import com.hotelopai.reservation.domain.RoomId
import com.hotelopai.reservation.domain.StayStatus
import com.hotelopai.reservation.infrastructure.InMemoryReservationRepository
import com.hotelopai.task.application.TaskLifecycleService
import com.hotelopai.task.domain.TaskIntentType
import com.hotelopai.task.domain.TaskPriority
import com.hotelopai.task.infrastructure.InMemoryTaskRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.mock.env.MockEnvironment
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

class ReservationTaskRecommendationServiceTest {
    private val clock = Clock.fixed(Instant.parse("2026-07-24T10:00:00Z"), ZoneId.of("UTC"))
    private val hotelId = UUID.fromString("00000000-0000-0000-0000-00000000b001")

    @Test
    fun `recommendations are disabled by default`() {
        val fixture = fixture(properties = ReservationTaskRecommendationProperties())
        fixture.repository.sources += source(fixture.reservation.id)

        val summary = fixture.service.generateBatch(UUID.randomUUID())

        assertThat(summary.processedReservations).isZero()
        assertThat(fixture.repository.recommendations).isEmpty()
    }

    @Test
    fun `internal demo provider generates review-required recommendation from sanitized context`() {
        val recordingProvider = RecordingProvider()
        val fixture = fixture(provider = recordingProvider, reservation = reservation(room = null, adults = 1, children = 0))
        fixture.repository.sources += source(fixture.reservation.id)

        val summary = fixture.service.generateBatch(UUID.randomUUID())

        assertThat(summary.generated).isEqualTo(1)
        assertThat(fixture.repository.recommendations.single().status).isEqualTo(RecommendationStatus.REVIEW_REQUIRED)
        val context = recordingProvider.contexts.single()
        assertThat(context.roomAssigned).isFalse()
        assertThat(context.reservationStatus).isEqualTo("CONFIRMED")
        assertThat(context.toString()).doesNotContain("Ada", "RES-", "MUC")
    }

    @Test
    fun `repeated generation is deduplicated`() {
        val fixture = fixture(reservation = reservation(room = null, adults = 1, children = 0))
        fixture.repository.sources += source(fixture.reservation.id)

        val first = fixture.service.generateBatch(UUID.randomUUID())
        fixture.repository.sources += source(fixture.reservation.id)
        val second = fixture.service.generateBatch(UUID.randomUUID())

        assertThat(first.generated).isEqualTo(1)
        assertThat(second.processedReservations).isZero()
        assertThat(fixture.repository.recommendations).hasSize(1)
    }

    @Test
    fun `approve apply and repeated apply are safe`() {
        val fixture = fixture(reservation = reservation(room = null, adults = 1, children = 0))
        fixture.repository.sources += source(fixture.reservation.id)
        fixture.service.generateBatch(UUID.randomUUID())
        val recommendation = fixture.repository.recommendations.single()

        val approved = fixture.service.approve(recommendation.id, UUID.randomUUID())
        val applied = fixture.service.apply(approved.id, UUID.randomUUID())

        assertThat(applied.status).isEqualTo(RecommendationStatus.APPLIED)
        assertThat(fixture.taskRepository.findAll()).hasSize(1)
        assertThat(fixture.taskRepository.findAll().single().title).isEqualTo("Review unassigned arrival")
        assertThat(fixture.taskRepository.findAll().single().description).doesNotContain("Ada", "RES-", "MUC")
        assertThrows(ReservationTaskRecommendationRejectedException::class.java) {
            fixture.service.apply(approved.id, UUID.randomUUID())
        }
        assertThat(fixture.taskRepository.findAll()).hasSize(1)
    }

    @Test
    fun `reject expire and retry lifecycle preserve review boundaries`() {
        val fixture = fixture(reservation = reservation(room = null, adults = 1, children = 0))
        fixture.repository.sources += source(fixture.reservation.id)
        fixture.service.generateBatch(UUID.randomUUID())
        val recommendation = fixture.repository.recommendations.single()

        val rejected = fixture.service.reject(recommendation.id, UUID.randomUUID())

        assertThat(rejected.status).isEqualTo(RecommendationStatus.REJECTED)
        assertThrows(ReservationTaskRecommendationRejectedException::class.java) {
            fixture.service.apply(rejected.id, UUID.randomUUID())
        }
    }

    @Test
    fun `provider failure is isolated from deterministic automation`() {
        val fixture = fixture(provider = FailingProvider())
        fixture.repository.sources += source(fixture.reservation.id)

        val summary = fixture.service.generateBatch(UUID.randomUUID())

        assertThat(summary.failed).isEqualTo(1)
        assertThat(fixture.taskRepository.findAll()).isEmpty()
    }

    @Test
    fun `provider registry rejects duplicate provider ids`() {
        assertThrows(IllegalArgumentException::class.java) {
            TaskRecommendationProviderRegistry(
                listOf(InternalDemoRecommendationProvider(), InternalDemoRecommendationProvider()),
                ReservationTaskRecommendationProperties()
            )
        }
    }

    @Test
    fun `provider registry exposes disabled external provider lifecycle safely`() {
        val properties = ReservationTaskRecommendationProperties()
        val registry = TaskRecommendationProviderRegistry(
            listOf(
                InternalDemoRecommendationProvider(properties),
                com.hotelopai.integration.openai.recommendation.OpenAiRecommendationProvider(
                    properties = properties,
                    privacyGateway = RecommendationPrivacyGateway(com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()),
                    promptFactory = RecommendationPromptFactory(),
                    responseValidator = StructuredRecommendationResponseValidator(properties),
                    credentialResolver = RecommendationCredentialResolver { "unused" },
                    httpClient = object : RecommendationHttpClient {
                        override fun postJson(request: RecommendationHttpRequest): RecommendationHttpResponse =
                            error("disabled provider should not issue HTTP")
                    },
                    objectMapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper(),
                    clock = clock
                )
            ),
            properties
        )

        val openAi = registry.summaries().single { it.providerId.value == "openai" }

        assertThat(openAi.providerType).isEqualTo(RecommendationProviderType.EXTERNAL)
        assertThat(openAi.lifecycle).isEqualTo(RecommendationProviderLifecycle.DISABLED)
        assertThat(openAi.status).isEqualTo(RecommendationProviderStatus.DISABLED)
        assertThat(openAi.activeModel).isNull()
        assertThat(openAi.toString()).doesNotContain("token", "OPENAI_API_KEY")
    }

    @Test
    fun `scheduler disabled by default and run history is sanitized`() {
        val fixture = fixture(properties = ReservationTaskRecommendationProperties())

        val status = fixture.service.schedulerStatus(UUID.randomUUID())
        val run = fixture.service.runScheduleNow(UUID.randomUUID())

        assertThat(status.configuredEnabled).isFalse()
        assertThat(status.effectiveEnabled).isFalse()
        assertThat(run.status).isEqualTo(RecommendationGenerationRunStatus.REJECTED)
        assertThat(run.toString()).doesNotContain("Ada", "RES-", "MUC")
    }

    @Test
    fun `scheduled generation records governed run and schema-aware recommendation`() {
        val properties = ReservationTaskRecommendationProperties(
            enabled = true,
            hotelId = hotelId,
            schedule = RecommendationGenerationScheduleProperties(enabled = true, batchSize = 1, maxReservationsPerExecution = 1)
        )
        val fixture = fixture(properties = properties, provider = InternalDemoRecommendationProvider(properties), reservation = reservation(room = null, adults = 1, children = 0))
        fixture.repository.sources += source(fixture.reservation.id)

        val run = fixture.service.processScheduledBatch()

        assertThat(run.status).isEqualTo(RecommendationGenerationRunStatus.SUCCEEDED)
        assertThat(run.trigger).isEqualTo(RecommendationGenerationTrigger.SCHEDULED)
        assertThat(run.recommendationsGenerated).isEqualTo(1)
        assertThat(fixture.repository.recommendations.single().contextSchemaVersion).isEqualTo(RECOMMENDATION_CONTEXT_SCHEMA_VERSION)
        assertThat(fixture.service.schedulerStatus(UUID.randomUUID()).lastGeneratedRecommendationCount).isEqualTo(1)
    }

    @Test
    fun `pause resume and expiration operations are durable`() {
        val properties = ReservationTaskRecommendationProperties(
            enabled = true,
            hotelId = hotelId,
            maximumReviewAge = Duration.ofDays(1)
        )
        val fixture = fixture(properties = properties, reservation = reservation(room = null, adults = 1, children = 0))
        fixture.repository.sources += source(fixture.reservation.id)
        fixture.service.generateBatch(UUID.randomUUID())
        val paused = fixture.service.pauseScheduler(UUID.randomUUID())
        val resumed = fixture.service.resumeScheduler(UUID.randomUUID())
        fixture.repository.recommendations.replaceAll { it.copy(createdAt = clock.instant().minus(Duration.ofDays(2))) }

        val expired = fixture.service.expireEligible(UUID.randomUUID())

        assertThat(paused.paused).isTrue()
        assertThat(resumed.paused).isFalse()
        assertThat(expired).isEqualTo(1)
        assertThat(fixture.repository.recommendations.single().status).isEqualTo(RecommendationStatus.EXPIRED)
    }

    private fun fixture(
        reservation: Reservation = reservation(),
        provider: TaskRecommendationProvider = InternalDemoRecommendationProvider(ReservationTaskRecommendationProperties(enabled = true, hotelId = hotelId)),
        properties: ReservationTaskRecommendationProperties = ReservationTaskRecommendationProperties(enabled = true, hotelId = hotelId)
    ): Fixture {
        val reservationRepository = InMemoryReservationRepository()
        reservationRepository.save(reservation)
        val taskRepository = InMemoryTaskRepository()
        val recommendationRepository = InMemoryRecommendationRepository()
        val registry = TaskRecommendationProviderRegistry(listOf(provider), properties, MockEnvironment().withProperty("spring.profiles.active", "test"))
        val service = ReservationTaskRecommendationService(
            providers = listOf(provider),
            providerRegistry = registry,
            recommendationRepository = recommendationRepository,
            reservationRepository = reservationRepository,
            taskApplicationPort = TaskLifecycleService(taskRepository),
            properties = properties,
            clock = clock
        )
        return Fixture(service, recommendationRepository, taskRepository, reservation)
    }

    private fun reservation(room: String? = "101", adults: Int = 2, children: Int = 1): Reservation =
        Reservation.create(
            externalReference = ExternalReservationReference("RES-123"),
            propertyId = PropertyId("MUC"),
            primaryGuest = Guest(GuestId("guest-1"), "Ada Lovelace"),
            stayPeriod = DateRange(LocalDate.parse("2026-07-25"), LocalDate.parse("2026-07-27")),
            reservationStatus = ReservationStatus.CONFIRMED,
            stayStatus = StayStatus.NOT_ARRIVED,
            roomAssignment = room?.let { RoomAssignment(RoomId(it), DateRange(LocalDate.parse("2026-07-25"), LocalDate.parse("2026-07-27"))) },
            occupancy = Occupancy(adults = adults, children = children),
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
        val service: ReservationTaskRecommendationService,
        val repository: InMemoryRecommendationRepository,
        val taskRepository: InMemoryTaskRepository,
        val reservation: Reservation
    )

    private class RecordingProvider : TaskRecommendationProvider {
        val contexts = mutableListOf<SanitizedReservationRecommendationContext>()
        override val providerName = "internal-demo"
        override val modelIdentifier: String? = null
        override val promptVersion = "recording-v1"
        override fun recommend(context: SanitizedReservationRecommendationContext): List<RecommendationTaskProposal> {
            contexts += context
            return InternalDemoRecommendationProvider(ReservationTaskRecommendationProperties(enabled = true, hotelId = UUID.randomUUID()))
                .recommend(context)
        }
    }

    private class FailingProvider : TaskRecommendationProvider {
        override val providerName = "internal-demo"
        override val modelIdentifier: String? = null
        override val promptVersion = "failing-v1"
        override fun recommend(context: SanitizedReservationRecommendationContext): List<RecommendationTaskProposal> =
            throw IllegalStateException("provider failed")
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

        override fun find(id: RecommendationId): ReservationTaskRecommendation? =
            recommendations.firstOrNull { it.id == id }

        override fun find(filter: RecommendationFilter): RecommendationPage =
            RecommendationPage(recommendations, filter.page, filter.size, recommendations.size.toLong(), if (recommendations.isEmpty()) 0 else 1)

        override fun claimEligibleAutomationExecutions(now: Instant, batchSize: Int, createdAfter: Instant): List<RecommendationSourceExecution> =
            sources
                .filter { !it.createdAt.isBefore(createdAfter) }
                .filterNot { source ->
                    recommendations.any {
                        it.reservationId == source.reservationId &&
                            it.status in setOf(RecommendationStatus.GENERATED, RecommendationStatus.REVIEW_REQUIRED, RecommendationStatus.APPROVED, RecommendationStatus.APPLIED)
                    }
                }
                .sortedWith(compareBy<RecommendationSourceExecution> { it.createdAt }.thenBy { it.outboxEventId })
                .take(batchSize)

        override fun retry(id: RecommendationId, now: Instant): ReservationTaskRecommendation =
            save(requireNotNull(find(id)).copy(status = RecommendationStatus.REVIEW_REQUIRED, nextAttemptAt = now))

        private val runs = mutableListOf<RecommendationGenerationRun>()
        private val states = linkedMapOf<String, RecommendationScheduleState>()

        override fun saveRun(run: RecommendationGenerationRun): RecommendationGenerationRun {
            val stored = run.copy(version = (runs.firstOrNull { it.id == run.id }?.version ?: -1) + 1)
            runs.removeIf { it.id == run.id }
            runs += stored
            return stored
        }

        override fun findRun(id: RecommendationGenerationRunId): RecommendationGenerationRun? =
            runs.firstOrNull { it.id == id }

        override fun findRuns(filter: RecommendationGenerationRunFilter): RecommendationGenerationRunPage =
            RecommendationGenerationRunPage(runs, filter.page, filter.size, runs.size.toLong(), if (runs.isEmpty()) 0 else 1)

        override fun runCount(statuses: Set<RecommendationGenerationRunStatus>): Long =
            runs.count { it.status in statuses }.toLong()

        override fun getOrCreateScheduleState(scheduleId: String, now: Instant): RecommendationScheduleState =
            states.getOrPut(scheduleId) { RecommendationScheduleState(scheduleId, paused = false, updatedAt = now) }

        override fun markSchedulePaused(scheduleId: String, now: Instant): RecommendationScheduleState {
            val current = getOrCreateScheduleState(scheduleId, now)
            return current.copy(paused = true, pausedAt = now, updatedAt = now).also { states[scheduleId] = it }
        }

        override fun markScheduleResumed(scheduleId: String, now: Instant): RecommendationScheduleState {
            val current = getOrCreateScheduleState(scheduleId, now)
            return current.copy(paused = false, resumedAt = now, updatedAt = now).also { states[scheduleId] = it }
        }

        override fun recordScheduleAttempt(
            scheduleId: String,
            run: RecommendationGenerationRun?,
            now: Instant,
            failureCategory: RecommendationFailureCategory?
        ): RecommendationScheduleState {
            val current = getOrCreateScheduleState(scheduleId, now)
            val success = failureCategory == null && run?.status in setOf(
                RecommendationGenerationRunStatus.SUCCEEDED,
                RecommendationGenerationRunStatus.PARTIALLY_SUCCEEDED
            )
            return current.copy(
                lastAttemptedAt = now,
                lastSuccessfulAt = if (success) now else current.lastSuccessfulAt,
                lastProcessedCandidateCount = run?.candidatesProcessed ?: 0,
                lastGeneratedRecommendationCount = run?.recommendationsGenerated ?: 0,
                lastFailureCategory = failureCategory,
                updatedAt = now
            ).also { states[scheduleId] = it }
        }

        override fun eligibleCandidateBacklogCount(now: Instant, createdAfter: Instant): Long =
            claimEligibleAutomationExecutions(now, 100, createdAfter).size.toLong()

        override fun activeRecommendationCount(reservationId: UUID): Long =
            recommendations.count {
                it.reservationId == reservationId &&
                    it.status in setOf(RecommendationStatus.GENERATED, RecommendationStatus.REVIEW_REQUIRED, RecommendationStatus.APPROVED)
            }.toLong()

        override fun unresolvedAutomationFailureExists(reservationId: UUID): Boolean = false

        override fun expireEligibleRecommendations(now: Instant, olderThan: Instant, limit: Int): Int {
            var expired = 0
            recommendations.replaceAll {
                if (expired < limit && it.status in setOf(RecommendationStatus.GENERATED, RecommendationStatus.REVIEW_REQUIRED, RecommendationStatus.APPROVED) && it.createdAt.isBefore(olderThan)) {
                    expired += 1
                    it.copy(status = RecommendationStatus.EXPIRED, updatedAt = now)
                } else {
                    it
                }
            }
            return expired
        }

        override fun expirePilotRecommendations(now: Instant, limit: Int): Int {
            var expired = 0
            recommendations.replaceAll {
                if (expired < limit && it.pilotRunId != null && it.status in setOf(RecommendationStatus.GENERATED, RecommendationStatus.REVIEW_REQUIRED, RecommendationStatus.APPROVED)) {
                    expired += 1
                    it.copy(status = RecommendationStatus.EXPIRED, updatedAt = now)
                } else {
                    it
                }
            }
            return expired
        }

        override fun cleanupTerminalRecords(
            runOlderThan: Instant,
            recommendationOlderThan: Instant,
            appliedOlderThan: Instant,
            limit: Int
        ): Int = 0
    }
}
