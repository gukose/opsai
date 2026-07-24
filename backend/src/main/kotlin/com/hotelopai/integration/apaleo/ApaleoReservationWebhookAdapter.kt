package com.hotelopai.integration.apaleo

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.hotelopai.pms.application.PmsCredentialResolver
import com.hotelopai.pms.application.PmsFailureCategory
import com.hotelopai.pms.application.PmsProviderRegistry
import com.hotelopai.reservation.application.ReservationWebhookAdapter
import com.hotelopai.reservation.application.ReservationWebhookEvent
import com.hotelopai.reservation.application.ReservationWebhookEventCategory
import com.hotelopai.reservation.application.ReservationWebhookProperties
import com.hotelopai.reservation.application.ReservationWebhookRequest
import com.hotelopai.reservation.application.ReservationWebhookVerificationResult
import com.hotelopai.shared.kernel.PersistenceInstant
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneOffset
import kotlin.math.abs

@Component
@EnableConfigurationProperties(ReservationWebhookProperties::class)
class ApaleoReservationWebhookAdapter(
    private val objectMapper: ObjectMapper,
    private val providerRegistry: PmsProviderRegistry,
    private val credentialResolver: PmsCredentialResolver,
    private val properties: ReservationWebhookProperties
) : ReservationWebhookAdapter {
    override val providerId: String = "apaleo"

    override fun validateConfiguration(): ReservationWebhookVerificationResult.Rejected? =
        configuredToken().rejection

    override fun verifyAndExtract(request: ReservationWebhookRequest): ReservationWebhookVerificationResult {
        val expectedToken = configuredToken()
            .also { validation -> validation.rejection?.let { return it } }
            .token
            ?: return rejected(PmsFailureCategory.CONFIGURATION, "webhook_token_unavailable")
        if (!constantTimeEquals(expectedToken, request.queryToken.orEmpty())) {
            return rejected(PmsFailureCategory.AUTHENTICATION, "invalid_webhook_token")
        }
        if (request.contentType?.substringBefore(";")?.trim()?.lowercase() != "application/json") {
            return rejected(PmsFailureCategory.VALIDATION, "unsupported_content_type")
        }
        val root = runCatching { objectMapper.readTree(request.body) }
            .getOrElse { return rejected(PmsFailureCategory.VALIDATION, "malformed_payload") }

        val id = root.text("id") ?: return rejected(PmsFailureCategory.VALIDATION, "missing_event_id")
        val topic = root.text("topic")?.lowercase().orEmpty()
        val type = root.text("type")?.lowercase().orEmpty()
        val propertyId = root.text("propertyId")
        val timestamp = root.long("timestamp")?.let(Instant::ofEpochMilli)
        if (timestamp != null && abs(request.receivedAt.toEpochMilli() - timestamp.toEpochMilli()) > properties.timestampTolerance.toMillis()) {
            return rejected(PmsFailureCategory.VALIDATION, "timestamp_outside_tolerance")
        }
        val entityId = root.path("data").text("entityId") ?: root.text("subjectId")
        val propertyHash = sha256("${request.providerId}:${propertyId.orEmpty()}")
        val entityHash = entityId?.let { sha256("${request.providerId}:$it") }

        return ReservationWebhookVerificationResult.Verified(
            ReservationWebhookEvent(
                providerId = providerId,
                providerEventId = id,
                category = mapCategory(topic, type),
                propertyScopeHash = propertyHash,
                propertyScopeLabel = if (propertyId.isNullOrBlank()) "not_configured" else "configured:${propertyHash.take(12)}",
                rawPropertyReference = propertyId,
                externalEntityHash = entityHash,
                providerEventTimestamp = timestamp,
                receivedAt = PersistenceInstant.toPersistencePrecision(request.receivedAt),
                payloadFingerprint = sha256(String(request.body, StandardCharsets.UTF_8)),
                affectedDate = timestamp?.atZone(ZoneOffset.UTC)?.toLocalDate(),
                safeMetadata = mapOf(
                    "topic" to topic.ifBlank { "unknown" },
                    "type" to type.ifBlank { "unknown" },
                    "trackingIdPresent" to request.headers.keys.any { it.equals("apaleo-tracking-id", ignoreCase = true) }.toString()
                )
            )
        )
    }

    private fun configuredToken(): ConfiguredWebhookToken {
        val providerConfig = providerRegistry.providerConfig(providerId)
            ?: return ConfiguredWebhookToken(rejected(PmsFailureCategory.CONFIGURATION, "provider_not_configured"))
        if (!providerConfig.enabled) {
            return ConfiguredWebhookToken(rejected(PmsFailureCategory.CONFIGURATION, "provider_disabled"))
        }
        val expectedTokenReference = providerConfig.settings["webhook-token-reference"].orEmpty()
        if (expectedTokenReference.isBlank()) {
            return ConfiguredWebhookToken(rejected(PmsFailureCategory.CONFIGURATION, "webhook_token_not_configured"))
        }
        val expectedToken = runCatching { credentialResolver.resolve(expectedTokenReference) }
            .getOrElse { return ConfiguredWebhookToken(rejected(PmsFailureCategory.CONFIGURATION, "webhook_token_unresolved")) }
        return ConfiguredWebhookToken(token = expectedToken)
    }

    private fun mapCategory(topic: String, type: String): ReservationWebhookEventCategory {
        if (topic == "system" && type == "healthcheck") return ReservationWebhookEventCategory.HEALTHCHECK
        if (topic != "reservation") return ReservationWebhookEventCategory.UNSUPPORTED
        return when (type) {
            "created" -> ReservationWebhookEventCategory.RESERVATION_CREATED
            "amended", "changed" -> ReservationWebhookEventCategory.RESERVATION_CHANGED
            "canceled", "cancelled" -> ReservationWebhookEventCategory.RESERVATION_CANCELLED
            "checked-in" -> ReservationWebhookEventCategory.GUEST_CHECKED_IN
            "checked-out" -> ReservationWebhookEventCategory.GUEST_CHECKED_OUT
            "unit-assigned", "unit-changed" -> ReservationWebhookEventCategory.ROOM_ASSIGNMENT_CHANGED
            else -> ReservationWebhookEventCategory.UNSUPPORTED
        }
    }

    private fun JsonNode.text(field: String): String? =
        path(field).takeIf { it.isTextual && it.asText().isNotBlank() }?.asText()

    private fun JsonNode.long(field: String): Long? =
        path(field).takeIf { it.isNumber }?.asLong()

    private fun rejected(category: PmsFailureCategory, reasonCode: String): ReservationWebhookVerificationResult.Rejected =
        ReservationWebhookVerificationResult.Rejected(category, reasonCode)

    private fun constantTimeEquals(expected: String, actual: String): Boolean {
        val expectedBytes = expected.toByteArray(StandardCharsets.UTF_8)
        val actualBytes = actual.toByteArray(StandardCharsets.UTF_8)
        return MessageDigest.isEqual(expectedBytes, actualBytes)
    }

    private fun sha256(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(StandardCharsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private data class ConfiguredWebhookToken(
        val rejection: ReservationWebhookVerificationResult.Rejected? = null,
        val token: String? = null
    )
}
