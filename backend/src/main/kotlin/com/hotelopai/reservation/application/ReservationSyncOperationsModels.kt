package com.hotelopai.reservation.application

import com.hotelopai.pms.application.PmsFailureCategory
import com.hotelopai.reservation.domain.DateRange
import com.hotelopai.reservation.domain.PropertyId
import com.hotelopai.shared.kernel.UuidV7Generator
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@JvmInline
value class ReservationSyncRunId(val value: UUID) {
    companion object {
        fun generate(): ReservationSyncRunId = ReservationSyncRunId(UuidV7Generator.generate())
    }
}

enum class ReservationSyncTriggerType {
    MANUAL,
    SCHEDULED,
    WEBHOOK,
    RECOVERY
}

enum class ReservationSyncRunStatus {
    REQUESTED,
    RUNNING,
    SUCCEEDED,
    PARTIALLY_SUCCEEDED,
    FAILED,
    REJECTED
}

data class ReservationPropertyScope(
    val propertyId: PropertyId,
    val hash: String,
    val label: String
)

data class ReservationSyncRun(
    val id: ReservationSyncRunId = ReservationSyncRunId.generate(),
    val providerId: String,
    val propertyScopeHash: String,
    val propertyScopeLabel: String,
    val requestedDateRange: DateRange,
    val triggerType: ReservationSyncTriggerType,
    val status: ReservationSyncRunStatus,
    val startedAt: Instant,
    val completedAt: Instant? = null,
    val fetchedCount: Int = 0,
    val createdCount: Int = 0,
    val updatedCount: Int = 0,
    val unchangedCount: Int = 0,
    val staleCount: Int = 0,
    val conflictCount: Int = 0,
    val boundedPageCount: Int = 0,
    val failureCategory: PmsFailureCategory? = null,
    val actorUserId: UUID? = null,
    val createdAt: Instant = startedAt,
    val updatedAt: Instant = startedAt,
    val version: Long = 0
) {
    init {
        require(providerId.isNotBlank()) { "provider id must not be blank" }
        require(propertyScopeHash.isNotBlank()) { "property scope hash must not be blank" }
        require(propertyScopeLabel.isNotBlank()) { "property scope label must not be blank" }
        require(listOf(fetchedCount, createdCount, updatedCount, unchangedCount, staleCount, conflictCount, boundedPageCount).all { it >= 0 }) {
            "sync run counters must not be negative"
        }
        require(version >= 0) { "sync run version must not be negative" }
    }
}

data class ReservationSyncRunPage(
    val content: List<ReservationSyncRun>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int
)

data class ReservationSyncRunFilter(
    val providerId: String? = null,
    val status: ReservationSyncRunStatus? = null,
    val page: Int = 0,
    val size: Int = 20
)

data class ReservationSyncOperationRequest(
    val startDate: LocalDate,
    val endDate: LocalDate,
    val triggerType: ReservationSyncTriggerType = ReservationSyncTriggerType.MANUAL
) {
    fun dateRange(): DateRange = DateRange(startDate, endDate)
}

data class ReservationSyncStateView(
    val providerId: String,
    val propertyScopeLabel: String,
    val status: ReservationSyncStatus,
    val lastAttemptedAt: Instant?,
    val lastSuccessfulAt: Instant?,
    val lastFailureCategory: PmsFailureCategory?,
    val windowStartDate: LocalDate?,
    val windowEndDate: LocalDate?,
    val fetchedCount: Int,
    val createdCount: Int,
    val updatedCount: Int,
    val unchangedCount: Int,
    val staleCount: Int,
    val conflictCount: Int
)

interface ReservationSyncRunRepository {
    fun save(run: ReservationSyncRun): ReservationSyncRun
    fun findById(id: ReservationSyncRunId): ReservationSyncRun?
    fun find(filter: ReservationSyncRunFilter): ReservationSyncRunPage
    fun deleteCompletedBefore(cutoff: Instant, limit: Int = Int.MAX_VALUE): Int
}

interface ReservationSyncRunLockRepository {
    fun acquire(
        providerId: String,
        propertyScopeHash: String,
        dateRange: DateRange,
        runId: ReservationSyncRunId,
        lockedUntil: Instant,
        now: Instant
    ): ReservationSyncRunLockResult

    fun release(runId: ReservationSyncRunId)
}

sealed class ReservationSyncRunLockResult {
    data object Acquired : ReservationSyncRunLockResult()
    data class Rejected(val conflictingRunId: ReservationSyncRunId) : ReservationSyncRunLockResult()
}

interface ReservationSyncOperationsAuditSink {
    fun record(event: ReservationSyncOperationsAuditEvent)
}

data class ReservationSyncOperationsAuditEvent(
    val actorUserId: UUID?,
    val providerId: String,
    val runId: UUID?,
    val action: ReservationSyncOperationsAuditAction,
    val outcome: String,
    val occurredAt: Instant,
    val failureCategory: PmsFailureCategory? = null
)

enum class ReservationSyncOperationsAuditAction {
    SYNC_REQUESTED,
    SYNC_STARTED,
    SYNC_COMPLETED,
    SYNC_REJECTED,
    HISTORY_INSPECTED,
    RETENTION_CLEANUP_EXECUTED,
    SCHEDULER_PAUSED,
    SCHEDULER_RESUMED,
    WEBHOOK_ACCEPTED,
    WEBHOOK_REJECTED,
    WEBHOOK_DUPLICATE,
    WEBHOOK_PROCESSING_STARTED,
    WEBHOOK_PROCESSING_COMPLETED,
    WEBHOOK_RETRY_REQUESTED,
    WEBHOOK_CLEANUP_EXECUTED,
    WEBHOOK_SCHEDULER_STARTED,
    WEBHOOK_SCHEDULER_COMPLETED,
    WEBHOOK_SCHEDULER_PAUSED,
    WEBHOOK_SCHEDULER_RESUMED,
    WEBHOOK_RUN_NOW_REQUESTED,
    WEBHOOK_DEAD_LETTER_CREATED,
    WEBHOOK_DEAD_LETTER_RETRY_REQUESTED
}
