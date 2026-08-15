package com.hotelopai.reservation.recommendation

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

@ConfigurationProperties("ops.ai.reservation.task-recommendations")
data class ReservationTaskRecommendationProperties(
    val enabled: Boolean = false,
    val hotelId: UUID? = null,
    val batchSize: Int = 10,
    val maxRecommendationsPerReservation: Int = 5,
    val maxReservationsPerBatch: Int = 10,
    val maxAttempts: Int = 3,
    val retryDelay: Duration = Duration.ofMinutes(5),
    val requestTimeout: Duration = Duration.ofSeconds(5),
    val dueTime: LocalTime = LocalTime.of(17, 0),
    val timezone: ZoneId = ZoneId.of("UTC"),
    val expiration: Duration = Duration.ofDays(7),
    val maximumReservationAge: Duration = Duration.ofDays(30),
    val maximumReviewAge: Duration = Duration.ofDays(7),
    val providerName: String = "internal-demo",
    val activeProvider: String = providerName,
    val promptVersion: String = "reservation-task-recommendation-demo-v1",
    val modelIdentifier: String? = null,
    val allowedProfiles: List<String> = emptyList(),
    val externalProviders: ExternalRecommendationProviderActivationProperties = ExternalRecommendationProviderActivationProperties(),
    val providers: RecommendationProviderGovernanceProperties = RecommendationProviderGovernanceProperties(),
    val pilot: RecommendationPilotProperties = RecommendationPilotProperties(),
    val pilotSchedule: RecommendationPilotScheduleProperties = RecommendationPilotScheduleProperties(),
    val pilotReview: RecommendationPilotReviewProperties = RecommendationPilotReviewProperties(),
    val schedule: RecommendationGenerationScheduleProperties = RecommendationGenerationScheduleProperties(),
    val retention: RecommendationRetentionProperties = RecommendationRetentionProperties()
) {
    init {
        if (enabled) {
            require(hotelId != null && hotelId != UUID(0L, 0L)) {
                "reservation task recommendations hotel id must be configured when enabled"
            }
        }
        require(batchSize in 1..100) { "reservation task recommendations batch size must be between 1 and 100" }
        require(maxRecommendationsPerReservation in 1..20) {
            "reservation task recommendations max recommendations per reservation must be between 1 and 20"
        }
        require(maxReservationsPerBatch in 1..100) {
            "reservation task recommendations max reservations per batch must be between 1 and 100"
        }
        require(maxAttempts in 1..10) { "reservation task recommendations max attempts must be between 1 and 10" }
        require(!retryDelay.isNegative && !retryDelay.isZero) {
            "reservation task recommendations retry delay must be positive"
        }
        require(!requestTimeout.isNegative && !requestTimeout.isZero) {
            "reservation task recommendations request timeout must be positive"
        }
        require(!expiration.isNegative && !expiration.isZero) {
            "reservation task recommendations expiration must be positive"
        }
        require(!maximumReservationAge.isNegative && !maximumReservationAge.isZero) {
            "reservation task recommendations maximum reservation age must be positive"
        }
        require(!maximumReviewAge.isNegative && !maximumReviewAge.isZero) {
            "reservation task recommendations maximum review age must be positive"
        }
        require(providerName.isNotBlank()) { "reservation task recommendations provider name must not be blank" }
        require(activeProvider.isNotBlank()) { "reservation task recommendations active provider must not be blank" }
        require(promptVersion.isNotBlank()) { "reservation task recommendations prompt version must not be blank" }
        if (schedule.enabled) {
            require(enabled) { "reservation task recommendations must be enabled before schedule generation is enabled" }
        }
        if (pilotSchedule.enabled) {
            require(pilot.enabled) { "recommendation pilot must be enabled before pilot scheduling is enabled" }
        }
    }
}

