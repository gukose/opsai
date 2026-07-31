package com.hotelopai.reservation.recommendation

import com.hotelopai.observability.OperationalObservability
import com.hotelopai.shared.kernel.PersistenceInstant
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.core.env.Environment
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.net.URI
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.util.UUID

@Service
@EnableConfigurationProperties(ReservationTaskRecommendationProperties::class)
class ExternalRecommendationProviderSmokeService(
    private val providerRegistry: TaskRecommendationProviderRegistry,
    private val diagnostics: RecommendationProviderDiagnosticRepository,
    private val credentialResolver: RecommendationCredentialResolver,
    private val properties: ReservationTaskRecommendationProperties,
    private val clock: Clock,
    private val environment: Environment? = null,
    private val auditSink: ReservationTaskRecommendationAuditSink = NoOpReservationTaskRecommendationAuditSink,
    private val observability: OperationalObservability = OperationalObservability.noop()
) {
    fun readiness(providerId: RecommendationProviderId, actorUserId: UUID?): RecommendationProviderReadiness {
        audit("provider_readiness", "inspected", actorUserId)
        recordReadinessMetric(readinessInternal(providerId).readiness)
        return readinessInternal(providerId)
    }

    fun readiness(actorUserId: UUID?): List<RecommendationProviderReadiness> {
        audit("provider_readiness", "inspected", actorUserId)
        return providerRegistry.summaries()
            .filter { it.providerType == RecommendationProviderType.EXTERNAL }
            .map { readinessInternal(it.providerId).also { readiness -> recordReadinessMetric(readiness.readiness) } }
    }

    @Transactional
    fun runSmokeTest(
        providerId: RecommendationProviderId,
        fixtureMode: RecommendationSmokeFixtureMode?,
        actorUserId: UUID?
    ): RecommendationSmokeTestResult {
        audit("provider_smoke_test", "requested", actorUserId)
        val started = PersistenceInstant.now(clock)
        val provider = providerRegistry.provider(providerId)
            ?: throw ReservationTaskRecommendationRejectedException("Recommendation provider is not registered.")
        if (provider.providerType != RecommendationProviderType.EXTERNAL) {
            throw ReservationTaskRecommendationRejectedException("Recommendation smoke tests are supported only for external providers.")
        }
        val readiness = readinessInternal(providerId)
        if (readiness.readiness !in SMOKE_READY_STATES) {
            val rejected = diagnostics.save(
                diagnostic(
                    providerId = providerId,
                    started = started,
                    completed = started,
                    outcome = RecommendationProviderDiagnosticOutcome.REJECTED,
                    failureCategory = readiness.failureCategory ?: RecommendationFailureCategory.CONFIGURATION_ERROR,
                    validationOutcome = RecommendationResponseValidationOutcome.NOT_APPLICABLE,
                    elapsed = Duration.ZERO
                )
            )
            audit("provider_smoke_test", "rejected", actorUserId)
            recordSmokeMetric("rejected", fixtureMode, rejected.failureCategory, rejected.latencyBand)
            return RecommendationSmokeTestResult(rejected, readinessInternal(providerId), 0)
        }
        if (fixtureMode != null && !smokeFixtureModeAllowed(providerId)) {
            throw ReservationTaskRecommendationRejectedException("Recommendation smoke fixture mode is not enabled for this provider.")
        }
        return try {
            val result = RecommendationSmokeFixtureScope.withFixture(fixtureMode) {
                provider.recommend(syntheticContext(started))
            }
            val completed = PersistenceInstant.now(clock)
            val saved = diagnostics.save(
                diagnostic(
                    providerId = providerId,
                    started = started,
                    completed = completed,
                    outcome = RecommendationProviderDiagnosticOutcome.SUCCEEDED,
                    failureCategory = null,
                    validationOutcome = RecommendationResponseValidationOutcome.VALID,
                    elapsed = Duration.between(started, completed)
                )
            )
            audit("provider_smoke_test", "succeeded", actorUserId)
            recordSmokeMetric("succeeded", fixtureMode, null, saved.latencyBand)
            RecommendationSmokeTestResult(saved, readinessInternal(providerId), result.size)
        } catch (exception: RecommendationProviderException) {
            val completed = PersistenceInstant.now(clock)
            val saved = diagnostics.save(
                diagnostic(
                    providerId = providerId,
                    started = started,
                    completed = completed,
                    outcome = RecommendationProviderDiagnosticOutcome.FAILED,
                    failureCategory = exception.failureCategory,
                    validationOutcome = if (exception.failureCategory == RecommendationFailureCategory.INVALID_RESPONSE) {
                        RecommendationResponseValidationOutcome.INVALID
                    } else {
                        RecommendationResponseValidationOutcome.NOT_APPLICABLE
                    },
                    elapsed = Duration.between(started, completed)
                )
            )
            audit("provider_smoke_test", "failed", actorUserId)
            recordSmokeMetric("failed", fixtureMode, exception.failureCategory, saved.latencyBand)
            RecommendationSmokeTestResult(saved, readinessInternal(providerId), 0)
        }
    }

    fun diagnostics(filter: RecommendationProviderDiagnosticFilter, actorUserId: UUID?): RecommendationProviderDiagnosticPage {
        audit("provider_diagnostics", "inspected", actorUserId)
        return diagnostics.find(filter.copy(page = filter.page.coerceAtLeast(0), size = filter.size.coerceIn(1, 100)))
    }

    fun diagnostic(id: RecommendationProviderDiagnosticId, actorUserId: UUID?): RecommendationProviderDiagnostic =
        diagnostics.find(id)?.also { audit("provider_diagnostics", "inspected", actorUserId) }
            ?: throw ReservationTaskRecommendationRejectedException("Recommendation provider diagnostic was not found.")

    @Transactional
    fun cleanupDiagnostics(actorUserId: UUID?): Int {
        val deleted = diagnostics.cleanupCompleted(
            olderThan = PersistenceInstant.now(clock).minus(properties.externalProviders.diagnosticsRetention),
            limit = properties.externalProviders.diagnosticsCleanupBatchSize
        )
        audit("provider_diagnostics_cleanup", "deleted_$deleted", actorUserId)
        observability.incrementCounter(
            "hotelopai.reservation.task_recommendation.provider_diagnostic_cleanup.total",
            "provider" to "all",
            "outcome" to "deleted",
            "failure_category" to "none"
        )
        return deleted
    }

    private fun readinessInternal(providerId: RecommendationProviderId): RecommendationProviderReadiness {
        val summary = providerRegistry.summaries().firstOrNull { it.providerId == providerId }
            ?: return unknownReadiness(providerId)
        if (summary.providerType != RecommendationProviderType.EXTERNAL) {
            return summary.toReadiness(RecommendationProviderReadinessStatus.DISABLED, endpointClassification(providerId), emptyList())
        }
        val openAi = properties.providers.openai
        val blocking = mutableListOf<String>()
        val endpointClassification = endpointClassification(providerId)
        val envClass = environmentClass()
        val prodBlocked = productionBlocked()
        val latest = diagnostics.latest(providerId)
        val latestSuccessful = diagnostics.latestSuccessful(providerId)
        if (!openAi.enabled) {
            return summary.toReadiness(
                readiness = RecommendationProviderReadinessStatus.DISABLED,
                endpointClassification = endpointClassification,
                blockingReasons = listOf("provider_disabled"),
                latest = latest,
                latestSuccessful = latestSuccessful
            )
        }
        if (prodBlocked) blocking += "production_blocked"
        if (!externalProfileAllowed(openAi.allowedProfiles)) blocking += "profile_not_allowed"
        if (openAi.model.isNullOrBlank()) blocking += "model_not_configured"
        if (openAi.credentialReference == null) {
            blocking += "credential_reference_not_configured"
        } else {
            runCatching { credentialResolver.resolve(openAi.credentialReference) }
                .onFailure { blocking += "credential_unresolved" }
        }
        if (endpointClassification == RecommendationEndpointClassification.INVALID) blocking += "endpoint_invalid"
        if (endpointClassification == RecommendationEndpointClassification.EXTERNAL_HTTP && properties.externalProviders.requireHttpsOutsideLocal) {
            blocking += "endpoint_https_required"
        }
        if (endpointClassification == RecommendationEndpointClassification.LOCAL_STUB && !openAi.smoke.enabled) {
            blocking += "local_stub_smoke_not_enabled"
        }
        val readiness = when {
            prodBlocked -> RecommendationProviderReadinessStatus.PRODUCTION_BLOCKED
            blocking.any { it == "profile_not_allowed" } -> RecommendationProviderReadinessStatus.BLOCKED_BY_ENVIRONMENT
            blocking.isNotEmpty() -> RecommendationProviderReadinessStatus.MISCONFIGURED
            latest?.outcome == RecommendationProviderDiagnosticOutcome.FAILED &&
                latest.failureCategory in TRANSIENT_FAILURES -> RecommendationProviderReadinessStatus.TEMPORARILY_UNAVAILABLE
            endpointClassification == RecommendationEndpointClassification.LOCAL_STUB || openAi.smoke.enabled ->
                RecommendationProviderReadinessStatus.READY_FOR_LOCAL_SMOKE
            openAi.activationMode == ExternalRecommendationActivationMode.RUNTIME_GENERATION ->
                RecommendationProviderReadinessStatus.READY_FOR_NON_PRODUCTION
            else -> RecommendationProviderReadinessStatus.READY_FOR_LOCAL_SMOKE
        }
        return summary.toReadiness(readiness, endpointClassification, blocking, latest, latestSuccessful)
    }

    private fun unknownReadiness(providerId: RecommendationProviderId): RecommendationProviderReadiness =
        RecommendationProviderReadiness(
            providerId = providerId,
            readiness = RecommendationProviderReadinessStatus.NOT_CONFIGURED,
            lifecycle = RecommendationProviderLifecycle.MISCONFIGURED,
            active = false,
            enabled = false,
            endpointClassification = RecommendationEndpointClassification.INVALID,
            environmentClass = environmentClass(),
            fallbackConfigured = false,
            productionUseBlocked = productionBlocked(),
            lastSmokeOutcome = null,
            lastSmokeAt = null,
            lastSuccessfulSmokeAt = null,
            consecutiveFailureBand = "none",
            latencyBand = "unknown",
            validationOutcome = RecommendationResponseValidationOutcome.NOT_APPLICABLE,
            failureCategory = RecommendationFailureCategory.CONFIGURATION_ERROR,
            blockingReasons = listOf("provider_not_registered"),
            capabilities = emptySet(),
            activeModel = null,
            promptVersion = "not_configured"
        )

    private fun RecommendationProviderSummary.toReadiness(
        readiness: RecommendationProviderReadinessStatus,
        endpointClassification: RecommendationEndpointClassification,
        blockingReasons: List<String>,
        latest: RecommendationProviderDiagnostic? = diagnostics.latest(providerId),
        latestSuccessful: RecommendationProviderDiagnostic? = diagnostics.latestSuccessful(providerId)
    ): RecommendationProviderReadiness =
        RecommendationProviderReadiness(
            providerId = providerId,
            readiness = readiness,
            lifecycle = lifecycle,
            active = active,
            enabled = status == RecommendationProviderStatus.ENABLED,
            endpointClassification = endpointClassification,
            environmentClass = environmentClass(),
            fallbackConfigured = properties.providers.openai.allowFallbackToInternalDemo,
            productionUseBlocked = productionBlocked(),
            lastSmokeOutcome = latest?.outcome,
            lastSmokeAt = latest?.completedAt,
            lastSuccessfulSmokeAt = latestSuccessful?.completedAt,
            consecutiveFailureBand = consecutiveFailureBand(providerId),
            latencyBand = latest?.latencyBand ?: "unknown",
            validationOutcome = latest?.responseValidationOutcome ?: RecommendationResponseValidationOutcome.NOT_APPLICABLE,
            failureCategory = blockingReasons.takeIf { it.isNotEmpty() }?.let { RecommendationFailureCategory.CONFIGURATION_ERROR }
                ?: latest?.failureCategory,
            blockingReasons = blockingReasons.sorted(),
            capabilities = capabilities,
            activeModel = activeModel,
            promptVersion = promptVersion
        )

    private fun endpointClassification(providerId: RecommendationProviderId): RecommendationEndpointClassification {
        if (providerId.value != TaskRecommendationProviderRegistry.OPENAI_PROVIDER_ID) return RecommendationEndpointClassification.INVALID
        val endpoint = properties.providers.openai.endpoint
        if (properties.providers.openai.smoke.fixtureModeEnabled) return RecommendationEndpointClassification.LOCAL_STUB
        val uri = runCatching { URI.create(endpoint) }.getOrNull() ?: return RecommendationEndpointClassification.INVALID
        val local = properties.externalProviders.localEndpointAllowlist.any { endpoint.startsWith(it) }
        return when {
            local -> RecommendationEndpointClassification.LOCAL_STUB
            uri.scheme == "https" -> RecommendationEndpointClassification.EXTERNAL_HTTPS
            uri.scheme == "http" -> RecommendationEndpointClassification.EXTERNAL_HTTP
            else -> RecommendationEndpointClassification.INVALID
        }
    }

    private fun smokeFixtureModeAllowed(providerId: RecommendationProviderId): Boolean =
        providerId.value == TaskRecommendationProviderRegistry.OPENAI_PROVIDER_ID &&
            properties.providers.openai.smoke.enabled &&
            properties.providers.openai.smoke.fixtureModeEnabled &&
            !productionBlocked()

    private fun syntheticContext(now: java.time.Instant): SanitizedReservationRecommendationContext =
        SanitizedReservationRecommendationContext(
            reservationId = SYNTHETIC_RESERVATION_ID,
            reservationStatus = "CONFIRMED",
            stayStatus = "PRE_ARRIVAL",
            arrivalDate = LocalDate.of(2030, 1, 15),
            departureDate = LocalDate.of(2030, 1, 17),
            nights = 2,
            adultOccupancy = 2,
            childOccupancy = 0,
            roomAssigned = true,
            deterministicTaskCreated = false,
            deterministicAutomationOutcomes = setOf("synthetic_smoke_context"),
            taskBacklogBand = "low",
            openTaskCountBand = "low",
            overdueTaskCountBand = "none",
            unresolvedAutomationFailure = false,
            activeRecommendationCountBand = "none",
            roomAssignmentCompleteness = "assigned",
            stayProximityBand = "upcoming",
            lifecycleChangeRecencyBand = "recent",
            propertyCapabilityFlags = setOf("synthetic", "read_only"),
            now = now
        )

    private fun diagnostic(
        providerId: RecommendationProviderId,
        started: java.time.Instant,
        completed: java.time.Instant,
        outcome: RecommendationProviderDiagnosticOutcome,
        failureCategory: RecommendationFailureCategory?,
        validationOutcome: RecommendationResponseValidationOutcome,
        elapsed: Duration
    ): RecommendationProviderDiagnostic =
        RecommendationProviderDiagnostic(
            providerId = providerId,
            diagnosticType = RecommendationProviderDiagnosticType.SMOKE_TEST,
            triggerType = RecommendationProviderDiagnosticTrigger.OPERATOR,
            startedAt = started,
            completedAt = completed,
            outcome = outcome,
            failureCategory = failureCategory,
            latencyBand = latencyBand(elapsed),
            retryCount = 0,
            responseValidationOutcome = validationOutcome,
            promptVersion = properties.providers.openai.promptVersion,
            modelIdentifier = properties.providers.openai.model,
            environmentClass = environmentClass(),
            endpointClassification = endpointClassification(providerId),
            createdAt = started
        )

    private fun consecutiveFailureBand(providerId: RecommendationProviderId): String =
        when (diagnostics.find(RecommendationProviderDiagnosticFilter(providerId = providerId, page = 0, size = 10)).content
            .takeWhile { it.outcome == RecommendationProviderDiagnosticOutcome.FAILED }.size) {
            0 -> "none"
            1 -> "one"
            in 2..3 -> "few"
            else -> "many"
        }

    private fun latencyBand(duration: Duration): String =
        when {
            duration.toMillis() < 100 -> "lt_100ms"
            duration.toMillis() < 500 -> "lt_500ms"
            duration.toMillis() < 2_000 -> "lt_2s"
            else -> "gte_2s"
        }

    private fun externalProfileAllowed(providerProfiles: List<String>): Boolean {
        val allowed = providerProfiles.ifEmpty { properties.externalProviders.allowedProfiles }
        if (allowed.isEmpty()) return true
        return activeProfiles().any { it in allowed }
    }

    private fun environmentClass(): String =
        when {
            activeProfiles().any { it == "prod" || it == "production" } -> "production"
            activeProfiles().any { it == "test" } -> "test"
            activeProfiles().any { it == "local" || it == "dev" } -> "local"
            else -> "non_production"
        }

    private fun productionBlocked(): Boolean =
        properties.externalProviders.productionProhibited && environmentClass() == "production"

    private fun activeProfiles(): Set<String> =
        environment?.activeProfiles?.map { it.lowercase() }?.toSet().orEmpty()

    private fun audit(action: String, outcome: String, actorUserId: UUID?) {
        auditSink.record(ReservationTaskRecommendationAuditEvent(actorUserId, null, action, outcome, PersistenceInstant.now(clock)))
    }

    private fun recordReadinessMetric(readiness: RecommendationProviderReadinessStatus) {
        observability.incrementCounter(
            "hotelopai.reservation.task_recommendation.provider_readiness.total",
            "provider" to TaskRecommendationProviderRegistry.OPENAI_PROVIDER_ID,
            "readiness" to readiness.name.lowercase(),
            "environment_class" to environmentClass(),
            "outcome" to "inspected"
        )
    }

    private fun recordSmokeMetric(
        outcome: String,
        fixtureMode: RecommendationSmokeFixtureMode?,
        failureCategory: RecommendationFailureCategory?,
        latency: String
    ) {
        observability.incrementCounter(
            "hotelopai.reservation.task_recommendation.provider_smoke.total",
            "provider" to TaskRecommendationProviderRegistry.OPENAI_PROVIDER_ID,
            "fixture_mode" to (fixtureMode?.name?.lowercase() ?: "none"),
            "outcome" to outcome,
            "failure_category" to (failureCategory?.name?.lowercase() ?: "none")
        )
        observability.recordTimer(
            "hotelopai.reservation.task_recommendation.provider_smoke.duration",
            when (latency) {
                "lt_100ms" -> Duration.ofMillis(50)
                "lt_500ms" -> Duration.ofMillis(250)
                "lt_2s" -> Duration.ofSeconds(1)
                else -> Duration.ofSeconds(2)
            },
            "provider" to TaskRecommendationProviderRegistry.OPENAI_PROVIDER_ID,
            "outcome" to outcome,
            "failure_category" to (failureCategory?.name?.lowercase() ?: "none")
        )
    }

    companion object {
        val SYNTHETIC_RESERVATION_ID: UUID = UUID.fromString("00000000-0000-0000-0000-0000000013f0")
        private val SMOKE_READY_STATES = setOf(
            RecommendationProviderReadinessStatus.READY_FOR_LOCAL_SMOKE,
            RecommendationProviderReadinessStatus.READY_FOR_NON_PRODUCTION,
            RecommendationProviderReadinessStatus.TEMPORARILY_UNAVAILABLE
        )
        private val TRANSIENT_FAILURES = setOf(
            RecommendationFailureCategory.NETWORK_ERROR,
            RecommendationFailureCategory.TIMEOUT,
            RecommendationFailureCategory.RATE_LIMIT,
            RecommendationFailureCategory.PROVIDER_UNAVAILABLE
        )
    }
}

data class RecommendationSmokeTestResult(
    val diagnostic: RecommendationProviderDiagnostic,
    val readiness: RecommendationProviderReadiness,
    val recommendationCount: Int
)
