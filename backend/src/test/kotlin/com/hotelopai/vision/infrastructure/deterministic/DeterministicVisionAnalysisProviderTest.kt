package com.hotelopai.vision.infrastructure.deterministic

import com.hotelopai.vision.application.VisionAnalysisPort
import com.hotelopai.vision.application.VisionAnalysisRequest
import com.hotelopai.vision.domain.VisionAnalysisProviderMode
import com.hotelopai.vision.domain.VisionAnalysisStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

class DeterministicVisionAnalysisProviderTest {
    private val provider = DeterministicVisionAnalysisProvider()

    @Test
    fun `deterministic provider implements vision analysis port`() {
        assertThat(provider).isInstanceOf(VisionAnalysisPort::class.java)
    }

    @Test
    fun `vision analysis request contains only server derived and explicit safe fields`() {
        val fields = VisionAnalysisRequest::class.java.declaredFields.map { it.name }.toSet()

        assertThat(fields).contains(
            "analysisId",
            "attachmentId",
            "conversationId",
            "hotelId",
            "userId",
            "providerMode",
            "idempotencyKey",
            "requestedAt",
            "storageReference",
            "fixtureKey"
        )
        assertThat(fields).doesNotContain(
            "localReference",
            "deviceUri",
            "fileUri",
            "clientUrl",
            "mediaUrl",
            "imageUrl",
            "base64",
            "rawBinary",
            "rawBytes",
            "providerSecret",
            "apiKey",
            "clientHotelId",
            "clientUserId"
        )
    }

    @Test
    fun `known fixture returns stable completed semantic output`() {
        val first = provider.analyze(request("leaking-sink"))
        val second = provider.analyze(request("leaking-sink"))

        assertThat(first.status).isEqualTo(VisionAnalysisStatus.COMPLETED)
        assertThat(first.analysisId).isEqualTo(second.analysisId)
        assertThat(first.providerId).isEqualTo(DeterministicVisionAnalysisProvider.PROVIDER_ID)
        assertThat(first.providerModel).isEqualTo(DeterministicVisionAnalysisProvider.MODEL)
        assertThat(first.providerVersion).isEqualTo(DeterministicVisionAnalysisProvider.VERSION)
        assertThat(first.confidence).isBetween(BigDecimal.ZERO, BigDecimal.ONE)
        assertThat(first.observations.map { it.order }).containsExactly(0, 1)
        assertThat(first.observations.map { it.label }).containsExactly("visible water", "sink fixture")
        assertThat(first.detectedIssueCategory).isEqualTo("MAINTENANCE")
        assertThat(first.detectedLocationHint).isEqualTo("sink")
        assertThat(second.observations).isEqualTo(first.observations)
        assertThat(second.detectedIssueCategory).isEqualTo(first.detectedIssueCategory)
        assertThat(second.detectedLocationHint).isEqualTo(first.detectedLocationHint)
    }

    @Test
    fun `repeated fixture call does not reorder observations and ignores storage reference`() {
        val withStorageReference = provider.analyze(request("broken-window", storageReference = "server-owned-future-ref"))
        val withoutStorageReference = provider.analyze(request("broken-window", storageReference = null))

        assertThat(withStorageReference.observations.map { it.description })
            .containsExactlyElementsOf(withoutStorageReference.observations.map { it.description })
        assertThat(withStorageReference.providerMetadata).containsEntry("fixtureKey", "broken-window")
        assertThat(withStorageReference.providerMetadata).doesNotContainKey("storageReference")
    }

    @Test
    fun `unknown fixture returns controlled low confidence no result`() {
        val result = provider.analyze(request("not-a-real-fixture"))

        assertThat(result.status).isEqualTo(VisionAnalysisStatus.COMPLETED)
        assertThat(result.confidence).isLessThan(BigDecimal("0.20"))
        assertThat(result.observations).isEmpty()
        assertThat(result.detectedIssueCategory).isNull()
        assertThat(result.detectedLocationHint).isNull()
        assertThat(result.providerMetadata).containsEntry("result", "unknown-fixture")
    }

    private fun request(fixtureKey: String, storageReference: String? = null): VisionAnalysisRequest =
        VisionAnalysisRequest(
            analysisId = UUID.fromString("00000000-0000-7000-8000-000000000001"),
            attachmentId = UUID.fromString("00000000-0000-7000-8000-000000000002"),
            conversationId = "conversation-test",
            hotelId = "hotel-test",
            userId = "user-test",
            providerMode = VisionAnalysisProviderMode.DETERMINISTIC_FIXTURE,
            idempotencyKey = "idem-test",
            requestedAt = Instant.parse("2026-07-15T12:00:00Z"),
            storageReference = storageReference,
            fixtureKey = fixtureKey
        )
}
