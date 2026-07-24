package com.hotelopai.reservation.application

import com.hotelopai.pms.application.PmsFailureCategory
import com.hotelopai.shared.kernel.UuidV7Generator
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@JvmInline
value class ReservationWebhookInboxId(val value: UUID) {
    companion object {
        fun generate(): ReservationWebhookInboxId = ReservationWebhookInboxId(UuidV7Generator.generate())
    }
}

enum class ReservationWebhookStatus {
    RECEIVED,
    VERIFIED,
    REJECTED,
    DUPLICATE,
    PROCESSING,
    SUCCEEDED,
    FAILED,
    IGNORED,
    DEAD_LETTER
}

enum class ReservationWebhookEventCategory {
    RESERVATION_CREATED,
    RESERVATION_CHANGED,
    RESERVATION_CANCELLED,
    GUEST_CHECKED_IN,
    GUEST_CHECKED_OUT,
    ROOM_ASSIGNMENT_CHANGED,
    HEALTHCHECK,
    UNSUPPORTED
}

data class ReservationWebhookEvent(
    val providerId: String,
    val providerEventId: String,
    val category: ReservationWebhookEventCategory,
    val propertyScopeHash: String,
    val propertyScopeLabel: String,
    val rawPropertyReference: String?,
    val externalEntityHash: String?,
    val providerEventTimestamp: Instant?,
    val receivedAt: Instant,
    val payloadFingerprint: String,
    val affectedDate: LocalDate?,
    val safeMetadata: Map<String, String> = emptyMap()
)

data class ReservationWebhookInboxRecord(
    val id: ReservationWebhookInboxId = ReservationWebhookInboxId.generate(),
    val providerId: String,
    val providerEventId: String,
    val eventCategory: ReservationWebhookEventCategory,
    val propertyScopeHash: String,
    val propertyScopeLabel: String,
    val externalEntityHash: String?,
    val providerEventTimestamp: Instant?,
    val receivedAt: Instant,
    val status: ReservationWebhookStatus,
    val failureCategory: PmsFailureCategory? = null,
    val attemptCount: Int = 0,
    val nextAttemptAt: Instant? = null,
    val processingStartedAt: Instant? = null,
    val completedAt: Instant? = null,
    val payloadFingerprint: String,
    val safeMetadata: Map<String, String> = emptyMap(),
    val syncRunId: ReservationSyncRunId? = null,
    val createdAt: Instant = receivedAt,
    val updatedAt: Instant = receivedAt,
    val version: Long = 0
)

data class ReservationWebhookInboxFilter(
    val providerId: String? = null,
    val status: ReservationWebhookStatus? = null,
    val page: Int = 0,
    val size: Int = 20
)

data class ReservationWebhookBacklogCounts(
    val byStatus: Map<ReservationWebhookStatus, Long>
) {
    val eligibleCount: Long =
        (byStatus[ReservationWebhookStatus.VERIFIED] ?: 0L) + (byStatus[ReservationWebhookStatus.FAILED] ?: 0L)
    val deadLetterCount: Long = byStatus[ReservationWebhookStatus.DEAD_LETTER] ?: 0L
}

data class ReservationWebhookInboxPage(
    val content: List<ReservationWebhookInboxRecord>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int
)

interface ReservationWebhookInboxRepository {
    fun insertIfAbsent(record: ReservationWebhookInboxRecord): ReservationWebhookInsertResult
    fun save(record: ReservationWebhookInboxRecord): ReservationWebhookInboxRecord
    fun findById(id: ReservationWebhookInboxId): ReservationWebhookInboxRecord?
    fun find(filter: ReservationWebhookInboxFilter): ReservationWebhookInboxPage
    fun claimReady(limit: Int, now: Instant, maxAttempts: Int): List<ReservationWebhookInboxRecord>
    fun recoverAbandoned(cutoff: Instant, now: Instant): Int
    fun deleteCompletedBefore(completedCutoff: Instant, rejectedCutoff: Instant, deadLetterCutoff: Instant, limit: Int): Int
    fun backlogCounts(): ReservationWebhookBacklogCounts
}

sealed class ReservationWebhookInsertResult {
    data class Inserted(val record: ReservationWebhookInboxRecord) : ReservationWebhookInsertResult()
    data class Duplicate(val existing: ReservationWebhookInboxRecord) : ReservationWebhookInsertResult()
}

interface ReservationWebhookAdapter {
    val providerId: String
    fun validateConfiguration(): ReservationWebhookVerificationResult.Rejected? = null
    fun verifyAndExtract(request: ReservationWebhookRequest): ReservationWebhookVerificationResult
}

data class ReservationWebhookRequest(
    val providerId: String,
    val contentType: String?,
    val queryToken: String?,
    val headers: Map<String, String>,
    val body: ByteArray,
    val receivedAt: Instant
)

sealed class ReservationWebhookVerificationResult {
    data class Verified(val event: ReservationWebhookEvent) : ReservationWebhookVerificationResult()
    data class Rejected(val failureCategory: PmsFailureCategory, val reasonCode: String) : ReservationWebhookVerificationResult()
}

data class ReservationWebhookProcessingSummary(
    val processedCount: Int,
    val succeededCount: Int,
    val failedCount: Int,
    val ignoredCount: Int,
    val retriedCount: Int,
    val deadLetterCount: Int = 0
)
