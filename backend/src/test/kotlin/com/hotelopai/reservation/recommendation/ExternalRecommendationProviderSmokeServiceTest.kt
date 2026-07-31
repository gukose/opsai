package com.hotelopai.reservation.recommendation

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.hotelopai.integration.openai.recommendation.OpenAiRecommendationProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.mock.env.MockEnvironment
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlin.math.ceil

class ExternalRecommendationProviderSmokeServiceTest {
    private val clock = Clock.fixed(Instant.parse("2026-07-31T10:00:00Z"), ZoneId.of("UTC"))

    @Test
    fun `OpenAI provider disabled by default is safe at startup`() {
        val fixture = fixture(properties = ReservationTaskRecommendationProperties())

        val readiness = fixture.service.readiness(RecommendationProviderId("openai"), UUID.randomUUID())

        assertThat(readiness.readiness).isEqualTo(RecommendationProviderReadinessStatus.DISABLED)
        assertThat(readiness.productionUseBlocked).isFalse()
        assertThat(readiness.toString()).doesNotContain("token", "OPENAI_API_KEY", "reservationId")
    }

    @Test
    fun `production activation is rejected with sanitized message`() {
        val properties = properties(openAi(enabled = true))

        val failure = assertThrows(ReservationTaskRecommendationRejectedException::class.java) {
            fixture(properties = properties, environment = MockEnvironment().apply { setActiveProfiles("prod") })
        }

        assertThat(failure.message).contains("blocked in production")
        assertThat(failure.message).doesNotContain("test-token", "OPENAI_API_KEY")
    }

    @Test
    fun `disallowed profile activation is rejected`() {
        val properties = properties(openAi(enabled = true, allowedProfiles = listOf("staging")))

        val failure = assertThrows(ReservationTaskRecommendationRejectedException::class.java) {
            fixture(properties = properties, environment = MockEnvironment().apply { setActiveProfiles("test") })
        }

        assertThat(failure.message).contains("not allowed")
    }

    @Test
    fun `local stub readiness requires explicit smoke mode`() {
        val properties = properties(
            openAi(
                enabled = true,
                endpoint = "http://localhost:65535/v1/chat/completions",
                smoke = RecommendationProviderSmokeProperties(enabled = true, fixtureModeEnabled = true)
            )
        )
        val fixture = fixture(properties = properties)

        val readiness = fixture.service.readiness(RecommendationProviderId("openai"), UUID.randomUUID())

        assertThat(readiness.readiness).isEqualTo(RecommendationProviderReadinessStatus.READY_FOR_LOCAL_SMOKE)
        assertThat(readiness.endpointClassification).isEqualTo(RecommendationEndpointClassification.LOCAL_STUB)
        assertThat(readiness.blockingReasons).isEmpty()
    }

    @Test
    fun `missing credential reference is misconfigured readiness without network call`() {
        val failure = assertThrows(IllegalArgumentException::class.java) {
            properties(openAi(enabled = true, credentialReference = null))
        }

        assertThat(failure.message).contains("credential reference")
    }

    @Test
    fun `successful fixture smoke test runs through OpenAI adapter and persists safe diagnostics`() {
        val fixture = fixture(properties = smokeProperties())

        val result = fixture.service.runSmokeTest(
            RecommendationProviderId("openai"),
            RecommendationSmokeFixtureMode.SUCCESS,
            UUID.randomUUID()
        )

        assertThat(result.diagnostic.outcome).isEqualTo(RecommendationProviderDiagnosticOutcome.SUCCEEDED)
        assertThat(result.diagnostic.responseValidationOutcome).isEqualTo(RecommendationResponseValidationOutcome.VALID)
        assertThat(result.recommendationCount).isEqualTo(1)
        assertThat(fixture.diagnostics.records).hasSize(1)
        assertThat(result.diagnostic.toString()).doesNotContain("test-token", "Bearer", "choices", "0000000013f0")
    }

    @Test
    fun `empty fixture smoke test is a valid success`() {
        val fixture = fixture(properties = smokeProperties())

        val result = fixture.service.runSmokeTest(
            RecommendationProviderId("openai"),
            RecommendationSmokeFixtureMode.EMPTY_SUCCESS,
            UUID.randomUUID()
        )

        assertThat(result.diagnostic.outcome).isEqualTo(RecommendationProviderDiagnosticOutcome.SUCCEEDED)
        assertThat(result.recommendationCount).isZero()
    }

