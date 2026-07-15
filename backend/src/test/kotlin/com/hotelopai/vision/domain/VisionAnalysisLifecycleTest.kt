package com.hotelopai.vision.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

class VisionAnalysisLifecycleTest {
    @Test
    fun `pending analysis can complete fail or become ineligible`() {
        val completed = pending().complete(result(), NOW.plusSeconds(1))
        val failed = pending(UUID.fromString("00000000-0000-7000-8000-000000000011"))
            .fail("PROVIDER_FAILURE", "provider failed", NOW.plusSeconds(1))
        val ineligible = pending(UUID.fromString("00000000-0000-7000-8000-000000000012"))
            .markIneligible("PROVIDER_MEDIA_UNAVAILABLE", "no media", NOW.plusSeconds(1))

        assertThat(completed.status).isEqualTo(VisionAnalysisStatus.COMPLETED)
        assertThat(completed.completedAt).isEqualTo(NOW.plusSeconds(1))
        assertThat(failed.status).isEqualTo(VisionAnalysisStatus.FAILED)
        assertThat(failed.failedAt).isEqualTo(NOW.plusSeconds(1))
        assertThat(ineligible.status).isEqualTo(VisionAnalysisStatus.INELIGIBLE)
        assertThat(ineligible.failedAt).isEqualTo(NOW.plusSeconds(1))
    }

    @Test
    fun `invalid terminal transitions are rejected without explicit retry`() {
        val completed = pending().complete(result())
        val failed = pending(UUID.fromString("00000000-0000-7000-8000-000000000013"))
            .fail("PROVIDER_FAILURE", "provider failed")
        val ineligible = pending(UUID.fromString("00000000-0000-7000-8000-000000000014"))
            .markIneligible("PROVIDER_MEDIA_UNAVAILABLE", "no media")

        assertThrows(IllegalArgumentException::class.java) { completed.fail("NOPE", "nope") }
        assertThrows(IllegalArgumentException::class.java) { completed.markIneligible("NOPE", "nope") }
        assertThrows(IllegalArgumentException::class.java) { failed.complete(result()) }
        assertThrows(IllegalArgumentException::class.java) { ineligible.complete(result()) }
    }

    @Test
    fun `explicit retry from failed or ineligible increments attempt count`() {
        val failedRetry = pending()
            .fail("PROVIDER_FAILURE", "provider failed")
            .retry(NOW.plusSeconds(2))
        val ineligibleRetry = pending(UUID.fromString("00000000-0000-7000-8000-000000000015"))
            .markIneligible("PROVIDER_MEDIA_UNAVAILABLE", "no media")
            .retry(NOW.plusSeconds(2))

        assertThat(failedRetry.status).isEqualTo(VisionAnalysisStatus.PENDING)
        assertThat(failedRetry.attemptCount).isEqualTo(2)
        assertThat(failedRetry.failureCode).isNull()
        assertThat(ineligibleRetry.status).isEqualTo(VisionAnalysisStatus.PENDING)
        assertThat(ineligibleRetry.attemptCount).isEqualTo(2)
    }

    @Test
    fun `duplicate terminal transition mutation is rejected`() {
        val completed = pending().complete(result())
        val failed = pending(UUID.fromString("00000000-0000-7000-8000-000000000016"))
            .fail("PROVIDER_FAILURE", "provider failed")

        assertThrows(IllegalArgumentException::class.java) { completed.complete(result()) }
        assertThrows(IllegalArgumentException::class.java) { failed.fail("PROVIDER_FAILURE", "provider failed") }
    }

    private fun pending(id: UUID = UUID.fromString("00000000-0000-7000-8000-000000000010")): VisionAnalysis =
        VisionAnalysis(
            id = id,
            attachmentId = UUID.fromString("00000000-0000-7000-8000-000000000020"),
            conversationId = "conversation-test",
            hotelId = "hotel-test",
            userId = "user-test",
            status = VisionAnalysisStatus.PENDING,
            providerId = "deterministic-local",
            idempotencyKey = "idem-test-${id.toString().takeLast(4)}",
            requestedAt = NOW,
            createdAt = NOW,
            updatedAt = NOW
        )

    private fun result(): VisionAnalysisResult =
        VisionAnalysisResult(
            analysisId = UUID.fromString("00000000-0000-7000-8000-000000000010"),
            status = VisionAnalysisStatus.COMPLETED,
            providerId = "deterministic-local",
            providerModel = "deterministic-fixture",
            providerVersion = "test",
            confidence = BigDecimal("0.80"),
            observations = listOf(
                VisionDetectedObservation(0, "label", "description", BigDecimal("0.80"))
            )
        )

    companion object {
        private val NOW = Instant.parse("2026-07-15T12:00:00Z")
    }
}
