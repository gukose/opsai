package com.hotelopai.reservation.recommendation

import org.springframework.stereotype.Component

@Component
class RecommendationPromptFactory {
    fun create(
        context: OutboundRecommendationContext,
        templateId: String,
        version: String,
        maxRecommendations: Int
    ): RecommendationPrompt =
        RecommendationPrompt(
            templateId = templateId,
            version = version,
            systemInstructions = SYSTEM_INSTRUCTIONS,
            recommendationTemplate = TEMPLATE,
            context = context,
            outputSchema = RecommendationOutputSchema(maxRecommendations = maxRecommendations)
        )

    companion object {
        private const val SYSTEM_INSTRUCTIONS =
            "You recommend hotel operations tasks from sanitized reservation signals only."

        private const val TEMPLATE =
            "Return structured recommendations with category, priority, confidence, explanation, supporting signals, title, and summary."
    }
}

@Component
class StructuredRecommendationResponseValidator(
    private val properties: ReservationTaskRecommendationProperties
) {
    fun validate(response: StructuredRecommendationResponse): StructuredRecommendationResponse {
        require(response.recommendations.size <= properties.maxRecommendationsPerReservation) {
            "Recommendation provider returned too many recommendations."
        }
        response.recommendations.forEach { item ->
            require(item.proposedTaskTitle.isNotBlank() && item.proposedTaskTitle.length <= MAX_TITLE_LENGTH) {
                "Recommendation provider returned an invalid task title."
            }
            require(item.proposedTaskSummary.isNotBlank() && item.proposedTaskSummary.length <= MAX_SUMMARY_LENGTH) {
                "Recommendation provider returned an invalid task summary."
            }
            require(item.explanation.situation.length <= MAX_EXPLANATION_LENGTH) {
                "Recommendation provider returned an oversized explanation."
            }
            require(item.explanation.rationale.length <= MAX_EXPLANATION_LENGTH) {
                "Recommendation provider returned an oversized rationale."
            }
            require(item.explanation.supportingSignals.size <= MAX_SIGNALS) {
                "Recommendation provider returned too many supporting signals."
            }
        }
        return response
    }

    companion object {
        private const val MAX_TITLE_LENGTH = 120
        private const val MAX_SUMMARY_LENGTH = 500
        private const val MAX_EXPLANATION_LENGTH = 500
        private const val MAX_SIGNALS = 8
    }
}