    @Test
    fun `malformed fixture records invalid response diagnostic`() {
        val fixture = fixture(properties = smokeProperties())

        val result = fixture.service.runSmokeTest(
            RecommendationProviderId("openai"),
            RecommendationSmokeFixtureMode.MALFORMED_RESPONSE,
            UUID.randomUUID()
        )

        assertThat(result.diagnostic.outcome).isEqualTo(RecommendationProviderDiagnosticOutcome.FAILED)
        assertThat(result.diagnostic.failureCategory).isEqualTo(RecommendationFailureCategory.INVALID_RESPONSE)
        assertThat(result.diagnostic.responseValidationOutcome).isEqualTo(RecommendationResponseValidationOutcome.INVALID)
    }

    @Test
    fun `rate limit and authentication fixture failures are classified safely`() {
        val fixture = fixture(properties = smokeProperties())

        val rateLimit = fixture.service.runSmokeTest(
            RecommendationProviderId("openai"),
            RecommendationSmokeFixtureMode.RATE_LIMITED,
            UUID.randomUUID()
        )
        val authentication = fixture.service.runSmokeTest(
            RecommendationProviderId("openai"),
            RecommendationSmokeFixtureMode.AUTHENTICATION_FAILURE,
            UUID.randomUUID()
        )

        assertThat(rateLimit.diagnostic.failureCategory).isEqualTo(RecommendationFailureCategory.RATE_LIMIT)
        assertThat(authentication.diagnostic.failureCategory).isEqualTo(RecommendationFailureCategory.AUTHENTICATION)
        assertThat(fixture.diagnostics.records.joinToString()).doesNotContain("test-token", "Bearer")
    }

    @Test
    fun `readiness changes after transient failure and recovers after success`() {
        val fixture = fixture(properties = smokeProperties())

        fixture.service.runSmokeTest(RecommendationProviderId("openai"), RecommendationSmokeFixtureMode.TIMEOUT, UUID.randomUUID())
        val afterFailure = fixture.service.readiness(RecommendationProviderId("openai"), UUID.randomUUID())
        fixture.service.runSmokeTest(RecommendationProviderId("openai"), RecommendationSmokeFixtureMode.SUCCESS, UUID.randomUUID())
        val afterSuccess = fixture.service.readiness(RecommendationProviderId("openai"), UUID.randomUUID())

        assertThat(afterFailure.readiness).isEqualTo(RecommendationProviderReadinessStatus.TEMPORARILY_UNAVAILABLE)
        assertThat(afterSuccess.readiness).isEqualTo(RecommendationProviderReadinessStatus.READY_FOR_LOCAL_SMOKE)
        assertThat(afterSuccess.lastSuccessfulSmokeAt).isNotNull()
    }

    @Test
    fun `diagnostics pagination and cleanup are bounded and preserve latest per provider`() {
        val fixture = fixture(properties = smokeProperties())
        repeat(3) {
            fixture.service.runSmokeTest(RecommendationProviderId("openai"), RecommendationSmokeFixtureMode.SUCCESS, UUID.randomUUID())
        }

        val page = fixture.service.diagnostics(RecommendationProviderDiagnosticFilter(page = 0, size = 2), UUID.randomUUID())
        val deleted = fixture.service.cleanupDiagnostics(UUID.randomUUID())

        assertThat(page.content).hasSize(2)
        assertThat(deleted).isEqualTo(0)
        assertThat(fixture.diagnostics.records).hasSize(3)
    }

    @Test
    fun `configured external provider does not silently fall back to internal demo`() {
        val properties = properties(openAi(enabled = false)).copy(enabled = true, hotelId = UUID.randomUUID(), activeProvider = "openai")

        assertThrows(ReservationTaskRecommendationRejectedException::class.java) {
            fixture(properties = properties)
        }
    }

    private fun fixture(
        properties: ReservationTaskRecommendationProperties = smokeProperties(),
        environment: MockEnvironment = MockEnvironment().apply { setActiveProfiles("test") },
        credentialResolver: RecommendationCredentialResolver = RecommendationCredentialResolver { "test-token" }
    ): Fixture {
        val objectMapper = jacksonObjectMapper()
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
        val diagnostics = InMemoryDiagnosticRepository(clock)
        val service = ExternalRecommendationProviderSmokeService(
            providerRegistry = registry,
            diagnostics = diagnostics,
            credentialResolver = credentialResolver,
            properties = properties,
            clock = clock,
            environment = environment
        )
        return Fixture(service, registry, diagnostics)
    }