data class RecommendationPilotProperties(
    val enabled: Boolean = false,
    val allowedProfiles: List<String> = listOf("local", "test"),
    val allowedProviderIds: List<String> = listOf("openai"),
    val allowedPropertyScopes: List<String> = emptyList(),
    val maxReservationsPerRun: Int = 5,
    val maxRecommendationsPerRun: Int = 10,
    val dailyRequestBudget: Int = 25,
    val dailyTokenBudget: Long? = null,
    val pilotStartDate: java.time.LocalDate? = null,
    val pilotEndDate: java.time.LocalDate? = null,
    val minimumProviderReadiness: RecommendationProviderReadinessStatus = RecommendationProviderReadinessStatus.READY_FOR_LOCAL_SMOKE,
    val requiredSuccessfulSmokeAge: Duration = Duration.ofHours(24),
    val mandatoryOperatorApprovalMode: Boolean = true,
    val maximumCandidateAge: Duration = Duration.ofDays(30)
) {
    init {
        require(maxReservationsPerRun in 1..100) { "recommendation pilot max reservations per run must be between 1 and 100" }
        require(maxRecommendationsPerRun in 1..500) { "recommendation pilot max recommendations per run must be between 1 and 500" }
        require(dailyRequestBudget in 1..10_000) { "recommendation pilot daily request budget must be between 1 and 10000" }
        dailyTokenBudget?.let { require(it in 1..100_000_000) { "recommendation pilot daily token budget must be positive and bounded" } }
        require(requiredSuccessfulSmokeAge > Duration.ZERO) { "recommendation pilot required smoke age must be positive" }
        require(maximumCandidateAge > Duration.ZERO) { "recommendation pilot maximum candidate age must be positive" }
        require(allowedProviderIds.all { it.isNotBlank() }) { "recommendation pilot allowed provider ids must not be blank" }
        require(allowedPropertyScopes.all { it.isNotBlank() }) { "recommendation pilot allowed property scopes must not be blank" }
        if (enabled) {
            require(mandatoryOperatorApprovalMode) {
                "recommendation pilot requires mandatory operator approval mode"
            }
            require(allowedProviderIds.isNotEmpty()) {
                "recommendation pilot allowed provider ids must be configured when enabled"
            }
        }
        if (pilotStartDate != null && pilotEndDate != null) {
            require(!pilotEndDate.isBefore(pilotStartDate)) {
                "recommendation pilot end date must not be before start date"
            }
        }
    }
}

data class RecommendationPilotScheduleProperties(
    val enabled: Boolean = false,
    val executionInterval: Duration = Duration.ofHours(6),
    val startupDelay: Duration = Duration.ofMinutes(2),
    val batchSize: Int = 5,
    val maxRunsPerDay: Int = 2,
    val allowedProfiles: List<String> = listOf("local", "test"),
    val lockTimeout: Duration = Duration.ofMinutes(10),
    val minimumIntervalBetweenRuns: Duration = Duration.ofHours(1),
    val pilotStartDateOverride: java.time.LocalDate? = null,
    val pilotEndDateOverride: java.time.LocalDate? = null,
    val retentionCleanupEnabled: Boolean = false,
    val cleanupExecutionInterval: Duration = Duration.ofHours(24),
    val cleanupBatchSize: Int = 100
) {
    init {
        require(!executionInterval.isNegative && !executionInterval.isZero) {
            "recommendation pilot schedule execution interval must be positive"
        }
        require(!startupDelay.isNegative) {
            "recommendation pilot schedule startup delay must not be negative"
        }
        require(batchSize in 1..100) {
            "recommendation pilot schedule batch size must be between 1 and 100"
        }
        require(maxRunsPerDay in 1..100) {
            "recommendation pilot schedule max runs per day must be between 1 and 100"
        }
        require(!lockTimeout.isNegative && !lockTimeout.isZero) {
            "recommendation pilot schedule lock timeout must be positive"
        }
        require(!minimumIntervalBetweenRuns.isNegative && !minimumIntervalBetweenRuns.isZero) {
            "recommendation pilot schedule minimum interval between runs must be positive"
        }
        require(!cleanupExecutionInterval.isNegative && !cleanupExecutionInterval.isZero) {
            "recommendation pilot schedule cleanup interval must be positive"
        }
        require(cleanupBatchSize in 1..1_000) {
            "recommendation pilot schedule cleanup batch size must be between 1 and 1000"
        }
        require(allowedProfiles.all { it.isNotBlank() }) {
            "recommendation pilot schedule allowed profiles must not be blank"
        }
        if (pilotStartDateOverride != null && pilotEndDateOverride != null) {
            require(!pilotEndDateOverride.isBefore(pilotStartDateOverride)) {
                "recommendation pilot schedule override end date must not be before start date"
            }
        }
    }
}

