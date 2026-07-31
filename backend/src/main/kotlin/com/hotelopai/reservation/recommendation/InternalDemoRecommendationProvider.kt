package com.hotelopai.reservation.recommendation

import com.hotelopai.task.domain.TaskIntentType
import com.hotelopai.task.domain.TaskPriority
import org.springframework.stereotype.Component
import java.security.MessageDigest

@Component
class InternalDemoRecommendationProvider(
    private val properties: ReservationTaskRecommendationProperties = ReservationTaskRecommendationProperties()
) : TaskRecommendationProvider {
    override val providerName: String = "internal-demo"
    override val id: RecommendationProviderId = RecommendationProviderId(TaskRecommendationProviderRegistry.INTERNAL_DEMO_PROVIDER_ID)
    override val displayName: String = "Internal Demo Recommendations"
    override val modelIdentifier: String? = null
    override val promptVersion: String = properties.providers.internalDemo.promptVersion
        ?: properties.promptVersion
    override val capabilities: Set<RecommendationProviderCapability> = setOf(
        RecommendationProviderCapability.BATCH_GENERATION,
        RecommendationProviderCapability.STRUCTURED_EXPLANATIONS,
        RecommendationProviderCapability.CONFIDENCE_SCORING,
        RecommendationProviderCapability.RETRYABLE_EXECUTION,
        RecommendationProviderCapability.DETERMINISTIC_OUTPUT
    )

    override fun recommend(context: SanitizedReservationRecommendationContext): List<RecommendationTaskProposal> {
        val proposals = mutableListOf<RecommendationTaskProposal>()
        if (context.reservationStatus == "CONFIRMED" && !context.roomAssigned) {
            proposals += proposal(
                context,
                RecommendationCategory.ROOM_ASSIGNMENT_REVIEW,
                RecommendationConfidence.MEDIUM,
                "Confirmed stay has no room assignment.",
                "Room assignment should be reviewed before operational handoff.",
                listOf("reservation_status_confirmed", "room_unassigned"),
                TaskIntentType.SHIFT_HANDOVER,
                "Review unassigned arrival",
                "Review operational readiness for a confirmed reservation without a room assignment.",
                TaskPriority.MEDIUM
            )
        }
        if (context.adultOccupancy + context.childOccupancy >= 3) {
            proposals += proposal(
                context,
                RecommendationCategory.OCCUPANCY_REVIEW,
                RecommendationConfidence.LOW,
                "Reservation has elevated occupancy.",
                "Operational teams may need to confirm room setup capacity.",
                listOf("occupancy_elevated", "canonical_reservation_snapshot"),
                TaskIntentType.HOUSEKEEPING,
                "Review occupancy setup",
                "Review room setup requirements for an elevated-occupancy stay.",
                TaskPriority.LOW
            )
        }
        if (context.stayStatus == "IN_HOUSE" && context.deterministicTaskCreated) {
            proposals += proposal(
                context,
                RecommendationCategory.ARRIVAL_RISK_REVIEW,
                RecommendationConfidence.MEDIUM,
                "Guest is in house after deterministic automation created operational work.",
                "A follow-up review may help confirm handoff completion.",
                listOf("stay_in_house", "deterministic_task_created"),
                TaskIntentType.GUEST_REQUEST,
                "Review in-house handoff",
                "Review whether in-house operational handoff needs additional follow-up.",
                TaskPriority.LOW
            )
        }
        return proposals.sortedWith(compareBy({ it.category.name }, { it.deduplicationKey }))
    }

    private fun proposal(
        context: SanitizedReservationRecommendationContext,
        category: RecommendationCategory,
        confidence: RecommendationConfidence,
        situation: String,
        rationale: String,
        signals: List<String>,
        intentType: TaskIntentType,
        title: String,
        description: String,
        priority: TaskPriority
    ): RecommendationTaskProposal {
        val localDate = context.now.atZone(properties.timezone).toLocalDate()
        val dueAt = localDate.atTime(properties.dueTime).atZone(properties.timezone).toInstant()
            .let { if (it.isAfter(context.now)) it else context.now.plusSeconds(3600) }
        return RecommendationTaskProposal(
            category = category,
            confidence = confidence,
            explanation = RecommendationExplanation(situation, rationale, signals.sorted()),
            intentType = intentType,
            title = title,
            description = description,
            priority = priority,
            dueAt = dueAt,
            deduplicationKey = sha256("${providerName}:${promptVersion}:${context.contextSchemaVersion}:${context.reservationId}:${category.name}:${context.reservationStatus}:${context.stayStatus}:${context.roomAssigned}:${context.adultOccupancy}:${context.childOccupancy}")
        )
    }

    private fun sha256(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
