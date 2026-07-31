package com.hotelopai.reservation.recommendation

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Component

@Component
class RecommendationPrivacyGateway(
    private val objectMapper: ObjectMapper
) {
    fun outboundContext(context: SanitizedReservationRecommendationContext): OutboundRecommendationContext =
        OutboundRecommendationContext(
            schemaVersion = context.contextSchemaVersion,
            reservationStatus = context.reservationStatus,
            stayStatus = context.stayStatus,
            stayTimingBand = context.stayProximityBand,
            nightsBand = nightsBand(context.nights),
            occupancyBand = occupancyBand(context.adultOccupancy + context.childOccupancy),
            roomAssignmentCompleteness = context.roomAssignmentCompleteness,
            deterministicAutomationOutcomes = context.deterministicAutomationOutcomes.toSortedSet(),
            taskBacklogBand = context.taskBacklogBand,
            activeRecommendationCountBand = context.activeRecommendationCountBand,
            unresolvedAutomationFailure = context.unresolvedAutomationFailure,
            propertyCapabilityFlags = context.propertyCapabilityFlags.toSortedSet()
        ).also(::assertSafeForExternalProvider)

    fun assertSafeForExternalProvider(context: OutboundRecommendationContext) {
        val serialized = objectMapper.writeValueAsString(context)
        FORBIDDEN_PATTERNS.forEach { pattern ->
            require(!pattern.containsMatchIn(serialized)) {
                "Outbound recommendation context contains restricted data."
            }
        }
    }

    private fun nightsBand(nights: Long): String =
        when {
            nights <= 1 -> "one_night"
            nights <= 3 -> "short"
            nights <= 7 -> "standard"
            else -> "extended"
        }

    private fun occupancyBand(occupants: Int): String =
        when {
            occupants <= 1 -> "single"
            occupants == 2 -> "double"
            occupants <= 4 -> "group"
            else -> "large_group"
        }

    companion object {
        private val FORBIDDEN_PATTERNS = listOf(
            Regex("guest", RegexOption.IGNORE_CASE),
            Regex("email", RegexOption.IGNORE_CASE),
            Regex("phone", RegexOption.IGNORE_CASE),
            Regex("note", RegexOption.IGNORE_CASE),
            Regex("payment", RegexOption.IGNORE_CASE),
            Regex("reservationId", RegexOption.IGNORE_CASE),
            Regex("propertyId", RegexOption.IGNORE_CASE),
            Regex("external", RegexOption.IGNORE_CASE),
            Regex("webhook", RegexOption.IGNORE_CASE),
            Regex("pms", RegexOption.IGNORE_CASE)
        )
    }
}