data class RecommendationPilotReviewProperties(
    val maxBulkReviewItems: Int = 50,
    val maxDecisionNoteLength: Int = 500,
    val maxExportRows: Int = 1_000,
    val maxExportDateRange: Duration = Duration.ofDays(31)
) {
    init {
        require(maxBulkReviewItems in 1..500) { "recommendation pilot review max bulk items must be between 1 and 500" }
        require(maxDecisionNoteLength in 0..500) { "recommendation pilot review decision note length must be between 0 and 500" }
        require(maxExportRows in 1..10_000) { "recommendation pilot review max export rows must be between 1 and 10000" }
        require(maxExportDateRange > Duration.ZERO) { "recommendation pilot review max export date range must be positive" }
    }
}

data class RecommendationProviderGovernanceProperties(
    val internalDemo: RecommendationConfiguredProviderProperties = RecommendationConfiguredProviderProperties(
        enabled = true,
        displayName = "Internal Demo Recommendations",
        deterministic = true
    ),
    val openai: OpenAiRecommendationProviderProperties = OpenAiRecommendationProviderProperties()
)

data class RecommendationConfiguredProviderProperties(
    val enabled: Boolean = true,
    val displayName: String = "Internal Demo Recommendations",
    val requestTimeout: Duration? = null,
    val modelIdentifier: String? = null,
    val promptVersion: String? = null,
    val deterministic: Boolean = false,
    val allowedProfiles: List<String> = emptyList()
) {
    init {
        require(displayName.isNotBlank()) { "recommendation provider display name must not be blank" }
        requestTimeout?.let {
            require(!it.isNegative && !it.isZero) { "recommendation provider request timeout must be positive" }
        }
        promptVersion?.let {
            require(it.isNotBlank()) { "recommendation provider prompt version must not be blank" }
        }
    }
}

data class OpenAiRecommendationProviderProperties(
    val enabled: Boolean = false,
    val displayName: String = "OpenAI Recommendations",
    val endpoint: String = "https://api.openai.com/v1/chat/completions",
    val model: String? = null,
    val timeout: Duration = Duration.ofSeconds(10),
    val retryPolicy: RecommendationProviderRetryProperties = RecommendationProviderRetryProperties(),
    val maximumTokens: Int = 512,
    val temperature: Double = 0.0,
    val topP: Double = 1.0,
    val allowedProfiles: List<String> = emptyList(),
    val activationMode: ExternalRecommendationActivationMode = ExternalRecommendationActivationMode.SMOKE_TEST_ONLY,
    val allowFallbackToInternalDemo: Boolean = false,
    val smoke: RecommendationProviderSmokeProperties = RecommendationProviderSmokeProperties(),
    val credentialReference: RecommendationCredentialReference? = null,
    val promptTemplateId: String = "reservation-task-recommendation-v1",
    val promptVersion: String = "reservation-task-recommendation-openai-v1"
) {
    init {
        require(displayName.isNotBlank()) { "OpenAI recommendation provider display name must not be blank" }
        require(endpoint.isNotBlank()) { "OpenAI recommendation provider endpoint must not be blank" }
        require(!timeout.isNegative && !timeout.isZero) { "OpenAI recommendation provider timeout must be positive" }
        require(maximumTokens in 1..16_384) { "OpenAI recommendation provider maximum tokens must be between 1 and 16384" }
        require(temperature in 0.0..2.0) { "OpenAI recommendation provider temperature must be between 0 and 2" }
        require(topP in 0.0..1.0) { "OpenAI recommendation provider top-p must be between 0 and 1" }
        require(promptTemplateId.isNotBlank()) { "OpenAI recommendation provider prompt template id must not be blank" }
        require(promptVersion.isNotBlank()) { "OpenAI recommendation provider prompt version must not be blank" }
        if (enabled) {
            require(!model.isNullOrBlank()) { "OpenAI recommendation provider model must be configured when enabled" }
            require(credentialReference != null) {
                "OpenAI recommendation provider credential reference must be configured when enabled"
            }
        }
    }
}

