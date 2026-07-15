package com.hotelopai.vision.infrastructure.deterministic

import com.hotelopai.vision.application.VisionAnalysisPort
import com.hotelopai.vision.application.VisionAnalysisRequest
import com.hotelopai.vision.domain.VisionAnalysisProviderMode
import com.hotelopai.vision.domain.VisionAnalysisResult
import com.hotelopai.vision.domain.VisionAnalysisStatus
import com.hotelopai.vision.domain.VisionDetectedObservation
import com.hotelopai.vision.domain.VisionProviderIds
import java.math.BigDecimal

class DeterministicVisionAnalysisProvider : VisionAnalysisPort {
    override fun analyze(request: VisionAnalysisRequest): VisionAnalysisResult {
        require(request.providerMode == VisionAnalysisProviderMode.DETERMINISTIC_FIXTURE) {
            "deterministic provider only supports fixture mode"
        }
        val fixtureKey = request.fixtureKey?.trim()?.lowercase()
            ?: throw IllegalArgumentException("fixtureKey is required for deterministic vision analysis")

        return fixtures[fixtureKey]?.toResult(request)
            ?: unknownFixture(request, fixtureKey)
    }

    private fun DeterministicFixture.toResult(request: VisionAnalysisRequest): VisionAnalysisResult =
        VisionAnalysisResult(
            analysisId = request.analysisId,
            status = VisionAnalysisStatus.COMPLETED,
            providerId = PROVIDER_ID,
            providerModel = MODEL,
            providerVersion = VERSION,
            confidence = confidence,
            observations = observations,
            detectedIssueCategory = detectedIssueCategory,
            detectedLocationHint = detectedLocationHint,
            providerMetadata = mapOf(
                "fixtureKey" to key,
                "fixtureVersion" to VERSION
            )
        )

    private fun unknownFixture(request: VisionAnalysisRequest, fixtureKey: String): VisionAnalysisResult =
        VisionAnalysisResult(
            analysisId = request.analysisId,
            status = VisionAnalysisStatus.COMPLETED,
            providerId = PROVIDER_ID,
            providerModel = MODEL,
            providerVersion = VERSION,
            confidence = confidence("0.10"),
            observations = emptyList(),
            providerMetadata = mapOf(
                "fixtureKey" to fixtureKey,
                "fixtureVersion" to VERSION,
                "result" to "unknown-fixture"
            )
        )

    private data class DeterministicFixture(
        val key: String,
        val confidence: BigDecimal,
        val observations: List<VisionDetectedObservation>,
        val detectedIssueCategory: String?,
        val detectedLocationHint: String?
    )

    companion object {
        const val PROVIDER_ID = VisionProviderIds.DETERMINISTIC_LOCAL
        const val MODEL = "deterministic-fixture"
        const val VERSION = "2026-07-15"

        private val fixtures = listOf(
            DeterministicFixture(
                key = "leaking-sink",
                confidence = confidence("0.92"),
                observations = listOf(
                    observation(0, "visible water", "Water is pooled below the sink fixture.", "0.91"),
                    observation(1, "sink fixture", "The fixture context is consistent with a bathroom or vanity sink.", "0.88")
                ),
                detectedIssueCategory = "MAINTENANCE",
                detectedLocationHint = "sink"
            ),
            DeterministicFixture(
                key = "broken-window",
                confidence = confidence("0.89"),
                observations = listOf(
                    observation(0, "damaged glass", "The window surface is represented as cracked or broken.", "0.90"),
                    observation(1, "room perimeter", "The issue appears associated with a window or exterior-facing opening.", "0.82")
                ),
                detectedIssueCategory = "MAINTENANCE",
                detectedLocationHint = "window"
            ),
            DeterministicFixture(
                key = "dirty-room",
                confidence = confidence("0.84"),
                observations = listOf(
                    observation(0, "room cleanliness", "The fixture represents a room that requires cleaning attention.", "0.84"),
                    observation(1, "housekeeping context", "The issue is categorized as housekeeping rather than maintenance.", "0.80")
                ),
                detectedIssueCategory = "HOUSEKEEPING",
                detectedLocationHint = "guest room"
            ),
            DeterministicFixture(
                key = "unknown",
                confidence = confidence("0.10"),
                observations = emptyList(),
                detectedIssueCategory = null,
                detectedLocationHint = null
            )
        ).associateBy { it.key }

        private fun observation(order: Int, label: String, description: String, confidence: String): VisionDetectedObservation =
            VisionDetectedObservation(
                order = order,
                label = label,
                description = description,
                confidence = confidence(confidence)
            )

        private fun confidence(value: String): BigDecimal =
            BigDecimal(value)
    }
}
