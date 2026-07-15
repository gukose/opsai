package com.hotelopai.vision.domain

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class VisionAnalysis(
    val id: UUID,
    val attachmentId: UUID,
    val conversationId: String,
    val hotelId: String,
    val userId: String,
    val status: VisionAnalysisStatus,
    val providerId: String,
    val providerModel: String? = null,
    val providerVersion: String? = null,
    val confidence: BigDecimal? = null,
    val observations: List<VisionDetectedObservation> = emptyList(),
    val detectedIssueCategory: String? = null,
    val detectedLocationHint: String? = null,
    val providerMetadata: Map<String, String> = emptyMap(),
    val failureCode: String? = null,
    val failureMessage: String? = null,
    val idempotencyKey: String,
    val attemptCount: Int = 1,
    val requestedAt: Instant,
    val completedAt: Instant? = null,
    val failedAt: Instant? = null,
    val createdAt: Instant = requestedAt,
    val updatedAt: Instant = requestedAt
) {
    init {
        require(idempotencyKey.isNotBlank()) { "idempotencyKey must not be blank" }
        require(providerId.isNotBlank()) { "providerId must not be blank" }
        require(attemptCount >= 1) { "attemptCount must be at least 1" }
        require(confidence == null || confidence in MIN_CONFIDENCE..MAX_CONFIDENCE) {
            "confidence must be between 0 and 1"
        }
    }

    fun complete(result: VisionAnalysisResult, now: Instant = Instant.now()): VisionAnalysis {
        require(status == VisionAnalysisStatus.PENDING) { "Only PENDING analysis can complete" }
        require(result.status == VisionAnalysisStatus.COMPLETED) { "completed result must have COMPLETED status" }
        val boundedConfidence = result.confidence
        require(boundedConfidence in MIN_CONFIDENCE..MAX_CONFIDENCE) { "confidence must be between 0 and 1" }

        return copy(
            status = VisionAnalysisStatus.COMPLETED,
            providerId = result.providerId,
            providerModel = result.providerModel,
            providerVersion = result.providerVersion,
            confidence = boundedConfidence,
            observations = result.observations,
            detectedIssueCategory = result.detectedIssueCategory,
            detectedLocationHint = result.detectedLocationHint,
            providerMetadata = result.providerMetadata,
            failureCode = null,
            failureMessage = null,
            completedAt = now,
            failedAt = null,
            updatedAt = now
        )
    }

    fun fail(code: String, message: String, now: Instant = Instant.now()): VisionAnalysis {
        require(status == VisionAnalysisStatus.PENDING) { "Only PENDING analysis can fail" }
        require(code.isNotBlank()) { "failure code must not be blank" }
        require(message.isNotBlank()) { "failure message must not be blank" }

        return copy(
            status = VisionAnalysisStatus.FAILED,
            confidence = null,
            observations = emptyList(),
            detectedIssueCategory = null,
            detectedLocationHint = null,
            failureCode = code,
            failureMessage = message,
            completedAt = null,
            failedAt = now,
            updatedAt = now
        )
    }

    fun markIneligible(code: String, message: String, now: Instant = Instant.now()): VisionAnalysis {
        require(status == VisionAnalysisStatus.PENDING) { "Only PENDING analysis can be marked ineligible" }
        require(code.isNotBlank()) { "failure code must not be blank" }
        require(message.isNotBlank()) { "failure message must not be blank" }

        return copy(
            status = VisionAnalysisStatus.INELIGIBLE,
            confidence = null,
            observations = emptyList(),
            detectedIssueCategory = null,
            detectedLocationHint = null,
            failureCode = code,
            failureMessage = message,
            completedAt = null,
            failedAt = now,
            updatedAt = now
        )
    }

    fun retry(now: Instant = Instant.now()): VisionAnalysis {
        require(status == VisionAnalysisStatus.FAILED || status == VisionAnalysisStatus.INELIGIBLE) {
            "Only FAILED or INELIGIBLE analysis can be retried"
        }

        return copy(
            status = VisionAnalysisStatus.PENDING,
            confidence = null,
            observations = emptyList(),
            detectedIssueCategory = null,
            detectedLocationHint = null,
            failureCode = null,
            failureMessage = null,
            attemptCount = attemptCount + 1,
            requestedAt = now,
            completedAt = null,
            failedAt = null,
            createdAt = now,
            updatedAt = now
        )
    }

    companion object {
        val MIN_CONFIDENCE: BigDecimal = BigDecimal.ZERO
        val MAX_CONFIDENCE: BigDecimal = BigDecimal.ONE
    }
}

enum class VisionAnalysisStatus {
    PENDING,
    COMPLETED,
    FAILED,
    INELIGIBLE
}

enum class VisionAnalysisProviderMode {
    REAL_PROVIDER,
    DETERMINISTIC_FIXTURE
}

data class VisionDetectedObservation(
    val order: Int,
    val label: String,
    val description: String,
    val confidence: BigDecimal
) {
    init {
        require(order >= 0) { "observation order must be non-negative" }
        require(label.isNotBlank()) { "observation label must not be blank" }
        require(confidence in VisionAnalysis.MIN_CONFIDENCE..VisionAnalysis.MAX_CONFIDENCE) {
            "observation confidence must be between 0 and 1"
        }
    }
}

data class VisionAnalysisResult(
    val analysisId: UUID,
    val status: VisionAnalysisStatus,
    val providerId: String,
    val providerModel: String? = null,
    val providerVersion: String? = null,
    val confidence: BigDecimal,
    val observations: List<VisionDetectedObservation> = emptyList(),
    val detectedIssueCategory: String? = null,
    val detectedLocationHint: String? = null,
    val providerMetadata: Map<String, String> = emptyMap()
) {
    init {
        require(providerId.isNotBlank()) { "providerId must not be blank" }
        require(confidence in BigDecimal.ZERO..BigDecimal.ONE) { "confidence must be between 0 and 1" }
    }
}

object VisionProviderIds {
    const val DETERMINISTIC_LOCAL = "deterministic-local"
    const val UNAVAILABLE = "unavailable"
}