data class ExternalRecommendationProviderActivationProperties(
    val allowedProfiles: List<String> = listOf("local", "test"),
    val productionProhibited: Boolean = true,
    val requireHttpsOutsideLocal: Boolean = true,
    val localEndpointAllowlist: List<String> = listOf("http://localhost", "http://127.0.0.1"),
    val diagnosticsRetention: Duration = Duration.ofDays(30),
    val diagnosticsCleanupBatchSize: Int = 100
) {
    init {
        require(diagnosticsRetention > Duration.ZERO) { "external recommendation provider diagnostics retention must be positive" }
        require(diagnosticsCleanupBatchSize in 1..1_000) {
            "external recommendation provider diagnostics cleanup batch size must be between 1 and 1000"
        }
        require(localEndpointAllowlist.all { it.isNotBlank() }) {
            "external recommendation provider local endpoint allowlist entries must not be blank"
        }
    }
}

data class RecommendationProviderSmokeProperties(
    val enabled: Boolean = false,
    val fixtureModeEnabled: Boolean = false
)

data class RecommendationProviderRetryProperties(
    val maxAttempts: Int = 2,
    val initialBackoff: Duration = Duration.ofMillis(250),
    val maxBackoff: Duration = Duration.ofSeconds(2)
) {
    init {
        require(maxAttempts in 1..5) { "recommendation provider retry max attempts must be between 1 and 5" }
        require(!initialBackoff.isNegative && !initialBackoff.isZero) {
            "recommendation provider retry initial backoff must be positive"
        }
        require(!maxBackoff.isNegative && !maxBackoff.isZero) {
            "recommendation provider retry max backoff must be positive"
        }
        require(maxBackoff >= initialBackoff) {
            "recommendation provider retry max backoff must be greater than or equal to initial backoff"
        }
    }
}

data class RecommendationCredentialReference(
    val source: RecommendationCredentialSource = RecommendationCredentialSource.ENVIRONMENT,
    val name: String
) {
    init {
        require(name.isNotBlank()) { "recommendation credential reference name must not be blank" }
    }
}

enum class RecommendationCredentialSource {
    ENVIRONMENT,
    SECRET_REFERENCE,
    VAULT
}

data class RecommendationGenerationScheduleProperties(
    val enabled: Boolean = false,
    val executionInterval: Duration = Duration.ofMinutes(5),
    val startupDelay: Duration = Duration.ofMinutes(2),
    val batchSize: Int = 10,
    val maxReservationsPerExecution: Int = 10,
    val lockTimeout: Duration = Duration.ofMinutes(5),
    val abandonedLeaseThreshold: Duration = Duration.ofMinutes(10),
    val allowedProfiles: List<String> = emptyList(),
    val retentionCleanupEnabled: Boolean = false,
    val cleanupExecutionInterval: Duration = Duration.ofHours(12),
    val cleanupBatchSize: Int = 100
) {
    init {
        require(!executionInterval.isNegative && !executionInterval.isZero) {
            "reservation task recommendation schedule execution interval must be positive"
        }
        require(!startupDelay.isNegative) {
            "reservation task recommendation schedule startup delay must not be negative"
        }
        require(batchSize in 1..100) {
            "reservation task recommendation schedule batch size must be between 1 and 100"
        }
        require(maxReservationsPerExecution in 1..1_000) {
            "reservation task recommendation schedule max reservations per execution must be between 1 and 1000"
        }
        require(!lockTimeout.isNegative && !lockTimeout.isZero) {
            "reservation task recommendation schedule lock timeout must be positive"
        }
        require(!abandonedLeaseThreshold.isNegative && !abandonedLeaseThreshold.isZero) {
            "reservation task recommendation schedule abandoned lease threshold must be positive"
        }
        require(!cleanupExecutionInterval.isNegative && !cleanupExecutionInterval.isZero) {
            "reservation task recommendation cleanup interval must be positive"
        }
        require(cleanupBatchSize in 1..1_000) {
            "reservation task recommendation cleanup batch size must be between 1 and 1000"
        }
    }
}

data class RecommendationRetentionProperties(
    val completedRunRetention: Duration = Duration.ofDays(30),
    val rejectedRecommendationRetention: Duration = Duration.ofDays(30),
    val failedRunRetention: Duration = Duration.ofDays(14),
    val appliedRecommendationRetention: Duration = Duration.ofDays(180)
) {
    init {
        listOf(completedRunRetention, rejectedRecommendationRetention, failedRunRetention, appliedRecommendationRetention).forEach {
            require(!it.isNegative && !it.isZero) { "reservation task recommendation retention durations must be positive" }
        }
    }
}
