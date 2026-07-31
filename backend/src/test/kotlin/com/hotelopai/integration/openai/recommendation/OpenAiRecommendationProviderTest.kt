package com.hotelopai.integration.openai.recommendation

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.hotelopai.reservation.recommendation.OpenAiRecommendationProviderProperties
import com.hotelopai.reservation.recommendation.RecommendationCredentialReference
import com.hotelopai.reservation.recommendation.RecommendationCredentialResolver
import com.hotelopai.reservation.recommendation.RecommendationFailureCategory
import com.hotelopai.reservation.recommendation.RecommendationHttpClient
import com.hotelopai.reservation.recommendation.RecommendationHttpRequest
import com.hotelopai.reservation.recommendation.RecommendationHttpResponse
import com.hotelopai.reservation.recommendation.RecommendationPromptFactory
import com.hotelopai.reservation.recommendation.RecommendationPrivacyGateway
import com.hotelopai.reservation.recommendation.RecommendationProviderException
import com.hotelopai.reservation.recommendation.RecommendationProviderRetryProperties
import com.hotelopai.reservation.recommendation.ReservationTaskRecommendationProperties
import com.hotelopai.reservation.recommendation.SanitizedReservationRecommendationContext
import com.hotelopai.reservation.recommendation.StructuredRecommendationResponseValidator
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

class OpenAiRecommendationProviderTest {
    private val objectMapper = jacksonObjectMapper()
    private val clock = Clock.fixed(Instant.parse("2026-07-24T10:00:00Z"), ZoneId.of("UTC"))

    @Test
    fun `OpenAI provider is disabled by default and does not make HTTP requests`() {
        val http = RecordingHttpClient(successBody())
        val provider = provider(properties(), http)

        val failure = assertThrows(RecommendationProviderException::class.java) {
            provider.recommend(context())
        }

        assertThat(failure.failureCategory).isEqualTo(RecommendationFailureCategory.INVALID_CONFIGURATION)
        assertThat(http.requests).isEmpty()
    }

    @Test
    fun `OpenAI provider sends only sanitized context and maps structured recommendations`() {
        val http = RecordingHttpClient(successBody())
        val provider = provider(enabledOpenAiProperties(), http)

        val proposals = provider.recommend(context())

        assertThat(proposals).hasSize(1)
        assertThat(proposals.single().title).isEqualTo("Review room readiness")
        val body = http.requests.single().body
        assertThat(body).contains("reservation-task-recommendation-openai-v1", "roomAssignmentCompleteness")
        assertThat(body).doesNotContain("Ada", "RES-", "MUC", "reservationId", "propertyId", "guest", "payment")
        assertThat(http.requests.single().headers["Authorization"]).isEqualTo("Bearer test-token")
    }

    @Test
    fun `malformed OpenAI response is rejected as invalid response`() {
        val http = RecordingHttpClient("""{"choices":[{"message":{"content":"{}"}}]}""")
        val provider = provider(enabledOpenAiProperties(), http)

        val failure = assertThrows(RecommendationProviderException::class.java) {
            provider.recommend(context())
        }

        assertThat(failure.failureCategory).isEqualTo(RecommendationFailureCategory.INVALID_RESPONSE)
    }

    @Test
    fun `rate limit response is classified safely`() {
        val http = RecordingHttpClient("""{"error":{"message":"rate limited"}}""", statusCode = 429)
        val provider = provider(enabledOpenAiProperties(maxAttempts = 1), http)

        val failure = assertThrows(RecommendationProviderException::class.java) {
            provider.recommend(context())
        }

        assertThat(failure.failureCategory).isEqualTo(RecommendationFailureCategory.RATE_LIMIT)
        assertThat(failure.message).doesNotContain("test-token", "rate limited")
    }

    private fun provider(
        properties: ReservationTaskRecommendationProperties,
        httpClient: RecordingHttpClient
    ): OpenAiRecommendationProvider =
        OpenAiRecommendationProvider(
            properties = properties,
            privacyGateway = RecommendationPrivacyGateway(objectMapper),
            promptFactory = RecommendationPromptFactory(),
            responseValidator = StructuredRecommendationResponseValidator(properties),
            credentialResolver = RecommendationCredentialResolver { "test-token" },
            httpClient = httpClient,
            objectMapper = objectMapper,
            clock = clock
        )

    private fun properties(openAi: OpenAiRecommendationProviderProperties = OpenAiRecommendationProviderProperties()): ReservationTaskRecommendationProperties =
        ReservationTaskRecommendationProperties(
            providers = com.hotelopai.reservation.recommendation.RecommendationProviderGovernanceProperties(openai = openAi)
        )

    private fun enabledOpenAiProperties(maxAttempts: Int = 1): ReservationTaskRecommendationProperties =
        properties(
            OpenAiRecommendationProviderProperties(
                enabled = true,
                model = "gpt-test",
                credentialReference = RecommendationCredentialReference(name = "OPENAI_API_KEY"),
                retryPolicy = RecommendationProviderRetryProperties(maxAttempts = maxAttempts, initialBackoff = Duration.ofMillis(1), maxBackoff = Duration.ofMillis(1))
            )
        )

    private fun context(): SanitizedReservationRecommendationContext =
        SanitizedReservationRecommendationContext(
            reservationId = UUID.fromString("00000000-0000-0000-0000-000000001234"),
            reservationStatus = "CONFIRMED",
            stayStatus = "NOT_ARRIVED",
            arrivalDate = LocalDate.parse("2026-07-25"),
            departureDate = LocalDate.parse("2026-07-27"),
            nights = 2,
            adultOccupancy = 2,
            childOccupancy = 0,
            roomAssigned = false,
            deterministicTaskCreated = true,
            deterministicAutomationOutcomes = setOf("CREATED"),
            taskBacklogBand = "low",
            activeRecommendationCountBand = "none",
            roomAssignmentCompleteness = "unassigned",
            stayProximityBand = "near",
            lifecycleChangeRecencyBand = "recent",
            propertyCapabilityFlags = setOf("canonical_reservation_snapshot"),
            now = clock.instant()
        )

    private fun successBody(): String {
        val content = objectMapper.writeValueAsString(
            mapOf(
                "recommendations" to listOf(
                    mapOf(
                        "category" to "ROOM_ASSIGNMENT_REVIEW",
                        "priority" to "MEDIUM",
                        "confidence" to "MEDIUM",
                        "intentType" to "SHIFT_HANDOVER",
                        "proposedTaskTitle" to "Review room readiness",
                        "proposedTaskSummary" to "Review operational room readiness before handoff.",
                        "explanation" to mapOf(
                            "situation" to "Confirmed arrival has incomplete room assignment.",
                            "rationale" to "Operations should confirm readiness before handoff.",
                            "supportingSignals" to listOf("reservation_status_confirmed", "room_unassigned")
                        )
                    )
                )
            )
        )
        return objectMapper.writeValueAsString(
            mapOf("choices" to listOf(mapOf("message" to mapOf("content" to content))))
        )
    }

    private class RecordingHttpClient(
        private val responseBody: String,
        private val statusCode: Int = 200
    ) : RecommendationHttpClient {
        val requests = mutableListOf<RecommendationHttpRequest>()

        override fun postJson(request: RecommendationHttpRequest): RecommendationHttpResponse {
            requests += request
            return RecommendationHttpResponse(statusCode, emptyMap(), responseBody)
        }
    }
}