    private fun smokeProperties(): ReservationTaskRecommendationProperties =
        properties(
            openAi(
                enabled = true,
                smoke = RecommendationProviderSmokeProperties(enabled = true, fixtureModeEnabled = true)
            )
        )

    private fun properties(openAi: OpenAiRecommendationProviderProperties): ReservationTaskRecommendationProperties =
        ReservationTaskRecommendationProperties(
            providers = RecommendationProviderGovernanceProperties(openai = openAi)
        )

    private fun openAi(
        enabled: Boolean,
        endpoint: String = "https://api.openai.com/v1/chat/completions",
        credentialReference: RecommendationCredentialReference? = RecommendationCredentialReference(name = "OPENAI_API_KEY"),
        smoke: RecommendationProviderSmokeProperties = RecommendationProviderSmokeProperties(),
        allowedProfiles: List<String> = emptyList()
    ): OpenAiRecommendationProviderProperties =
        OpenAiRecommendationProviderProperties(
            enabled = enabled,
            endpoint = endpoint,
            model = "gpt-test",
            credentialReference = credentialReference,
            retryPolicy = RecommendationProviderRetryProperties(
                maxAttempts = 1,
                initialBackoff = Duration.ofMillis(1),
                maxBackoff = Duration.ofMillis(1)
            ),
            smoke = smoke,
            allowedProfiles = allowedProfiles
        )

    private data class Fixture(
        val service: ExternalRecommendationProviderSmokeService,
        val registry: TaskRecommendationProviderRegistry,
        val diagnostics: InMemoryDiagnosticRepository
    )

    private class InMemoryDiagnosticRepository(private val clock: Clock) : RecommendationProviderDiagnosticRepository {
        val records = mutableListOf<RecommendationProviderDiagnostic>()

        override fun save(diagnostic: RecommendationProviderDiagnostic): RecommendationProviderDiagnostic {
            records.removeIf { it.id == diagnostic.id }
            records += diagnostic
            return diagnostic
        }

        override fun find(id: RecommendationProviderDiagnosticId): RecommendationProviderDiagnostic? =
            records.firstOrNull { it.id == id }

        override fun find(filter: RecommendationProviderDiagnosticFilter): RecommendationProviderDiagnosticPage {
            val page = filter.page.coerceAtLeast(0)
            val size = filter.size.coerceIn(1, 100)
            val filtered = records
                .filter { filter.providerId == null || it.providerId == filter.providerId }
                .filter { filter.outcome == null || it.outcome == filter.outcome }
                .sortedWith(compareByDescending<RecommendationProviderDiagnostic> { it.startedAt }.thenByDescending { it.id.value })
            val content = filtered.drop(page * size).take(size)
            return RecommendationProviderDiagnosticPage(content, page, size, filtered.size.toLong(), if (filtered.isEmpty()) 0 else ceil(filtered.size.toDouble() / size).toInt())
        }

        override fun latest(providerId: RecommendationProviderId): RecommendationProviderDiagnostic? =
            find(RecommendationProviderDiagnosticFilter(providerId = providerId, page = 0, size = 1)).content.firstOrNull()

        override fun latestSuccessful(providerId: RecommendationProviderId): RecommendationProviderDiagnostic? =
            records
                .filter { it.providerId == providerId && it.outcome == RecommendationProviderDiagnosticOutcome.SUCCEEDED }
                .maxWithOrNull(compareBy<RecommendationProviderDiagnostic> { it.startedAt }.thenBy { it.id.value })

        override fun cleanupCompleted(olderThan: Instant, limit: Int): Int {
            val latestIds = records.groupBy { it.providerId }.values.mapNotNull {
                it.maxWithOrNull(compareBy<RecommendationProviderDiagnostic> { record -> record.startedAt }.thenBy { record -> record.id.value })?.id
            }.toSet()
            val candidates = records
                .filter { it.completedAt?.isBefore(olderThan) == true && it.id !in latestIds }
                .take(limit)
            records.removeAll(candidates.toSet())
            return candidates.size
        }
    }
}
